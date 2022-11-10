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
package io.openmessaging.benchmark.driver.nats;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.ErrorListener;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.support.JsonUtils;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsBenchmarkDriver implements BenchmarkDriver {
    private NatsConfig config;
    private Connection connection;
    private JetStream jetStream;
    private JetStreamManagement jetStreamManagement;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger)
            throws IOException, InterruptedException {
        config = mapper.readValue(configurationFile, NatsConfig.class);
        log.info("read config file," + config.toString());
        this.connection =
                Nats.connect(
                        new Options.Builder()
                                .server(config.natsHostUrl)
                                .maxReconnects(5)
                                .reconnectWait(Duration.ofSeconds(1))
                                .connectionTimeout(Duration.ofSeconds(5))
                                .pingInterval(Duration.ofSeconds(60))
                                .errorListener(
                                        new ErrorListener() {
                                            @Override
                                            public void errorOccurred(Connection conn, String error) {
                                                log.error("Error on connection {}: {}", conn, error);
                                            }

                                            @Override
                                            public void exceptionOccurred(Connection conn, Exception exp) {
                                                log.error("Exception on connection {}", conn, exp);
                                            }
                                        })
                                .build());
        this.jetStream = connection.jetStream();
        this.jetStreamManagement = connection.jetStreamManagement();
    }

    @Override
    public String getTopicNamePrefix() {
        return "Nats-benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        try {
            JetStreamManagement jsm = connection.jetStreamManagement();
            StreamInfo streamInfo =
                    jsm.addStream(
                            StreamConfiguration.builder()
                                    .name(topic)
                                    .subjects(topic)
                                    .storageType(StorageType.File)
                                    .replicas(config.replicationFactor)
                                    .build());
            log.info("Created stream {} -- {}", topic, JsonUtils.getFormatted(streamInfo));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.completedFuture(new NatsBenchmarkProducer(jetStream, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {

        ConsumerConfiguration cc =
                ConsumerConfiguration.builder()
                        .durable("durable-" + subscriptionName)
                        .deliverSubject("delivery-subject-" + subscriptionName)
                        .deliverGroup("group-" + subscriptionName)
                        .build();
        PushSubscribeOptions pso = PushSubscribeOptions.builder().configuration(cc).build();

        try {
            jetStreamManagement.addOrUpdateConsumer(topic, cc);

            Dispatcher dispatcher = connection.createDispatcher();

            JetStreamSubscription sub =
                    jetStream.subscribe(
                            topic,
                            dispatcher,
                            (Message msg) -> {
                                long publishTimestamp = readLongFromBytes(msg.getData());
                                consumerCallback.messageReceived(msg.getData(), publishTimestamp);
                                msg.ack();
                            },
                            false,
                            pso);
            return CompletableFuture.completedFuture(new NatsBenchmarkConsumer());
        } catch (Exception e) {
            CompletableFuture<BenchmarkConsumer> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    @Override
    public void close() throws Exception {
        this.connection.close();
    }

    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkDriver.class);
    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static long readLongFromBytes(final byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
