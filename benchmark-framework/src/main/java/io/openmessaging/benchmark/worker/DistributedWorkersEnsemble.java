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
package io.openmessaging.benchmark.worker;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static org.asynchttpclient.Dsl.asyncHttpClient;

import com.beust.jcommander.internal.Maps;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.openmessaging.benchmark.utils.ListPartition;
import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CountersStats;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicSubscription;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import org.HdrHistogram.Histogram;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedWorkersEnsemble implements Worker {
    private final Thread shutdownHook = new Thread(this::stopAll);
    private final List<String> workers;
    private final List<String> producerWorkers;
    private final List<String> consumerWorkers;

    private final AsyncHttpClient httpClient;

    private int numberOfUsedProducerWorkers;

    public DistributedWorkersEnsemble(List<String> workers, boolean extraConsumerWorkers) {
        Preconditions.checkArgument(workers.size() > 1);
        DefaultAsyncHttpClientConfig.Builder clientBuilder =
                Dsl.config().setReadTimeout(600000).setRequestTimeout(600000);
        httpClient = asyncHttpClient(clientBuilder);
        this.workers = unmodifiableList(workers);

        // For driver-jms extra consumers are required.
        // If there is an odd number of workers then allocate the extra to consumption.
        int numberOfProducerWorkers =
                extraConsumerWorkers ? (workers.size() + 2) / 3 : workers.size() / 2;
        List<List<String>> partitions =
                Lists.partition(Lists.reverse(workers), workers.size() - numberOfProducerWorkers);
        this.producerWorkers = partitions.get(1);
        this.consumerWorkers = partitions.get(0);

        log.info("Workers list - producers: {}", producerWorkers);
        log.info("Workers list - consumers: {}", consumerWorkers);

        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void initializeDriver(File configurationFile) throws IOException {
        byte[] confFileContent = Files.readAllBytes(Paths.get(configurationFile.toString()));
        sendPost(workers, "/initialize-driver", confFileContent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> createTopics(TopicsInfo topicsInfo) throws IOException {
        // Create all topics from a single worker node
        return (List<String>)
                post(workers.get(0), "/create-topics", writer.writeValueAsBytes(topicsInfo), List.class)
                        .join();
    }

    @Override
    public void createProducers(List<String> topics) {
        List<List<String>> topicsPerProducer =
                ListPartition.partitionList(topics, producerWorkers.size());
        Map<String, List<String>> topicsPerProducerMap = Maps.newHashMap();
        int i = 0;
        for (List<String> assignedTopics : topicsPerProducer) {
            topicsPerProducerMap.put(producerWorkers.get(i++), assignedTopics);
        }

        // Number of actually used workers might be less than available workers
        numberOfUsedProducerWorkers =
                (int) topicsPerProducerMap.values().stream().filter(t -> !t.isEmpty()).count();
        log.debug(
                "Producing worker count: {} of {}", numberOfUsedProducerWorkers, producerWorkers.size());

        CompletableFuture<Void>[] futures =
                topicsPerProducerMap.keySet().stream()
                        .map(
                                producer -> {
                                    try {
                                        return sendPost(
                                                producer,
                                                "/create-producers",
                                                writer.writeValueAsBytes(topicsPerProducerMap.get(producer)));
                                    } catch (Exception e) {
                                        CompletableFuture<Void> future = new CompletableFuture<>();
                                        future.completeExceptionally(e);
                                        return future;
                                    }
                                })
                        .toArray(this::newArray);

        CompletableFuture.allOf(futures).join();
    }

    @Override
    public void startLoad(ProducerWorkAssignment producerWorkAssignment) throws IOException {
        // Reduce the publish rate across all the brokers
        producerWorkAssignment.publishRate /= numberOfUsedProducerWorkers;
        log.debug(
                "Setting worker assigned publish rate to {} msgs/sec", producerWorkAssignment.publishRate);
        sendPost(producerWorkers, "/start-load", writer.writeValueAsBytes(producerWorkAssignment));
    }

    @Override
    public void probeProducers() throws IOException {
        sendPost(producerWorkers, "/probe-producers", new byte[0]);
    }

    @Override
    public void adjustPublishRate(double publishRate) throws IOException {
        // Reduce the publish rate across all the brokers
        publishRate /= numberOfUsedProducerWorkers;
        log.debug("Adjusting worker publish rate to {} msgs/sec", publishRate);
        sendPost(producerWorkers, "/adjust-publish-rate", writer.writeValueAsBytes(publishRate));
    }

    @Override
    public void stopAll() {
        sendPost(workers, "/stop-all", new byte[0]);
    }

    @Override
    public void pauseConsumers() throws IOException {
        sendPost(consumerWorkers, "/pause-consumers", new byte[0]);
    }

    @Override
    public void resumeConsumers() throws IOException {
        sendPost(consumerWorkers, "/resume-consumers", new byte[0]);
    }

    @Override
    public void createConsumers(ConsumerAssignment overallConsumerAssignment) {
        List<List<TopicSubscription>> subscriptionsPerConsumer =
                ListPartition.partitionList(
                        overallConsumerAssignment.topicsSubscriptions, consumerWorkers.size());
        Map<String, ConsumerAssignment> topicsPerWorkerMap = Maps.newHashMap();
        int i = 0;
        for (List<TopicSubscription> tsl : subscriptionsPerConsumer) {
            ConsumerAssignment individualAssignement = new ConsumerAssignment();
            individualAssignement.topicsSubscriptions = tsl;
            topicsPerWorkerMap.put(consumerWorkers.get(i++), individualAssignement);
        }

        CompletableFuture<Void>[] futures =
                topicsPerWorkerMap.keySet().stream()
                        .map(
                                consumer -> {
                                    try {
                                        return sendPost(
                                                consumer,
                                                "/create-consumers",
                                                writer.writeValueAsBytes(topicsPerWorkerMap.get(consumer)));
                                    } catch (Exception e) {
                                        CompletableFuture<Void> future = new CompletableFuture<>();
                                        future.completeExceptionally(e);
                                        return future;
                                    }
                                })
                        .toArray(this::newArray);

        CompletableFuture.allOf(futures).join();
    }

    @Override
    public PeriodStats getPeriodStats() {
        List<PeriodStats> individualStats = get(workers, "/period-stats", PeriodStats.class);
        PeriodStats stats = new PeriodStats();
        individualStats.forEach(
                is -> {
                    stats.messagesSent += is.messagesSent;
                    stats.messageSendErrors += is.messageSendErrors;
                    stats.bytesSent += is.bytesSent;
                    stats.messagesReceived += is.messagesReceived;
                    stats.bytesReceived += is.bytesReceived;
                    stats.totalMessagesSent += is.totalMessagesSent;
                    stats.totalMessageSendErrors += is.totalMessageSendErrors;
                    stats.totalMessagesReceived += is.totalMessagesReceived;

                    try {
                        stats.publishLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.publishLatencyBytes), TimeUnit.SECONDS.toMicros(30)));

                        stats.publishDelayLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.publishDelayLatencyBytes), TimeUnit.SECONDS.toMicros(30)));

                        stats.endToEndLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.endToEndLatencyBytes), TimeUnit.HOURS.toMicros(12)));
                    } catch (ArrayIndexOutOfBoundsException | DataFormatException e) {
                        throw new RuntimeException(e);
                    }
                });

        return stats;
    }

    @Override
    public CumulativeLatencies getCumulativeLatencies() {
        List<CumulativeLatencies> individualStats =
                get(workers, "/cumulative-latencies", CumulativeLatencies.class);

        CumulativeLatencies stats = new CumulativeLatencies();
        individualStats.forEach(
                is -> {
                    try {
                        stats.publishLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.publishLatencyBytes), TimeUnit.SECONDS.toMicros(30)));
                    } catch (Exception e) {
                        log.error(
                                "Failed to decode publish latency: {}",
                                ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.publishLatencyBytes)));
                        throw new RuntimeException(e);
                    }

                    try {
                        stats.publishDelayLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.publishDelayLatencyBytes), TimeUnit.SECONDS.toMicros(30)));
                    } catch (Exception e) {
                        log.error(
                                "Failed to decode publish delay latency: {}",
                                ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.publishDelayLatencyBytes)));
                        throw new RuntimeException(e);
                    }

                    try {
                        stats.endToEndLatency.add(
                                Histogram.decodeFromCompressedByteBuffer(
                                        ByteBuffer.wrap(is.endToEndLatencyBytes), TimeUnit.HOURS.toMicros(12)));
                    } catch (Exception e) {
                        log.error(
                                "Failed to decode end-to-end latency: {}",
                                ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.endToEndLatencyBytes)));
                        throw new RuntimeException(e);
                    }
                });

        return stats;
    }

    @Override
    public CountersStats getCountersStats() throws IOException {
        List<CountersStats> individualStats = get(workers, "/counters-stats", CountersStats.class);

        CountersStats stats = new CountersStats();
        individualStats.forEach(
                is -> {
                    stats.messagesSent += is.messagesSent;
                    stats.messagesReceived += is.messagesReceived;
                    stats.messageSendErrors += is.messageSendErrors;
                });

        return stats;
    }

    @Override
    public void resetStats() throws IOException {
        sendPost(workers, "/reset-stats", new byte[0]);
    }

    /**
     * Send a request to multiple hosts and wait for all responses.
     *
     * @param hosts
     * @param path
     * @param body
     */
    private void sendPost(List<String> hosts, String path, byte[] body) {
        CompletableFuture<Void>[] futures =
                hosts.stream().map(w -> sendPost(w, path, body)).toArray(this::newArray);
        CompletableFuture.allOf(futures).join();
    }

    private CompletableFuture<Void> sendPost(String host, String path, byte[] body) {
        return httpClient
                .preparePost(host + path)
                .setBody(body)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        x -> {
                            if (x.getStatusCode() != 200) {
                                log.error(
                                        "Failed to do HTTP post request to {}{} -- code: {}",
                                        host,
                                        path,
                                        x.getStatusCode());
                            }
                            Preconditions.checkArgument(x.getStatusCode() == 200);
                            return (Void) null;
                        });
    }

    private <T> List<T> get(List<String> hosts, String path, Class<T> clazz) {
        CompletableFuture<T>[] futures =
                hosts.stream().map(w -> get(w, path, clazz)).toArray(this::newArray);

        CompletableFuture<List<T>> resultFuture = new CompletableFuture<>();
        CompletableFuture.allOf(futures)
                .thenRun(
                        () -> {
                            resultFuture.complete(
                                    Stream.of(futures).map(CompletableFuture::join).collect(toList()));
                        })
                .exceptionally(
                        ex -> {
                            resultFuture.completeExceptionally(ex);
                            return null;
                        });

        return resultFuture.join();
    }

    private <T> CompletableFuture<T> get(String host, String path, Class<T> clazz) {
        return httpClient
                .prepareGet(host + path)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        response -> {
                            try {
                                if (response.getStatusCode() != 200) {
                                    log.error(
                                            "Failed to do HTTP get request to {}{} -- code: {}",
                                            host,
                                            path,
                                            response.getStatusCode());
                                }
                                Preconditions.checkArgument(response.getStatusCode() == 200);
                                return mapper.readValue(response.getResponseBody(), clazz);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    private <T> CompletableFuture<T> post(String host, String path, byte[] body, Class<T> clazz) {
        return httpClient
                .preparePost(host + path)
                .setBody(body)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        response -> {
                            try {
                                if (response.getStatusCode() != 200) {
                                    log.error(
                                            "Failed to do HTTP post request to {}{} -- code: {}",
                                            host,
                                            path,
                                            response.getStatusCode());
                                }
                                Preconditions.checkArgument(response.getStatusCode() == 200);
                                return mapper.readValue(response.getResponseBody(), clazz);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void close() throws Exception {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        httpClient.close();
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T>[] newArray(int size) {
        return new CompletableFuture[size];
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    private static final Logger log = LoggerFactory.getLogger(DistributedWorkersEnsemble.class);
}
