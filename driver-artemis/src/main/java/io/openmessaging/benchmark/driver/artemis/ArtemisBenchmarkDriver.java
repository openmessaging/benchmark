/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.artemis;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtemisBenchmarkDriver implements BenchmarkDriver {
    private ArtemisConfig config;

    private ClientSessionFactory sessionFactory;
    private ClientSession session;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        this.config = readConfig(configurationFile);
        log.info("ActiveMQ Artemis driver configuration: {}", writer.writeValueAsString(config));
        try {
            ServerLocator serverLocator = ActiveMQClient.createServerLocator(config.brokerAddress);
            // confirmation window size is in bytes, set to 1MB
            serverLocator.setConfirmationWindowSize(1024 * 1024);
            // use asynchronous sending of messages where SendAcknowledgementHandler reports
            // success/failure
            serverLocator.setBlockOnDurableSend(false);
            serverLocator.setBlockOnNonDurableSend(false);
            // use async acknowledgement
            serverLocator.setBlockOnAcknowledge(false);

            sessionFactory = serverLocator.createSessionFactory();
            session = sessionFactory.createSession();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return "test";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (partitions != 1) {
            future.completeExceptionally(
                    new IllegalArgumentException("Partitions are not supported in Artemis"));
            return future;
        }

        ForkJoinPool.commonPool()
                .submit(
                        () -> {
                            try {
                                session.createAddress(
                                        SimpleString.toSimpleString(topic), RoutingType.MULTICAST, true);
                                future.complete(null);
                            } catch (ActiveMQException e) {
                                future.completeExceptionally(e);
                            }
                        });

        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        try {
            return CompletableFuture.completedFuture(new ArtemisBenchmarkProducer(topic, sessionFactory));
        } catch (ActiveMQException e) {
            CompletableFuture<BenchmarkProducer> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool()
                .submit(
                        () -> {
                            try {
                                String queueName = topic + "-" + subscriptionName;
                                BenchmarkConsumer consumer =
                                        new ArtemisBenchmarkConsumer(
                                                topic, queueName, sessionFactory, consumerCallback);
                                future.complete(consumer);
                            } catch (ActiveMQException e) {
                                future.completeExceptionally(e);
                            }
                        });

        return future;
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down ActiveMQ Artemis benchmark driver");

        if (session != null) {
            session.close();
        }

        if (sessionFactory != null) {
            sessionFactory.close();
        }

        log.info("ActiveMQ Artemis benchmark driver successfully shut down");
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static ArtemisConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, ArtemisConfig.class);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(ArtemisBenchmarkProducer.class);
}
