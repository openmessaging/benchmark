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

public class MqttConsumerConfig {
    /** Consume in clean session mode or not. */
    public Boolean cleanSession = true;

    /** The session expiry interval in seconds when cleanSession is false. */
    public Integer sessionExpiryInterval = 3 * 24 * 60 * 60;

    /** The maximum number of unacknowledged QoS 1 and 2 messages. */
    public Integer receiveMaximum = 256;
}
