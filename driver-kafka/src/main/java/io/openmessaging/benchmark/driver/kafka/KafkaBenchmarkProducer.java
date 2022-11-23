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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class KafkaBenchmarkProducer implements BenchmarkProducer {

    private final KafkaProducer<String, byte[]> producer;
    private final String topic;

    private final int batchSize;

    private final boolean useTransactions;

    public KafkaBenchmarkProducer(KafkaProducer<String, byte[]> producer, String topic,
                                  int batchSize, boolean useTransactions) {
        this.producer = producer;
        this.topic = topic;
        this.batchSize = batchSize;
        this.useTransactions = useTransactions;
    }

    @Override
    public CompletableFuture<Integer> sendAsync(Optional<String> key, byte[] payload) {
        if (useTransactions) {
            // with transactions, we must "block", because the KafkaProducer can do only one transaction at a time
            producer.beginTransaction();
            CompletableFuture<Integer> result = internalSendAsync(key, payload);
            result.join();
            producer.commitTransaction();
            return result;
        } else {
            return internalSendAsync(key, payload);
        }
    }

    private CompletableFuture<Integer> internalSendAsync(Optional<String> key, byte[] payload) {

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key.orElse(null), payload);
        if (batchSize <= 1) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(exception);
                } else {
                    future.complete(1);
                }
            });
            return future;
        }

        List<CompletableFuture> handles = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            handles.add(future);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(exception);
                } else {
                    future.complete(1);
                }
            });
        }
        return CompletableFuture
                .allOf(handles.toArray(new CompletableFuture[0])).thenApply(___ -> {
                    return batchSize;
                });
    }

    @Override
    public void close() throws Exception {
        producer.close();
    }

}
