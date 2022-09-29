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


import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerConfig;

public class Config {

    public ClientType producerType;
    public ClientType consumerType;
    public long pollTimeoutMs = 100;
    public PulsarConfig pulsarConfig;
    public String kafkaConfig;

    public Properties getKafkaProperties() {
        if (StringUtils.isEmpty(kafkaConfig)) {
            throw new IllegalArgumentException(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG + " is not set");
        }
        final Properties props = new Properties();
        try {
            props.load(new StringReader(kafkaConfig));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        if (props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) == null) {
            throw new IllegalArgumentException(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG + " is not set");
        }
        return props;
    }
}
