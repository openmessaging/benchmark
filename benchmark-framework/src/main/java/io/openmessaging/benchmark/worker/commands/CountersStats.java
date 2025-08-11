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

public record CountersStats(
        @JsonProperty("messagesSent") long messagesSent,
        @JsonProperty("messageSendErrors") long messageSendErrors,
        @JsonProperty("messagesReceived") long messagesReceived) {

    public CountersStats() {
        this(0, 0, 0);
    }

    /**
     * Combines this CountersStats with another, returning a new record with the summed values.
     *
     * @param other The other CountersStats to add.
     * @return A new CountersStats instance with the combined values.
     */
    public CountersStats plus(CountersStats other) {
        return new CountersStats(
                this.messagesSent + other.messagesSent,
                this.messageSendErrors + other.messageSendErrors,
                this.messagesReceived + other.messagesReceived);
    }
}
