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
package io.openmessaging.benchmark.driver.kop;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.kafka.KafkaBenchmarkConsumer;
import io.openmessaging.benchmark.driver.kafka.KafkaBenchmarkProducer;
import io.openmessaging.benchmark.driver.kop.config.ClientType;
import io.openmessaging.benchmark.driver.kop.config.Config;
import io.openmessaging.benchmark.driver.kop.config.PulsarConfig;
import io.openmessaging.benchmark.driver.pulsar.PulsarBenchmarkConsumer;
import io.openmessaging.benchmark.driver.pulsar.PulsarBenchmarkProducer;
import io.streamnative.pulsar.handlers.kop.KafkaPayloadProcessor;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.util.FutureUtil;

public class KopBenchmarkDriver implements BenchmarkDriver {

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final List<BenchmarkProducer> producers = new CopyOnWriteArrayList<>();
    private final List<BenchmarkConsumer> consumers = new CopyOnWriteArrayList<>();

    private Config config;
    private AdminClient admin;
    private Properties producerProperties;
    private Properties consumerProperties;
    private PulsarClient client = null;
    private ProducerBuilder<byte[]> producerBuilder = null;
    private ConsumerBuilder<ByteBuffer> consumerBuilder = null;

    public static Config loadConfig(File file) throws IOException {
        return mapper.readValue(file, Config.class);
    }

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = loadConfig(configurationFile);
        final Properties commonProperties = config.getKafkaProperties();
        admin = AdminClient.create(commonProperties);

        producerProperties = new Properties();
        commonProperties.forEach(producerProperties::put);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        consumerProperties = new Properties();
        commonProperties.forEach(consumerProperties::put);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        final PulsarConfig pulsarConfig = config.pulsarConfig;
        if (config.producerType.equals(ClientType.PULSAR)) {
            producerBuilder =
                    getPulsarClient(pulsarConfig.serviceUrl)
                            .newProducer()
                            .enableBatching(pulsarConfig.batchingEnabled)
                            .blockIfQueueFull(pulsarConfig.blockIfQueueFull)
                            .batchingMaxPublishDelay(
                                    pulsarConfig.batchingMaxPublishDelayMs, TimeUnit.MILLISECONDS)
                            .batchingMaxBytes(pulsarConfig.batchingMaxBytes)
                            .maxPendingMessages(pulsarConfig.pendingQueueSize)
                            .maxPendingMessagesAcrossPartitions(pulsarConfig.maxPendingMessagesAcrossPartitions);
        }
        if (config.consumerType.equals(ClientType.PULSAR)) {
            consumerBuilder =
                    getPulsarClient(pulsarConfig.serviceUrl)
                            .newConsumer(Schema.BYTEBUFFER)
                            .subscriptionType(SubscriptionType.Failover)
                            .receiverQueueSize(pulsarConfig.receiverQueueSize)
                            .maxTotalReceiverQueueSizeAcrossPartitions(
                                    pulsarConfig.maxTotalReceiverQueueSizeAcrossPartitions);
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-topic";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        // replicationFactor is meaningless in KoP
        final NewTopic newTopic = new NewTopic(topic, partitions, (short) 1L);
        final CompletableFuture<Void> future = new CompletableFuture<>();
        admin
                .createTopics(Collections.singletonList(newTopic))
                .all()
                .whenComplete(
                        (result, throwable) -> {
                            if (throwable == null) {
                                future.complete(result);
                            } else {
                                future.completeExceptionally(throwable);
                            }
                        });
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        if (config.producerType.equals(ClientType.KAFKA)) {
            final BenchmarkProducer producer =
                    new KafkaBenchmarkProducer(new KafkaProducer<>(producerProperties), topic);
            producers.add(producer);
            return CompletableFuture.completedFuture(producer);
        } else if (config.consumerType.equals(ClientType.PULSAR)) {
            return producerBuilder
                    .clone()
                    .topic(topic)
                    .createAsync()
                    .thenApply(PulsarBenchmarkProducer::new);
        } else {
            throw new IllegalArgumentException("producerType " + config.producerType + " is invalid");
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        if (config.consumerType.equals(ClientType.KAFKA)) {
            final Properties properties = new Properties();
            consumerProperties.forEach(properties::put);
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, subscriptionName);
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            final KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(properties);
            kafkaConsumer.subscribe(Collections.singleton(topic));
            final BenchmarkConsumer consumer =
                    new KafkaBenchmarkConsumer(
                            kafkaConsumer, properties, consumerCallback, config.pollTimeoutMs);
            consumers.add(consumer);
            return CompletableFuture.completedFuture(consumer);
        } else if (config.consumerType.equals(ClientType.PULSAR)) {
            final List<CompletableFuture<Consumer<ByteBuffer>>> futures = new ArrayList<>();
            return client
                    .getPartitionsForTopic(topic)
                    .thenCompose(
                            partitions -> {
                                partitions.forEach(
                                        p ->
                                                futures.add(
                                                        createInternalPulsarConsumer(p, subscriptionName, consumerCallback)));
                                return FutureUtil.waitForAll(futures);
                            })
                    .thenApply(
                            __ ->
                                    new PulsarBenchmarkConsumer(
                                            futures.stream().map(CompletableFuture::join).collect(Collectors.toList())));
        } else {
            throw new IllegalArgumentException("consumerType " + config.consumerType + " is invalid");
        }
    }

    @Override
    public void close() throws Exception {
        for (BenchmarkProducer producer : producers) {
            producer.close();
        }
        for (BenchmarkConsumer consumer : consumers) {
            consumer.close();
        }
        admin.close();
        if (client != null) {
            client.close();
        }
    }

    private PulsarClient getPulsarClient(String serviceUrl) throws PulsarClientException {
        if (client == null) {
            client = PulsarClient.builder().serviceUrl(serviceUrl).build();
        }
        return client;
    }

    private CompletableFuture<Consumer<ByteBuffer>> createInternalPulsarConsumer(
            String topic, String subscriptionName, ConsumerCallback callback) {
        return consumerBuilder
                .clone()
                .topic(topic)
                .subscriptionName(subscriptionName)
                .messagePayloadProcessor(
                        new KafkaPayloadProcessor()) // support consuming Kafka format messages
                .poolMessages(true)
                .messageListener(
                        (c, msg) -> {
                            try {
                                callback.messageReceived(msg.getValue(), msg.getPublishTime());
                                c.acknowledgeAsync(msg);
                            } finally {
                                msg.release();
                            }
                        })
                .subscribeAsync();
    }
}
