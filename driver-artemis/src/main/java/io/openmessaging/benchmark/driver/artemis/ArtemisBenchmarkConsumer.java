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

public final class ArtemisBenchmarkConsumer implements BenchmarkConsumer {

    private final ClientSession session;
    private final ClientConsumer consumer;

    // Private constructor - cannot throw exceptions
    private ArtemisBenchmarkConsumer(ClientSession session, ClientConsumer consumer) {
        this.session = session;
        this.consumer = consumer;
    }

    /**
     * Factory method to create ArtemisBenchmarkConsumer safely. This method handles all the
     * exception-throwing initialization logic.
     *
     * @param topic the topic name to consume from
     * @param queueName the queue name for the consumer
     * @param sessionFactory the client session factory
     * @param callback the callback to handle received messages
     * @return a new ArtemisBenchmarkConsumer instance
     * @throws ActiveMQException if initialization fails
     */
    public static ArtemisBenchmarkConsumer create(
            String topic,
            String queueName,
            ClientSessionFactory sessionFactory,
            ConsumerCallback callback)
            throws ActiveMQException {

        ClientSession tempSession = null;
        ClientConsumer tempConsumer = null;

        try {
            tempSession = sessionFactory.createSession();
            tempSession.createQueue(
                    SimpleString.toSimpleString(topic),
                    RoutingType.MULTICAST,
                    SimpleString.toSimpleString(queueName),
                    true /* durable */);
            tempConsumer = tempSession.createConsumer(queueName);
            tempConsumer.setMessageHandler(
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

            tempSession.start();

            // Create the consumer instance only after all operations succeed
            return new ArtemisBenchmarkConsumer(tempSession, tempConsumer);

        } catch (ActiveMQException e) {
            // Clean up resources if initialization fails
            if (tempConsumer != null) {
                try {
                    tempConsumer.close();
                } catch (ActiveMQException closeException) {
                    log.warn("Failed to close consumer during cleanup", closeException);
                }
            }
            if (tempSession != null) {
                try {
                    tempSession.close();
                } catch (ActiveMQException closeException) {
                    log.warn("Failed to close session during cleanup", closeException);
                }
            }
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
        if (session != null) {
            session.close();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ArtemisBenchmarkConsumer.class);
}
