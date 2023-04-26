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
    private final double targetP99EndToEndLatency;
    private final double targetP99PublishLatency;

    @Getter(PACKAGE)
    private double rampingFactor;

    private long previousTotalPublished = 0;
    private long previousTotalReceived = 0;

    private int hintTargetLatencyTimes = 0;

    private int notHintMaxRateTimes = 0;

    private int hintTargetLatencyTimesGlobal = 0;

    private double maxRate = 0;

    RateController() {
        publishBacklogLimit = Env.getLong("PUBLISH_BACKLOG_LIMIT", 1_000);
        receiveBacklogLimit = Env.getLong("RECEIVE_BACKLOG_LIMIT", 1_000);
        minRampingFactor = Env.getDouble("MIN_RAMPING_FACTOR", 0.01);
        maxRampingFactor = Env.getDouble("MAX_RAMPING_FACTOR", 1);
        targetP99EndToEndLatency = Env.getDouble("TARGET_P99_END_TO_END_LATENCY", 0);
        targetP99PublishLatency = Env.getDouble("TARGET_P99_PUBLISH_LATENCY", 0);

        rampingFactor = maxRampingFactor;
    }

    double nextRate(
            double rate,
            long periodNanos,
            long totalPublished,
            long totalReceived,
            double p99PublishLatency,
            double p99EndToEndLatency) {
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

        log.info("Current p99PublishLatency {}", p99PublishLatency);
        log.info("Current targetP99PublishLatency {}", targetP99PublishLatency);

        if ((targetP99EndToEndLatency != 0 && p99EndToEndLatency > targetP99EndToEndLatency)
                || (targetP99PublishLatency != 0 && p99PublishLatency > targetP99PublishLatency)) {
            rampDown();
            hintTargetLatencyTimes += 1;

            if (hintTargetLatencyTimes >= 2 && hintTargetLatencyTimesGlobal > 3) {
                maxRate = rate;
                hintTargetLatencyTimesGlobal = 0;
            }

            if (hintTargetLatencyTimes > 20) {
                hintTargetLatencyTimesGlobal += 1;
                log.info("Hint target latency global {}", hintTargetLatencyTimesGlobal);
                log.info("Hint target latency for 20 times, decrease rate from {} to {}", rate, rate * 0.8);
                hintTargetLatencyTimes = 0;
                notHintMaxRateTimes = 0;
                return rate * 0.8;
            }
            return rate;
        }

        notHintMaxRateTimes += 1;
        hintTargetLatencyTimes = 0;

        if (notHintMaxRateTimes > 50) {
            log.info("Increase rate from {} to rate {}", rate, rate * 1.2);
            notHintMaxRateTimes = 0;
            if (maxRate != 0) {
                return Math.min(maxRate, rate * 1.2);
            } else {
                return rate * 1.2;
            }
        }
        return rate;
    }

    private double nextRate(long periodNanos, long actual, long expected, long backlog, String type) {
        log.debug("{} backlog: {}", type, backlog);
        rampDown();
        long nextExpected = Math.max(0, expected - backlog);
        double nextExpectedRate = rate(nextExpected, periodNanos);
        double actualRate = rate(actual, periodNanos);
        return 0.2 * actualRate + 0.8 * nextExpectedRate;
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
