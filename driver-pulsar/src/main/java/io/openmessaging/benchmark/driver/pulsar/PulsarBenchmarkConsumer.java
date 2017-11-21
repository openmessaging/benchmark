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
package io.openmessaging.benchmark.driver.pulsar;

import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.pulsar.client.api.Consumer;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;

import java.util.concurrent.CompletableFuture;

public class PulsarBenchmarkConsumer implements BenchmarkConsumer {

    private final Consumer consumer;

    public PulsarBenchmarkConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void close() throws Exception {
        consumer.close();
    }

    @Override
    public CompletableFuture<Void> receiveAsync(ConsumerCallback callback) {
        // TODO:
        if (callback == null) {
            throw new RuntimeException("Unimplemented consumer for pulsar");
        }
        return null;
    }
}
