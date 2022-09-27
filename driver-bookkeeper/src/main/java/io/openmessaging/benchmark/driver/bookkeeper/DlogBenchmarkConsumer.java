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
package io.openmessaging.benchmark.driver.bookkeeper;


import dlshade.com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.distributedlog.DLSN;
import org.apache.distributedlog.LogRecordWithDLSN;
import org.apache.distributedlog.api.DistributedLogManager;
import org.apache.distributedlog.api.LogReader;
import org.apache.distributedlog.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DlogBenchmarkConsumer implements BenchmarkConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlogBenchmarkConsumer.class);

    private final DistributedLogManager dlm;
    private final ExecutorService executor;
    private final Future<?> readerTask;
    private volatile boolean closing = false;

    private static boolean backoff(long backoffTime, TimeUnit timeUnit) {
        try {
            timeUnit.sleep(backoffTime);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted at backoff {} ms", timeUnit.toMillis(backoffTime), e);
            return false;
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    public DlogBenchmarkConsumer(DistributedLogManager dlm, ConsumerCallback callback) {
        this.dlm = dlm;
        this.executor =
                Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder().setNameFormat("dlog-benchmark-reader-thread-%d").build());

        this.readerTask =
                executor.submit(
                        () -> {
                            LogReader reader = null;
                            DLSN lastDLSN = DLSN.InitialDLSN;
                            LogRecordWithDLSN record;

                            while (!closing) {
                                if (null == reader) {
                                    try {
                                        reader = dlm.openLogReader(lastDLSN);
                                        log.info(
                                                "Successfully open log reader for stream {} at {}",
                                                dlm.getStreamName(),
                                                lastDLSN);
                                    } catch (IOException e) {
                                        log.error(
                                                "Failed to open reader of stream {} at {}",
                                                dlm.getStreamName(),
                                                lastDLSN,
                                                e);
                                        if (backoff(10, TimeUnit.SECONDS)) {
                                            continue;
                                        } else {
                                            break;
                                        }
                                    }
                                }

                                try {
                                    record = reader.readNext(false);
                                    if (null == record) {
                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                        continue;
                                    }

                                    callback.messageReceived(record.getPayload(), record.getTransactionId());

                                    lastDLSN = record.getDlsn();
                                } catch (IOException e) {
                                    log.info(
                                            "Encountered error on reading records from reading stream {}, last record = {}",
                                            dlm.getStreamName(),
                                            lastDLSN,
                                            e);
                                    Utils.closeQuietly(reader);
                                    reader = null;
                                }
                            }

                            Utils.closeQuietly(reader);
                        });
    }

    @Override
    public void close() throws Exception {
        closing = true;
        executor.shutdown();
        readerTask.get();
        if (null != dlm) {
            dlm.close();
        }
    }
}
