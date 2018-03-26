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
package io.openmessaging.benchmark.driver.rabbitmq;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;

public class RabbitMqBenchmarkDriver implements BenchmarkDriver {

    private RabbitMqConfig config;

    private Connection connection;
    private Channel channel;

    @Override
    public void initialize(File configurationFile) throws IOException {
        config = mapper.readValue(configurationFile, RabbitMqConfig.class);

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        connectionFactory.setHost(config.brokerAddress);

        try {
            connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            channel.confirmSelect();
        } catch (TimeoutException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        channel.close();
        connection.close();
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-topic";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (partitions != 1) {
            throw new IllegalArgumentException("Cannot create topic with partitions in RabbitMQ");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                channel.exchangeDeclare(topic, BuiltinExchangeType.FANOUT);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic, StatsLogger statsLogger) {
        return CompletableFuture.completedFuture(new RabbitMqBenchmarkProducer(channel, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
            ConsumerCallback consumerCallback, StatsLogger statsLogger) {

        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                String queueName = topic + "-" + subscriptionName;

                // Create the queue
                channel.queueDeclare(queueName, true, false, false, Collections.emptyMap());
                channel.queueBind(queueName, topic, "");
                future.complete(new RabbitMqBenchmarkConsumer(channel, queueName, consumerCallback));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
