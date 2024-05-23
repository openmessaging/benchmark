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
package io.openmessaging.benchmark.driver.kafka;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.openmessaging.benchmark.driver.BenchmarkDriver.TopicInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;

@Slf4j
@RequiredArgsConstructor
class KafkaTopicCreator {
    private static final int MAX_BATCH_SIZE = 500;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AdminClient admin;
    private final Map<String, String> topicConfigs;
    private final short replicationFactor;
    private final int maxBatchSize;

    KafkaTopicCreator(AdminClient admin, Map<String, String> topicConfigs, short replicationFactor) {
        this(admin, topicConfigs, replicationFactor, MAX_BATCH_SIZE);
    }

    CompletableFuture<Void> create(List<TopicInfo> topicInfos) {
        return CompletableFuture.runAsync(() -> createBlocking(topicInfos));
    }

    private void createBlocking(List<TopicInfo> topicInfos) {
        BlockingQueue<TopicInfo> queue = new ArrayBlockingQueue<>(topicInfos.size(), true, topicInfos);
        List<TopicInfo> batch = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();

        ScheduledFuture<?> loggingFuture =
                executor.scheduleAtFixedRate(
                        () -> log.info("Created topics {}/{}", succeeded.get(), topicInfos.size()),
                        10,
                        10,
                        SECONDS);

        try {
            while (succeeded.get() < topicInfos.size()) {
                int batchSize = queue.drainTo(batch, maxBatchSize);
                if (batchSize > 0) {
                    executeBatch(batch)
                            .forEach(
                                    (topicInfo, success) -> {
                                        if (success) {
                                            succeeded.incrementAndGet();
                                        } else {
                                            //noinspection ResultOfMethodCallIgnored
                                            queue.offer(topicInfo);
                                        }
                                    });
                    batch.clear();
                }
            }
        } finally {
            loggingFuture.cancel(true);
        }
    }

    private Map<TopicInfo, Boolean> executeBatch(List<TopicInfo> batch) {
        log.debug("Executing batch, size: {}", batch.size());
        Map<String, TopicInfo> lookup = batch.stream().collect(toMap(TopicInfo::getTopic, identity()));

        List<NewTopic> newTopics = batch.stream().map(this::newTopic).collect(toList());

        return admin.createTopics(newTopics).values().entrySet().stream()
                .collect(toMap(e -> lookup.get(e.getKey()), e -> isSuccess(e.getValue())));
    }

    private NewTopic newTopic(TopicInfo topicInfo) {
        NewTopic newTopic =
                new NewTopic(topicInfo.getTopic(), topicInfo.getPartitions(), replicationFactor);
        newTopic.configs(topicConfigs);
        return newTopic;
    }

    private boolean isSuccess(KafkaFuture<Void> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            log.debug(e.getMessage());
            return e.getCause() instanceof TopicExistsException;
        }
        return true;
    }
}
