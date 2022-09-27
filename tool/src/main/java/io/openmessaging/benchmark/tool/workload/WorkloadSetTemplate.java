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
package io.openmessaging.benchmark.tool.workload;


import io.openmessaging.benchmark.utils.distributor.KeyDistributorType;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A template that defines a set of workload definitions. This is much like the {@link
 * io.openmessaging.benchmark.Workload} entity, except that for many values that typically change in
 * a benchmark, one can specify a sequence of values.
 */
@Data
@NoArgsConstructor
public class WorkloadSetTemplate {
    public static final String DEFAULT_NAME_TEMPLATE =
            "${topics}-topics-${partitionsPerTopic}-partitions-${messageSize}b"
                    + "-${producersPerTopic}p-${consumerPerSubscription}c-${producerRate}";
    public String nameFormat = DEFAULT_NAME_TEMPLATE;

    /** Number of topics to create in the test. */
    public List<Integer> topics = Collections.emptyList();
    /** Number of partitions each topic will contain. */
    public List<Integer> partitionsPerTopic = Collections.emptyList();

    public List<Integer> messageSize = Collections.emptyList();
    public List<Integer> subscriptionsPerTopic = Collections.emptyList();
    public List<Integer> producersPerTopic = Collections.emptyList();
    public List<Integer> consumerPerSubscription = Collections.emptyList();
    public List<Integer> producerRate = Collections.emptyList();

    public KeyDistributorType keyDistributor = KeyDistributorType.NO_KEY;
    public String payloadFile = null;
    public boolean useRandomizedPayloads = false;
    public double randomBytesRatio = 0.0;
    public int randomizedPayloadPoolSize = 0;
    public long consumerBacklogSizeGB = 0;
    public int testDurationMinutes = 5;
    public int warmupDurationMinutes = 1;
}
