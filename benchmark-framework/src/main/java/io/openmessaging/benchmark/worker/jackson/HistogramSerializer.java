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
import java.io.IOException;
import java.nio.ByteBuffer;
import org.HdrHistogram.Histogram;

public class HistogramSerializer extends StdSerializer<Histogram> {

    private final ThreadLocal<ByteBuffer> threadBuffer =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(8 * 1024 * 1024));

    public HistogramSerializer() {
        super(Histogram.class);
    }

    @Override
    public void serialize(
            Histogram histogram, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        ByteBuffer buffer = threadBuffer.get();
        buffer.clear();
        histogram.encodeIntoCompressedByteBuffer(buffer);
        byte[] arr = new byte[buffer.position()];
        buffer.flip();
        buffer.get(arr);
        jsonGenerator.writeBinary(arr);
    }
}
