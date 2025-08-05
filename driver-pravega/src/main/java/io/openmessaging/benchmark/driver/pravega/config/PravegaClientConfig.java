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

public record PravegaClientConfig(String controllerURI, String scopeName) {

    // Use a static factory method as the creator, which takes a map.
    @JsonCreator
    public static PravegaClientConfig fromMap(Map<String, Object> properties) {
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        return new PravegaClientConfig(
                getString(properties, "controllerURI").orElse("tcp://localhost:9090"),
                getString(properties, "scopeName").orElse("openmessaging-benchmark"));
    }

    // Default constructor
    public PravegaClientConfig() {
        this("tcp://localhost:9090", "openmessaging-benchmark");
    }

    private static Optional<String> getString(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString);
    }
}
