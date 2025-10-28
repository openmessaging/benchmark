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
package io.openmessaging.benchmark.worker.commands;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;

public class Payload {
    @JsonDeserialize(using = FlexibleByteArrayDeserializer.class)
    public byte[] data;

    @Nullable public Map<String, String> headers;

    public Payload() {}

    public Payload(byte[] data) {
        this.data = data;
    }
}

class FlexibleByteArrayDeserializer extends JsonDeserializer<byte[]> {
    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();

        if (token == JsonToken.VALUE_STRING) {
            try {
                return Base64.getDecoder().decode(p.getValueAsString());
            } catch (IllegalArgumentException e) {
                // Not Base64, treat as UTF-8 string
            }
            return p.getValueAsString().getBytes(StandardCharsets.UTF_8);
        } else if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            // Handle as JSON object/array - serialize it
            JsonNode node = p.readValueAsTree();
            return new JsonMapper().writeValueAsBytes(node);
        }

        throw new IOException("Cannot deserialize byte[] from " + token);
    }
}
