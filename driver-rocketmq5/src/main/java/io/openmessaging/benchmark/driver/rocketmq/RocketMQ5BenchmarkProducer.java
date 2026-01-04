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
package io.openmessaging.benchmark.driver.rocketmq;


import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.java.message.MessageBuilderImpl;

public class RocketMQ5BenchmarkProducer implements BenchmarkProducer {
    private final Producer rmqProducer;
    private final String rmqTopic;
    private final Boolean sendDelayMsg;
    private final Long delayTimeInSec;

    public RocketMQ5BenchmarkProducer(final Producer rmqProducer, final String rmqTopic) {
        this.rmqProducer = rmqProducer;
        this.rmqTopic = rmqTopic;
        this.sendDelayMsg = false;
        this.delayTimeInSec = 0L;
    }

    public RocketMQ5BenchmarkProducer(
            final Producer rmqProducer,
            final String rmqTopic,
            Boolean sendDelayMsg,
            Long delayTimeInSec) {
        this.rmqProducer = rmqProducer;
        this.rmqTopic = rmqTopic;
        this.sendDelayMsg = sendDelayMsg;
        this.delayTimeInSec = delayTimeInSec;
    }

    @Override
    public CompletableFuture<Void> sendAsync(final Optional<String> key, final byte[] payload) {
        MessageBuilder messageBuilder = new MessageBuilderImpl();
        messageBuilder.setBody(payload);
        messageBuilder.setTopic(this.rmqTopic);

        key.ifPresent(messageBuilder::setKeys);

        if (this.sendDelayMsg) {
            // 延时消息，单位秒（s），在指定延迟时间（当前时间之后）进行投递，例如消息在10秒后投递。
            long delayTime = System.currentTimeMillis() + this.delayTimeInSec * 1000;
            // 设置消息需要被投递的时间。
            messageBuilder.setDeliveryTimestamp(delayTime);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.rmqProducer
                .sendAsync(messageBuilder.build())
                .whenComplete(
                        (sendReceipt, throwable) -> {
                            if (sendReceipt != null) {
                                future.complete(null);
                            } else {
                                future.completeExceptionally(throwable);
                            }
                        });
        return future;
    }

    @Override
    public void close() throws Exception {
        if (rmqProducer != null) {
            rmqProducer.close();
        }
    }
}
