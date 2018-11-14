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

import com.rabbitmq.client.ConfirmListener;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMqBenchmarkProducer implements BenchmarkProducer {

    private final Channel channel;
    private final String exchange;
    private final ConfirmListener listener;
    /**To record msg and it's future structure.**/
    volatile SortedSet<Long> ackSet = Collections.synchronizedSortedSet(new TreeSet<Long>());
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> futureConcurrentHashMap = new ConcurrentHashMap<>();
    private boolean messagePersistence = false;

    public RabbitMqBenchmarkProducer(Channel channel, String exchange, boolean messagePersistence) {
        this.channel = channel;
        this.exchange = exchange;
        this.messagePersistence = messagePersistence;
        this.listener = new ConfirmListener() {
            @Override
            public void handleNack(long deliveryTag, boolean multiple) throws IOException {
                if (multiple) {
                    SortedSet<Long> treeHeadSet = ackSet.headSet(deliveryTag + 1);
                    synchronized(ackSet) {
                        for(Iterator iterator = treeHeadSet.iterator(); iterator.hasNext();) {
                            long value = (long)iterator.next();
                            iterator.remove();
                            CompletableFuture<Void> future = futureConcurrentHashMap.get(value);
                            if (future != null) {
                                future.completeExceptionally(null);
                                futureConcurrentHashMap.remove(value);
                            }
                        }
                        treeHeadSet.clear();
                    }

                } else {
                    CompletableFuture<Void> future = futureConcurrentHashMap.get(deliveryTag);
                    if (future != null) {
                        future.completeExceptionally(null);
                        futureConcurrentHashMap.remove(deliveryTag);
                    }
                    ackSet.remove(deliveryTag);
                }
            }
            @Override
            public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                if (multiple) {
                    SortedSet<Long> treeHeadSet = ackSet.headSet(deliveryTag + 1);
                    synchronized(ackSet) {
                        for(long value : treeHeadSet) {
                            CompletableFuture<Void> future = futureConcurrentHashMap.get(value);
                            if (future != null) {
                                future.complete(null);
                                futureConcurrentHashMap.remove(value);
                            }
                        }
                        treeHeadSet.clear();
                    }
                } else {
                    CompletableFuture<Void> future = futureConcurrentHashMap.get(deliveryTag);
                    if (future != null) {
                        future.complete(null);
                        futureConcurrentHashMap.remove(deliveryTag);
                    }
                    ackSet.remove(deliveryTag);
                }

            }
        };
        channel.addConfirmListener(listener);
    }

    @Override
    public void close() throws Exception {
        if (channel.isOpen()) {
            channel.removeConfirmListener(listener);
            channel.close();
        }
    }

    private static final BasicProperties defaultProperties = new BasicProperties();

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        BasicProperties.Builder builder = defaultProperties.builder().timestamp(new Date());
        if (messagePersistence) {
            builder.deliveryMode(2);
        }
        BasicProperties props = builder.build();
        CompletableFuture<Void> future = new CompletableFuture<>();
        long msgId = channel.getNextPublishSeqNo();
        ackSet.add(msgId);
        futureConcurrentHashMap.putIfAbsent(msgId, future);
        try {
            channel.basicPublish(exchange, key.orElse(""), props, payload);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

}
