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
package io.openmessaging.benchmark.driver.nats;

import io.nats.client.ConnectionListener;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import io.nats.client.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import javax.sql.ConnectionEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsBenchmarkProducer implements BenchmarkProducer {
    private final String topic;
    private final Connection natsProducer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Semaphore semaphore = new Semaphore(1000);

    public NatsBenchmarkProducer(final Connection natsProducer, final String topic) {
        this.natsProducer = natsProducer;
        this.topic = topic;
    }

    @Override public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            semaphore.acquire();

            executor.submit(() -> {
                try {
                    natsProducer.publish(topic, Long.toString(System.currentTimeMillis()), payload);
//                    natsProducer.flush(Duration.ofSeconds(1));
                } catch (Exception e) {
                    log.error("send exception" + e);
                    future.exceptionally(null);
                } finally {
                    semaphore.release();
                }
                future.complete(null);

            });
        } catch (Exception e) {
            log.error("send exception", e);
            future.exceptionally(null);
            semaphore.release();
        }
        return future;
    }

    @Override public void close() throws Exception {
        log.info("close a producer");
        natsProducer.close();
    }
    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkProducer.class);
}
