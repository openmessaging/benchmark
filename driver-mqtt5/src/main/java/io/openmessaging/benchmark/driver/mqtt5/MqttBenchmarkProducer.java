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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttBenchmarkProducer implements BenchmarkProducer {

    private static final Logger log = LoggerFactory.getLogger(MqttBenchmarkProducer.class);

    private final String topic;
    private final MqttQos qos;
    private Mqtt5AsyncClient client;
    private volatile boolean closed = false;

    public MqttBenchmarkProducer(String topic, int qosCode) {
        this.topic = topic;
        this.qos = MqttBenchmarkDriver.intToQoS(qosCode);
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {

        Mqtt5UserProperties properties = Mqtt5UserProperties.builder()
            .add(MqttBenchmarkDriver.USER_PROPERTY_KEY_PUBLISH_TIMESTAMP,
                MqttUtf8String.of(String.valueOf(System.currentTimeMillis())))
            .build();
        Mqtt5Publish message = Mqtt5Publish.builder()
            .topic(topic)
            .payload(payload)
            .qos(qos)
            .userProperties(properties)
            .build();

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.client.publish(message)
            .whenComplete(((result, ex) -> {
                if (ex != null || result.getError().isPresent()) {
                    Throwable error = ex != null ? ex : result.getError().get();
                    future.completeExceptionally(error);
                    log.error("Client[{}] failed to publish MQTT message, topic={}", topic, error);
                } else {
                    future.complete(null);
                }
            }));

        return future;
    }

    @Override
    public void close() throws Exception {
        MqttBenchmarkDriver.closeClient(client);
        closed = true;
    }

    public void setClient(Mqtt5AsyncClient client) {
        this.client = client;
    }

    public boolean isClosed() {
        return closed;
    }
}
