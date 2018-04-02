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
package io.openmessaging.benchmark.driver.rocketmq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.BaseEncoding;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.rocketmq.client.RocketMQClientConfig;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.command.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQBenchmarkDriver implements BenchmarkDriver {
    private DefaultMQAdminExt rmqAdmin;
    private RocketMQClientConfig rmqClientConfig;
    @Override
    public void initialize(final File configurationFile, final StatsLogger statsLogger) throws IOException {
        this.rmqClientConfig = readConfig(configurationFile);

        this.rmqAdmin = new DefaultMQAdminExt();
        this.rmqAdmin.setNamesrvAddr(this.rmqClientConfig.namesrvAddr);
        this.rmqAdmin.setInstanceName("AdminInstance-" + getRandomString());
        try {
            this.rmqAdmin.start();
        } catch (MQClientException e) {
            log.error("Start the RocketMQ admin tool failed.");
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return "RocketMQ-Benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(final String topic, final int partitions) {
        return CompletableFuture.runAsync(() -> {
            TopicConfig topicConfig = new TopicConfig();
            topicConfig.setOrder(false);
            topicConfig.setPerm(6);
            topicConfig.setReadQueueNums(partitions);
            topicConfig.setWriteQueueNums(partitions);
            topicConfig.setTopicName(topic);

            try {
                Set<String> brokerList = CommandUtil.fetchMasterAddrByClusterName(this.rmqAdmin, this.rmqClientConfig.clusterName);
                topicConfig.setReadQueueNums(Math.max(1, partitions / brokerList.size()));
                topicConfig.setWriteQueueNums(Math.max(1, partitions / brokerList.size()));

                for (String brokerAddr : brokerList) {
                    this.rmqAdmin.createAndUpdateTopicConfig(brokerAddr, topicConfig);
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("Failed to create topic [%s] to cluster [%s]", topic, this.rmqClientConfig.clusterName), e);
            }
        });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(final String topic) {
        DefaultMQProducer rmqProducer = new DefaultMQProducer("ProducerGroup_" + getRandomString());
        rmqProducer.setNamesrvAddr(this.rmqClientConfig.namesrvAddr);
        rmqProducer.setInstanceName("ProducerInstance" + getRandomString());
        try {
            rmqProducer.start();
        } catch (MQClientException e) {
            log.error("Failed to start the created producer instance.", e);
        }
        return CompletableFuture.completedFuture(new RocketMQBenchmarkProducer(rmqProducer, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(final String topic, final String subscriptionName,
        final ConsumerCallback consumerCallback) {
        DefaultMQPushConsumer rmqConsumer = new DefaultMQPushConsumer(subscriptionName);
        rmqConsumer.setNamesrvAddr(this.rmqClientConfig.namesrvAddr);
        rmqConsumer.setInstanceName("ConsumerInstance" + getRandomString());
        try {
            rmqConsumer.subscribe(topic, "*");
            rmqConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt message : msgs) {
                    consumerCallback.messageReceived(message.getBody(), message.getBornTimestamp());
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            rmqConsumer.start();
        } catch (MQClientException e) {
            log.error("Failed to create consumer instance.", e);
        }

        return CompletableFuture.completedFuture(new RocketMQBenchmarkConsumer(rmqConsumer));
    }

    @Override
    public void close() throws Exception {
        this.rmqAdmin.shutdown();
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static RocketMQClientConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, RocketMQClientConfig.class);
    }

    private static final Random random = new Random();

    private static final String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(RocketMQBenchmarkDriver.class);
}
