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
package io.openmessaging.benchmark.driver.rabbitmq;

import com.rabbitmq.client.AlreadyClosedException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMqBenchmarkDriver implements BenchmarkDriver {

    private RabbitMqConfig config;
    private final AtomicInteger uriIndex = new AtomicInteger();
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, RabbitMqConfig.class);
    }

    @Override
    public void close() {
        for(Iterator<Map.Entry<String, Connection>> it = connections.entrySet().iterator(); it.hasNext(); ) {
            Connection connection = it.next().getValue();
            try {
                connection.close();
            } catch (AlreadyClosedException e) {
                log.warn("Connection already closed", e);
            } catch (Exception e) {
                log.error("Couldn't close connection", e);
            }
            it.remove();
        }

    }

    @Override
    public String getTopicNamePrefix() {
        // Do a round-robin on AMQP URIs
        URI configUri = URI.create(config.amqpUris.get(uriIndex.getAndIncrement() % config.amqpUris.size()));
        URI topicUri = configUri.resolve(configUri.getRawPath() + "?exchange=test-exchange");
        return topicUri.toString();
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (partitions != 1) {
            throw new IllegalArgumentException("Cannot create topic with partitions in RabbitMQ");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);

        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        CompletableFuture<BenchmarkProducer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                String uri = topic.split("\\?")[0];
                Connection connection = getOrCreateConnection(uri);
                Channel channel = connection.createChannel();
                String exchange = getExchangeName(topic);
                channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true);
                channel.confirmSelect();
                future.complete(new RabbitMqBenchmarkProducer(channel, exchange, config.messagePersistence));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
            ConsumerCallback consumerCallback) {

        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                String uri = topic.split("\\?")[0];
                Connection connection = getOrCreateConnection(uri);
                Channel channel = connection.createChannel();
                String exchange = getExchangeName(topic);
                String queueName = exchange + "-" + subscriptionName;
                channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true);
                // Create the queue
                channel.queueDeclare(queueName, true, false, false, Collections.emptyMap());
                channel.queueBind(queueName, exchange, "");
                future.complete(new RabbitMqBenchmarkConsumer(channel, queueName, consumerCallback));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private String getExchangeName(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = decoder.parameters();

        if(!parameters.containsKey("exchange")) {
            throw new IllegalArgumentException("Missing exchange param");
        }
        return parameters.get("exchange").get(0);
    }

    private Connection getOrCreateConnection(String uri) {
        return connections.computeIfAbsent(uri, uriKey -> {
            try {
                ConnectionFactory connectionFactory = new ConnectionFactory();
                connectionFactory.setAutomaticRecoveryEnabled(true);
                connectionFactory.setUri(uri);
                return connectionFactory.newConnection();
            } catch (Exception e) {
                throw new RuntimeException("Couldn't establish connection", e);
            }
        });
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger log = LoggerFactory.getLogger(RabbitMqBenchmarkDriver.class);
}
