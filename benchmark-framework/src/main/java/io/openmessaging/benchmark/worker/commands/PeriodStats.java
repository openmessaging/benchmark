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
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
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

    @JsonIgnore
    public Histogram publishLatency = new Histogram(TimeUnit.SECONDS.toMicros(60), 5);
    public byte[] publishLatencyBytes;

    @JsonIgnore
    public Histogram publishDelayLatency = new Histogram(TimeUnit.SECONDS.toMicros(60), 5);
    public byte[] publishDelayLatencyBytes;


    @JsonIgnore
    public Histogram endToEndLatency = new Histogram(TimeUnit.HOURS.toMicros(12), 5);
    public byte[] endToEndLatencyBytes;

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

        try {
            result.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.publishLatencyBytes), SECONDS.toMicros(30)));

            result.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.publishDelayLatencyBytes), SECONDS.toMicros(30)));

            result.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.endToEndLatencyBytes), HOURS.toMicros(12)));
        } catch (ArrayIndexOutOfBoundsException | DataFormatException e) {
            throw new RuntimeException(e);
        }

        result.messagesSent += toAdd.messagesSent;
        result.messageSendErrors += toAdd.messageSendErrors;
        result.bytesSent += toAdd.bytesSent;
        result.messagesReceived += toAdd.messagesReceived;
        result.bytesReceived += toAdd.bytesReceived;
        result.totalMessagesSent += toAdd.totalMessagesSent;
        result.totalMessageSendErrors += toAdd.totalMessageSendErrors;
        result.totalMessagesReceived += toAdd.totalMessagesReceived;

        try {
            result.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.publishLatencyBytes), SECONDS.toMicros(30)));

            result.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.publishDelayLatencyBytes), SECONDS.toMicros(30)));

            result.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.endToEndLatencyBytes), HOURS.toMicros(12)));
        } catch (ArrayIndexOutOfBoundsException | DataFormatException e) {
            throw new RuntimeException(e);
        }

        return result;
    }
}
