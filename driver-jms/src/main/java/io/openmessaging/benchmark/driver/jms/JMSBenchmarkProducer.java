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

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class JMSBenchmarkProducer implements BenchmarkProducer {

    private final JMSContext context;
    private final Destination destination;

    public JMSBenchmarkProducer(JMSContext context, Destination destination) {
        this.context = context;
        this.destination = destination;
    }

    @Override
    public void close() throws Exception {
        context.close();
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        JMSProducer producer = context.createProducer();

        if (key.isPresent()) {
            producer.setProperty("JMSGroupId", key.get());
        }
        CompletableFuture<Void> res = new CompletableFuture<>();
        producer.setAsync(new CompletionListener() {
            @Override
            public void onCompletion(Message message)
            {
                res.complete(null);
            }

            @Override
            public void onException(Message message, Exception exception)
            {
                res.completeExceptionally(exception);
            }
        }).send(destination, payload);
        return res;
    }

}
