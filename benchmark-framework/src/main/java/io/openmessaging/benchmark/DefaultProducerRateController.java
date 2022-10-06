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

import static io.openmessaging.benchmark.DefaultProducerRateController.State.MAIN_CONTROL_LOOP;
import static io.openmessaging.benchmark.DefaultProducerRateController.State.PRODUCER_BACKOFF_LOOP;
import static io.openmessaging.benchmark.DefaultProducerRateController.State.PRODUCER_CATCHUP_LOOP;

import io.openmessaging.benchmark.utils.PaddingDecimalFormat;
import java.text.DecimalFormat;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The original rate controller refactored into a {@link ProducerRateController}. Note that this
 * implementation cannot be used in conjunction with a consumer backlog as it'll attempt to reduce
 * the producer rate when the consumer backlog increases.
 */
public class DefaultProducerRateController implements ProducerRateController {
    private double maxRate = Double.MAX_VALUE; // Discovered max sustainable rate
    private double minRate = 0.1;
    private double reducedTargetRate;
    private int successfulPeriods = 0;
    private long catchUpStartMs = -1L;
    private State state = MAIN_CONTROL_LOOP;
    private final Clock clock;

    DefaultProducerRateController(Clock clock, Workload workload) {
        this.clock = clock;
    }

    @Override
    public Optional<Double> nextTargetProducerRate(
            long observationTimeStampNanos,
            BenchmarkPhase phase,
            double targetProducerRate,
            double observedProducerRate,
            double observedConsumerRate,
            long observedProducerBacklog,
            long observedConsumerBacklog) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Current rate: {} -- Publish rate {} -- Consume Rate: {} -- min-rate: {} -- max-rate: {}",
                    dec.format(targetProducerRate),
                    dec.format(observedProducerRate),
                    dec.format(observedConsumerRate),
                    dec.format(minRate),
                    dec.format(maxRate));
        }

        switch (state) {
            case MAIN_CONTROL_LOOP:
                {
                    if (observedProducerRate < targetProducerRate * 0.95) {
                        // Producer is not able to publish as fast as requested
                        maxRate = targetProducerRate * 1.1;
                        double nextRate = minRate + (targetProducerRate - minRate) / 2;
                        log.debug("Publishers are not meeting requested rate - reducing to {}", nextRate);
                        return Optional.of(nextRate);
                    } else if (observedConsumerRate < observedProducerRate * 0.98) {
                        // If the consumers are building backlog, we should slow down publish rate
                        reducedTargetRate = minRate + (targetProducerRate - minRate) / 2;
                        log.debug(
                                "Consumers are not meeting requested rate - reducing to {}", reducedTargetRate);
                        maxRate = targetProducerRate;
                        state = PRODUCER_BACKOFF_LOOP;
                        return Optional.of(minRate / 10);
                    } else if (targetProducerRate < maxRate) {
                        minRate = targetProducerRate;
                        double nextRate = Math.min(targetProducerRate * 2, maxRate);
                        log.debug("No bottleneck found - increasing the rate to {}", nextRate);
                        return Optional.of(nextRate);
                    } else if (++successfulPeriods > 3) {
                        minRate = targetProducerRate * 0.95;
                        maxRate = targetProducerRate * 1.05;
                        successfulPeriods = 0;
                    }
                    // Keep going at the rate we're at
                    return Optional.empty();
                }
            case PRODUCER_BACKOFF_LOOP:
                {
                    if (observedConsumerBacklog >= 1000L) {
                        // Wait until backlog is near empty until setting producer to reduced rate
                        return Optional.empty();
                    }
                    state = PRODUCER_CATCHUP_LOOP;
                    log.debug("Resuming load at reduced rate");
                    catchUpStartMs = clock.millis();
                    return Optional.of(reducedTargetRate);
                }
            case PRODUCER_CATCHUP_LOOP:
                {
                    // Wait a while before returning to the main control loop and potentially making further
                    // adjustments
                    if (clock.millis() - catchUpStartMs > 500) {
                        state = MAIN_CONTROL_LOOP;
                    }
                    return Optional.empty();
                }
            default:
                throw new IllegalStateException("Unhandled state: " + state);
        }
    }

    enum State {
        MAIN_CONTROL_LOOP,
        PRODUCER_BACKOFF_LOOP,
        PRODUCER_CATCHUP_LOOP
    }

    public static class Factory implements ProducerRateController.Factory {

        @Override
        public ProducerRateController newInstance(Workload workload) {
            return new DefaultProducerRateController(Clock.systemUTC(), workload);
        }
    }

    private static final DecimalFormat dec = new PaddingDecimalFormat("0.0", 4);
    private static final Logger log = LoggerFactory.getLogger(DefaultProducerRateController.class);
}
