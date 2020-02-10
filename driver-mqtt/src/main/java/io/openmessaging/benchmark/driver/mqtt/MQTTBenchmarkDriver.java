/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.openmessaging.benchmark.driver.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.BaseEncoding;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.mqtt.client.MQTTClientConfig;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1_1;

public class MQTTBenchmarkDriver implements BenchmarkDriver {
    public MQTTClientConfig mqttClientConfig;
    private static MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();

    @Override
    public void initialize(final File configurationFile, final StatsLogger statsLogger) throws IOException {
        this.mqttClientConfig = readConfig(configurationFile);
        mqttConnectOptions.setUserName(this.mqttClientConfig.userName);
        mqttConnectOptions.setPassword(this.mqttClientConfig.password.toCharArray());
        mqttConnectOptions.setCleanSession(this.mqttClientConfig.cleanSession);
        mqttConnectOptions.setKeepAliveInterval(this.mqttClientConfig.keepAliveInterval);
        mqttConnectOptions.setMaxInflight(this.mqttClientConfig.maxInflight);
        mqttConnectOptions.setAutomaticReconnect(false);
        mqttConnectOptions.setConnectionTimeout(10000);
        mqttConnectOptions.setMqttVersion(MQTT_VERSION_3_1_1);
    }

    @Override
    public String getTopicNamePrefix() {
        return "MQTT-Benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(final String topic, final int partitions) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(final String topic) {
        MqttAsyncClient mqttProducer = null;
        try {
            String clientId = this.mqttClientConfig.clientIdPrefix + "-" + getRandomString();
            mqttProducer = new MqttAsyncClient(this.mqttClientConfig.brokerAddr, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            e.printStackTrace();
        }
        mqttProducer.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void connectionLost(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                System.out.println(
                    "receive msg from topic " + s + " , body is " + new String(mqttMessage.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
            }
        });
        try {
            mqttProducer.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("connect success");
                }

                @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("connect failed");
                }
            }).waitForCompletion();
        } catch (MqttException e) {
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(new MQTTBenchmarkPublisher(mqttProducer, topic, this.mqttClientConfig.pubQos));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(final String topic, final String subscriptionName,
        final ConsumerCallback consumerCallback) {
        final Integer subQos = this.mqttClientConfig.subQos;
        MqttAsyncClient mqttConsumer = null;
        try {
            String clientId = this.mqttClientConfig.clientIdPrefix + "-" + getRandomString();
            mqttConsumer = new MqttAsyncClient(this.mqttClientConfig.brokerAddr, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            e.printStackTrace();
        }
        mqttConsumer.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void connectionLost(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                consumerCallback.messageReceived(mqttMessage.getPayload(), System.currentTimeMillis());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                System.out.println("send msg succeed topic is : " + iMqttDeliveryToken.getTopics()[0]);
            }
        });
        try {
            MqttAsyncClient finalMqttConsumer = mqttConsumer;
            mqttConsumer.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("connect success");
                    try {
                        finalMqttConsumer.subscribe(topic, subQos);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

                @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("connect failed");
                }
            }).waitForCompletion();
        } catch (MqttException e) {
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(new MQTTBenchmarkSubscriber(mqttConsumer));
    }

    @Override
    public void close() throws Exception {
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static MQTTClientConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, MQTTClientConfig.class);
    }

    private static final Random random = new Random();

    private static final String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(MQTTBenchmarkDriver.class);
}
