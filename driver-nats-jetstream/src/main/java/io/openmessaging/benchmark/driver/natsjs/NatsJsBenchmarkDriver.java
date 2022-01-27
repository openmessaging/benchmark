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
package io.openmessaging.benchmark.driver.natsjs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NatsJsBenchmarkDriver implements BenchmarkDriver {
    private static final String TOPIC_NAME_PREFIX = "njs";
    private static final String STREAM_NAME_SUBJECT = "strm";

    private NatsJsConfig config;
    private List<Connection> connections;
    private List<NatsJsBenchmarkConsumer> consumers;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, NatsJsConfig.class);
        log.info("Nats JetStream driver configuration: {}", writer.writeValueAsString(config));
        connections = Collections.synchronizedList(new ArrayList<>());
        consumers = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public String getTopicNamePrefix() {
        return TOPIC_NAME_PREFIX;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = Nats.connect(normalizedOptions())) {
                String stream = safeName(topic, STREAM_NAME_SUBJECT);
                String subject = safeName(topic, null);
                StorageType storageType = "file".equalsIgnoreCase(config.jsStorageType)
                    ? StorageType.File
                    : StorageType.Memory;
                JetStreamManagement jsm = conn.jetStreamManagement();

                try {
                    conn.jetStreamManagement().deleteStream(stream);
                } catch (Exception e) {
                    // don't care if it doesn't exist
                }
                StreamConfiguration streamConfig = StreamConfiguration.builder()
                    .name(stream)
                    .subjects(subject)
                    .storageType(storageType)
                    .build();
                jsm.addStream(streamConfig);
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String subject = safeName(topic, null);
                return new NatsJsBenchmarkProducer(config, connection(), subject);
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String subject = safeName(topic, null);
                NatsJsBenchmarkConsumer consumer =
                    new NatsJsBenchmarkConsumer(config, connection(), subject, subscriptionName, consumerCallback);
                consumers.add(consumer);
                return consumer;
            }
            catch (RuntimeException re) { throw re; }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public void close() throws Exception {
        connections.forEach(conn -> {
            try {
                conn.close();
            }
            catch (Exception ignore) {}
        });
        consumers.forEach(conn -> {
            try {
                conn.close();
            }
            catch (Exception ignore) {}
        });
    }

    private Connection connection() throws IOException, InterruptedException {
        Connection conn = Nats.connect(normalizedOptions());
        connections.add(conn);
        return conn;
    }

    private String patchUserPass(String baseUrl) {
        // monkey patch this for a benchmark
        // assumes nats://
        int index = 7;
        String pre = baseUrl.substring(0,index);
        String post = baseUrl.substring(index);
        return pre + "UserA:s3cr3t@" + post;
    }

    private Options normalizedOptions() {
        Options.Builder builder = new Options.Builder();
        builder.maxReconnects(5);
        builder.errorListener(new NatsJsBenchmarkErrorListener());
        for(int i=0; i < config.workers.length; i++) {
            builder.server(patchUserPass(config.workers[i]));
        }
        return builder.build();
    }

    private String safeName(String unsafe, String safeExtra) {
        String end = safeExtra == null ? "" : safeExtra;
        if (unsafe.startsWith(TOPIC_NAME_PREFIX)) {
            byte[] encodePart = unsafe.substring(TOPIC_NAME_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
            return TOPIC_NAME_PREFIX + Base64.getUrlEncoder().encodeToString(encodePart) + end;
        }
        byte[] encodePart = unsafe.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().encodeToString(encodePart) + end;
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final Logger log = LoggerFactory.getLogger(NatsJsBenchmarkDriver.class);
}
