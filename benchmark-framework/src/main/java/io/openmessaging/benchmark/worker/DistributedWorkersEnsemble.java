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
import static java.util.stream.Collectors.joining;
import com.beust.jcommander.internal.Maps;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedWorkersEnsemble implements Worker {
    private final Thread shutdownHook = new Thread(this::stopAll);
    private final List<Worker> workers;
    private final List<Worker> producerWorkers;
    private final List<Worker> consumerWorkers;
    private final Worker leader;

    private int numberOfUsedProducerWorkers;

    public DistributedWorkersEnsemble(List<Worker> workers, boolean extraConsumerWorkers) {
        Preconditions.checkArgument(workers.size() > 1);
        this.workers = unmodifiableList(workers);
        leader = workers.get(0);

        // For driver-jms extra consumers are required.
        // If there is an odd number of workers then allocate the extra to consumption.
        int numberOfProducerWorkers = extraConsumerWorkers ? (workers.size() + 2) / 3 : workers.size() / 2;
        List<List<Worker>> partitions = Lists.partition(Lists.reverse(workers), workers.size() - numberOfProducerWorkers);
        this.producerWorkers = partitions.get(1);
        this.consumerWorkers = partitions.get(0);

        log.info("Workers list - producers: [{}]", producerWorkers.stream().map(Worker::id).collect(joining(",")));
        log.info("Workers list - consumers: {}", consumerWorkers.stream().map(Worker::id).collect(joining(",")));

        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void initializeDriver(File configurationFile) throws IOException {
        workers.parallelStream().forEach(w -> {
            try {
                w.initializeDriver(configurationFile);
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> createTopics(TopicsInfo topicsInfo) throws IOException {
        return leader.createTopics(topicsInfo);
    }

    @Override
    public void createProducers(List<String> topics) {
        List<List<String>> topicsPerProducer = ListPartition.partitionList(topics, producerWorkers.size());
        Map<Worker, List<String>> topicsPerProducerMap = Maps.newHashMap();
        int i = 0;
        for (List<String> assignedTopics : topicsPerProducer) {
            topicsPerProducerMap.put(producerWorkers.get(i++), assignedTopics);
        }

        // Number of actually used workers might be less than available workers
        numberOfUsedProducerWorkers = (int) topicsPerProducerMap.values().stream().filter(t -> !t.isEmpty()).count();
        log.debug("Producing worker count: {} of {}", numberOfUsedProducerWorkers, producerWorkers.size());
        topicsPerProducerMap.entrySet().stream().forEach(e -> {
            try {
                e.getKey().createProducers(e.getValue());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public void startLoad(ProducerWorkAssignment producerWorkAssignment) throws IOException {
        // Reduce the publish rate across all the brokers
        ProducerWorkAssignment newAssignment = new ProducerWorkAssignment();
        newAssignment.keyDistributorType = producerWorkAssignment.keyDistributorType;
        newAssignment.payloadData = producerWorkAssignment.payloadData;
        newAssignment.publishRate = producerWorkAssignment.publishRate / numberOfUsedProducerWorkers;
        log.debug("Setting worker assigned publish rate to {} msgs/sec", newAssignment.publishRate);
        // Reduce the publish rate across all the brokers
        producerWorkers.parallelStream().forEach(w -> {
            try {
                w.startLoad(newAssignment);
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    public void probeProducers() throws IOException {
        consumerWorkers.parallelStream().forEach(w -> {
            try {
                w.probeProducers();
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    public void adjustPublishRate(double publishRate) throws IOException {
        // Reduce the publish rate across all the brokers
        producerWorkers.parallelStream().forEach(w -> {
            try {
                w.adjustPublishRate(publishRate / numberOfUsedProducerWorkers);
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    public void stopAll() {
        workers.parallelStream().forEach(Worker::stopAll);
    }

    @Override
    public String id() {
        return "Ensemble[" + workers.stream().map(Worker::id).collect(joining(",")) + "]";
    }

    @Override
    public void pauseConsumers() throws IOException {
        consumerWorkers.parallelStream().forEach(w -> {
            try {
                w.pauseConsumers();
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    public void resumeConsumers() throws IOException {
        consumerWorkers.parallelStream().forEach(w -> {
            try {
                w.resumeConsumers();
            } catch (IOException e) {
                // Swallow
            }
        });
    }

    @Override
    public void createConsumers(ConsumerAssignment overallConsumerAssignment) {
        List<List<TopicSubscription>> subscriptionsPerConsumer = ListPartition.partitionList(
                overallConsumerAssignment.topicsSubscriptions,
                consumerWorkers.size());
        Map<Worker, ConsumerAssignment> topicsPerWorkerMap = Maps.newHashMap();
        int i = 0;
        for (List<TopicSubscription> tsl : subscriptionsPerConsumer) {
            ConsumerAssignment individualAssignement = new ConsumerAssignment();
            individualAssignement.topicsSubscriptions = tsl;
            topicsPerWorkerMap.put(consumerWorkers.get(i++), individualAssignement);
        }
        topicsPerWorkerMap.entrySet().parallelStream().forEach(e -> {
            try {
                e.getKey().createConsumers(e.getValue());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public PeriodStats getPeriodStats() {
        PeriodStats stats = new PeriodStats();
        workers.parallelStream().map(worker -> {
            try {
                return worker.getPeriodStats();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).forEach(is -> {
            stats.messagesSent += is.messagesSent;
            stats.messageSendErrors += is.messageSendErrors;
            stats.bytesSent += is.bytesSent;
            stats.messagesReceived += is.messagesReceived;
            stats.bytesReceived += is.bytesReceived;
            stats.totalMessagesSent += is.totalMessagesSent;
            stats.totalMessageSendErrors += is.totalMessageSendErrors;
            stats.totalMessagesReceived += is.totalMessagesReceived;

            try {
                stats.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.publishLatencyBytes), TimeUnit.SECONDS.toMicros(30)));

                stats.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.publishDelayLatencyBytes), TimeUnit.SECONDS.toMicros(30)));

                stats.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.endToEndLatencyBytes), TimeUnit.HOURS.toMicros(12)));
            } catch (ArrayIndexOutOfBoundsException | DataFormatException e) {
                throw new RuntimeException(e);
            }
        });
        return stats;
    }

    @Override
    public CumulativeLatencies getCumulativeLatencies() {
        CumulativeLatencies stats = new CumulativeLatencies();
        workers.parallelStream().map(worker -> {
            try {
                return worker.getCumulativeLatencies();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).forEach(is -> {
            try {
                stats.publishLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.publishLatencyBytes), TimeUnit.SECONDS.toMicros(30)));
            } catch (Exception e) {
                log.error("Failed to decode publish latency: {}",
                        ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.publishLatencyBytes)));
                throw new RuntimeException(e);
            }

            try {
                stats.publishDelayLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.publishDelayLatencyBytes), TimeUnit.SECONDS.toMicros(30)));
            } catch (Exception e) {
                log.error("Failed to decode publish delay latency: {}",
                        ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.publishDelayLatencyBytes)));
                throw new RuntimeException(e);
            }

            try {
                stats.endToEndLatency.add(Histogram.decodeFromCompressedByteBuffer(
                        ByteBuffer.wrap(is.endToEndLatencyBytes), TimeUnit.HOURS.toMicros(12)));
            } catch (Exception e) {
                log.error("Failed to decode end-to-end latency: {}",
                        ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(is.endToEndLatencyBytes)));
                throw new RuntimeException(e);
            }
        });
        return stats;

    }

    @Override
    public CountersStats getCountersStats() throws IOException {
        CountersStats stats = new CountersStats();
        workers.parallelStream().map(worker -> {
            try {
                return worker.getCountersStats();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).forEach(is -> {
            stats.messagesSent += is.messagesSent;
            stats.messagesReceived += is.messagesReceived;
            stats.messageSendErrors += is.messageSendErrors;
        });
        return stats;
    }

    @Override
    public void resetStats() throws IOException {
        workers.parallelStream().forEach(w -> {
            try {
                w.resetStats();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void close() throws Exception {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        for (Worker worker : workers) {
            worker.close();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(DistributedWorkersEnsemble.class);

}
