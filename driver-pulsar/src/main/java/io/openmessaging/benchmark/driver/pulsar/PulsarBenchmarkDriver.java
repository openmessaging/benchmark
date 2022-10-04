
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
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.PulsarAdminException.ConflictException;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.SubscriptionMode;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.policies.data.BacklogQuota;
import org.apache.pulsar.common.policies.data.BacklogQuota.RetentionPolicy;
import org.apache.pulsar.common.policies.data.PersistencePolicies;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.pulsar.config.PulsarClientConfig.PersistenceConfiguration;
import io.openmessaging.benchmark.driver.pulsar.config.PulsarConfig;

public class PulsarBenchmarkDriver implements BenchmarkDriver {

    private PulsarClient client;
    private PulsarAdmin adminClient;

    private PulsarConfig config;



    private String namespace;
    private ProducerBuilder<byte[]> producerBuilder;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        this.config = readConfig(configurationFile);
        log.info("Pulsar driver configuration: {}", writer.writeValueAsString(config));

        ClientBuilder clientBuilder = PulsarClient.builder()
                .ioThreads(config.client.ioThreads)
                .connectionsPerBroker(config.client.connectionsPerBroker)
                .statsInterval(0, TimeUnit.SECONDS)
                .serviceUrl(config.client.serviceUrl)
                .maxConcurrentLookupRequests(50000)
                .maxLookupRequests(100000)
                .listenerThreads(Runtime.getRuntime().availableProcessors());

        if (config.client.serviceUrl.startsWith("pulsar+ssl")) {
            clientBuilder.allowTlsInsecureConnection(config.client.tlsAllowInsecureConnection)
                            .enableTlsHostnameVerification(config.client.tlsEnableHostnameVerification)
                            .tlsTrustCertsFilePath(config.client.tlsTrustCertsFilePath);
        }

        PulsarAdminBuilder pulsarAdminBuilder = PulsarAdmin.builder().serviceHttpUrl(config.client.httpUrl);
        if (config.client.httpUrl.startsWith("https")) {
            pulsarAdminBuilder.allowTlsInsecureConnection(config.client.tlsAllowInsecureConnection)
                            .enableTlsHostnameVerification(config.client.tlsEnableHostnameVerification)
                            .tlsTrustCertsFilePath(config.client.tlsTrustCertsFilePath);
        }

        if (config.client.authentication.plugin != null && !config.client.authentication.plugin.isEmpty()) {
            clientBuilder.authentication(config.client.authentication.plugin, config.client.authentication.data);
            pulsarAdminBuilder.authentication(config.client.authentication.plugin, config.client.authentication.data);
        }

        client = clientBuilder.build();

        log.info("Created Pulsar client for service URL {}", config.client.serviceUrl);

        adminClient = pulsarAdminBuilder.build();

        log.info("Created Pulsar admin client for HTTP URL {}", config.client.httpUrl);

        producerBuilder = client.newProducer().enableBatching(config.producer.batchingEnabled)
                        .batchingMaxPublishDelay(config.producer.batchingMaxPublishDelayMs, TimeUnit.MILLISECONDS)
                        .blockIfQueueFull(config.producer.blockIfQueueFull)
                        .maxPendingMessages(config.producer.pendingQueueSize);

        try {
            // Create namespace and set the configuration
            String tenant = config.client.namespacePrefix.split("/")[0];
            String cluster = config.client.clusterName;
	    boolean astra = config.client.astraStreaming;
            PersistenceConfiguration p = config.client.persistence;
            if (astra) {
		// Atsra streaming has limitations on the number of namespaces along with more constrained rules for the characters in the string.
		log.info("Astra Streaming - tenant {} in cluster {}", tenant, cluster);
		this.namespace = config.client.namespacePrefix;
		log.info("Created Pulsar namespace {}", namespace);
		try {
		    adminClient.namespaces().createNamespace(namespace);
		} catch (PulsarAdminException e) {
		    // ignore assuming this means the namespace is already there
		}
	    } else {
		if (!adminClient.tenants().getTenants().contains(tenant)) {
		    try {
			adminClient.tenants().createTenant(tenant,
				    TenantInfo.builder().adminRoles(Collections.emptySet()).allowedClusters(Sets.newHashSet(cluster)).build());
		    } catch (ConflictException e) {
			// Ignore. This can happen when multiple workers are initializing at the same time
		    }
		}
		log.info("Pulsar tenant {} with allowed cluster {}", tenant, cluster);
		this.namespace = config.client.namespacePrefix + "-" + getRandomString();
		adminClient.namespaces().createNamespace(namespace);
		log.info("Created Pulsar namespace {}", namespace);
		// only set persistence if not in Astra
		adminClient.namespaces().setPersistence(namespace,
                            new PersistencePolicies(p.ensembleSize, p.writeQuorum, p.ackQuorum, 1.0));
            }

            adminClient.namespaces().setBacklogQuota(namespace,
                    BacklogQuota.builder()
                            .limitSize(-1L)
                            .limitTime(-1)
                            .retentionPolicy(RetentionPolicy.producer_exception)
                            .build());
            adminClient.namespaces().setDeduplicationStatus(namespace, p.deduplicationEnabled);
            log.info("Applied persistence configuration for namespace {}/{}/{}: {}", tenant, cluster, namespace,
                            writer.writeValueAsString(p));

        } catch (PulsarAdminException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return config.client.topicType + "://" + namespace + "/test";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (partitions == 1) {
            // No-op
            return CompletableFuture.completedFuture(null);
        }
	log.info("Create topic: {}; Partitions: {}", topic, partitions);
        return adminClient.topics().createPartitionedTopicAsync(topic, partitions);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return producerBuilder.topic(topic).createAsync()
                        .thenApply(PulsarBenchmarkProducer::new);
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
                    ConsumerCallback consumerCallback) {
        SubscriptionType subscriptionType = SubscriptionType.valueOf(config.consumer.subscriptionType);
        SubscriptionMode subscriptionMode = SubscriptionMode.valueOf(config.consumer.subscriptionMode);
        log.info("Creating consumer subscriptionType {} subscriptionMode {}", subscriptionType, subscriptionMode);
        return client.newConsumer()
                .subscriptionType(subscriptionType)
                .subscriptionMode(subscriptionMode)
                .messageListener((consumer, msg) -> {
            // call acknowledgeAsync before executing messageReceived
            // because in backlog draining workloads the method messageReceived
            // is blocked while building the backlog, and this leads to
            // messages dispatched but not acknowledged for very long time
            consumer.acknowledgeAsync(msg);
            consumerCallback.messageReceived(msg.getData(), msg.getPublishTime());
        }).topic(topic).subscriptionName(subscriptionName).subscribeAsync()
                        .thenApply(consumer -> new PulsarBenchmarkConsumer(consumer));


    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down Pulsar benchmark driver");

        if (client != null) {
            client.close();
        }

        if (adminClient != null) {
            adminClient.close();
        }

        log.info("Pulsar benchmark driver successfully shut down");
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static PulsarConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, PulsarConfig.class);
    }

    private static final Random random = new Random();

    private static final String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(PulsarBenchmarkProducer.class);
}
