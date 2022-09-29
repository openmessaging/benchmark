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
package io.openmessaging.benchmark.driver.pravega.config;

public class PravegaConfig {
    // By default, Stream auto-scaling is not configured. So the scaling thresholds are initialized
    // with -1.
    public static final int DEFAULT_STREAM_AUTOSCALING_VALUE = -1;

    public PravegaClientConfig client;
    public PravegaWriterConfig writer;

    // includeTimestampInEvent must be true to measure end-to-end latency.
    public boolean includeTimestampInEvent = true;
    public boolean enableTransaction = false;
    // defines how many events the benchmark writes on each transaction prior
    // committing it (only applies if transactional writers are enabled).
    public int eventsPerTransaction = 1;

    // Enable the configuration of Streams with auto-scaling policies
    public boolean enableStreamAutoScaling = false;
    // Number of events/kbytes per second to trigger a Segment split in Pravega.
    public int eventsPerSecond = DEFAULT_STREAM_AUTOSCALING_VALUE;
    public int kbytesPerSecond = DEFAULT_STREAM_AUTOSCALING_VALUE;

    // Create a Pravega scope. Must set to false in Streaming Data Platform.
    public boolean createScope = true;

    // By default, streams created for benchmarking will be deleted at the end of the test.
    // Set to false to keep the streams.
    public boolean deleteStreams = true;
}
