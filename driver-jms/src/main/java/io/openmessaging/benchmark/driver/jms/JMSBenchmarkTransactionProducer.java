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

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.jms.config.JMSConfig;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JMSBenchmarkTransactionProducer implements BenchmarkProducer {

    private final String destination;
    private final boolean useAsyncSend;
    private final Connection connection;
    private final List<JMSConfig.AddProperty> properties;

    // Private constructor - cannot throw exceptions
    private JMSBenchmarkTransactionProducer(
            Connection connection,
            String destination,
            boolean useAsyncSend,
            List<JMSConfig.AddProperty> properties) {
        this.destination = destination;
        this.useAsyncSend = useAsyncSend;
        this.connection = connection;
        this.properties = properties != null ? properties : Collections.emptyList();
    }

    /**
     * Factory method to create JMSBenchmarkTransactionProducer safely. This method handles all the
     * exception-throwing initialization logic.
     *
     * @param connection the JMS connection
     * @param destination the destination topic name
     * @param useAsyncSend whether to use asynchronous sending
     * @param properties additional properties to set on messages
     * @return a new JMSBenchmarkTransactionProducer instance
     * @throws Exception if initialization fails
     */
    public static JMSBenchmarkTransactionProducer create(
            Connection connection,
            String destination,
            boolean useAsyncSend,
            List<JMSConfig.AddProperty> properties)
            throws Exception {

        // For transaction producer, we don't need to initialize anything that can throw exceptions
        // in the constructor since sessions are created per-message in sendAsync()
        return new JMSBenchmarkTransactionProducer(connection, destination, useAsyncSend, properties);
    }

    @Override
    public void close() {}

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        try {
            // start a new Session every time, we cannot share the same Session
            // among the Producers because we want to have control over the commit operation
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createTopic(destination));
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(payload);
            if (key.isPresent()) {
                // a behaviour similar to https://activemq.apache.org/message-groups
                bytesMessage.setStringProperty("JMSXGroupID", key.get());
            }
            for (JMSConfig.AddProperty prop : properties) {
                bytesMessage.setStringProperty(prop.name, prop.value);
            }
            // Add a timer property for end to end
            bytesMessage.setLongProperty("E2EStartMillis", System.currentTimeMillis());
            if (useAsyncSend) {
                CompletableFuture<Void> res = new CompletableFuture<>();
                producer.send(
                        bytesMessage,
                        new CompletionListener() {
                            @Override
                            public void onCompletion(Message message) {
                                res.complete(null);
                            }

                            @Override
                            public void onException(Message message, Exception exception) {
                                log.info("send completed with error", exception);
                                res.completeExceptionally(exception);
                            }
                        });
                return res.whenCompleteAsync(
                        (msg, error) -> {
                            if (error == null) {
                                // you cannot close the producer and session inside the CompletionListener
                                try {
                                    session.commit();
                                } catch (JMSException err) {
                                    throw new CompletionException(err);
                                }
                            }
                            ensureClosed(producer, session);
                        });
            } else {

                try {
                    producer.send(bytesMessage);
                    session.commit();
                    CompletableFuture<Void> res = new CompletableFuture<>();
                    res.complete(null);
                    return res;
                } finally {
                    ensureClosed(producer, session);
                }
            }
        } catch (JMSException err) {
            CompletableFuture<Void> res = new CompletableFuture<>();
            res.completeExceptionally(err);
            return res;
        }
    }

    private void ensureClosed(MessageProducer producer, Session session) {
        try {
            producer.close();
        } catch (Throwable err) {
            log.error("Error closing producer {}", err.toString());
        }
        try {
            session.close();
        } catch (Throwable err) {
            log.error("Error closing session {}", err.toString());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkTransactionProducer.class);
}
