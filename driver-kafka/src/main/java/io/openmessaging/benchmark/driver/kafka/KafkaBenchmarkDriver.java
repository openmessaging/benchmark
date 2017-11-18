/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.driver.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class KafkaBenchmarkDriver implements BenchmarkDriver {
    private static final Logger log = LoggerFactory.getLogger(KafkaBenchmarkDriver.class);

    private Config config;

    private KafkaProducer<byte[], byte[]> producer;
    private List<BenchmarkConsumer> consumers = Collections.synchronizedList(new ArrayList<>());

    private Properties producerProperties;
    private Properties consumerProperties;

    private AdminClient admin;

    @Override
    public void initialize(File configurationFile) throws IOException {
        config = mapper.readValue(configurationFile, Config.class);

        Properties commonProperties = new Properties();
        commonProperties.load(new StringReader(config.commonConfig));

        producerProperties = new Properties();
        commonProperties.forEach((key, value) -> producerProperties.put(key, value));
        producerProperties.load(new StringReader(config.producerConfig));
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        consumerProperties = new Properties();
        commonProperties.forEach((key, value) -> consumerProperties.put(key, value));
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        admin = AdminClient.create(commonProperties);

        log.info("Producer props: {}", producerProperties);
        producer = new KafkaProducer<>(producerProperties);
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-topic";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions, boolean onlyValidate) {
        return CompletableFuture.runAsync(() -> {
            try {
                ListTopicsResult allTopics = admin.listTopics();
                Set<String> topicsStrs = allTopics.names().get();
                if (topicsStrs.contains(topic)) {
                    if (!onlyValidate) {
                        DeleteTopicsResult deleteResult = admin.deleteTopics(Collections.singleton(topic));
                        deleteResult.all().get();
                        Thread.sleep(TimeUnit.SECONDS.toMillis(3)); // TODO: Grrrr...more polling
                        createNewKafkaTopic(topic, partitions);
                    }
                } else {
                    createNewKafkaTopic(topic, partitions);
                }


                // Make sure topic gets a leader
                int leaderElectedCount = 0;
                while (leaderElectedCount != partitions) {
                    DescribeTopicsResult existingTopics = admin.describeTopics(Collections.singleton(topic));
                    Map<String, TopicDescription> topicDescriptionMap = existingTopics.all().get();
                    /*TopicDescription topicDescription = topicDescriptionMap.get(topic);
                    if (topicDescription.partitions().size() != partitions) {
                        throw new RuntimeException(String.format("Topic %s doesn't have declared number of partitions " +
                                "(want: %s have: %s)", topic, partitions, topicDescription.partitions().size()));
                    }
                    if (topicDescription.partitions().get(0).replicas().size() != config.replicationFactor) {
                        throw new RuntimeException(String.format("Topic %s doesn't have declared replication " +
                                "(want: %s have: %s)", topic, config.replicationFactor,
                                topicDescription.partitions().get(0).replicas().size()));
                    }*/
                    for (TopicPartitionInfo topicPartition : topicDescriptionMap.get(topic).partitions()) {
                        if (topicPartition.leader().isEmpty()) {
                            log.info("Leader not elected for topic {} partition {}; waiting 1 second");
                            leaderElectedCount = 0;
                            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                            break; // Loop back and check everything again
                        } else {
                            leaderElectedCount++;
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void createNewKafkaTopic(String topic, int partitions) throws InterruptedException, ExecutionException {
        admin.createTopics(Collections.singleton(new NewTopic(topic, partitions, config.replicationFactor))).all()
                .get();
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.completedFuture(new KafkaBenchmarkProducer(producer, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
                                                               ConsumerCallback consumerCallback, int partitionsPerTopic) {
        Properties properties = new Properties();
        consumerProperties.forEach(properties::put);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, subscriptionName);
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties);
        try {
            consumer.subscribe(Arrays.asList(topic));
            CompletableFuture<BenchmarkConsumer> completedConsumerFuture =
                    CompletableFuture.completedFuture(new KafkaBenchmarkConsumer(consumer, topic, partitionsPerTopic));
            consumers.add(completedConsumerFuture.get());
            return completedConsumerFuture;
        } catch (Throwable t) {
            try {
                consumer.close();
            } finally {
                CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
                future.completeExceptionally(t);
                return future;
            }
        }

    }

    @Override
    public void close() throws Exception {
        try {
            producer.close();
        } finally {
            for (BenchmarkConsumer consumer : consumers) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    log.warn("Error while closing consumer", e);
                }
            }
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
