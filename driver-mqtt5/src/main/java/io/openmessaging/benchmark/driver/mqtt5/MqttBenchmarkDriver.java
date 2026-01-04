/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.mqtt5;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HostAndPort;
import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.MqttClientTransportConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.mqtt5.client.MqttConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttBenchmarkDriver implements BenchmarkDriver {

    private static final Logger log = LoggerFactory.getLogger(MqttBenchmarkDriver.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Random random = new Random();
    private static final Pattern server_uri_pattern = Pattern.compile("(?:[^:]*://)?([^:]+)(?::(\\w+))?");
    public static final MqttUtf8String USER_PROPERTY_KEY_PUBLISH_TIMESTAMP =
        MqttUtf8String.of("benchmark-publish-timestamp");

    private MqttConfig config;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException, InterruptedException {
        this.config = readConfig(configurationFile);
        log.info("MqttBenchmarkDriver configuration: {}", writer.writeValueAsString(config));
    }

    @Override
    public String getTopicNamePrefix() {
        return config.client.topicPrefix + "/test";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        // MQTT topics are created on the fly when messages are published or subscribed to.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        CompletableFuture<BenchmarkProducer> future = new CompletableFuture<>();

        MqttBenchmarkProducer producer = new MqttBenchmarkProducer(topic, config.client.qos);
        Mqtt5ClientBuilder clientBuilder = getClientBuilder(buildPublisherClientId(), producer::isClosed);
        Mqtt5AsyncClient client = clientBuilder.buildAsync();
        client.connect().whenComplete(((connAck, ex) -> {
            if (handleConnResult(client, connAck, ex, future)) {
                return;
            }
            producer.setClient(client);
            future.complete(producer);
        }));

        return future;
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
        ConsumerCallback consumerCallback) {
        CompletableFuture<BenchmarkConsumer> future = new CompletableFuture<>();

        MqttQos qos = intToQoS(config.client.qos);
        List<Mqtt5Subscription> subscriptions = new ArrayList<>();
        Splitter.on(",").split(topic).forEach(topicFilter -> {
            topicFilter = topicFilter.trim();
            if (StringUtils.isNotEmpty(topicFilter)) {
                Mqtt5Subscription subscription = Mqtt5Subscription.builder()
                    // Use subscriptionName as shared subscription group name
                    .topicFilter(toSharedSubscription(topicFilter, subscriptionName))
                    .qos(qos)
                    .build();
                subscriptions.add(subscription);
            }
        });

        MqttBenchmarkConsumer consumer = new MqttBenchmarkConsumer();
        Mqtt5ClientBuilder clientBuilder = getClientBuilder(buildSubscriberClientId(), consumer::isClosed);
        Mqtt5AsyncClient client = clientBuilder.buildAsync();
        client.subscribeWith()
            .addSubscriptions(subscriptions)
            .callback(message -> {
                long publishTime = extractPublishTimestamp(message.getUserProperties());
                consumerCallback.messageReceived(message.getPayloadAsBytes(), publishTime);
                message.acknowledge();
            })
            .manualAcknowledgement(true)
            .send()
            .whenComplete(((subAck, ex) -> {
                String clientId = extractClientId(client.getConfig());
                if (ex != null) {
                    log.error("Client[{}] failed to subscribe, subscriptions={}", clientId, subscriptions, ex);
                } else if (subAck == null || subAck.getReasonCodes().size() != subscriptions.size()) {
                    log.error("Client[{}] received invalid subAck={}, subscriptions={}",
                        clientId, subAck, subscriptions);
                } else {
                    int size = subAck.getReasonCodes().size();
                    for (int i = 0; i < size; i++) {
                        Mqtt5SubAckReasonCode reasonCode = subAck.getReasonCodes().get(i);
                        Mqtt5Subscription subscription = subscriptions.get(i);
                        if (reasonCode == Mqtt5SubAckReasonCode.GRANTED_QOS_0
                            || reasonCode == Mqtt5SubAckReasonCode.GRANTED_QOS_1
                            || reasonCode == Mqtt5SubAckReasonCode.GRANTED_QOS_2) {
                            log.info("Client[{}] subscribed topic-filters={}, qos={}, granted-qos={}",
                                clientId, subscription.getTopicFilter(), subscription.getQos().getCode(),
                                reasonCode.getCode());
                        } else {
                            log.warn("Client[{}] failed to subscribe topic-filters={}, qos={}, reasonCode={}",
                                clientId, subscription.getTopicFilter(), subscription.getQos().getCode(),
                                reasonCode.name());
                        }
                    }
                }
            }));

        client.connectWith()
            .cleanStart(config.consumer.cleanSession)
            .sessionExpiryInterval(config.consumer.cleanSession ? 0 : config.consumer.sessionExpiryInterval)
            .restrictions()
            .receiveMaximum(config.consumer.receiveMaximum)
            .applyRestrictions()
            .send().whenComplete(((connAck, ex) -> {
                if (handleConnResult(client, connAck, ex, future)) {
                    return;
                }
                consumer.setClient(client);
                future.complete(consumer);
            }));

        return future;
    }

    private boolean handleConnResult(Mqtt5AsyncClient client, Mqtt5ConnAck connAck, Throwable ex,
        CompletableFuture<?> future) {
        if (ex != null) {
            future.completeExceptionally(ex);
            log.error("Client[{}] failed to connect to MQTT broker", extractClientId(client.getConfig()), ex);
            return true;
        }
        if (connAck.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
            future.completeExceptionally(new RuntimeException("ConnAck-ReasonCode: " + connAck.getReasonCode()));
            log.warn("Client[{}] was rejected by MQTT broker, ConnAck-ReasonCode={}",
                extractClientId(client.getConfig()),
                connAck.getReasonCode());
            return true;
        }
        return false;
    }

    private Mqtt5ClientBuilder getClientBuilder(String clientId, Supplier<Boolean> closed) {
        HostAndPort hostAndPort = parseServerUri(this.config.client.serverUri);
        Mqtt5ClientBuilder clientBuilder = Mqtt5Client.builder()
            .identifier(clientId)
            .transportConfig(MqttClientTransportConfig.builder()
                .serverHost(hostAndPort.getHost())
                .serverPort(hostAndPort.getPort())
                .socketConnectTimeout(3, TimeUnit.SECONDS)
                .mqttConnectTimeout(3, TimeUnit.SECONDS)
                .build());

        // Simple Auth with username and password
        if (StringUtils.isNotEmpty(this.config.client.username)
            && StringUtils.isNotEmpty(this.config.client.password)) {
            clientBuilder = clientBuilder
                .simpleAuth()
                .username(this.config.client.username)
                .password(this.config.client.password.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth();
        }

        // Auto reconnect with initial delay 100 mills, max delay 10 seconds
        clientBuilder = clientBuilder
            .automaticReconnect(MqttClientAutoReconnect.builder()
                .initialDelay(100, TimeUnit.MILLISECONDS)
                .maxDelay(10, TimeUnit.SECONDS)
                .build());

        // Listen to connection events
        clientBuilder = clientBuilder
            .addConnectedListener(new MqttClientConnectedListener() {
                @Override
                public void onConnected(MqttClientConnectedContext context) {
                    log.info("Client[{}] connected to MQTT broker {}",
                        extractClientId(context.getClientConfig()),
                        context.getClientConfig().getServerAddress());
                }
            })
            .addDisconnectedListener(new MqttClientDisconnectedListener() {
                @Override
                public void onDisconnected(MqttClientDisconnectedContext context) {
                    String clientId = extractClientId(context.getClientConfig());
                    log.warn("Client[{}] lost connection to MQTT broker {}, by {}",
                        clientId,
                        context.getClientConfig().getServerAddress(),
                        context.getSource().name(),
                        context.getCause());
                    if (closed.get()) {
                        log.warn("Client[{}] stops reconnecting to MQTT broker since the client wasn't created "
                            + "successfully or has been stopped already", clientId);
                        context.getReconnector().reconnect(false);
                    }
                }
            });

        return clientBuilder;
    }

    @Override
    public void close() throws Exception {
        // Nothing to close
    }

    private static MqttConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, MqttConfig.class);
    }

    private static HostAndPort parseServerUri(String serverUri) {
        Matcher matcher = server_uri_pattern.matcher(serverUri);
        if (matcher.find()) {
            String host = matcher.group(1);
            String portStr = matcher.group(2);
            int port = StringUtils.isNotEmpty(portStr) ? Integer.parseInt(portStr) : 80;
            return HostAndPort.fromParts(host, port);
        } else {
            throw new IllegalArgumentException("Invalid serverUri: " + serverUri);
        }
    }

    private static String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static String buildPublisherClientId() {
        return Joiner.on("_").join("benchmark", "pub", getRandomString(),
            System.currentTimeMillis());
    }

    private static String buildSubscriberClientId() {
        return Joiner.on("_").join("benchmark", "sub", getRandomString(),
            System.currentTimeMillis());
    }

    private static String extractClientId(MqttClientConfig clientConfig) {
        if (clientConfig.getClientIdentifier().isPresent()) {
            return clientConfig.getClientIdentifier().get().toString();
        }
        return "";
    }

    private static String toSharedSubscription(String topicFilter, String sharedGroup) {
        if (StringUtils.isEmpty(topicFilter)) {
            return topicFilter;
        }

        if (topicFilter.startsWith("$share")) {
            return topicFilter;
        }

        return Joiner.on('/').join("$share", sharedGroup, topicFilter);
    }

    // Extract publishTimestamp from user properties, return System.currentTimeMillis() if not found
    private static long extractPublishTimestamp(Mqtt5UserProperties userProperties) {
        List<? extends Mqtt5UserProperty> propertyList = userProperties.asList();
        for (Mqtt5UserProperty property : propertyList) {
            if (USER_PROPERTY_KEY_PUBLISH_TIMESTAMP.equals(property.getName())) {
                try {
                    return Long.parseLong(property.getValue().toString());
                } catch (NumberFormatException ignore) {
                    return System.currentTimeMillis();
                }
            }
        }
        return System.currentTimeMillis();
    }

    public static MqttQos intToQoS(int val) {
        MqttQos qos = MqttQos.fromCode(val);
        if (qos == null) {
            qos = MqttQos.AT_LEAST_ONCE;
        }
        return qos;
    }

    public static void closeClient(Mqtt5AsyncClient client) {
        if (client != null) {
            String clientId = String.valueOf(client.getConfig().getClientIdentifier());
            log.info("Client[{}] disconnecting...", clientId);
            client.disconnect().exceptionally(ex -> {
                log.error("Client[{}] failed to disconnect", clientId, ex);
                return null;
            });
        }
    }
}
