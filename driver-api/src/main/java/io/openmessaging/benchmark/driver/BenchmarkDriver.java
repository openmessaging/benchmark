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
package io.openmessaging.benchmark.driver;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;

/** Base driver interface. */
public interface BenchmarkDriver extends AutoCloseable {
    /**
     * Driver implementation can use this method to initialize the client libraries, with the provided
     * configuration file.
     *
     * <p>The format of the configuration file is specific to the driver implementation.
     *
     * @param configurationFile the configuration file
     * @param statsLogger stats logger to collect stats from benchmark driver
     * @throws IOException if configuration file cannot be read
     * @throws InterruptedException if initialization is interrupted
     */
    void initialize(File configurationFile, StatsLogger statsLogger)
            throws IOException, InterruptedException;

    /**
     * Get a driver specific prefix to be used in creating multiple topic names.
     *
     * @return the topic name prefix
     */
    String getTopicNamePrefix();

    /**
     * Create a new topic with a given number of partitions.
     *
     * @param topic the topic name
     * @param partitions the number of partitions
     * @return a future that completes when the topic is created
     */
    CompletableFuture<Void> createTopic(String topic, int partitions);

    /**
     * Create a list of new topics with the given number of partitions.
     *
     * @param topicInfos the list of topics to create
     * @return a future that completes when the topics are created
     */
    default CompletableFuture<Void> createTopics(List<TopicInfo> topicInfos) {
        return CompletableFuture.allOf(
                topicInfos.stream()
                        .map(topicInfo -> createTopic(topicInfo.topic(), topicInfo.partitions()))
                        .toArray(CompletableFuture[]::new));
    }

    /**
     * Create a producer for a given topic.
     *
     * @param topic the topic name
     * @return a producer future
     */
    CompletableFuture<BenchmarkProducer> createProducer(String topic);

    /**
     * Create producers for given topics.
     *
     * @param producers the list of producers to create
     * @return a producers future
     */
    default CompletableFuture<List<BenchmarkProducer>> createProducers(List<ProducerInfo> producers) {
        List<CompletableFuture<BenchmarkProducer>> futures =
                producers.stream().map(producerInfo -> createProducer(producerInfo.topic())).toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    /**
     * Create a benchmark consumer relative to one particular topic and subscription.
     *
     * <p>It is responsibility of the driver implementation to invoke the <code>consumerCallback
     * </code> each time a message is received.
     *
     * @param topic the topic name
     * @param subscriptionName the subscription name
     * @param consumerCallback the callback to invoke when messages are received
     * @return a consumer future
     */
    CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback);

    /**
     * Create consumers for given topics.
     *
     * @param consumers the list of consumers to create
     * @return a consumers future
     */
    default CompletableFuture<List<BenchmarkConsumer>> createConsumers(List<ConsumerInfo> consumers) {
        List<CompletableFuture<BenchmarkConsumer>> futures =
                consumers.stream()
                        .map(
                                consumerInfo ->
                                        createConsumer(
                                                consumerInfo.topic(),
                                                consumerInfo.subscriptionName(),
                                                consumerInfo.consumerCallback()))
                        .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    /**
     * Topic information record containing topic name and partition count.
     *
     * @param topic the topic name
     * @param partitions the number of partitions
     */
    record TopicInfo(String topic, int partitions) {}

    /**
     * Producer information record containing producer ID and topic.
     *
     * @param id the producer ID
     * @param topic the topic name
     */
    record ProducerInfo(int id, String topic) {}

    /**
     * Consumer information record containing consumer details.
     *
     * @param id the consumer ID
     * @param topic the topic name
     * @param subscriptionName the subscription name
     * @param consumerCallback the callback to invoke when messages are received
     */
    record ConsumerInfo(
            int id, String topic, String subscriptionName, ConsumerCallback consumerCallback) {}
}
