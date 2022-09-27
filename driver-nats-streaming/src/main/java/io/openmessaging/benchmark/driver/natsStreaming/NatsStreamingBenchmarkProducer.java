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
package io.openmessaging.benchmark.driver.natsStreaming;


import io.nats.streaming.AckHandler;
import io.nats.streaming.StreamingConnection;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NatsStreamingBenchmarkProducer implements BenchmarkProducer {
    private StreamingConnection natsStreamingPublisher;
    private String topic;

    public NatsStreamingBenchmarkProducer(StreamingConnection natsStreamingPublisher, String topic) {
        this.natsStreamingPublisher = natsStreamingPublisher;
        this.topic = topic;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        final String[] guid = new String[1];
        AckHandler acb =
                new AckHandler() {
                    @Override
                    public void onAck(String s, Exception e) {
                        if ((e != null) || !guid[0].equals(s)) {
                            future.completeExceptionally(
                                    e != null ? e : new IllegalStateException("guid != nuid"));
                        } else {
                            future.complete(null);
                        }
                    }
                };
        try {
            guid[0] = natsStreamingPublisher.publish(topic, payload, acb);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        if (guid[0].isEmpty()) {
            future.completeExceptionally(new Exception("Expected non-empty guid to be returned."));
        }
        return future;
    }

    @Override
    public void close() throws Exception {}
}
