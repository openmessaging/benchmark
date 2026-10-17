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
package io.openmessaging.benchmark.driver.queen;


import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An OMB producer: a topic and the lane (shared accumulator) it writes to; see {@link QueenSender}.
 * Without a key it writes one partition per batch of its lane, the next batch the next partition.
 */
final class QueenBenchmarkProducer implements BenchmarkProducer {
    final QueenSender.Lane lane;
    final QueenSender.Topic topic;
    /** The partition of the lane batch named by batchSeen; both guarded by the lane's lock. */
    int sticky;

    long batchSeen = -1;

    QueenBenchmarkProducer(QueenSender.Lane lane, QueenSender.Topic topic) {
        this.lane = lane;
        this.topic = topic;
        this.sticky = ThreadLocalRandom.current().nextInt(topic.partitions);
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        return lane.send(this, key, payload);
    }

    @Override
    public void close() {
        lane.flush();
    }
}
