/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.driver.artemis;

import java.util.concurrent.CompletableFuture;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class ArtemisBenchmarkProducer implements BenchmarkProducer {

    private final ClientSession session;
    private final ClientProducer producer;

    public ArtemisBenchmarkProducer(String address, ClientSessionFactory sessionFactory) throws ActiveMQException {
        session = sessionFactory.createSession();
        producer = session.createProducer(address);
        session.start();
    }

    @Override
    public void close() throws Exception {
        producer.close();
        session.close();
    }

    @Override
    public CompletableFuture<Void> sendAsync(String key, byte[] payload) {
        ClientMessage msg = session.createMessage(true /* durable */ );
        msg.setTimestamp(System.currentTimeMillis());
        msg.getBodyBuffer().writeBytes(payload);

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            producer.send(msg, message -> {
                future.complete(null);
            });
        } catch (ActiveMQException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

}
