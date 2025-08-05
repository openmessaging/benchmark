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
package io.openmessaging.benchmark.driver.rabbitmq;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RabbitMqBenchmarkConsumer extends DefaultConsumer implements BenchmarkConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqBenchmarkConsumer.class);

    private final Channel channel;
    private final ConsumerCallback callback;

    /**
     * Private constructor for the RabbitMQ consumer.
     *
     * @param channel The RabbitMQ channel.
     * @param callback The callback to invoke on message receipt.
     */
    RabbitMqBenchmarkConsumer(Channel channel, ConsumerCallback callback) {
        super(channel);
        this.channel = channel;
        this.callback = callback;
    }

    /**
     * Safely creates and starts a new RabbitMQ benchmark consumer.
     *
     * @param channel The RabbitMQ channel to use.
     * @param queueName The name of the queue to consume from.
     * @param callback The callback for handling received messages.
     * @return A new, initialized {@link RabbitMqBenchmarkConsumer}.
     * @throws IOException if the consumer cannot be started.
     */
    public static RabbitMqBenchmarkConsumer create(
            Channel channel, String queueName, ConsumerCallback callback) throws IOException {
        RabbitMqBenchmarkConsumer consumer = new RabbitMqBenchmarkConsumer(channel, callback);
        channel.basicConsume(queueName, true, consumer);
        return consumer;
    }

    @Override
    public void handleDelivery(
            String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
        callback.messageReceived(body, properties.getTimestamp().getTime());
    }

    @Override
    public void close() throws Exception {
        try {
            channel.close();
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed", e);
        }
    }
}
