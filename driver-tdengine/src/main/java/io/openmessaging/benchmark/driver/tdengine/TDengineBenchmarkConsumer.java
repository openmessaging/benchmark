/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

package io.openmessaging.benchmark.driver.tdengine;

import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.TaosConsumer;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class TDengineBenchmarkConsumer implements BenchmarkConsumer {
    private static final Logger log = LoggerFactory.getLogger(TDengineBenchmarkConsumer.class);
    private final ExecutorService executor;
    private final Future<?> consumerTask;
    private volatile boolean closing = false;

    public TDengineBenchmarkConsumer(String topic, String group, ConsumerCallback callback) {
        this.executor = Executors.newSingleThreadExecutor();
        this.consumerTask = this.executor.submit(() -> {
            callback.messageReceived(new byte[10], System.currentTimeMillis());
            Properties config = new Properties();
            config.setProperty("msg.with.table.name", "true");
            config.setProperty("enable.auto.commit", "true");
            config.setProperty("group.id", group);
            config.setProperty("value.deserializer", ValueDecoder.class.getName());
            try (TaosConsumer<Record> consumer = new TaosConsumer<>(config)) {
                String topicName = '`' + topic + '`';
                consumer.subscribe(Collections.singletonList(topicName));
                while (!closing) {
                    ConsumerRecords<Record> consumerRecords = consumer.poll(Duration.ofMillis(-1));
                    for (Record record : consumerRecords) {
                        callback.messageReceived(record.payload, record.ts / 1000000);
                    }
                }
            } catch (Exception e) {
                log.debug("Error {} {}", topic, e.getMessage());
                e.printStackTrace();
            }
            log.debug("consumer task stopped {}", topic);
        });
    }

    @Override
    public void close() throws Exception {
        this.closing = true;
        this.executor.shutdown();
        this.consumerTask.get();
    }
}
