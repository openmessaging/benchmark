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
package io.openmessaging.benchmark.utils.payload;

import static java.nio.file.Files.readAllBytes;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.openmessaging.benchmark.worker.commands.Payload;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayloadReader {
    private static final Logger log = LoggerFactory.getLogger(PayloadReader.class);
    public final int expectedLength;
    private static final ObjectMapper mapper =
            new ObjectMapper(
                            new YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false))
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    public PayloadReader(int expectedLength) {
        this.expectedLength = expectedLength;
    }

    private void checkPayloadLength(List<Payload> payload) {
        if (payload.size() == 1 && payload.get(0).data.length != expectedLength) {
            throw new PayloadException(
                    MessageFormat.format(
                            "payload length mismatch. Actual payload size is: {0}, but expected: {1} ",
                            payload.get(0).data.length, expectedLength));
        }
        int total = 0;
        for (Payload p : payload) {
            total += p.data.length;
        }
        int avg = total / payload.size();
        int expect10p = (int) (expectedLength * 0.1);
        if (expectedLength - expect10p < avg || expectedLength + expect10p > avg) {
            log.warn(
                    "Average payload length {} differs from expected length {} by over 10% "
                            + "this means that throughput target maybe incorrect",
                    avg, expectedLength);
        }
    }

    public List<Payload> load(String payloadFile) {
        List<Payload> out = new ArrayList<>();
        try {
            File f = new File(payloadFile);
            if (Files.isDirectory(f.toPath())) {
                File[] files = f.listFiles();
                if (files == null) {
                    throw new PayloadException("list files returned null for file " + payloadFile);
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    if (file.getName().endsWith(".payload.yaml")) {
                        Payload p = mapper.readValue(file, Payload.class);
                        log.info(
                                "Loaded payload from file file='{}', headers='{}', data='{}'",
                                file.getAbsolutePath(),
                                p.headers,
                                new String(p.data, StandardCharsets.UTF_8));
                        out.add(p);
                    } else {
                        byte[] payload = readAllBytes(file.toPath());
                        out.add(new Payload(payload));
                    }
                }
            } else {
                byte[] payload = readAllBytes(f.toPath());
                out.add(new Payload(payload));
            }
            checkPayloadLength(out);
            return out;
        } catch (IOException e) {
            throw new PayloadException(e.getMessage());
        }
    }
}
