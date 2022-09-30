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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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
import java.util.List;
import java.util.Map;
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
        int numberOfProducerWorkers = getNumberOfProducerWorkers(workers, extraConsumerWorkers);
        List<List<Worker>> partitions =
                Lists.partition(Lists.reverse(workers), workers.size() - numberOfProducerWorkers);
        this.producerWorkers = partitions.get(1);
        this.consumerWorkers = partitions.get(0);

        log.info(
                "Workers list - producers: [{}]",
                producerWorkers.stream().map(Worker::id).collect(joining(",")));
        log.info(
                "Workers list - consumers: {}",
                consumerWorkers.stream().map(Worker::id).collect(joining(",")));

        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /*
     * For driver-jms extra consumers are required. If there is an odd number of workers then allocate the extra
     * to consumption.
     */
    @VisibleForTesting
    static int getNumberOfProducerWorkers(List<Worker> workers, boolean extraConsumerWorkers) {
        return extraConsumerWorkers ? (workers.size() + 2) / 3 : workers.size() / 2;
    }

    @Override
    public void initializeDriver(File configurationFile) throws IOException {
        workers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.initializeDriver(configurationFile);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
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
        List<List<String>> topicsPerProducer =
                ListPartition.partitionList(topics, producerWorkers.size());
        Map<Worker, List<String>> topicsPerProducerMap = Maps.newHashMap();
        int i = 0;
        for (List<String> assignedTopics : topicsPerProducer) {
            topicsPerProducerMap.put(producerWorkers.get(i++), assignedTopics);
        }

        // Number of actually used workers might be less than available workers
        numberOfUsedProducerWorkers =
                (int) topicsPerProducerMap.values().stream().filter(t -> !t.isEmpty()).count();
        log.debug(
                "Producing worker count: {} of {}", numberOfUsedProducerWorkers, producerWorkers.size());
        topicsPerProducerMap.entrySet().parallelStream()
                .forEach(
                        e -> {
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
        double newRate = producerWorkAssignment.publishRate / numberOfUsedProducerWorkers;
        log.debug("Setting worker assigned publish rate to {} msgs/sec", newRate);
        // Reduce the publish rate across all the brokers
        producerWorkers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.startLoad(producerWorkAssignment.withPublishRate(newRate));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void probeProducers() throws IOException {
        producerWorkers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.probeProducers();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void adjustPublishRate(double publishRate) throws IOException {
        double newRate = publishRate / numberOfUsedProducerWorkers;
        log.debug("Adjusting producer publish rate to {} msgs/sec", newRate);
        producerWorkers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.adjustPublishRate(newRate);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
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
        consumerWorkers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.pauseConsumers();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void resumeConsumers() throws IOException {
        consumerWorkers.parallelStream()
                .forEach(
                        w -> {
                            try {
                                w.resumeConsumers();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void createConsumers(ConsumerAssignment overallConsumerAssignment) {
        List<List<TopicSubscription>> subscriptionsPerConsumer =
                ListPartition.partitionList(
                        overallConsumerAssignment.topicsSubscriptions, consumerWorkers.size());
        Map<Worker, ConsumerAssignment> topicsPerWorkerMap = Maps.newHashMap();
        int i = 0;
        for (List<TopicSubscription> tsl : subscriptionsPerConsumer) {
            ConsumerAssignment individualAssignment = new ConsumerAssignment();
            individualAssignment.topicsSubscriptions = tsl;
            topicsPerWorkerMap.put(consumerWorkers.get(i++), individualAssignment);
        }
        topicsPerWorkerMap.entrySet().parallelStream()
                .forEach(
                        e -> {
                            try {
                                e.getKey().createConsumers(e.getValue());
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
    }

    @Override
    public PeriodStats getPeriodStats() {
        return workers.parallelStream()
                .map(
                        w -> {
                            try {
                                return w.getPeriodStats();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .reduce(new PeriodStats(), PeriodStats::plus);
    }

    @Override
    public CumulativeLatencies getCumulativeLatencies() {
        return workers.parallelStream()
                .map(
                        w -> {
                            try {
                                return w.getCumulativeLatencies();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .reduce(new CumulativeLatencies(), CumulativeLatencies::plus);
    }

    @Override
    public CountersStats getCountersStats() throws IOException {
        return workers.parallelStream()
                .map(
                        w -> {
                            try {
                                return w.getCountersStats();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .reduce(new CountersStats(), CountersStats::plus);
    }

    @Override
    public void resetStats() throws IOException {
        workers.parallelStream()
                .forEach(
                        w -> {
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
        for (Worker w : workers) {
            try {
                w.close();
            } catch (Exception ignored) {
                log.trace("Ignored error while closing worker {}", w, ignored);
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(DistributedWorkersEnsemble.class);
}
