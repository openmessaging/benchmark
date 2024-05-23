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
package io.openmessaging.benchmark.driver.kafka;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaBenchmarkDriver implements BenchmarkDriver {

    private Config config;

    private List<BenchmarkProducer> producers = Collections.synchronizedList(new ArrayList<>());
    private List<BenchmarkConsumer> consumers = Collections.synchronizedList(new ArrayList<>());

    private Properties topicProperties;
    private Properties producerProperties;
    private Properties consumerProperties;

    private AdminClient admin;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, Config.class);

        Properties commonProperties = new Properties();
        commonProperties.load(new StringReader(config.commonConfig));

        producerProperties = new Properties();
        commonProperties.forEach((key, value) -> producerProperties.put(key, value));
        producerProperties.load(new StringReader(config.producerConfig));
        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        consumerProperties = new Properties();
        commonProperties.forEach((key, value) -> consumerProperties.put(key, value));
        consumerProperties.load(new StringReader(config.consumerConfig));
        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        topicProperties = new Properties();
        topicProperties.load(new StringReader(config.topicConfig));

        admin = AdminClient.create(commonProperties);
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-topic";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return createTopics(Collections.singletonList(new TopicInfo(topic, partitions)));
    }

    @Override
    public CompletableFuture<Void> createTopics(List<TopicInfo> topicInfos) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, String> topicConfigs = new HashMap<>((Map) topicProperties);
        KafkaTopicCreator topicCreator =
                new KafkaTopicCreator(admin, topicConfigs, config.replicationFactor);
        return topicCreator.create(topicInfos);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        KafkaProducer<String, byte[]> kafkaProducer = new KafkaProducer<>(producerProperties);
        BenchmarkProducer benchmarkProducer = new KafkaBenchmarkProducer(kafkaProducer, topic);
        try {
            // Add to producer list to close later
            producers.add(benchmarkProducer);
            return CompletableFuture.completedFuture(benchmarkProducer);
        } catch (Throwable t) {
            kafkaProducer.close();
            CompletableFuture<BenchmarkProducer> future = new CompletableFuture<>();
            future.completeExceptionally(t);
            return future;
        }
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        Properties properties = new Properties();
        consumerProperties.forEach((key, value) -> properties.put(key, value));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, subscriptionName);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
        try {
            consumer.subscribe(Arrays.asList(topic));
            return CompletableFuture.completedFuture(
                    new KafkaBenchmarkConsumer(consumer, consumerProperties, consumerCallback));
        } catch (Throwable t) {
            consumer.close();
            CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
            future.completeExceptionally(t);
            return future;
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
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
