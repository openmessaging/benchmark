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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class JMSBenchmarkProducer implements BenchmarkProducer {

    private final Session session;
    private final Destination destination;
    private final MessageProducer producer;
    private final boolean useAsyncSend;

    public JMSBenchmarkProducer(Session session, Destination destination, boolean useAsyncSend) throws Exception {
        this.session = session;
        this.destination = destination;
        this.useAsyncSend = useAsyncSend;
        this.producer = session.createProducer(destination);
    }

    @Override
    public void close() throws Exception {
        session.close();
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        CompletableFuture<Void> res = new CompletableFuture<>();
        try
        {
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(payload);
            if (key.isPresent())
            {
                bytesMessage.setStringProperty("JMSGroupId", key.get());
            }
            if (useAsyncSend) {
                producer.send(bytesMessage, new CompletionListener()
                {
                    @Override
                    public void onCompletion(Message message)
                    {
                        res.complete(null);
                    }

                    @Override
                    public void onException(Message message, Exception exception)
                    {
                        log.info("send completed with error", exception);
                        res.completeExceptionally(exception);
                    }
                });
            } else {
                producer.send(bytesMessage);
                res.complete(null);
            }
        } catch (JMSException err) {
            res.completeExceptionally(err);
        }
        return res;
    }
    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkProducer.class);
}
