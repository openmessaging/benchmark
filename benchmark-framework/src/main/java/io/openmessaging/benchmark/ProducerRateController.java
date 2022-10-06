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


import java.util.Optional;

/**
 * Abstraction that allows the dynamic tuning of producer message rates to meet some optimization
 * goal such as the minimization of consumer and producer backlogs. System observations are
 * periodically passed via {@link #nextTargetProducerRate(long, BenchmarkPhase, double, double,
 * double, long, long) nextTargetProducerRate}, which the implementation should then use to
 * determine the next target producer rate. The {@link WorkloadGenerator} will then set the producer
 * rate to this target value.
 */
public interface ProducerRateController {

    /**
     * Determines the next target producer rate, possibly based on the lastes observation of the sstem
     * performance, and possibly previous states that have been captured by the implementing class.
     * Note that the controller can explicitly indicate not to adjust the rate by returning an empty
     * {@link Optional}.
     *
     * @param observationTimeStampNanos The time at which the observation was made in epoch nanos.
     * @param phase The phase that the benchmark was in when the observation was made.
     * @param targetProducerRate The previously requested target producer rate (msgs/sec).
     * @param observedProducerRate The observed producer rate (msgs/sec).
     * @param observedConsumerRate The observed consumer rate (msgs/sec).
     * @param observedProducerBacklog The observed producer backlog (msgs).
     * @param observedConsumerBacklog The observed consumer backlog (msgs).
     * @return The new target rate for message production, or an empty {@link Optional} if no
     *     adjustment should be made.
     */
    Optional<Double> nextTargetProducerRate(
            long observationTimeStampNanos,
            BenchmarkPhase phase,
            double targetProducerRate,
            double observedProducerRate,
            double observedConsumerRate,
            long observedProducerBacklog,
            long observedConsumerBacklog);

    /** Factory for creating instances of {@link ProducerRateController}. */
    interface Factory {
        /**
         * Creates a new instance of the producer rate controller. One should be created per {@link
         * Workload}.
         *
         * @param workload The workload being benchmarked.
         * @return The created instance.
         */
        ProducerRateController newInstance(Workload workload);
    }
}
