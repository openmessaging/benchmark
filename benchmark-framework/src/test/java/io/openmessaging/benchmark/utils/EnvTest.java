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

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnvTest {
    private static final String ENV_KEY = "KEY";

    @Test
    void envLong() throws Exception {
        withEnvironmentVariable(ENV_KEY, "2")
                .execute(
                        () -> {
                            assertThat(Env.getLong(ENV_KEY, 1)).isEqualTo(2);
                        });
    }

    @Test
    void envLongDefault() throws Exception {
        withEnvironmentVariable(ENV_KEY, null)
                .execute(
                        () -> {
                            assertThat(Env.getLong(ENV_KEY, 1)).isEqualTo(1);
                        });
    }

    @Test
    void envDouble() throws Exception {
        withEnvironmentVariable(ENV_KEY, "2.34")
                .execute(
                        () -> {
                            assertThat(Env.getDouble(ENV_KEY, 1.23)).isEqualTo(2.34);
                        });
    }

    @Test
    void envDoubleDefault() throws Exception {
        withEnvironmentVariable(ENV_KEY, null)
                .execute(
                        () -> {
                            assertThat(Env.getDouble(ENV_KEY, 1.23)).isEqualTo(1.23);
                        });
    }
}
