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
package io.openmessaging.benchmark;

import static java.util.concurrent.TimeUnit.SECONDS;
import static lombok.AccessLevel.PACKAGE;

import io.openmessaging.benchmark.utils.Env;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class RateController {
    private static final long ONE_SECOND_IN_NANOS = SECONDS.toNanos(1);
    private final long publishBacklogLimit;
    private final long receiveBacklogLimit;
    private final double minRampingFactor;
    private final double maxRampingFactor;

    @Getter(PACKAGE)
    private double rampingFactor;

    private long previousTotalPublished = 0;
    private long previousTotalReceived = 0;

    RateController() {
        publishBacklogLimit = Env.getLong("PUBLISH_BACKLOG_LIMIT", 1_000);
        receiveBacklogLimit = Env.getLong("RECEIVE_BACKLOG_LIMIT", 1_000);
        minRampingFactor = Env.getDouble("MIN_RAMPING_FACTOR", 0.01);
        maxRampingFactor = Env.getDouble("MAX_RAMPING_FACTOR", 1);
        rampingFactor = maxRampingFactor;
    }

    double nextRate(double rate, long periodNanos, long totalPublished, long totalReceived) {
        long expected = (long) ((rate / ONE_SECOND_IN_NANOS) * periodNanos);
        long published = totalPublished - previousTotalPublished;
        long received = totalReceived - previousTotalReceived;

        previousTotalPublished = totalPublished;
        previousTotalReceived = totalReceived;

        if (log.isDebugEnabled()) {
            log.debug(
                    "Current rate: {} -- Publish rate {} -- Receive Rate: {}",
                    rate,
                    rate(published, periodNanos),
                    rate(received, periodNanos));
        }

        long receiveBacklog = totalPublished - totalReceived;
        if (receiveBacklog > receiveBacklogLimit) {
            return nextRate(periodNanos, received, expected, receiveBacklog, "Receive");
        }

        long publishBacklog = expected - published;
        if (publishBacklog > publishBacklogLimit) {
            return nextRate(periodNanos, published, expected, publishBacklog, "Publish");
        }

        rampUp();

        return rate + (rate * rampingFactor);
    }

    private double nextRate(long periodNanos, long actual, long expected, long backlog, String type) {
        log.debug("{} backlog: {}", type, backlog);
        rampDown();
        long nextExpected = Math.max(0, expected - backlog);
        double nextExpectedRate = rate(nextExpected, periodNanos);
        double actualRate = rate(actual, periodNanos);
        return Math.min(actualRate, nextExpectedRate);
    }

    private double rate(long count, long periodNanos) {
        return (count / (double) periodNanos) * ONE_SECOND_IN_NANOS;
    }

    private void rampUp() {
        rampingFactor = Math.min(maxRampingFactor, rampingFactor * 2);
    }

    private void rampDown() {
        rampingFactor = Math.max(minRampingFactor, rampingFactor / 2);
    }
}
