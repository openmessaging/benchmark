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
package io.openmessaging.benchmark.worker.jackson;


import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistogramDeserializer extends StdDeserializer<Histogram> {

    private final ThreadLocal<ByteBuffer> threadBuffer =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(8 * 1024 * 1024));

    public HistogramDeserializer() {
        super(Histogram.class);
    }

    @Override
    public Histogram deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        ByteBuffer buffer = threadBuffer.get();
        buffer.clear();
        try (OutputStream os = new ByteBufferBackedOutputStream(buffer)) {
            jsonParser.readBinaryValue(os);
            buffer.flip();
            // Long.MIN_VALUE used so that Histogram will defer to the value encoded in the histogram
            // value. This assumes that it is acceptable for the deserialized value we create to
            // share the same parameters of the source histogram that was serialized.
            return Histogram.decodeFromCompressedByteBuffer(buffer, Long.MIN_VALUE);
        } catch (Exception e) {
            log.error(
                    "Failed to decode publish delay latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(buffer)));
            throw new RuntimeException(e);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(HistogramDeserializer.class);
}
