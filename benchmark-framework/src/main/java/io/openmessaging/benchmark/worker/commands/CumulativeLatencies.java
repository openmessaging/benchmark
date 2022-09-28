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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CumulativeLatencies {

    @JsonIgnore
    public Histogram publishLatency = new Histogram(SECONDS.toMicros(60), 5);
    public byte[] publishLatencyBytes;

    @JsonIgnore
    public Histogram publishDelayLatency = new Histogram(SECONDS.toMicros(60), 5);
    public byte[] publishDelayLatencyBytes;

    @JsonIgnore
    public Histogram endToEndLatency = new Histogram(HOURS.toMicros(12), 5);
    public byte[] endToEndLatencyBytes;

    public CumulativeLatencies plus(CumulativeLatencies toAdd) {
        CumulativeLatencies result = new CumulativeLatencies();
        try {
            result.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.publishLatencyBytes), SECONDS.toMicros(30)));
        } catch (Exception e) {
            log.error("Failed to decode publish latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(this.publishLatencyBytes)));
            throw new RuntimeException(e);
        }

        try {
            result.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.publishDelayLatencyBytes), SECONDS.toMicros(30)));
        } catch (Exception e) {
            log.error("Failed to decode publish delay latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(this.publishDelayLatencyBytes)));
            throw new RuntimeException(e);
        }

        try {
            result.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(this.endToEndLatencyBytes), HOURS.toMicros(12)));
        } catch (Exception e) {
            log.error("Failed to decode end-to-end latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(this.endToEndLatencyBytes)));
            throw new RuntimeException(e);
        }

        try {
            result.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.publishLatencyBytes), SECONDS.toMicros(30)));
        } catch (Exception e) {
            log.error("Failed to decode publish latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(toAdd.publishLatencyBytes)));
            throw new RuntimeException(e);
        }

        try {
            result.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.publishDelayLatencyBytes), SECONDS.toMicros(30)));
        } catch (Exception e) {
            log.error("Failed to decode publish delay latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(toAdd.publishDelayLatencyBytes)));
            throw new RuntimeException(e);
        }

        try {
            result.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                    ByteBuffer.wrap(toAdd.endToEndLatencyBytes), HOURS.toMicros(12)));
        } catch (Exception e) {
            log.error("Failed to decode end-to-end latency: {}",
                    ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(toAdd.endToEndLatencyBytes)));
            throw new RuntimeException(e);
        }
        return result;
    }

    private static final Logger log = LoggerFactory.getLogger(CumulativeLatencies.class);
}
