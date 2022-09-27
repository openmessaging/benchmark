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

import static java.util.Collections.unmodifiableList;

import io.openmessaging.benchmark.Workload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Expands a {@link io.openmessaging.benchmark.tool.workload.WorkloadSetTemplate} into a set of
 * {@link io.openmessaging.benchmark.Workload Workloads}.
 */
@Slf4j
class WorkloadGenerator {
    private final WorkloadSetTemplate template;
    private final WorkloadNameFormat nameFormat;

    WorkloadGenerator(@NonNull WorkloadSetTemplate template) {
        this.template = template;
        nameFormat = new WorkloadNameFormat(template.nameFormat);
    }

    List<Workload> generate() throws IOException {
        List<Workload> workloads = new ArrayList<>();
        Workload workload = new Workload();
        workload.keyDistributor = template.keyDistributor;
        workload.payloadFile = template.payloadFile;
        workload.randomBytesRatio = template.randomBytesRatio;
        workload.randomizedPayloadPoolSize = template.randomizedPayloadPoolSize;
        workload.consumerBacklogSizeGB = template.consumerBacklogSizeGB;
        workload.testDurationMinutes = template.testDurationMinutes;
        workload.warmupDurationMinutes = template.warmupDurationMinutes;
        workload.useRandomizedPayloads = template.useRandomizedPayloads;
        for (int t : template.topics) {
            for (int pa : template.partitionsPerTopic) {
                for (int ms : template.messageSize) {
                    for (int pd : template.producersPerTopic) {
                        for (int st : template.subscriptionsPerTopic) {
                            for (int cn : template.consumerPerSubscription) {
                                for (int pr : template.producerRate) {
                                    workload.topics = t;
                                    workload.partitionsPerTopic = pa;
                                    workload.messageSize = ms;
                                    workload.producersPerTopic = pd;
                                    workload.subscriptionsPerTopic = st;
                                    workload.consumerPerSubscription = cn;
                                    workload.producerRate = pr;
                                    Workload copy = copyOf(workload);
                                    workloads.add(copy);
                                    log.info("Generated: {}", copy.name);
                                }
                            }
                        }
                    }
                }
            }
        }
        log.info("Generated {} workloads.", workloads.size());
        return unmodifiableList(workloads);
    }

    private Workload copyOf(Workload workload) {
        Workload copy = new Workload();
        copy.keyDistributor = workload.keyDistributor;
        copy.payloadFile = workload.payloadFile;
        copy.randomBytesRatio = workload.randomBytesRatio;
        copy.randomizedPayloadPoolSize = workload.randomizedPayloadPoolSize;
        copy.consumerBacklogSizeGB = workload.consumerBacklogSizeGB;
        copy.testDurationMinutes = workload.testDurationMinutes;
        copy.warmupDurationMinutes = workload.warmupDurationMinutes;
        copy.topics = workload.topics;
        copy.partitionsPerTopic = workload.partitionsPerTopic;
        copy.messageSize = workload.messageSize;
        copy.producersPerTopic = workload.producersPerTopic;
        copy.subscriptionsPerTopic = workload.subscriptionsPerTopic;
        copy.consumerPerSubscription = workload.consumerPerSubscription;
        copy.producerRate = workload.producerRate;
        copy.useRandomizedPayloads = workload.useRandomizedPayloads;
        copy.name = nameFormat.from(copy);
        return copy;
    }
}
