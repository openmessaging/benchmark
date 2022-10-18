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

import static io.openmessaging.benchmark.BenchmarkPhase.BACKLOG_DRAIN;
import static io.openmessaging.benchmark.BenchmarkPhase.BACKLOG_FILL;
import static io.openmessaging.benchmark.BenchmarkPhase.LOAD;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateControllerTest {
    private final RateController rateController = new RateController();
    private double rate = 10_000;
    private long periodNanos = SECONDS.toNanos(1);

    @Test
    void receiveBacklog() {
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // no backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 10_000, 10_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // receive backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 20_000, 15_000);
        assertThat(rate).isEqualTo(5_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);
    }

    @Test
    void receiveBacklogFill() {
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // no backlog
        rate = rateController.nextRate(BACKLOG_FILL, rate, periodNanos, 10_000, 10_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // stop consumers to fill backlog
        rate = rateController.nextRate(BACKLOG_FILL, rate, periodNanos, 20_000, 10_000);
        assertThat(rate).isEqualTo(10_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);
    }

    @Test
    void receiveBacklogDrain() {
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // no backlog
        rate = rateController.nextRate(BACKLOG_DRAIN, rate, periodNanos, 40_000, 10_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // start consumers to drain backlog
        rate = rateController.nextRate(BACKLOG_DRAIN, rate, periodNanos, 50_000, 10_000);
        assertThat(rate).isEqualTo(10_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);

        rate = rateController.nextRate(BACKLOG_DRAIN, rate, periodNanos, 60_000, 20_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1.0);

        rate = rateController.nextRate(BACKLOG_DRAIN, rate, periodNanos, 70_000, 30_000);
        assertThat(rate).isEqualTo(10_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);

        rate = rateController.nextRate(BACKLOG_DRAIN, rate, periodNanos, 80_000, 40_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1.0);
    }

    @Test
    void publishBacklog() {
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // no backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 10_000, 10_000);
        assertThat(rate).isEqualTo(20_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // publish backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 15_000, 20_000);
        assertThat(rate).isEqualTo(5_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);
    }

    @Test
    void rampUp() {
        assertThat(rateController.getRampingFactor()).isEqualTo(1);

        // receive backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 10_000, 5_000);
        assertThat(rate).isEqualTo(5_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(0.5);

        // no backlog
        rate = rateController.nextRate(LOAD, rate, periodNanos, 20_000, 20_000);
        assertThat(rate).isEqualTo(10_000);
        assertThat(rateController.getRampingFactor()).isEqualTo(1);
    }
}
