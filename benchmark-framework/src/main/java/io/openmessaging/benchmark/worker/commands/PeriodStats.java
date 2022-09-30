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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.HdrHistogram.Histogram;

public class PeriodStats {
    public long messagesSent = 0;
    public long messageSendErrors = 0;
    public long bytesSent = 0;

    public long messagesReceived = 0;
    public long bytesReceived = 0;

    public long totalMessagesSent = 0;
    public long totalMessageSendErrors = 0;
    public long totalMessagesReceived = 0;

    public Histogram publishLatency = new Histogram(SECONDS.toMicros(60), 5);
    public Histogram publishDelayLatency = new Histogram(SECONDS.toMicros(60), 5);
    public Histogram endToEndLatency = new Histogram(HOURS.toMicros(12), 5);

    public PeriodStats plus(PeriodStats toAdd) {
        PeriodStats result = new PeriodStats();

        result.messagesSent += this.messagesSent;
        result.messageSendErrors += this.messageSendErrors;
        result.bytesSent += this.bytesSent;
        result.messagesReceived += this.messagesReceived;
        result.bytesReceived += this.bytesReceived;
        result.totalMessagesSent += this.totalMessagesSent;
        result.totalMessageSendErrors += this.totalMessageSendErrors;
        result.totalMessagesReceived += this.totalMessagesReceived;
        result.publishLatency.add(this.publishLatency);
        result.publishDelayLatency.add(this.publishDelayLatency);
        result.endToEndLatency.add(this.endToEndLatency);

        result.messagesSent += toAdd.messagesSent;
        result.messageSendErrors += toAdd.messageSendErrors;
        result.bytesSent += toAdd.bytesSent;
        result.messagesReceived += toAdd.messagesReceived;
        result.bytesReceived += toAdd.bytesReceived;
        result.totalMessagesSent += toAdd.totalMessagesSent;
        result.totalMessageSendErrors += toAdd.totalMessageSendErrors;
        result.totalMessagesReceived += toAdd.totalMessagesReceived;
        result.publishLatency.add(toAdd.publishLatency);
        result.publishDelayLatency.add(toAdd.publishDelayLatency);
        result.endToEndLatency.add(toAdd.endToEndLatency);

        return result;
    }
}
