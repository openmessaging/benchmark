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
package io.openmessaging.benchmark.driver.nats;


import io.nats.client.JetStream;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NatsBenchmarkProducer implements BenchmarkProducer {
    private final String topic;

    private final JetStream jetStream;

    public NatsBenchmarkProducer(JetStream jetStream, final String topic) {
        this.jetStream = jetStream;
        this.topic = topic;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        writeLongToBytes(System.currentTimeMillis(), payload);
        return jetStream.publishAsync(topic, payload).thenApply(x -> null);
    }

    @Override
    public void close() throws Exception {}

    public static void writeLongToBytes(long l, byte[] dst) {
        for (int i = 7; i >= 0; i--) {
            dst[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
    }
}
