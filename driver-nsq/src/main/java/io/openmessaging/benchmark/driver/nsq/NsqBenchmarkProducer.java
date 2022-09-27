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
package io.openmessaging.benchmark.driver.nsq;


import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.exceptions.NSQException;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NsqBenchmarkProducer implements BenchmarkProducer {
    private final NSQProducer nsqProducer;
    private final String topic;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Semaphore semaphore = new Semaphore(1000);

    public NsqBenchmarkProducer(final NSQProducer nsqProducer, final String topic) {
        this.nsqProducer = nsqProducer;
        this.topic = topic;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            semaphore.acquire();

            executor.submit(
                    () -> {
                        try {
                            nsqProducer.produce(topic, payload);
                        } catch (NSQException e) {
                            log.error("send exception", e);
                            future.exceptionally(null);
                        } catch (TimeoutException e) {
                            log.error("send exception", e);
                            future.exceptionally(null);
                        } finally {
                            semaphore.release();
                        }
                        future.complete(null);
                    });
        } catch (InterruptedException e) {
            log.error("semaphore exception", e);
            future.exceptionally(null);
            semaphore.release();
        }
        return future;
    }

    @Override
    public void close() throws Exception {
        this.nsqProducer.shutdown();
    }

    private static final Logger log = LoggerFactory.getLogger(NsqBenchmarkProducer.class);
}
