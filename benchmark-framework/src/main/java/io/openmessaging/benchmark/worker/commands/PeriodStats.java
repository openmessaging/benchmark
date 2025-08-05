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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.HdrHistogram.Histogram;

public record PeriodStats(
        @JsonProperty("messagesSent") long messagesSent,
        @JsonProperty("messageSendErrors") long messageSendErrors,
        @JsonProperty("bytesSent") long bytesSent,
        @JsonProperty("messagesReceived") long messagesReceived,
        @JsonProperty("bytesReceived") long bytesReceived,
        @JsonProperty("totalMessagesSent") long totalMessagesSent,
        @JsonProperty("totalMessageSendErrors") long totalMessageSendErrors,
        @JsonProperty("totalMessagesReceived") long totalMessagesReceived,
        @JsonProperty("publishLatency") Histogram publishLatency,
        @JsonProperty("publishDelayLatency") Histogram publishDelayLatency,
        @JsonProperty("endToEndLatency") Histogram endToEndLatency) {

    public PeriodStats() {
        this(0, 0, 0, 0, 0, 0, 0, 0, new Histogram(5), new Histogram(5), new Histogram(5));
    }

    /**
     * Combines this PeriodStats with another, returning a new record with the summed values.
     *
     * @param other The other PeriodStats to add.
     * @return A new PeriodStats instance with the combined values.
     */
    public PeriodStats plus(PeriodStats other) {
        Histogram combinedPublishLatency = new Histogram(5);
        combinedPublishLatency.add(this.publishLatency);
        combinedPublishLatency.add(other.publishLatency);

        Histogram combinedPublishDelayLatency = new Histogram(5);
        combinedPublishDelayLatency.add(this.publishDelayLatency);
        combinedPublishDelayLatency.add(other.publishDelayLatency);

        Histogram combinedEndToEndLatency = new Histogram(5);
        combinedEndToEndLatency.add(this.endToEndLatency);
        combinedEndToEndLatency.add(other.endToEndLatency);

        return new PeriodStats(
                this.messagesSent + other.messagesSent,
                this.messageSendErrors + other.messageSendErrors,
                this.bytesSent + other.bytesSent,
                this.messagesReceived + other.messagesReceived,
                this.bytesReceived + other.bytesReceived,
                this.totalMessagesSent + other.totalMessagesSent,
                this.totalMessageSendErrors + other.totalMessageSendErrors,
                this.totalMessagesReceived + other.totalMessagesReceived,
                combinedPublishLatency,
                combinedPublishDelayLatency,
                combinedEndToEndLatency);
    }
}
