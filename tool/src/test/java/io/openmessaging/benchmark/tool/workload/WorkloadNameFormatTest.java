package io.openmessaging.benchmark.tool.workload;

import static org.assertj.core.api.Assertions.assertThat;

import io.openmessaging.benchmark.Workload;
import org.junit.jupiter.api.Test;

class WorkloadNameFormatTest {

    public String nameFormat = "${topics}-topics-${partitionsPerTopic}-partitions-${messageSize}b"
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