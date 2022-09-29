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

class PeriodStatsTest {

    @Test
    void plus() {
        PeriodStats one = new PeriodStats();
        one.messagesSent = 1;
        one.messageSendErrors = 2;
        one.bytesSent = 3;
        one.messagesReceived = 4;
        one.bytesReceived = 5;
        one.totalMessagesSent = 6;
        one.totalMessageSendErrors = 7;
        one.totalMessagesReceived = 8;
        PeriodStats two = new PeriodStats();
        two.messagesSent = 10;
        two.messageSendErrors = 20;
        two.bytesSent = 30;
        two.messagesReceived = 40;
        two.bytesReceived = 50;
        two.totalMessagesSent = 60;
        two.totalMessageSendErrors = 70;
        two.totalMessagesReceived = 80;
        PeriodStats result = one.plus(two);
        assertThat(result)
                .satisfies(
                        r -> {
                            assertThat(r.messagesSent).isEqualTo(11);
                            assertThat(r.messageSendErrors).isEqualTo(22);
                            assertThat(r.bytesSent).isEqualTo(33);
                            assertThat(r.messagesReceived).isEqualTo(44);
                            assertThat(r.bytesReceived).isEqualTo(55);
                            assertThat(r.totalMessagesSent).isEqualTo(66);
                            assertThat(r.totalMessageSendErrors).isEqualTo(77);
                            assertThat(r.totalMessagesReceived).isEqualTo(88);

                            two.publishLatency.add(one.publishLatency);
                            two.publishDelayLatency.add(one.publishDelayLatency);
                            two.endToEndLatency.add(one.endToEndLatency);

                            assertThat(r.publishLatency).isEqualTo(two.publishLatency);
                            assertThat(r.publishDelayLatency).isEqualTo(two.publishDelayLatency);
                            assertThat(r.endToEndLatency).isEqualTo(two.endToEndLatency);
                        });
    }

    @Test
    void zeroPlus() {
        PeriodStats one = new PeriodStats();
        PeriodStats two = new PeriodStats();
        two.messagesSent = 10;
        two.messageSendErrors = 20;
        two.bytesSent = 30;
        two.messagesReceived = 40;
        two.bytesReceived = 50;
        two.totalMessagesSent = 60;
        two.totalMessageSendErrors = 70;
        two.totalMessagesReceived = 80;
        PeriodStats result = one.plus(two);
        assertThat(result)
                .satisfies(
                        r -> {
                            assertThat(r.messagesSent).isEqualTo(10);
                            assertThat(r.messageSendErrors).isEqualTo(20);
                            assertThat(r.bytesSent).isEqualTo(30);
                            assertThat(r.messagesReceived).isEqualTo(40);
                            assertThat(r.bytesReceived).isEqualTo(50);
                            assertThat(r.totalMessagesSent).isEqualTo(60);
                            assertThat(r.totalMessageSendErrors).isEqualTo(70);
                            assertThat(r.totalMessagesReceived).isEqualTo(80);

                            assertThat(r.publishLatency).isEqualTo(two.publishLatency);
                            assertThat(r.publishDelayLatency).isEqualTo(two.publishDelayLatency);
                            assertThat(r.endToEndLatency).isEqualTo(two.endToEndLatency);
                        });
    }
}
