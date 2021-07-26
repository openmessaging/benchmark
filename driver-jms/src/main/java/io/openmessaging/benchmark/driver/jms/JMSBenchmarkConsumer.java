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
package io.openmessaging.benchmark.driver.jms;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

public class JMSBenchmarkConsumer implements BenchmarkConsumer {

    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;

    public JMSBenchmarkConsumer(Connection connection,
            Session session,
            MessageConsumer consumer, ConsumerCallback callback) throws Exception {
        this.connection = connection;
        this.consumer = consumer;
        this.session = session;
        consumer.setMessageListener(message -> {
            try {
                byte[] payload = message.getBody(byte[].class);
                callback.messageReceived(payload, message.getJMSTimestamp());

                message.acknowledge();
            } catch (Exception e) {
                log.warn("Failed to acknowledge message", e);
            }
        });
        // Kafka JMS client does not allow you to add a listener after the connection has been started
        connection.start();
    }

    @Override
    public void close() throws Exception {
        consumer.close();
        session.close();
        connection.close();
    }

    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkConsumer.class);
}
