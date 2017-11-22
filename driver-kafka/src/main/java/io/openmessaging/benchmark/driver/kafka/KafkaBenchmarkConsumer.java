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
package io.openmessaging.benchmark.driver.kafka;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaBenchmarkConsumer implements BenchmarkConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaBenchmarkConsumer.class);

    private final KafkaConsumer<byte[], byte[]> consumer;

    private final ExecutorService executor;

    public KafkaBenchmarkConsumer(KafkaConsumer<byte[], byte[]> consumer) {
        this.consumer = consumer;
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void close() throws Exception {
        try {
            // We try to close the consumer in the load generator thread because consumer is not thread-safe.
            consumer.close();
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("This consumer has already been closed.")) {
                log.info("Successfully closed consumer");
            } else {
                log.error("Failed consumer close", e);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public CompletableFuture<Void> receiveAsync(ConsumerCallback callback, final boolean testCompleted) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                while (!testCompleted) {
                    ConsumerRecords<byte[], byte[]> records = consumer.poll(100);
                    Map<TopicPartition, OffsetAndMetadata> offsetMap = new HashMap<>();
                    for (ConsumerRecord<byte[], byte[]> record : records) {
                        callback.messageReceived(record.value(), System.nanoTime());

                        offsetMap.put(new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset()));
                    }

                    if (!offsetMap.isEmpty()) {
                        consumer.commitSync(offsetMap);
                    }
                }

                consumer.close();
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            future.complete(null);
        });
        return future;
    }
}
