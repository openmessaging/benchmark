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


import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.distributedlog.LogRecord;
import org.apache.distributedlog.api.AsyncLogWriter;
import org.apache.distributedlog.util.TimeSequencer;

public class DlogBenchmarkProducer implements BenchmarkProducer {

    private final AsyncLogWriter writer;
    private final TimeSequencer sequencer = new TimeSequencer();

    public DlogBenchmarkProducer(AsyncLogWriter writer) {
        this.writer = writer;
        sequencer.setLastId(writer.getLastTxId());
    }

    @Override
    public void close() throws Exception {
        writer.asyncClose().get();
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        LogRecord record = new LogRecord(sequencer.nextId(), payload);

        return writer.write(record).thenApply(dlsn -> null);
    }
}
