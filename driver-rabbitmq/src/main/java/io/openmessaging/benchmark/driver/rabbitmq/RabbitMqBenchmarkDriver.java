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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.ResourceCreator;
import io.openmessaging.benchmark.driver.ResourceCreator.CreationResult;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMqBenchmarkDriver implements BenchmarkDriver {

    private RabbitMqConfig config;
    private final AtomicInteger uriIndex = new AtomicInteger();
    /**
     * Map of client's primary broker to the connection -- the connection may still be able to fall
     * back to secondary brokers.
     */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, RabbitMqConfig.class);
    }

    @Override
    public void close() {
        for (Iterator<Map.Entry<String, Connection>> it = connections.entrySet().iterator();
                it.hasNext(); ) {
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
        // Distribute topics by performing a round-robin on AMQP URIs
        String primaryBrokerUri =
                config.amqpUris.get(uriIndex.getAndIncrement() % config.amqpUris.size());
        URI configUri = URI.create(primaryBrokerUri);
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
        ForkJoinPool.commonPool()
                .execute(
                        () -> {
                            try {
                                String uri = topic.split("\\?")[0];
                                Connection connection = getOrCreateConnection(uri);
                                Channel channel = connection.createChannel();
                                String exchange = getExchangeName(topic);
                                channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true);
                                channel.confirmSelect();
                                future.complete(
                                        new RabbitMqBenchmarkProducer(channel, exchange, config.messagePersistence));
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        });
        return future;
    }

    @Override
    public CompletableFuture<List<BenchmarkProducer>> createProducers(List<ProducerInfo> producers) {
        return new ResourceCreator<ProducerInfo, BenchmarkProducer>(
                        "producer",
                        config.producerCreationBatchSize,
                        config.producerCreationDelay,
                        ps -> ps.stream().collect(toMap(p -> p, p -> createProducer(p.getTopic()))),
                        fc -> {
                            try {
                                return new CreationResult<>(fc.get(), true);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } catch (ExecutionException e) {
                                log.debug(e.getMessage());
                                return new CreationResult<>(null, false);
                            }
                        })
                .create(producers);
    }

    @Override
    public CompletableFuture<List<BenchmarkConsumer>> createConsumers(List<ConsumerInfo> consumers) {
        return new ResourceCreator<ConsumerInfo, BenchmarkConsumer>(
                        "consumer",
                        config.consumerCreationBatchSize,
                        config.consumerCreationDelay,
                        cs ->
                                cs.stream()
                                        .collect(
                                                toMap(
                                                        c -> c,
                                                        c ->
                                                                createConsumer(
                                                                        c.getTopic(),
                                                                        c.getSubscriptionName(),
                                                                        c.getConsumerCallback()))),
                        fc -> {
                            try {
                                return new CreationResult<>(fc.get(), true);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } catch (ExecutionException e) {
                                log.debug(e.getMessage());
                                return new CreationResult<>(null, false);
                            }
                        })
                .create(consumers);
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {

        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool()
                .execute(
                        () -> {
                            try {
                                String uri = topic.split("\\?")[0];
                                Connection connection = getOrCreateConnection(uri);
                                Channel channel = connection.createChannel();
                                String exchange = getExchangeName(topic);
                                String queueName = exchange + "-" + subscriptionName;
                                channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true);
                                // Create the queue
                                channel.queueDeclare(
                                        queueName, true, false, false, config.queueType.queueOptions());
                                channel.queueBind(queueName, exchange, "");
                                future.complete(
                                        new RabbitMqBenchmarkConsumer(channel, queueName, consumerCallback));
                            } catch (IOException e) {
                                future.completeExceptionally(e);
                            }
                        });

        return future;
    }

    private String getExchangeName(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = decoder.parameters();

        if (!parameters.containsKey("exchange")) {
            throw new IllegalArgumentException("Missing exchange param");
        }
        return parameters.get("exchange").get(0);
    }

    private Connection getOrCreateConnection(String primaryBrokerUri) {
        return connections.computeIfAbsent(
                primaryBrokerUri,
                p -> {
                    // RabbitMQ will pick the first available address from the list. Future reconnection
                    // attempts will pick a random accessible address from the provided list.
                    List<Address> addresses =
                            Stream.concat(Stream.of(p), config.amqpUris.stream().filter(s -> !s.equals(p)))
                                    .map(s -> newURI(s))
                                    .map(u -> new Address(u.getHost(), u.getPort()))
                                    .collect(toList());
                    try {
                        ConnectionFactory connectionFactory = new ConnectionFactory();
                        connectionFactory.setAutomaticRecoveryEnabled(true);
                        String userInfo = newURI(primaryBrokerUri).getUserInfo();
                        if (userInfo != null) {
                            String[] userInfoElems = userInfo.split(":");
                            connectionFactory.setUsername(userInfoElems[0]);
                            connectionFactory.setPassword(userInfoElems[1]);
                        }
                        return connectionFactory.newConnection(addresses);
                    } catch (Exception e) {
                        throw new RuntimeException("Couldn't establish connection to: " + primaryBrokerUri, e);
                    }
                });
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger log = LoggerFactory.getLogger(RabbitMqBenchmarkDriver.class);

    private static URI newURI(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
