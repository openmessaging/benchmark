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
package io.openmessaging.benchmark.driver.pulsar;


import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.TypedMessageBuilder;

public class PulsarBenchmarkProducer implements BenchmarkProducer {

    private final Producer<byte[]> producer;

    public PulsarBenchmarkProducer(Producer<byte[]> producer) {
        this.producer = producer;
    }

    @Override
    public void close() throws Exception {
        producer.close();
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        TypedMessageBuilder<byte[]> msgBuilder = producer.newMessage().value(payload);
        if (key.isPresent()) {
            msgBuilder.key(key.get());
        }

        return msgBuilder.sendAsync().thenApply(msgId -> null);
    }
}
