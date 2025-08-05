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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic resource creator that handles batch creation of resources with retry logic, progress
 * logging, and configurable delays between batches.
 *
 * @param <R> the type of resource request/input
 * @param <C> the type of created resource/output
 */
public class ResourceCreator<R, C> {
    private static final Logger log = LoggerFactory.getLogger(ResourceCreator.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final String name;
    private final int maxBatchSize;
    private final long interBatchDelayMs;
    private final Function<List<R>, Map<R, CompletableFuture<C>>> invokeBatchFn;
    private final Function<CompletableFuture<C>, CreationResult<C>> complete;

    /**
     * Creates a new ResourceCreator with the specified configuration.
     *
     * @param name the name of the resource type being created (for logging)
     * @param maxBatchSize maximum number of resources to process in a single batch
     * @param interBatchDelayMs delay in milliseconds between batch executions
     * @param invokeBatchFn function that takes a batch of resources and returns a map of futures
     * @param complete function that converts a CompletableFuture to a CreationResult
     */
    public ResourceCreator(
            String name,
            int maxBatchSize,
            long interBatchDelayMs,
            Function<List<R>, Map<R, CompletableFuture<C>>> invokeBatchFn,
            Function<CompletableFuture<C>, CreationResult<C>> complete) {
        this.name = name;
        this.maxBatchSize = maxBatchSize;
        this.interBatchDelayMs = interBatchDelayMs;
        this.invokeBatchFn = invokeBatchFn;
        this.complete = complete;
    }

    /**
     * Creates all requested resources asynchronously.
     *
     * @param resources the list of resources to create
     * @return a CompletableFuture containing the list of successfully created resources
     */
    public CompletableFuture<List<C>> create(List<R> resources) {
        return CompletableFuture.supplyAsync(() -> createBlocking(resources));
    }

    /**
     * Creates all requested resources synchronously with batching and retry logic. Failed resources
     * are retried until all resources are successfully created.
     *
     * @param resources the list of resources to create
     * @return the list of successfully created resources
     */
    private List<C> createBlocking(List<R> resources) {
        if (resources.isEmpty()) {
            return List.of();
        }

        BlockingQueue<R> queue = new ArrayBlockingQueue<>(resources.size(), true, resources);
        List<R> batch = new ArrayList<>();
        List<C> created = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();

        ScheduledFuture<?> loggingFuture = scheduleProgressLogging(succeeded, resources.size());

        try {
            while (succeeded.get() < resources.size()) {
                int batchSize = queue.drainTo(batch, maxBatchSize);
                if (batchSize > 0) {
                    processBatch(batch, queue, created, succeeded);
                    batch.clear();
                } else {
                    // No items to process, avoid busy waiting
                    sleepSafely(100);
                }
            }
        } finally {
            loggingFuture.cancel(true);
        }

        log.info("Successfully created all {} {}s", resources.size(), name);
        return created;
    }

    /**
     * Processes a single batch of resources, handling successes and failures.
     *
     * @param batch the batch of resources to process
     * @param retryQueue the queue to put failed resources for retry
     * @param created the list to add successfully created resources to
     * @param succeeded the counter for successfully created resources
     */
    private void processBatch(
            List<R> batch, BlockingQueue<R> retryQueue, List<C> created, AtomicInteger succeeded) {

        try {
            Map<R, CreationResult<C>> results = executeBatch(batch);

            results.forEach(
                    (resource, result) -> {
                        if (result.success()) {
                            created.add(result.created());
                            succeeded.incrementAndGet();
                        } else {
                            // Re-queue failed resources for retry
                            if (!retryQueue.offer(resource)) {
                                log.warn("Failed to re-queue resource for retry: {}", resource);
                            }
                        }
                    });
        } catch (Exception e) {
            log.warn("Batch execution failed, re-queuing all resources in batch", e);
            // Re-queue all resources in the batch for retry
            batch.forEach(
                    resource -> {
                        if (!retryQueue.offer(resource)) {
                            log.warn("Failed to re-queue resource after batch failure: {}", resource);
                        }
                    });
        }
    }

    /**
     * Executes a single batch with the configured delay.
     *
     * @param batch the batch of resources to process
     * @return a map of resources to their creation results
     * @throws InterruptedException if the thread is interrupted during delay
     */
    private Map<R, CreationResult<C>> executeBatch(List<R> batch) throws InterruptedException {
        log.debug("Executing batch, size: {}", batch.size());

        if (interBatchDelayMs > 0) {
            Thread.sleep(interBatchDelayMs);
        }

        return invokeBatchFn.apply(batch).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> complete.apply(e.getValue())));
    }

    /**
     * Schedules periodic progress logging.
     *
     * @param succeeded the atomic counter for successful creations
     * @param total the total number of resources to create
     * @return the scheduled future for the logging task
     */
    private ScheduledFuture<?> scheduleProgressLogging(AtomicInteger succeeded, int total) {
        return executor.scheduleAtFixedRate(
                () -> log.info("Created {}s {}/{}", name, succeeded.get(), total), 10, 10, SECONDS);
    }

    /**
     * Safe sleep method that handles InterruptedException properly.
     *
     * @param millis the number of milliseconds to sleep
     */
    private void sleepSafely(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during resource creation", e);
        }
    }

    /**
     * Shuts down the internal executor service. Should be called when the ResourceCreator is no
     * longer needed.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Result of a resource creation attempt.
     *
     * @param created the created resource (may be null if creation failed)
     * @param success whether the creation was successful
     * @param <C> the type of the created resource
     */
    public record CreationResult<C>(C created, boolean success) {

        /**
         * Creates a successful creation result.
         *
         * @param created the successfully created resource
         * @param <C> the type of the created resource
         * @return a successful CreationResult
         */
        public static <C> CreationResult<C> success(C created) {
            return new CreationResult<>(created, true);
        }

        /**
         * Creates a failed creation result.
         *
         * @param <C> the type of the created resource
         * @return a failed CreationResult
         */
        public static <C> CreationResult<C> failure() {
            return new CreationResult<>(null, false);
        }
    }
}
