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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

public class RabbitMqBenchmarkConsumer extends DefaultConsumer implements BenchmarkConsumer {

    private final Channel channel;
    private final ConsumerCallback callback;

    public RabbitMqBenchmarkConsumer(Channel channel, String queueName, ConsumerCallback callback) throws IOException {
        super(channel);

        this.channel = channel;
        this.callback = callback;
        channel.basicConsume(queueName, true, this);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
            throws IOException {
        callback.messageReceived(body, properties.getTimestamp().getTime());
    }

    @Override
    public void close() throws Exception {
        if (this.channel.isOpen()) {
            this.channel.close();
        }
    }

}
