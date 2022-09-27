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

import static org.assertj.core.api.Assertions.assertThat;

import io.openmessaging.benchmark.Workload;
import org.junit.jupiter.api.Test;

class WorkloadNameFormatTest {

    public String nameFormat =
            "${topics}-topics-${partitionsPerTopic}-partitions-${messageSize}b"
                    + "-${producersPerTopic}p-${consumerPerSubscription}c-${producerRate}";

    @Test
    void nameOverride() {
        Workload workload = new Workload();
        workload.name = "x";
        String name = new WorkloadNameFormat(nameFormat).from(workload);
        assertThat(name).isEqualTo("x");
    }

    @Test
    void from() {
        Workload workload = new Workload();
        workload.topics = 1456;
        workload.partitionsPerTopic = 2123;
        workload.messageSize = 617890;
        workload.producersPerTopic = 45;
        workload.consumerPerSubscription = 541;
        workload.producerRate = 1000000;
        String name = new WorkloadNameFormat(nameFormat).from(workload);
        assertThat(name).isEqualTo("1k-topics-2k-partitions-617kb-45p-541c-1m");
    }
}
