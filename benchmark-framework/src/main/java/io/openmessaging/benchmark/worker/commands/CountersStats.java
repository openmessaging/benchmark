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

public class CountersStats {
    public long messagesSent;
    public long messagesReceived;
    public long messageSendErrors;

    public CountersStats plus(CountersStats toAdd) {
        CountersStats result = new CountersStats();
        result.messagesSent += this.messagesSent;
        result.messagesReceived += this.messagesReceived;
        result.messageSendErrors += this.messageSendErrors;

        result.messagesSent += toAdd.messagesSent;
        result.messagesReceived += toAdd.messagesReceived;
        result.messageSendErrors += toAdd.messageSendErrors;
        return result;
    }
}
