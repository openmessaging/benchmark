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
package io.openmessaging.benchmark.utils.distributor;


import com.google.common.io.BaseEncoding;
import java.util.Random;

public abstract class KeyDistributor {

    private static final int UNIQUE_COUNT = 10_000;
    private static final int KEY_BYTE_SIZE = 7;

    private static final String[] randomKeys = new String[UNIQUE_COUNT];

    static {
        // Generate a number of random keys to be used when publishing
        byte[] buffer = new byte[KEY_BYTE_SIZE];
        Random random = new Random();
        for (int i = 0; i < randomKeys.length; i++) {
            random.nextBytes(buffer);
            randomKeys[i] = BaseEncoding.base64Url().omitPadding().encode(buffer);
        }
    }

    protected String get(int index) {
        return randomKeys[index];
    }

    protected int getLength() {
        return UNIQUE_COUNT;
    }

    public abstract String next();

    public static KeyDistributor build(KeyDistributorType keyType) {
        KeyDistributor keyDistributor = null;
        switch (keyType) {
            case NO_KEY:
                keyDistributor = new NoKeyDistributor();
                break;
            case KEY_ROUND_ROBIN:
                keyDistributor = new KeyRoundRobin();
                break;
            case RANDOM_NANO:
                keyDistributor = new RandomNano();
                break;
            default:
                throw new IllegalStateException("Unexpected KeyDistributorType: " + keyType);
        }
        return keyDistributor;
    }
}
