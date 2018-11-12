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
package io.openmessaging.benchmark.driver.nats;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nats.client.Connection;


public class NatsBenchmarkDriver implements BenchmarkDriver {
    private NatsConfig config;
    @Override public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, NatsConfig.class);
        log.info("read config file," + config.toString());
    }

    @Override public String getTopicNamePrefix() {
        return "Nats-benchmark";
    }

    @Override public CompletableFuture<Void> createTopic(String topic, int partitions) {
        log.info("nats create a topic" + topic);
        log.info("ignore partitions");
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    @Override public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        Connection natsProducer;
        try {
            Options options = new Options.Builder().server(config.natsHostUrl).maxReconnects(5).build();
            natsProducer = Nats.connect(options);
        } catch (Exception e) {
            log.error("createProducer excetopin " + e);
            return null;
        }
        return CompletableFuture.completedFuture(new NatsBenchmarkProducer(natsProducer, topic));
    }

    @Override public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
        ConsumerCallback consumerCallback) {
        Dispatcher natsConsumer;
        Connection cn;
        log.info("createConsumer");
        try {
            Options options = new Options.Builder().server(config.natsHostUrl).maxReconnects(5).build();
            cn = Nats.connect(options);
            natsConsumer = cn.createDispatcher((msg) -> {
                consumerCallback.messageReceived(msg.getData(), Long.parseLong(msg.getReplyTo()));
            });
            natsConsumer.subscribe(topic, subscriptionName);
            cn.flush(Duration.ZERO);
        } catch (Exception e) {
            log.error("createConsumer excetopin " + e);
            return null;
        }
        log.info("createCOnsumer done");
        return CompletableFuture.completedFuture(new NatsBenchmarkConsumer(cn));
    }

    @Override public void close() throws Exception {

    }

    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkDriver.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

}
