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


import io.openmessaging.benchmark.Workload;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.text.StrSubstitutor;

/**
 * Generates {@link Workload} names based on a template. Substitutes template place-holders of the
 * form {@code ${variableName}}, where {@code variableName} is the name of a public member in {@link
 * Workload}. Note that the set of variables is statically assigned. Numeric values will typically
 * be in a form that includes an SI suffix.
 */
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
        params.put(
                "producerRate",
                (workload.producerRate >= MAX_PRODUCER_RATE)
                        ? "max-rate"
                        : countToDisplaySize(workload.producerRate));
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
