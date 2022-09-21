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
package io.openmessaging.benchmark.driver.kop.config;

public class PulsarConfig {

    public String serviceUrl;

    // producer configs
    public boolean batchingEnabled = true;
    public boolean blockIfQueueFull = true;
    public int batchingMaxPublishDelayMs = 1;
    public int batchingMaxBytes = 128 * 1024;
    public int pendingQueueSize = 1000;
    public int maxPendingMessagesAcrossPartitions = 50000;

    // consumer configs
    public int maxTotalReceiverQueueSizeAcrossPartitions = 50000;
    public int receiverQueueSize = 1000;
}
