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
import com.rabbitmq.client.ConnectionFactory;
import io.openmessaging.benchmark.driver.rabbitmq.RabbitMqBenchmarkDriver;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
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

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        StarlightRabbitMqConfig config = mapper.readValue(configurationFile, StarlightRabbitMqConfig.class);


        PulsarAdminBuilder pulsarAdminBuilder = PulsarAdmin.builder().serviceHttpUrl(config.pulsarHttpUrl);
        /*if (config.client.httpUrl.startsWith("https")) {
            pulsarAdminBuilder.allowTlsInsecureConnection(config.client.tlsAllowInsecureConnection)
                .enableTlsHostnameVerification(config.client.tlsEnableHostnameVerification)
                .tlsTrustCertsFilePath(config.client.tlsTrustCertsFilePath);
        }

        if (config.client.authentication.plugin != null && !config.client.authentication.plugin.isEmpty()) {
            clientBuilder.authentication(config.client.authentication.plugin, config.client.authentication.data);
            pulsarAdminBuilder.authentication(config.client.authentication.plugin, config.client.authentication.data);
        }*/

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

            namespace = config.namespacePrefix; //+ "-" + getRandomString();
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

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        try {
            connectionFactory.setUri(config.amqpUri);
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException(e);
        }
        connectionFactory.setVirtualHost(namespace);

        try {
            for (int i = 0; i < 10; i++) {
                try {
                    connection = connectionFactory.newConnection();
                } catch (TimeoutException | IOException e) {
                    log.warn("Connection error", e);
                    Thread.sleep(random.nextInt(200));
                }
            }
        } catch (InterruptedException e) {

        }
        if (connection == null) {
            throw new IOException("Couldn't establish connection");
        }
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down Starlight-RabbitMQ benchmark driver");
        super.close();

        if (adminClient != null) {
            adminClient.close();
        }

        log.info("Starlight-RabbitMQ benchmark driver successfully shut down");
    }


    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (partitions == 1) {
            // No-op
            return CompletableFuture.completedFuture(null);
        }

        return adminClient.topics().createPartitionedTopicAsync(topic, partitions);
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
