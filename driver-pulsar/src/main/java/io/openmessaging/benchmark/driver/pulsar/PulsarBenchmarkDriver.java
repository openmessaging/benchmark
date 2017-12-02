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
package io.openmessaging.benchmark.driver.pulsar;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.ClientConfiguration;
import org.apache.pulsar.client.api.ConsumerConfiguration;
import org.apache.pulsar.client.api.ProducerConfiguration;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.pulsar.config.PulsarConfig;

public class PulsarBenchmarkDriver implements BenchmarkDriver {

    private PulsarClient client;
    private PulsarAdmin adminClient;

    private PulsarConfig config;

    private ProducerConfiguration producerConfiguration;

    public void initialize(File configurationFile) throws IOException {
        this.config = readConfig(configurationFile);
        log.info("Pulsar driver configuration: {}", writer.writeValueAsString(config));

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setIoThreads(config.client.ioThreads);
        clientConfiguration.setConnectionsPerBroker(config.client.connectionsPerBroker);

        // Disable internal stats since we're already collecting in the framework
        clientConfiguration.setStatsInterval(0, TimeUnit.SECONDS);

        client = PulsarClient.create(config.client.serviceUrl, clientConfiguration);
        adminClient = new PulsarAdmin(new URL(config.client.httpUrl), clientConfiguration);

        producerConfiguration = new ProducerConfiguration();
        producerConfiguration.setBatchingEnabled(config.producer.batchingEnabled);
        producerConfiguration.setBatchingMaxPublishDelay(config.producer.batchingMaxPublishDelayMs,
                TimeUnit.MILLISECONDS);
        producerConfiguration.setBlockIfQueueFull(config.producer.blockIfQueueFull);
    }

    @Override
    public String getTopicNamePrefix() {
        return config.client.topicPrefix;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (partitions == 1) {
            // No-op
            return CompletableFuture.completedFuture(null);
        }

        return adminClient.persistentTopics().createPartitionedTopicAsync(topic, partitions);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return client.createProducerAsync(topic, producerConfiguration)
                .thenApply(pulsarProducer -> new PulsarBenchmarkProducer(pulsarProducer));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
            ConsumerCallback consumerCallback) {
        ConsumerConfiguration conf = new ConsumerConfiguration();
        conf.setSubscriptionType(SubscriptionType.Failover);
        conf.setMessageListener((consumer, msg) -> {
            consumerCallback.messageReceived(msg.getData(), msg.getPublishTime());
            consumer.acknowledgeAsync(msg);
        });

        return client.subscribeAsync(topic, subscriptionName, conf)
                .thenApply(consumer -> new PulsarBenchmarkConsumer(consumer));
    }

    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
        }

        if (adminClient != null) {
            adminClient.close();
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static PulsarConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, PulsarConfig.class);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(PulsarBenchmarkProducer.class);
}
