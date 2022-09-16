package io.openmessaging.benchmark.tool.workload;

import static java.util.Collections.unmodifiableList;
import io.openmessaging.benchmark.Workload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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
