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
package io.openmessaging.benchmark.driver.starlightrabbitmq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.rabbitmq.RabbitMqBenchmarkConsumer;
import io.openmessaging.benchmark.driver.rabbitmq.RabbitMqBenchmarkProducer;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.common.policies.data.BacklogQuota;
import org.apache.pulsar.common.policies.data.PersistencePolicies;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarlightRabbitMqBenchmarkDriver implements BenchmarkDriver {

    private PulsarAdmin adminClient;
    private String namespace;

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private StarlightRabbitMqConfig config;
    private final AtomicInteger uriIndex = new AtomicInteger();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, StarlightRabbitMqConfig.class);

        PulsarAdminBuilder pulsarAdminBuilder = PulsarAdmin.builder().serviceHttpUrl(config.pulsarHttpUrl);
        adminClient = pulsarAdminBuilder.build();

        log.info("Created Pulsar admin client for HTTP URL {}", config.pulsarHttpUrl);

        try {
            // Create namespace and set the configuration
            String tenant = config.namespacePrefix.split("/")[0];
            String cluster = config.clusterName;
            if (!adminClient.tenants().getTenants().contains(tenant)) {
                try {
                    adminClient.tenants().createTenant(tenant,
                        TenantInfo.builder().adminRoles(Collections.emptySet()).allowedClusters(Sets.newHashSet(cluster)).build());
                } catch (PulsarAdminException.ConflictException e) {
                    // Ignore. This can happen when multiple workers are initializing at the same time
                }
            }
            log.info("Created Pulsar tenant {} with allowed cluster {}", tenant, cluster);

            namespace = config.namespacePrefix + "-" + getRandomString();
            adminClient.namespaces().createNamespace(namespace);
            log.info("Created Pulsar namespace {}", namespace);

            StarlightRabbitMqConfig.PersistenceConfiguration p = config.persistence;
            adminClient.namespaces().setPersistence(namespace,
                new PersistencePolicies(p.ensembleSize, p.writeQuorum, p.ackQuorum, 1.0));

            adminClient.namespaces().setBacklogQuota(namespace,
                BacklogQuota.builder()
                    .limitSize(-1L)
                    .limitTime(-1)
                    .retentionPolicy(BacklogQuota.RetentionPolicy.producer_exception)
                    .build());
            adminClient.namespaces().setDeduplicationStatus(namespace, p.deduplicationEnabled);
            log.info("Applied persistence configuration for namespace {}/{}/{}: {}", tenant, cluster, namespace,
                writer.writeValueAsString(p));

        } catch (PulsarAdminException.ConflictException e) {
            // Ignore. This can happen when multiple workers are initializing at the same time
        } catch (PulsarAdminException e) {
            throw new IOException(e);
        }

    }

    @Override
    public void close() {
        log.info("Shutting down Starlight-RabbitMQ benchmark driver");
        for(Iterator<Map.Entry<String, Connection>> it = connections.entrySet().iterator(); it.hasNext(); ) {
            Connection connection = it.next().getValue();
            try {
                if (connection.isOpen()) {
                    connection.close();
                }
            } catch (IOException e) {
                log.error("Couldn't close connection", e);
            }
            it.remove();
        }

        if (adminClient != null) {
            adminClient.close();
        }

        log.info("Starlight-RabbitMQ benchmark driver successfully shut down");
    }

    @Override
    public String getTopicNamePrefix() {
        URI configUri = URI.create(config.amqpUris.get(uriIndex.getAndIncrement() % config.amqpUris.size()));
        try {
            URI topicUri = new URI(configUri.getScheme(), configUri.getAuthority(), "/" + URLEncoder.encode(namespace, StandardCharsets.UTF_8.name()), "topic=test-topic", null);
            return topicUri.toString();
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        try {
            String pulsarTopic = getPulsarTopic(topic);
            if (partitions == 1) {
                return adminClient.topics().createNonPartitionedTopicAsync(pulsarTopic);
            }
            log.info("Create partitioned topic {} with {} partitions", pulsarTopic, partitions);
            return adminClient.topics().createPartitionedTopicAsync(pulsarTopic, partitions);
        } catch (Exception e) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }

    }

    private String getPulsarTopic(String topic) {
        String pulsarTopic = null;
        URI uri = URI.create(topic);
        String[] params = uri.getQuery().split("&");
        for (String param : params) {
            if (param.startsWith("topic=")) {
                pulsarTopic = param.substring(6);
                break;
            }
        }
        if (pulsarTopic == null) {
            throw new IllegalArgumentException("Missing topic param");
        }
        return pulsarTopic;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        CompletableFuture<BenchmarkProducer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                String uri = topic.split("\\?")[0];
                Connection connection = getOrCreateConnection(uri);
                Channel channel = connection.createChannel();
                String pulsarTopic = getPulsarTopic(topic);
                channel.exchangeDeclare(pulsarTopic, BuiltinExchangeType.FANOUT);
                log.info("Declared exchange {} on connection {}", pulsarTopic, connection);
                channel.confirmSelect();
                future.complete(new RabbitMqBenchmarkProducer(channel, pulsarTopic, true));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
                                                               ConsumerCallback consumerCallback) {

        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                String uri = topic.split("\\?")[0];
                Connection connection = getOrCreateConnection(uri);
                Channel channel = connection.createChannel();
                String pulsarTopic = getPulsarTopic(topic);
                channel.exchangeDeclare(pulsarTopic, BuiltinExchangeType.FANOUT);
                // Create the queue
                String queueName = pulsarTopic + "-" + subscriptionName;
                channel.queueDeclare(queueName, true, false, false, Collections.emptyMap());
                channel.queueBind(queueName, pulsarTopic, "");
                log.info("Declared queue {} bound to exchange {} on connection {}", queueName, pulsarTopic, connection);
                future.complete(new RabbitMqBenchmarkConsumer(channel, queueName, consumerCallback));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Connection getOrCreateConnection(String uri) {
        return connections.computeIfAbsent(uri, uriKey -> {
            try {
                ConnectionFactory connectionFactory = new ConnectionFactory();
                connectionFactory.setAutomaticRecoveryEnabled(true);
                connectionFactory.setUri(uri);
                return connectionFactory.newConnection();
            } catch (Exception e) {
                throw new RuntimeException("Couldn't establish connection", e);
            }
        });
    }

    private static final Random random = new Random();

    private static String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final Logger log = LoggerFactory.getLogger(StarlightRabbitMqBenchmarkDriver.class);
}
