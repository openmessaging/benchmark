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
import io.openmessaging.benchmark.driver.rabbitmq.RabbitMqBenchmarkDriver;
import io.openmessaging.benchmark.driver.rabbitmq.RabbitMqConfig;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.common.policies.data.BacklogQuota;
import org.apache.pulsar.common.policies.data.PersistencePolicies;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarlightRabbitMqBenchmarkDriver extends RabbitMqBenchmarkDriver {

    private PulsarAdmin adminClient;
    private String namespace;
    private static final Random random = new Random();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        StarlightRabbitMqConfig config = mapper.readValue(configurationFile, StarlightRabbitMqConfig.class);

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

            RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();
            rabbitMqConfig.messagePersistence = true;
            config.amqpUris.forEach(
                uri -> {
                    try {
                        URI configUri = URI.create(uri);
                        rabbitMqConfig.amqpUris.add(
                            configUri.resolve("/" + URLEncoder.encode(namespace, StandardCharsets.UTF_8.name()))
                                .toString()
                        );
                    } catch (UnsupportedEncodingException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
            super.initialize(rabbitMqConfig);

        } catch (PulsarAdminException.ConflictException e) {
            // Ignore. This can happen when multiple workers are initializing at the same time
        } catch (PulsarAdminException e) {
            throw new IOException(e);
        }

    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        try {
            String pulsarTopic = getExchangeName(topic);
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
