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
package io.openmessaging.benchmark.driver.natsJetStream;

import io.nats.client.JetStream;
import io.nats.client.Connection;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.openmessaging.benchmark.driver.BenchmarkProducer;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsBenchmarkProducer implements BenchmarkProducer {
    private String sub;
    private Connection con;
    private JetStream js;

    private Semaphore semaphore = new Semaphore(1000);

    public NatsBenchmarkProducer(Connection con, String sub, JetStream js) {
        this.con = con;
        this.sub = sub;
        this.js = js;
    }

    @Override public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {

        // The benchmark wants a CompletableFuture<Void>, but NATS JS async idiom is CompletableFuture<PublishAck>

        // The "key" here is a partition key, which we ignore...

        CompletableFuture<Void> voidFuture = new CompletableFuture<Void>();

        //
        Headers hs = new Headers();
        hs.add("pubstamp", Long.toString(System.nanoTime()));

        Message msg = NatsMessage.builder()
                .subject(sub)
                .headers(hs)
                .data(payload)
                .build();

        try {
            semaphore.acquire();

            // Try to redefine voidFuture as chain from completed (or exceptionally) CompletableFuture<PublishAck>
            voidFuture = js.publishAsync(msg)
                    .thenAccept(pa -> {
                        pa.getSeqno();
                        // An exception should result and bubble to voidFuture if PublishAck not result
                    });

        } catch (Exception e) {
            log.error("send exception" + e);
            voidFuture.exceptionally(null);
        } finally {
            semaphore.release();
        }

        return voidFuture;
    }

    @Override public void close() throws Exception {
        log.info("close a producer");
        con.flush(Duration.ofSeconds(10));
        con.close();
    }
    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkProducer.class);
}
