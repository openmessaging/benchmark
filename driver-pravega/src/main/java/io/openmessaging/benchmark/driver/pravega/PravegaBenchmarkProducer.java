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
package io.openmessaging.benchmark.driver.pravega;


import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.impl.ByteBufferSerializer;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PravegaBenchmarkProducer implements BenchmarkProducer {
    private static final Logger log = LoggerFactory.getLogger(PravegaBenchmarkProducer.class);

    private final EventStreamWriter<ByteBuffer> writer;
    private final boolean includeTimestampInEvent;
    private ByteBuffer timestampAndPayload;

    public PravegaBenchmarkProducer(
            String streamName,
            EventStreamClientFactory clientFactory,
            boolean includeTimestampInEvent,
            boolean enableConnectionPooling) {
        log.info("PravegaBenchmarkProducer: BEGIN: streamName={}", streamName);
        writer =
                clientFactory.createEventWriter(
                        streamName,
                        new ByteBufferSerializer(),
                        EventWriterConfig.builder().enableConnectionPooling(enableConnectionPooling).build());
        this.includeTimestampInEvent = includeTimestampInEvent;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        if (includeTimestampInEvent) {
            if (timestampAndPayload == null
                    || timestampAndPayload.limit() != Long.BYTES + payload.length) {
                timestampAndPayload = ByteBuffer.allocate(Long.BYTES + payload.length);
            } else {
                timestampAndPayload.position(0);
            }
            timestampAndPayload.putLong(System.currentTimeMillis()).put(payload).flip();
            return writeEvent(key, timestampAndPayload);
        }
        return writeEvent(key, ByteBuffer.wrap(payload));
    }

    private CompletableFuture<Void> writeEvent(Optional<String> key, ByteBuffer payload) {
        return (key.isPresent()) ? writer.writeEvent(key.get(), payload) : writer.writeEvent(payload);
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
