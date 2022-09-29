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
}
