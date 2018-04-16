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
package io.openmessaging.benchmark.driver.rabbitmq;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class RabbitMqBenchmarkProducer implements BenchmarkProducer {

    private final Channel channel;
    private final String exchange;

    public RabbitMqBenchmarkProducer(Channel channel, String exchange) {
        this.channel = channel;
        this.exchange = exchange;
    }

    @Override
    public void close() throws Exception {

    }

    private static final BasicProperties defaultProperties = new BasicProperties();

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        BasicProperties props = defaultProperties.builder().timestamp(new Date()).build();
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            channel.basicPublish(exchange, key.orElse(null), props, payload);
            channel.waitForConfirms();
            future.complete(null);
        } catch (IOException | InterruptedException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

}
