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
package io.openmessaging.benchmark.driver.natsjs;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.impl.NatsJsBenchmarkMessage;
import io.openmessaging.benchmark.driver.BenchmarkProducer;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NatsJsBenchmarkProducer implements BenchmarkProducer {
    // provided
    private final String subject;

    // internal
    private final JetStream js;

    public NatsJsBenchmarkProducer(NatsJsConfig config, Connection conn, String subject) throws IOException {
        this.subject = subject;
        js = conn.jetStream(); // maybe will use config later?
    }

    @Override public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        // The "key" here is a partition key, which we ignore...
        return js.publishAsync(new NatsJsBenchmarkMessage(subject, payload)).thenAccept(pa -> {});
    }

    @Override
    public void close() throws Exception {
    }
}
