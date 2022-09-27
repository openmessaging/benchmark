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
package io.openmessaging.benchmark.driver.kop;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import io.openmessaging.benchmark.driver.kop.config.ClientType;
import io.openmessaging.benchmark.driver.kop.config.Config;
import io.openmessaging.benchmark.driver.kop.config.PulsarConfig;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import org.testng.annotations.Test;

public class KopBenchmarkDriverTest {

    @Test
    public void testLoadDefaultConfig() throws URISyntaxException, IOException {
        final URL url = getClass().getClassLoader().getResource("kop_required.yaml");
        assertNotNull(url);

        final Config config = KopBenchmarkDriver.loadConfig(new File(url.toURI()));
        assertEquals(config.producerType, ClientType.PULSAR);
        assertEquals(config.consumerType, ClientType.KAFKA);
        assertEquals(config.pollTimeoutMs, 100);

        final PulsarConfig pulsarConfig = config.pulsarConfig;
        assertEquals(pulsarConfig.serviceUrl, "pulsar://localhost:6650");
        assertTrue(pulsarConfig.batchingEnabled);
        assertEquals(pulsarConfig.batchingMaxPublishDelayMs, 1);
        assertEquals(pulsarConfig.batchingMaxBytes, 131072);
        assertTrue(pulsarConfig.blockIfQueueFull);
        assertEquals(pulsarConfig.pendingQueueSize, 1000);
        assertEquals(pulsarConfig.maxPendingMessagesAcrossPartitions, 50000);
        assertEquals(pulsarConfig.maxTotalReceiverQueueSizeAcrossPartitions, 50000);
        assertEquals(pulsarConfig.receiverQueueSize, 1000);

        assertEquals(config.getKafkaProperties().get("bootstrap.servers"), "localhost:9092");
    }

    @Test
    public void testLoadCustomConfig() throws Exception {
        final URL url = this.getClass().getClassLoader().getResource("kop_custom.yaml");
        assertNotNull(url);

        final Config config = KopBenchmarkDriver.loadConfig(new File(url.toURI()));
        assertEquals(config.producerType, ClientType.KAFKA);
        assertEquals(config.consumerType, ClientType.PULSAR);
        assertEquals(config.pollTimeoutMs, 1000);

        final PulsarConfig pulsarConfig = config.pulsarConfig;
        assertEquals(pulsarConfig.serviceUrl, "pulsar+ssl://localhost:6651");
        assertFalse(pulsarConfig.batchingEnabled);
        assertEquals(pulsarConfig.batchingMaxPublishDelayMs, 10);
        assertEquals(pulsarConfig.batchingMaxBytes, 1310720);
        assertFalse(pulsarConfig.blockIfQueueFull);
        assertEquals(pulsarConfig.pendingQueueSize, 10000);
        assertEquals(pulsarConfig.maxPendingMessagesAcrossPartitions, 500000);
        assertEquals(pulsarConfig.maxTotalReceiverQueueSizeAcrossPartitions, 500000);
        assertEquals(pulsarConfig.receiverQueueSize, 10000);

        final Properties props = config.getKafkaProperties();
        assertEquals(props.size(), 3);
        assertEquals(props.get("bootstrap.servers"), "localhost:9092");
        assertEquals(props.get("linger.ms"), "1");
        assertEquals(props.get("batch.size"), "1048576");
    }

    @Test
    public void testLoadWrongKafkaConfig() throws Exception {
        final URL url = this.getClass().getClassLoader().getResource("kop_wrong_kafka_config.yaml");
        assertNotNull(url);

        final Config config = KopBenchmarkDriver.loadConfig(new File(url.toURI()));
        try {
            config.getKafkaProperties();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "bootstrap.servers is not set");
        }
    }
}
