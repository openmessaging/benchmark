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


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** What the producers and consumers of one driver share. */
final class QueenContext {
    final QueenConfig cfg;
    final QueenHttp http;
    final List<String> urls;
    /** Linger timers of every producer. */
    final ScheduledExecutorService timer;
    /** Compression of push batches (compression: zstd), else null. */
    final ExecutorService encoder;

    final boolean zstd;
    final QueenStats stats = new QueenStats();
    /** The pops of every consumer of this worker (set right after the context is built). */
    QueenPopPool pool;
    /** The producer lanes of this worker (set right after the context is built). */
    QueenSender sender;
    /**
     * Round-robin over urls; starts at a random place so several workers do not all begin at urls[0].
     */
    private final AtomicInteger next =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(1 << 20));

    QueenContext(
            QueenConfig cfg,
            QueenHttp http,
            List<String> urls,
            ScheduledExecutorService timer,
            ExecutorService encoder,
            boolean zstd) {
        this.cfg = cfg;
        this.http = http;
        this.urls = urls;
        this.timer = timer;
        this.encoder = encoder;
        this.zstd = zstd;
    }

    /* The next broker base URL, round-robin. */
    String nextUrl() {
        return urls.get(Math.floorMod(next.getAndIncrement(), urls.size()));
    }
}
