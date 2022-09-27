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

public class JMSBenchmarkConsumer implements BenchmarkConsumer {

    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;
    private final boolean useGetBody;

    public JMSBenchmarkConsumer(
            Connection connection,
            Session session,
            MessageConsumer consumer,
            ConsumerCallback callback,
            boolean useGetBody)
            throws Exception {
        this.connection = connection;
        this.consumer = consumer;
        this.session = session;
        this.useGetBody = useGetBody;
        consumer.setMessageListener(
                message -> {
                    try {
                        byte[] payload = getPayload(message);
                        callback.messageReceived(payload, message.getLongProperty("E2EStartMillis"));
                        message.acknowledge();
                    } catch (Throwable e) {
                        log.warn("Failed to acknowledge message", e);
                    }
                });
        // Kafka JMS client does not allow you to add a listener after the connection has been started
        connection.start();
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

    private byte[] getPayload(Message message) throws Exception {
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
