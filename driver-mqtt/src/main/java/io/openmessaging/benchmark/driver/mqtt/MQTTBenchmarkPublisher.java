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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MQTTBenchmarkPublisher implements BenchmarkProducer {
    private final MqttAsyncClient mqttAsyncClient;
    private final String topic;
    private final int qos;

    public MQTTBenchmarkPublisher(final MqttAsyncClient mqttAsyncClient, final String topic, final int qos) {
        this.mqttAsyncClient = mqttAsyncClient;
        this.topic = topic;
        this.qos = qos;
    }

    @Override
    public CompletableFuture<Void> sendAsync(final Optional<String> key, final byte[] payload) {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            this.mqttAsyncClient.publish(this.topic, message, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    future.complete(null);
                }

                @Override public void onFailure(IMqttToken token, Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void close() throws Exception {
        this.mqttAsyncClient.disconnect();
    }
}
