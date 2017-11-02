package com.streamlio.messaging.benchmark.driver.kafka;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.streamlio.messaging.benchmark.driver.BenchmarkConsumer;
import com.streamlio.messaging.benchmark.driver.BenchmarkDriver;
import com.streamlio.messaging.benchmark.driver.BenchmarkProducer;
import com.streamlio.messaging.benchmark.driver.ConsumerCallback;

public class KafkaBenchmarkDriver implements BenchmarkDriver {

    KafkaProducer<byte[], byte[]> producer;
    List<BenchmarkConsumer> consumers = Collections.synchronizedList(new ArrayList<>());

    private Properties producerProperties;
    private Properties consumerProperties;

    private AdminClient admin;

    @Override
    public void initialize(File configurationFile) throws IOException {
        Config config = mapper.readValue(configurationFile, Config.class);

        Properties commonProperties = new Properties();
        commonProperties.load(new StringReader(config.commonConfig));

        producerProperties = new Properties(commonProperties);
        producerProperties.load(new StringReader(config.producerConfig));
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        consumerProperties = new Properties(commonProperties);
        consumerProperties.load(new StringReader(config.consumerConfig));
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        admin = AdminClient.create(commonProperties);

        producer = new KafkaProducer<>(producerProperties);
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-topic";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return CompletableFuture.runAsync(() -> {
            try {
                admin.createTopics(Arrays.asList(new NewTopic(topic, partitions, (short) 3))).all().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.completedFuture(new KafkaBenchmarkProducer(producer, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
            ConsumerCallback consumerCallback) {
        Properties properties = new Properties(consumerProperties);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, subscriptionName);
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties);
        try {
            consumer.subscribe(Arrays.asList(topic));
            return CompletableFuture.completedFuture(new KafkaBenchmarkConsumer(consumer, consumerCallback));
        } catch (Throwable t) {
            consumer.close();
            CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
            future.completeExceptionally(t);
            return future;
        }

    }

    @Override
    public void close() throws Exception {
        producer.close();

        for (BenchmarkConsumer consumer : consumers) {
            consumer.close();
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
