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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.HdrHistogram.Histogram;

public class HistogramSerializer extends StdSerializer<Histogram> {

    private final ThreadLocal<ByteBuffer> threadBuffer =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(8 * 1024 * 1024));

    public HistogramSerializer() {
        super(Histogram.class);
    }

    static byte[] toByteArray(ByteBuffer buffer) {
        byte[] encodedBuffer = new byte[buffer.remaining()];
        buffer.get(encodedBuffer);
        return encodedBuffer;
    }

    static ByteBuffer serializeHistogram(Histogram histo, ByteBuffer buffer) {
        buffer.clear();
        while (true) {
            final int outBytes = histo.encodeIntoCompressedByteBuffer(buffer);
            Preconditions.checkState(outBytes == buffer.position());
            final int capacity = buffer.capacity();
            if (outBytes < capacity) {
                // encoding succesful
                break;
            }
            // We filled the entire buffer, an indication that the buffer was not
            // large enough, so we double the buffer and try again.
            // See: https://github.com/HdrHistogram/HdrHistogram/issues/201
            buffer = ByteBuffer.allocate(capacity * 2);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void serialize(
            Histogram histogram, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        ByteBuffer buffer = threadBuffer.get();
        ByteBuffer newBuffer = serializeHistogram(histogram, buffer);
        if (newBuffer != buffer) {
            threadBuffer.set(newBuffer);
        }
        jsonGenerator.writeBinary(toByteArray(newBuffer));
    }
}
