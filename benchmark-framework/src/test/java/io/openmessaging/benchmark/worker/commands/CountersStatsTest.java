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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountersStatsTest {

    @Test
    void plus() {
        CountersStats one = new CountersStats();
        one.messagesSent = 1;
        one.messageSendErrors = 10;
        one.messagesReceived = 100;
        CountersStats two = new CountersStats();
        two.messagesSent = 2;
        two.messageSendErrors = 20;
        two.messagesReceived = 200;

        CountersStats result = one.plus(two);
        assertThat(result)
                .satisfies(
                        r -> {
                            assertThat(r.messagesSent).isEqualTo(3);
                            assertThat(r.messageSendErrors).isEqualTo(30);
                            assertThat(r.messagesReceived).isEqualTo(300);
                        });
    }

    @Test
    void zeroPlus() {
        CountersStats zero = new CountersStats();
        CountersStats two = new CountersStats();
        two.messagesSent = 2;
        two.messageSendErrors = 20;
        two.messagesReceived = 200;

        CountersStats result = zero.plus(two);
        assertThat(result)
                .satisfies(
                        r -> {
                            assertThat(r.messagesSent).isEqualTo(2);
                            assertThat(r.messageSendErrors).isEqualTo(20);
                            assertThat(r.messagesReceived).isEqualTo(200);
                        });
    }
}
