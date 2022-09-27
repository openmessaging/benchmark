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
package io.openmessaging.benchmark.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaddingDecimalFormatTest {

    @Test
    void format() {
        PaddingDecimalFormat format = new PaddingDecimalFormat("0.0", 7);
        assertThat(format.format(1L)).isEqualTo("    1.0");
        assertThat(format.format(1000L)).isEqualTo(" 1000.0");
        assertThat(format.format(10000000L)).isEqualTo("10000000.0");
    }
}
