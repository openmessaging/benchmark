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

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtemisBenchmarkProducer implements BenchmarkProducer {

    private final ClientSession session;
    private final ClientProducer producer;

    // Private constructor - cannot throw exceptions
    private ArtemisBenchmarkProducer(ClientSession session, ClientProducer producer) {
        this.session = session;
        this.producer = producer;
    }

    /**
     * Factory method to create ArtemisBenchmarkProducer safely. This method handles all the
     * exception-throwing initialization logic.
     *
     * @param address the address to send messages to
     * @param sessionFactory the client session factory
     * @return a new ArtemisBenchmarkProducer instance
     * @throws ActiveMQException if initialization fails
     */
    public static ArtemisBenchmarkProducer create(String address, ClientSessionFactory sessionFactory)
            throws ActiveMQException {

        ClientSession tempSession = null;
        ClientProducer tempProducer = null;

        try {
            tempSession = sessionFactory.createSession();
            tempProducer = tempSession.createProducer(address);
            tempSession.start();

            // Create the producer instance only after all operations succeed
            return new ArtemisBenchmarkProducer(tempSession, tempProducer);

        } catch (ActiveMQException e) {
            // Clean up resources if initialization fails
            if (tempProducer != null) {
                try {
                    tempProducer.close();
                } catch (ActiveMQException closeException) {
                    log.warn("Failed to close producer during cleanup", closeException);
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
        if (producer != null) {
            producer.close();
        }
        if (session != null) {
            session.close();
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        ClientMessage msg = session.createMessage(true /* durable */);
        msg.setTimestamp(System.currentTimeMillis());
        msg.getBodyBuffer().writeBytes(payload);

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            producer.send(
                    msg,
                    new SendAcknowledgementHandler() {
                        @Override
                        public void sendAcknowledged(Message message) {
                            future.complete(null);
                        }

                        @Override
                        public void sendFailed(Message message, Exception e) {
                            future.completeExceptionally(e);
                        }
                    });
        } catch (ActiveMQException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private static final Logger log = LoggerFactory.getLogger(ArtemisBenchmarkProducer.class);
}
