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
package io.openmessaging.benchmark.driver.mqtt5.client;

public class MqttClientConfig {
    /**
     * The MQTT server URI.
     */
    public String serverUri = "tcp://localhost:1883";

    /**
     * The username used for MQTT server-side authentication.
     */
    public String username = "";

    /**
     * The password that matches the username.
     */
    public String password = "";

    /**
     * The Quality of Service level for message delivery (0, 1, or 2).
     */
    public int qos = 1;

    /**
     * The topic prefix for topics used in the benchmark. No need to add a trailing slash.
     */
    public String topicPrefix = "benchmark";
}
