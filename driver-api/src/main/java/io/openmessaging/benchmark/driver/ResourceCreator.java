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
package io.openmessaging.benchmark.driver;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ResourceCreator<R, C> {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final String name;
    private final int maxBatchSize;
    private final long interBatchDelayMs;
    private final Function<List<R>, Map<R, CompletableFuture<C>>> invokeBatchFn;
    private final Function<CompletableFuture<C>, CreationResult<C>> complete;

    public CompletableFuture<List<C>> create(List<R> resources) {
        return CompletableFuture.completedFuture(createBlocking(resources));
    }

    private List<C> createBlocking(List<R> resources) {
        BlockingQueue<R> queue = new ArrayBlockingQueue<>(resources.size(), true, resources);
        List<R> batch = new ArrayList<>();
        List<C> created = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();

        ScheduledFuture<?> loggingFuture =
                executor.scheduleAtFixedRate(
                        () -> log.info("Created {}s {}/{}", name, succeeded.get(), resources.size()),
                        10,
                        10,
                        SECONDS);

        try {
            while (succeeded.get() < resources.size()) {
                int batchSize = queue.drainTo(batch, maxBatchSize);
                if (batchSize > 0) {
                    executeBatch(batch)
                            .forEach(
                                    (resource, result) -> {
                                        if (result.success) {
                                            created.add(result.created);
                                            succeeded.incrementAndGet();
                                        } else {
                                            //noinspection ResultOfMethodCallIgnored
                                            queue.offer(resource);
                                        }
                                    });
                    batch.clear();
                }
            }
        } finally {
            loggingFuture.cancel(true);
        }
        return created;
    }

    @SneakyThrows
    private Map<R, CreationResult<C>> executeBatch(List<R> batch) {
        log.debug("Executing batch, size: {}", batch.size());
        Thread.sleep(interBatchDelayMs);
        return invokeBatchFn.apply(batch).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> complete.apply(e.getValue())));
    }

    @Value
    public static class CreationResult<C> {
        C created;
        boolean success;
    }
}
