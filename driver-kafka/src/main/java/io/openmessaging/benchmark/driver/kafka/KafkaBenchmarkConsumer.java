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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

public class KafkaBenchmarkConsumer implements BenchmarkConsumer {

    private final KafkaConsumer<byte[], byte[]> consumer;

    private final ExecutorService executor;

    public KafkaBenchmarkConsumer(KafkaConsumer<byte[], byte[]> consumer, ConsumerCallback callback) {
        this.consumer = consumer;
        this.executor = Executors.newSingleThreadExecutor();

        this.executor.execute(() -> {
            while (true) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(100);
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    callback.messageReceived(record.value());

                    Map<TopicPartition, OffsetAndMetadata> offsetMap = new HashMap<>();
                    offsetMap.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset()));
                    consumer.commitAsync(offsetMap, (offsets, exception) -> {
                        // Offset committed
                    });
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        consumer.close();
        executor.shutdownNow();
    }

}
