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


import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;

public class MqttBenchmarkConsumer implements BenchmarkConsumer {
    private Mqtt5AsyncClient client;
    private volatile boolean closed = false;

    public MqttBenchmarkConsumer() {}

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
