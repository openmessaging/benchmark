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
package io.openmessaging.benchmark.driver.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Config(
        @JsonProperty("replicationFactor") short replicationFactor,
        @JsonProperty("topicConfig") String topicConfig,
        @JsonProperty("commonConfig") String commonConfig,
        @JsonProperty("producerConfig") String producerConfig,
        @JsonProperty("consumerConfig") String consumerConfig) {
    @JsonCreator
    public Config {
        // Compact constructor body - validation can be added here if needed
    }

    // Default constructor for Jackson deserialization when no arguments are provided
    public Config() {
        this((short) 0, null, null, null, null);
    }
}
