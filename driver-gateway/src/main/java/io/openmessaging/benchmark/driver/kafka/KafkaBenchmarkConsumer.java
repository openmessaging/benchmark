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


import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBenchmarkConsumer implements BenchmarkConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaBenchmarkConsumer.class);

    private final KafkaConsumer<String, byte[]> consumer;

    private final ExecutorService executor;
    private final Future<?> consumerTask;
    private volatile boolean closing = false;
    private boolean autoCommit;

    public KafkaBenchmarkConsumer(
            KafkaConsumer<String, byte[]> consumer,
            Properties consumerConfig,
            ConsumerCallback callback) {
        this(consumer, consumerConfig, callback, 100L);
    }

    public KafkaBenchmarkConsumer(
            KafkaConsumer<String, byte[]> consumer,
            Properties consumerConfig,
            ConsumerCallback callback,
            long pollTimeoutMs) {
        this.consumer = consumer;
        this.executor = Executors.newSingleThreadExecutor();
        this.autoCommit =
                Boolean.valueOf(
                        (String)
                                consumerConfig.getOrDefault(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"));
        this.consumerTask =
                this.executor.submit(
                        () -> {
                            while (!closing) {
                                try {
                                    ConsumerRecords<String, byte[]> records =
                                            consumer.poll(Duration.ofMillis(pollTimeoutMs));

                                    Map<TopicPartition, OffsetAndMetadata> offsetMap = new HashMap<>();
                                    for (ConsumerRecord<String, byte[]> record : records) {
                                        callback.messageReceived(record.value(), record.timestamp());

                                        offsetMap.put(
                                                new TopicPartition(record.topic(), record.partition()),
                                                new OffsetAndMetadata(record.offset() + 1));
                                    }

                                    if (!autoCommit && !offsetMap.isEmpty()) {
                                        // Async commit all messages polled so far
                                        consumer.commitAsync(offsetMap, null);
                                    }
                                } catch (Exception e) {
                                    log.error("exception occur while consuming message", e);
                                }
                            }
                        });
    }

    @Override
    public void close() throws Exception {
        closing = true;
        executor.shutdown();
        consumerTask.get();
        consumer.close();
    }
}
