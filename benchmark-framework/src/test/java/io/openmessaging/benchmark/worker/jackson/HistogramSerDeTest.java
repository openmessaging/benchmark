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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

class HistogramSerDeTest {

    @Test
    void deserialize() throws IOException {
        Histogram value = new Histogram(100_000, 3);
        value.recordValue(1);
        value.recordValue(100);
        value.recordValue(10_000);

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Histogram.class, new HistogramSerializer());
        module.addDeserializer(Histogram.class, new HistogramDeserializer());
        mapper.registerModule(module);

        byte[] serialized = mapper.writeValueAsBytes(value);
        Histogram deserialized = mapper.readValue(serialized, Histogram.class);

        assertThat(deserialized).isEqualTo(value);
    }

    /**
     * Create a random histogram and insert the given number of samples.
     *
     * @param samples the number of samples to record into the histogram
     * @return a Histogram with the given number of samples
     */
    private Histogram randomHisto(int samples) {
        Random r = new Random(0xBADBEEF);
        Histogram h = new org.HdrHistogram.Histogram(5);
        for (int i = 0; i < samples; i++) {
            h.recordValue(r.nextInt(10000000));
        }

        return h;
    }

    byte[] serializeRandomHisto(int samples, int initialBufferSize) throws Exception {
        ByteBuffer inbuffer = ByteBuffer.allocate(initialBufferSize);
        Histogram inHisto = randomHisto(samples);
        byte[] serialBytes =
                HistogramSerializer.toByteArray(HistogramSerializer.serializeHistogram(inHisto, inbuffer));

        // check roundtrip
        Histogram outHisto =
                Histogram.decodeFromCompressedByteBuffer(ByteBuffer.wrap(serialBytes), Long.MIN_VALUE);
        assertThat(inHisto).isEqualTo(outHisto);

        return serialBytes;
    }

    @Test
    public void testHistogram() throws Exception {

        // in the worker it's 8 MB but it takes a while to make a histogram that big
        final int bufSize = 1002;

        int samples = 300;

        // we do an exponential search to fit the crossover point where we need to grow the buffer
        while (true) {
            byte[] serialBytes = serializeRandomHisto(samples, bufSize);
            // System.out.println("Samples: " + samples + ", histogram size: " + serialBytes.length);
            if (serialBytes.length >= bufSize) {
                break;
            }
            samples *= 1.05;
        }

        // then walk backwards across the point linearly with increment of 1 to check the boundary
        // carefully
        while (true) {
            samples--;
            byte[] serialBytes = serializeRandomHisto(samples, bufSize);
            // System.out.println("Samples: " + samples + ", histogram size: " + serialBytes.length);
            if (serialBytes.length < bufSize - 10) {
                break;
            }
        }
    }
}
