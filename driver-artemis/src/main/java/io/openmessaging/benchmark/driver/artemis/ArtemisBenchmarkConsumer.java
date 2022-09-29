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
package io.openmessaging.benchmark.driver.artemis;


import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtemisBenchmarkConsumer implements BenchmarkConsumer {

    private final ClientSession session;
    private final ClientConsumer consumer;

    public ArtemisBenchmarkConsumer(
            String topic,
            String queueName,
            ClientSessionFactory sessionFactory,
            ConsumerCallback callback)
            throws ActiveMQException {
        session = sessionFactory.createSession();
        session.createQueue(
                SimpleString.toSimpleString(topic),
                RoutingType.MULTICAST,
                SimpleString.toSimpleString(queueName),
                true /* durable */);
        consumer = session.createConsumer(queueName);
        consumer.setMessageHandler(
                message -> {
                    byte[] payload = new byte[message.getBodyBuffer().readableBytes()];
                    message.getBodyBuffer().readBytes(payload);
                    callback.messageReceived(payload, message.getTimestamp());
                    try {
                        message.acknowledge();
                    } catch (ActiveMQException e) {
                        log.warn("Failed to acknowledge message", e);
                    }
                });

        session.start();
    }

    @Override
    public void close() throws Exception {
        consumer.close();
        session.close();
    }

    private static final Logger log = LoggerFactory.getLogger(ArtemisBenchmarkConsumer.class);
}
