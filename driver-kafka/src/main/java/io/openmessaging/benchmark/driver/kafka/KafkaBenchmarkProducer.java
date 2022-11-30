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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBenchmarkProducer implements BenchmarkProducer {

    final String localId;

    private final KafkaProducer<String, byte[]> producer;

    private final BlockingQueue<KafkaProducer<String, byte[]>> transactions;
    private final List<KafkaProducer<String, byte[]>> transactionsCopy;
    private final String topic;

    private final Config config;

    public KafkaBenchmarkProducer(Config config, Properties producerProperties, String topic) {
        String id;
        try {
            id = InetAddress.getLocalHost().getHostName() + "_" + UUID.randomUUID();
        } catch (Exception err) {
            id = UUID.randomUUID().toString();
        }
        localId = id;

        this.config = config;


        if (config.useTransactions) {
            // in Kafka one Producer can run only 1 transaction at a time,
            // so if you want to have N concurrent transactions you have to start N
            // producers
            // each Producer must have a unique "transactional.id"
            this.transactions = new ArrayBlockingQueue<>(config.maxConcurrentTransactions);
            this.transactionsCopy = new ArrayList<>(config.maxConcurrentTransactions);
            log.info("Creating a pool of {} transactions", config.maxConcurrentTransactions);
            for (int i = 0; i < config.maxConcurrentTransactions; i++) {
                Properties copy = new Properties();
                copy.putAll(producerProperties);
                copy.put("transactional.id", localId + "_tx_" + i);
                log.info("Creating transactional producer with config {}", copy);
                KafkaProducer transaction = new KafkaProducer<>(copy);
                transaction.initTransactions();
                transactions.add(transaction);
                transactionsCopy.add(transaction);
            }
            this.producer = null;
        } else {
            this.transactions = null;
            this.transactionsCopy = null;
            log.info("Creating non-transactional producer with config {}", producerProperties);
            this.producer = new KafkaProducer<>(producerProperties);
        }

        this.topic = topic;
    }

    @Override
    public CompletableFuture<Integer> sendAsync(Optional<String> key, byte[] payload) {
        if (config.useTransactions) {
            try {
                // there is a bounded number of concurrent transactions
                // this "take" method blocks until there is an available transaction in the pool
                KafkaProducer<String, byte[]> transaction = transactions.take();
                try {
                    transaction.beginTransaction();
                } catch (Exception err) {
                    // add the transaction back to the pool
                    transactions.add(transaction);
                    throw err;
                }
                CompletableFuture<Integer> result = internalSendAsync(transaction, key, payload)
                        .thenApplyAsync((numMessages) -> {
                    // commit
                    transaction.commitTransaction();
                    return numMessages;
                });

                // add back the transaction to the pool
                result.whenComplete( (numberOfMessages, error) -> {
                    transactions.add(transaction);
                });

                return result;
            } catch (Exception err) {
                CompletableFuture<Integer> result = new CompletableFuture<>();
                result.completeExceptionally(err);
                return result;
            }
        } else {
            return internalSendAsync(producer, key, payload);
        }
    }

    private CompletableFuture<Integer> internalSendAsync(KafkaProducer<String, byte[]> producer,
                                                         Optional<String> key, byte[] payload) {

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key.orElse(null), payload);
        if (config.batchSize <= 1) {
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

        List<CompletableFuture> handles = new ArrayList<>(config.batchSize);
        for (int i = 0; i < config.batchSize; i++) {
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
                    return config.batchSize;
                });
    }

    @Override
    public void close() throws Exception {
        if (producer != null) {
            producer.close();
        }
        if (transactionsCopy != null) {
            for (KafkaProducer<String, byte[]> prod : transactionsCopy) {
                prod.close();
            }
        }

    }

    private static final Logger log = LoggerFactory.getLogger(KafkaBenchmarkProducer.class);
}
