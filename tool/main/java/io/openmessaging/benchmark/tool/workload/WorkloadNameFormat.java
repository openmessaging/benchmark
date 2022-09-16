package io.openmessaging.benchmark.tool.workload;

import io.openmessaging.benchmark.Workload;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrSubstitutor;

@RequiredArgsConstructor
class WorkloadNameFormat {

    private static final long MAX_PRODUCER_RATE = 10_000_000;

    private final String format;

    String from(Workload workload) {
        if (workload.name != null) {
            return workload.name;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("topics", countToDisplaySize(workload.topics));
        params.put("partitionsPerTopic", countToDisplaySize(workload.partitionsPerTopic));
        params.put("messageSize", countToDisplaySize(workload.messageSize));
        params.put("subscriptionsPerTopic", countToDisplaySize(workload.subscriptionsPerTopic));
        params.put("producersPerTopic", countToDisplaySize(workload.producersPerTopic));
        params.put("consumerPerSubscription", countToDisplaySize(workload.consumerPerSubscription));
        params.put("producerRate",
                (workload.producerRate >= MAX_PRODUCER_RATE) ? "max-rate" : countToDisplaySize(workload.producerRate));
        params.put("keyDistributor", workload.keyDistributor);
        params.put("payloadFile", workload.payloadFile);
        params.put("useRandomizedPayloads", workload.useRandomizedPayloads);
        params.put("randomBytesRatio", workload.randomBytesRatio);
        params.put("randomizedPayloadPoolSize", countToDisplaySize(workload.randomizedPayloadPoolSize));
        params.put("consumerBacklogSizeGB", countToDisplaySize(workload.consumerBacklogSizeGB));
        params.put("testDurationMinutes", workload.testDurationMinutes);
        params.put("warmupDurationMinutes", workload.warmupDurationMinutes);
        return StrSubstitutor.replace(format, params, "${", "}");
    }

    private static String countToDisplaySize(long size) {
        String displaySize;
        if (size / 1_000_000_000L > 0L) {
            displaySize = size / 1_000_000_000L + "g";
        } else if (size / 1_000_000L > 0L) {
            displaySize = size / 1_000_000L + "m";
        } else if (size / 1_000L > 0L) {
            displaySize = size / 1_000 + "k";
        } else {
            displaySize = size + "";
        }
        return displaySize;
    }
}
