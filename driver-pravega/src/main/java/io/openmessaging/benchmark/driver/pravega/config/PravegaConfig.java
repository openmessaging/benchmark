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
package io.openmessaging.benchmark.driver.pravega.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public record PravegaConfig(
        boolean includeTimestampInEvent,
        PravegaClientConfig client,
        PravegaWriterConfig writer,
        boolean createScope,
        boolean deleteStreams,
        boolean enableStreamAutoScaling,
        int eventsPerSecond,
        int kbytesPerSecond,
        boolean enableTransaction,
        int eventsPerTransaction) {

    public static final int DEFAULT_STREAM_AUTOSCALING_VALUE = -1;

    @JsonCreator
    public static PravegaConfig fromMap(Map<String, Object> properties) {
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        // Jackson will deserialize nested objects into Maps, which we can use to create the nested
        // records.
        PravegaClientConfig clientConfig =
                new PravegaClientConfig(
                        (String)
                                ((Map<String, Object>) properties.getOrDefault("client", Collections.emptyMap()))
                                        .get("controllerURI"),
                        (String)
                                ((Map<String, Object>) properties.getOrDefault("client", Collections.emptyMap()))
                                        .get("scopeName"));
        PravegaWriterConfig writerConfig =
                new PravegaWriterConfig(
                        (Boolean)
                                ((Map<String, Object>) properties.getOrDefault("writer", Collections.emptyMap()))
                                        .get("enableConnectionPooling"));

        return new PravegaConfig(
                getBoolean(properties, "includeTimestampInEvent").orElse(true),
                clientConfig,
                writerConfig,
                getBoolean(properties, "createScope").orElse(true),
                getBoolean(properties, "deleteStreams").orElse(true),
                getBoolean(properties, "enableStreamAutoScaling").orElse(false),
                getInt(properties, "eventsPerSecond").orElse(DEFAULT_STREAM_AUTOSCALING_VALUE),
                getInt(properties, "kbytesPerSecond").orElse(DEFAULT_STREAM_AUTOSCALING_VALUE),
                getBoolean(properties, "enableTransaction").orElse(false),
                getInt(properties, "eventsPerTransaction").orElse(100));
    }

    // Default constructor
    public PravegaConfig() {
        this(
                true,
                new PravegaClientConfig(),
                new PravegaWriterConfig(),
                true,
                true,
                false,
                DEFAULT_STREAM_AUTOSCALING_VALUE,
                DEFAULT_STREAM_AUTOSCALING_VALUE,
                false,
                100);
    }

    // Helper methods to safely extract values from the map
    private static Optional<Boolean> getBoolean(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(v -> Boolean.parseBoolean(v.toString()));
    }

    private static Optional<Integer> getInt(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(v -> Integer.parseInt(v.toString()));
    }
}
