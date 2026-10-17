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


import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * A consumer: a handle on its subscription (the topic's queue and the Queen consumer group of the
 * OMB subscription) in the worker's {@link QueenPopPool}. It owns no thread and no connection; the
 * pool pops for it and calls its callback.
 */
final class QueenBenchmarkConsumer implements BenchmarkConsumer {
    final QueenPopPool pool;
    final QueenPopPool.Sub sub;
    final ConsumerCallback callback;
    private volatile boolean closed;

    QueenBenchmarkConsumer(QueenPopPool pool, QueenPopPool.Sub sub, ConsumerCallback callback) {
        this.pool = pool;
        this.sub = sub;
        this.callback = callback;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pool.unregister(this);
    }

    /**
     * verify: true. Which (partition id, offset) pairs one subscription's consumers on this worker
     * were handed, and how often; the driver logs the totals when it closes.
     */
    static final class Verifier {
        private final ConcurrentHashMap<Long, BitSet> seen = new ConcurrentHashMap<>();
        private final LongAdder distinct = new LongAdder();
        private final LongAdder duplicates = new LongAdder();

        void saw(long pid, long offset) {
            if (offset < 0 || offset > Integer.MAX_VALUE) {
                return;
            }
            BitSet s = seen.computeIfAbsent(pid, k -> new BitSet());
            synchronized (s) {
                if (s.get((int) offset)) {
                    duplicates.increment();
                } else {
                    s.set((int) offset);
                    distinct.increment();
                }
            }
        }

        /* {partitions, distinct messages, delivered again, holes}. */
        long[] totals() {
            long holes = 0;
            for (BitSet s : seen.values()) {
                synchronized (s) {
                    int first = s.nextSetBit(0);
                    if (first >= 0) {
                        holes += (s.length() - first) - s.cardinality();
                    }
                }
            }
            return new long[] {seen.size(), distinct.sum(), duplicates.sum(), holes};
        }

        String report() {
            long[] t = totals();
            return String.format(
                    "%d partitions, %d distinct messages, %d delivered again, %d holes between the first and"
                            + " the last offset delivered",
                    t[0], t[1], t[2], t[3]);
        }
    }
}
