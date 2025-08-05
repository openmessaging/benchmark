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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.Workload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Expands a {@link WorkloadSetTemplate} into a set of {@link Workload Workloads}. */
class WorkloadGenerator {
    private static final Logger log = LoggerFactory.getLogger(WorkloadGenerator.class);

    private final WorkloadSetTemplate template;
    private final WorkloadNameFormat nameFormat;

    /**
     * Creates a new WorkloadGenerator with the specified template.
     *
     * @param template the workload set template to use for generation
     * @throws IllegalArgumentException if template is null
     */
    WorkloadGenerator(WorkloadSetTemplate template) {
        this.template = Objects.requireNonNull(template, "Template cannot be null");
        this.nameFormat = new WorkloadNameFormat(template.nameFormat());
    }

    /**
     * Generates a list of workloads based on the template configuration. Creates all combinations of
     * the parameter lists defined in the template.
     *
     * @return a list of generated workloads
     * @throws IOException if there's an error during workload generation
     */
    List<Workload> generate() throws IOException {
        log.info("Generating workloads from template with name format: {}", template.nameFormat());

        List<Workload> workloads = new ArrayList<>();

        // Get parameter lists, using defaults if empty
        List<Integer> topics = getOrDefault(template.topics(), List.of(1));
        List<Integer> partitionsPerTopic = getOrDefault(template.partitionsPerTopic(), List.of(1));
        List<Integer> messageSize = getOrDefault(template.messageSize(), List.of(1024));
        List<Integer> subscriptionsPerTopic =
                getOrDefault(template.subscriptionsPerTopic(), List.of(1));
        List<Integer> producersPerTopic = getOrDefault(template.producersPerTopic(), List.of(1));
        List<Integer> consumerPerSubscription =
                getOrDefault(template.consumerPerSubscription(), List.of(1));
        List<Integer> producerRate = getOrDefault(template.producerRate(), List.of(10000));

        // Generate all combinations
        for (int topicCount : topics) {
            for (int partitions : partitionsPerTopic) {
                for (int msgSize : messageSize) {
                    for (int subscriptions : subscriptionsPerTopic) {
                        for (int producers : producersPerTopic) {
                            for (int consumers : consumerPerSubscription) {
                                for (int rate : producerRate) {
                                    Workload workload =
                                            createWorkload(
                                                    topicCount,
                                                    partitions,
                                                    msgSize,
                                                    subscriptions,
                                                    producers,
                                                    consumers,
                                                    rate);
                                    workloads.add(workload);
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("Generated {} workloads", workloads.size());
        return workloads;
    }

    /**
     * Creates a single workload with the specified parameters.
     *
     * @param topics the number of topics
     * @param partitionsPerTopic the number of partitions per topic
     * @param messageSize the message size in bytes
     * @param subscriptionsPerTopic the number of subscriptions per topic
     * @param producersPerTopic the number of producers per topic
     * @param consumerPerSubscription the number of consumers per subscription
     * @param producerRate the producer rate
     * @return a new workload instance with the specified parameters
     */
    private Workload createWorkload(
            int topics,
            int partitionsPerTopic,
            int messageSize,
            int subscriptionsPerTopic,
            int producersPerTopic,
            int consumerPerSubscription,
            int producerRate) {

        Workload workload = new Workload();

        // Set workload parameters
        workload.topics = topics;
        workload.partitionsPerTopic = partitionsPerTopic;
        workload.messageSize = messageSize;
        workload.subscriptionsPerTopic = subscriptionsPerTopic;
        workload.producersPerTopic = producersPerTopic;
        workload.consumerPerSubscription = consumerPerSubscription;
        workload.producerRate = producerRate;

        // Set template-level parameters
        workload.keyDistributor = template.keyDistributor();
        workload.payloadFile = template.payloadFile();
        workload.useRandomizedPayloads = template.useRandomizedPayloads();
        workload.randomBytesRatio = template.randomBytesRatio();
        workload.randomizedPayloadPoolSize = template.randomizedPayloadPoolSize();
        workload.consumerBacklogSizeGB = template.consumerBacklogSizeGB();
        workload.testDurationMinutes = template.testDurationMinutes();
        workload.warmupDurationMinutes = template.warmupDurationMinutes();
        workload.name = nameFormat.from(workload);

        return workload;
    }

    /**
     * Creates a deep copy of the given workload.
     *
     * @param workload the workload to copy
     * @return a new workload instance with the same values
     */
    @SuppressWarnings("unused")
    private Workload copyOf(Workload workload) {
        Objects.requireNonNull(workload, "Workload cannot be null");

        try {
            // Use Jackson for deep copying
            ObjectMapper mapper =
                    new ObjectMapper(new YAMLFactory())
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            String serialized = mapper.writeValueAsString(workload);
            return mapper.readValue(serialized, Workload.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy workload", e);
        }
    }

    /**
     * Returns the provided list if it's not null and not empty, otherwise returns the default list.
     *
     * @param <T> the type of elements in the list
     * @param list the list to check
     * @param defaultValue the default list to return if the input list is null or empty
     * @return the original list if not null/empty, otherwise the default list
     */
    private <T> List<T> getOrDefault(List<T> list, List<T> defaultValue) {
        return (list != null && !list.isEmpty()) ? list : defaultValue;
    }
}
