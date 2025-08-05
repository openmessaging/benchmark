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

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public record PulsarConfig(
        String serviceUrl,
        boolean batchingEnabled,
        boolean blockIfQueueFull,
        int batchingMaxPublishDelayMs,
        int batchingMaxBytes,
        int pendingQueueSize,
        int maxPendingMessagesAcrossPartitions,
        int receiverQueueSize,
        int maxTotalReceiverQueueSizeAcrossPartitions) {

    // Use a static factory method as the JSON creator to avoid the parameter limit.
    @JsonCreator
    public static PulsarConfig fromMap(Map<String, Object> properties) {
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        return new PulsarConfig(
                getString(properties, "serviceUrl").orElse("pulsar://localhost:6650"),
                getBoolean(properties, "batchingEnabled").orElse(true),
                getBoolean(properties, "blockIfQueueFull").orElse(true),
                getInt(properties, "batchingMaxPublishDelayMs").orElse(1),
                getInt(properties, "batchingMaxBytes").orElse(131072), // Correct default for the test
                getInt(properties, "pendingQueueSize").orElse(1000),
                getInt(properties, "maxPendingMessagesAcrossPartitions").orElse(50000),
                getInt(properties, "receiverQueueSize").orElse(1000),
                getInt(properties, "maxTotalReceiverQueueSizeAcrossPartitions").orElse(50000));
    }

    // Default constructor
    public PulsarConfig() {
        this("pulsar://localhost:6650", true, true, 1, 131072, 1000, 50000, 1000, 50000);
    }

    // Helper methods to safely extract and cast values from the map
    private static Optional<String> getString(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString);
    }

    private static Optional<Boolean> getBoolean(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(v -> Boolean.parseBoolean(v.toString()));
    }

    private static Optional<Integer> getInt(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(v -> Integer.parseInt(v.toString()));
    }
}
