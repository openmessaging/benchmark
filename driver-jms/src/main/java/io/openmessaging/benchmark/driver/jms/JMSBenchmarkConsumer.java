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
package io.openmessaging.benchmark.driver.jms;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JMSBenchmarkConsumer implements BenchmarkConsumer {

    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;
    private final boolean useGetBody;

    // Private constructor - cannot throw exceptions
    private JMSBenchmarkConsumer(
            Connection connection, Session session, MessageConsumer consumer, boolean useGetBody) {
        this.connection = connection;
        this.consumer = consumer;
        this.session = session;
        this.useGetBody = useGetBody;
    }

    /**
     * Factory method to create JMSBenchmarkConsumer safely. This method handles all the
     * exception-throwing initialization logic.
     *
     * @param connection the JMS connection
     * @param session the JMS session
     * @param consumer the message consumer
     * @param callback the callback to handle received messages
     * @param useGetBody whether to use getBody method for message extraction
     * @return a new JMSBenchmarkConsumer instance
     * @throws Exception if initialization fails
     */
    public static JMSBenchmarkConsumer create(
            Connection connection,
            Session session,
            MessageConsumer consumer,
            ConsumerCallback callback,
            boolean useGetBody)
            throws Exception {

        try {
            consumer.setMessageListener(
                    message -> {
                        try {
                            byte[] payload = getPayload(message, useGetBody);
                            callback.messageReceived(payload, message.getLongProperty("E2EStartMillis"));
                            message.acknowledge();
                        } catch (Throwable e) {
                            log.warn("Failed to acknowledge message", e);
                        }
                    });
            // Kafka JMS client does not allow you to add a listener after the connection has been started
            connection.start();

            // Create the consumer instance only after all operations succeed
            return new JMSBenchmarkConsumer(connection, session, consumer, useGetBody);

        } catch (Exception e) {
            // Clean up resources if initialization fails
            try {
                consumer.close();
            } catch (Exception closeException) {
                log.warn("Failed to close consumer during cleanup", closeException);
            }
            try {
                session.close();
            } catch (Exception closeException) {
                log.warn("Failed to close session during cleanup", closeException);
            }
            try {
                connection.close();
            } catch (Exception closeException) {
                log.warn("Failed to close connection during cleanup", closeException);
            }
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        // This exception may be thrown: java.util.concurrent.ExecutionException:
        // java.util.ConcurrentModificationException: KafkaConsumer is not safe for multi-threaded
        // access
        // See https://jakarta.ee/specifications/platform/8/apidocs/javax/jms/session#close--
        // and https://jakarta.ee/specifications/platform/8/apidocs/javax/jms/connection#close--
        // It should be enough to just close the connection.
        connection.close();
    }

    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkConsumer.class);

    private static byte[] getPayload(Message message, boolean useGetBody) throws Exception {
        if (useGetBody) {
            return message.getBody(byte[].class);
        } else {
            BytesMessage msg = (BytesMessage) message;
            byte[] res = new byte[(int) msg.getBodyLength()];
            msg.readBytes(res);
            return res;
        }
    }
}
