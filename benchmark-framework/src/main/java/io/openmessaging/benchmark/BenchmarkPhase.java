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

/** Used to indicate the Workload benchmarks phase. */
public enum BenchmarkPhase {
    /** The workload benchmark has not been invoked. */
    IDLE,
    /**
     * The benchmark will connect to the system under test, potentially creating topics and
     * subscriptions with brokers that do so eagerly.
     */
    INITIALIZE,
    /**
     * Benchmark is validating that a message can be passed from producer to consumer on each topic.
     * This test exists to prime brokers that lazily create topics or subscriptions.
     */
    READINESS_CHECK,
    /**
     * Benchmark running a load through the system, producing at the requested target rate and
     * consuming as quickly as possible. The benchmark will not collect any statistics in this phase.
     */
    WARM_UP,
    /**
     * Benchmark has stopped consumers and is building a backlog in the system under test to the
     * requested size, using the target producer rate. The statistics gathered in this phase will
     * contribute to the test results.
     */
    BACKLOG_FILL,
    /**
     * Benchmark has restarted consumers and will be consuming the backlog as quickly as possible. It
     * continues to produce messages at the target producer rate. The test wil continue until the
     * backlog is considered empty. The statistics gathered in this phase will contribute to the test
     * results.
     */
    BACKLOG_DRAIN,
    /**
     * Benchmark is attempting to send messages at the target producer rate and consume them as
     * quickly as possible. This phase will continue until the requested test duration has been
     * achieved. The statistics gathered in this phase will contribute to the test results.
     */
    LOAD
}
