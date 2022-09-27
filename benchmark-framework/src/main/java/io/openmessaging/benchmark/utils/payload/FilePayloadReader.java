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

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class FilePayloadReader implements PayloadReader {

    private final int expectedLength;

    public FilePayloadReader(int expectedLength) {
        this.expectedLength = expectedLength;
    }

    @Override
    public byte[] load(String resourceName) {
        byte[] payload;
        try {
            payload = readAllBytes(new File(resourceName).toPath());
            checkPayloadLength(payload);
            return payload;
        } catch (IOException e) {
            throw new PayloadException(e.getMessage());
        }
    }

    private void checkPayloadLength(byte[] payload) {
        if (expectedLength != payload.length) {
            throw new PayloadException(
                    MessageFormat.format(
                            "Payload length mismatch. Actual is: {0}, but expected: {1} ",
                            payload.length, expectedLength));
        }
    }
}
