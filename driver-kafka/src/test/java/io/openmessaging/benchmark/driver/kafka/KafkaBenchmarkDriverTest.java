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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KafkaBenchmarkDriverTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @CsvSource({
        "client.id=test_az={zone.id},\"\",\"\",test_az=az0,test_az=az0",
        "client.id=test_az={zone.id},client.id=prod_az={zone.id},client.id=cons_az={zone.id},prod_az=az0,cons_az=az0",
        "\"\",client.id=prod_az={zone.id},client.id=cons_az={zone.id},prod_az=az0,cons_az=az0",
        "\"\",client.id=prod_az={zone.id},\"\",prod_az=az0,",
        "\"\",\"\",client.id=cons_az={zone.id},,cons_az=az0"
    })
    void testInitClientIdWithZoneId(
            String commonConfig,
            String producerConfig,
            String consumerConfig,
            String producerClientId,
            String consumerClientId)
            throws Exception {
        // Given these configs
        final Path configPath = tempDir.resolve("config");
        Config config = new Config();
        config.replicationFactor = 1;
        config.commonConfig = "bootstrap.servers=localhost:9092\n" + commonConfig;
        config.producerConfig = producerConfig;
        config.consumerConfig = consumerConfig;
        config.topicConfig = "";

        // and the system property set for zone id
        System.setProperty("zone.id", "az0");

        try (KafkaBenchmarkDriver driver = new KafkaBenchmarkDriver()) {
            // When initializing kafka driver
            Files.write(configPath, KafkaBenchmarkDriver.mapper.writeValueAsBytes(config));
            driver.initialize(configPath.toFile(), null);

            // Then
            if (producerClientId != null) {
                assertThat(driver.producerProperties).containsEntry("client.id", producerClientId);
            } else {
                assertThat(driver.producerProperties).doesNotContainKey("client.id");
            }
            if (consumerClientId != null) {
                assertThat(driver.consumerProperties).containsEntry("client.id", consumerClientId);
            } else {
                assertThat(driver.consumerProperties).doesNotContainKey("client.id");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "client.rack=test_az={zone.id},\"\",\"\",test_az=az0,test_az=az0",
        "client.rack=test_az={zone.id},client.rack=prod_az={zone.id},client.rack=cons_az={zone.id},prod_az=az0,cons_az=az0",
        "\"\",client.rack=prod_az={zone.id},client.rack=cons_az={zone.id},prod_az=az0,cons_az=az0",
        "\"\",client.rack=prod_az={zone.id},\"\",prod_az=az0,",
        "\"\",\"\",client.rack=cons_az={zone.id},,cons_az=az0"
    })
    void testInitClientRackWithZoneId(
        String commonConfig,
        String producerConfig,
        String consumerConfig,
        String producerClientRack,
        String consumerClientRack)
        throws Exception {
        // Given these configs
        final Path configPath = tempDir.resolve("config");
        Config config = new Config();
        config.replicationFactor = 1;
        config.commonConfig = "bootstrap.servers=localhost:9092\n" + commonConfig;
        config.producerConfig = producerConfig;
        config.consumerConfig = consumerConfig;
        config.topicConfig = "";

        // and the system property set for zone id
        System.setProperty("zone.id", "az0");

        try (KafkaBenchmarkDriver driver = new KafkaBenchmarkDriver()) {
            // When initializing kafka driver
            Files.write(configPath, KafkaBenchmarkDriver.mapper.writeValueAsBytes(config));
            driver.initialize(configPath.toFile(), null);

            // Then
            if (producerClientRack != null) {
                assertThat(driver.producerProperties).containsEntry("client.rack", producerClientRack);
            } else {
                assertThat(driver.producerProperties).doesNotContainKey("client.rack");
            }
            if (consumerClientRack != null) {
                assertThat(driver.consumerProperties).containsEntry("client.rack", consumerClientRack);
            } else {
                assertThat(driver.consumerProperties).doesNotContainKey("client.rack");
            }
        }
    }
}
