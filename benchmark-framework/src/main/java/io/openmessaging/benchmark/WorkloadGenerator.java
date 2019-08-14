/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark;

import io.openmessaging.benchmark.utils.RandomGenerator;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.openmessaging.benchmark.utils.PaddingDecimalFormat;
import io.openmessaging.benchmark.utils.Timer;
import io.openmessaging.benchmark.utils.payload.FilePayloadReader;
import io.openmessaging.benchmark.utils.payload.PayloadReader;
import io.openmessaging.benchmark.worker.Worker;
import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CountersStats;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicSubscription;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;

public class WorkloadGenerator implements AutoCloseable {

    private final String driverName;
    private final Workload workload;
    private final Worker worker;

    private final ExecutorService executor = Executors
            .newCachedThreadPool(new DefaultThreadFactory("messaging-benchmark"));

    private volatile boolean runCompleted = false;
    private volatile boolean needToWaitForBacklogDraining = false;

    private volatile double targetPublishRate;

    public WorkloadGenerator(String driverName, Workload workload, Worker worker) {
        this.driverName = driverName;
        this.workload = workload;
        this.worker = worker;

        if (workload.consumerBacklogSizeGB > 0 && workload.producerRate == 0) {
            throw new IllegalArgumentException("Cannot probe producer sustainable rate when building backlog");
        }
    }

    public TestResult run() throws Exception {
        Timer timer = new Timer();
        List<String> topics = worker.createTopics(new TopicsInfo(workload.topics, workload.partitionsPerTopic));
        log.info("Created {} topics in {} ms", topics.size(), timer.elapsedMillis());

        createConsumers(topics);
        createProducers(topics);

        ensureTopicsAreReady();

        if (workload.producerRate > 0) {
            targetPublishRate = workload.producerRate;
        } else {
            // Producer rate is 0 and we need to discover the sustainable rate
            targetPublishRate = 10000;

            executor.execute(() -> {
                // Run background controller to adjust rate
                try {
                    findMaximumSustainableRate(targetPublishRate);
                } catch (IOException e) {
                    log.warn("Failure in finding max sustainable rate", e);
                }
            });
        }

        final PayloadReader payloadReader = new FilePayloadReader(workload.messageSize);

        ProducerWorkAssignment producerWorkAssignment = new ProducerWorkAssignment();
        producerWorkAssignment.keyDistributorType = workload.keyDistributor;
        producerWorkAssignment.publishRate = targetPublishRate;
        producerWorkAssignment.payloadData = payloadReader.load(workload.payloadFile);

        log.info("----- Starting warm-up traffic ------");

        worker.startLoad(producerWorkAssignment);

        printAndCollectStats(1, TimeUnit.MINUTES);

        if (workload.consumerBacklogSizeGB > 0) {
            executor.execute(() -> {
                try {
                    buildAndDrainBacklog(topics);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        worker.resetStats();
        log.info("----- Starting benchmark traffic ------");

        TestResult result = printAndCollectStats(workload.testDurationMinutes, TimeUnit.MINUTES);
        runCompleted = true;

        worker.stopAll();
        return result;
    }

    private void ensureTopicsAreReady() throws IOException {
        log.info("Waiting for consumers to be ready");
        // This is work around the fact that there's no way to have a consumer ready in Kafka without first publishing
        // some message on the topic, which will then trigger the partitions assignment to the consumers

        int expectedMessages = workload.topics * workload.subscriptionsPerTopic;

        // In this case we just publish 1 message and then wait for consumers to receive the data
        worker.probeProducers();

        while (true) {
            CountersStats stats = worker.getCountersStats();

            if (stats.messagesReceived < expectedMessages) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                break;
            }
        }

        log.info("All consumers are ready");
    }

    /**
     * Adjust the publish rate to a level that is sustainable, meaning that we can consume all the messages that are
     * being produced
     */
    private void findMaximumSustainableRate(double currentRate) throws IOException {
        double maxRate = Double.MAX_VALUE; // Discovered max sustainable rate
        double minRate = 0.1;

        CountersStats stats = worker.getCountersStats();

        long localTotalMessagesSentCounter = stats.messagesSent;
        long localTotalMessagesReceivedCounter = stats.messagesReceived;

        int controlPeriodMillis = 3000;
        long lastControlTimestamp = System.nanoTime();

        int successfulPeriods = 0;

        while (!runCompleted) {
            // Check every few seconds and adjust the rate
            try {
                Thread.sleep(controlPeriodMillis);
            } catch (InterruptedException e) {
                return;
            }

            // Consider multiple copies when using multiple subscriptions
            stats = worker.getCountersStats();
            long currentTime = System.nanoTime();
            long totalMessagesSent = stats.messagesSent;
            long totalMessagesReceived = stats.messagesReceived;
            long messagesPublishedInPeriod = totalMessagesSent - localTotalMessagesSentCounter;
            long messagesReceivedInPeriod = totalMessagesReceived - localTotalMessagesReceivedCounter;
            double publishRateInLastPeriod = messagesPublishedInPeriod / (double) (currentTime - lastControlTimestamp)
                    * TimeUnit.SECONDS.toNanos(1);
            double receiveRateInLastPeriod = messagesReceivedInPeriod / (double) (currentTime - lastControlTimestamp)
                    * TimeUnit.SECONDS.toNanos(1);

            if (log.isDebugEnabled()) {
                log.debug(
                        "total-send: {} -- total-received: {} -- int-sent: {} -- int-received: {} -- sent-rate: {} -- received-rate: {}",
                        totalMessagesSent, totalMessagesReceived, messagesPublishedInPeriod, messagesReceivedInPeriod,
                        publishRateInLastPeriod, receiveRateInLastPeriod);
            }

            localTotalMessagesSentCounter = totalMessagesSent;
            localTotalMessagesReceivedCounter = totalMessagesReceived;
            lastControlTimestamp = currentTime;

            if (log.isDebugEnabled()) {
                log.debug("Current rate: {} -- Publish rate {} -- Consume Rate: {} -- min-rate: {} -- max-rate: {}",
                        dec.format(currentRate), dec.format(publishRateInLastPeriod),
                        dec.format(receiveRateInLastPeriod), dec.format(minRate), dec.format(maxRate));
            }

            if (publishRateInLastPeriod < currentRate * 0.95) {
                // Producer is not able to publish as fast as requested
                maxRate = currentRate * 1.1;
                currentRate = minRate + (currentRate - minRate) / 2;

                log.debug("Publishers are not meeting requested rate. reducing to {}", currentRate);
            } else if (receiveRateInLastPeriod < publishRateInLastPeriod * 0.98) {
                // If the consumers are building backlog, we should slow down publish rate
                maxRate = currentRate;
                currentRate = minRate + (currentRate - minRate) / 2;
                log.debug("Consumers are not meeting requested rate. reducing to {}", currentRate);

                // Slows the publishes to let the consumer time to absorb the backlog
                worker.adjustPublishRate(minRate / 10);
                while (true) {
                    stats = worker.getCountersStats();
                    long backlog = workload.subscriptionsPerTopic * stats.messagesSent - stats.messagesReceived;
                    if (backlog < 1000) {
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                log.debug("Resuming load at reduced rate");
                worker.adjustPublishRate(currentRate);

                try {
                    // Wait some more time for the publish rate to catch up
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }

                stats = worker.getCountersStats();
                localTotalMessagesSentCounter = stats.messagesSent;
                localTotalMessagesReceivedCounter = stats.messagesReceived;

            } else if (currentRate < maxRate) {
                minRate = currentRate;
                currentRate = Math.min(currentRate * 2, maxRate);
                log.debug("No bottleneck found, increasing the rate to {}", currentRate);
            } else if (++successfulPeriods > 3) {
                minRate = currentRate * 0.95;
                maxRate = currentRate * 1.05;
                successfulPeriods = 0;
            }

            worker.adjustPublishRate(currentRate);
        }
    }

    @Override
    public void close() throws Exception {
        worker.stopAll();
        executor.shutdownNow();
    }

    private void createConsumers(List<String> topics) throws IOException {
        ConsumerAssignment consumerAssignment = new ConsumerAssignment();

        for(String topic: topics){
            for(int i = 0; i < workload.subscriptionsPerTopic; i++){
                String subscriptionName = String.format("sub-%03d-%s", i, RandomGenerator.getRandomString());
                for (int j = 0; j < workload.consumerPerSubscription; j++) {
                    consumerAssignment.topicsSubscriptions
                        .add(new TopicSubscription(topic, subscriptionName));
                }
            }
        }

        Collections.shuffle(consumerAssignment.topicsSubscriptions);

        Timer timer = new Timer();

        worker.createConsumers(consumerAssignment);
        log.info("Created {} consumers in {} ms", consumerAssignment.topicsSubscriptions.size(), timer.elapsedMillis());
    }

    private void createProducers(List<String> topics) throws IOException {
        List<String> fullListOfTopics = new ArrayList<>();

        // Add the topic multiple times, one for each producer
        for (int i = 0; i < workload.producersPerTopic; i++) {
            topics.forEach(fullListOfTopics::add);
        }

        Collections.shuffle(fullListOfTopics);

        Timer timer = new Timer();

        worker.createProducers(fullListOfTopics);
        log.info("Created {} producers in {} ms", fullListOfTopics.size(), timer.elapsedMillis());
    }

    private void buildAndDrainBacklog(List<String> topics) throws IOException {
        log.info("Stopping all consumers to build backlog");
        worker.pauseConsumers();

        this.needToWaitForBacklogDraining = true;

        long requestedBacklogSize = workload.consumerBacklogSizeGB * 1024 * 1024 * 1024;

        while (true) {
            CountersStats stats = worker.getCountersStats();
            long currentBacklogSize = (workload.subscriptionsPerTopic * stats.messagesSent - stats.messagesReceived)
                    * workload.messageSize;

            if (currentBacklogSize >= requestedBacklogSize) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        log.info("--- Start draining backlog ---");

        worker.resumeConsumers();

        final long minBacklog = 1000;

        while (true) {
            CountersStats stats = worker.getCountersStats();
            long currentBacklog = workload.subscriptionsPerTopic * stats.messagesSent - stats.messagesReceived;
            if (currentBacklog <= minBacklog) {
                log.info("--- Completed backlog draining ---");
                needToWaitForBacklogDraining = false;
                return;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private TestResult printAndCollectStats(long testDurations, TimeUnit unit) throws IOException {
        long startTime = System.nanoTime();

        // Print report stats
        long oldTime = System.nanoTime();

        long testEndTime = testDurations > 0 ? startTime + unit.toNanos(testDurations) : Long.MAX_VALUE;

        TestResult result = new TestResult();
        result.workload = workload.name;
        result.driver = driverName;

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                break;
            }

            PeriodStats stats = worker.getPeriodStats();

            long now = System.nanoTime();
            double elapsed = (now - oldTime) / 1e9;

            double publishRate = stats.messagesSent / elapsed;
            double publishThroughput = stats.bytesSent / elapsed / 1024 / 1024;

            double consumeRate = stats.messagesReceived / elapsed;
            double consumeThroughput = stats.bytesReceived / elapsed / 1024 / 1024;

            long currentBacklog = workload.subscriptionsPerTopic * stats.totalMessagesSent
                    - stats.totalMessagesReceived;

            log.info(
                    "Pub rate {} msg/s / {} Mb/s | Cons rate {} msg/s / {} Mb/s | Backlog: {} K | Pub Latency (ms) avg: {} - 50%: {} - 99%: {} - 99.9%: {} - Max: {}",
                    rateFormat.format(publishRate), throughputFormat.format(publishThroughput),
                    rateFormat.format(consumeRate), throughputFormat.format(consumeThroughput),
                    dec.format(currentBacklog / 1000.0), //
                    dec.format(microsToMillis(stats.publishLatency.getMean())),
                    dec.format(microsToMillis(stats.publishLatency.getValueAtPercentile(50))),
                    dec.format(microsToMillis(stats.publishLatency.getValueAtPercentile(99))),
                    dec.format(microsToMillis(stats.publishLatency.getValueAtPercentile(99.9))),
                    throughputFormat.format(microsToMillis(stats.publishLatency.getMaxValue())));

            result.publishRate.add(publishRate);
            result.consumeRate.add(consumeRate);
            result.backlog.add(currentBacklog);
            result.publishLatencyAvg.add(microsToMillis(stats.publishLatency.getMean()));
            result.publishLatency50pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(50)));
            result.publishLatency75pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(75)));
            result.publishLatency95pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(95)));
            result.publishLatency99pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(99)));
            result.publishLatency999pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(99.9)));
            result.publishLatency9999pct.add(microsToMillis(stats.publishLatency.getValueAtPercentile(99.99)));
            result.publishLatencyMax.add(microsToMillis(stats.publishLatency.getMaxValue()));

            result.endToEndLatencyAvg.add(microsToMillis(stats.endToEndLatency.getMean()));
            result.endToEndLatency50pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(50)));
            result.endToEndLatency75pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(75)));
            result.endToEndLatency95pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(95)));
            result.endToEndLatency99pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(99)));
            result.endToEndLatency999pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(99.9)));
            result.endToEndLatency9999pct.add(microsToMillis(stats.endToEndLatency.getValueAtPercentile(99.99)));
            result.endToEndLatencyMax.add(microsToMillis(stats.endToEndLatency.getMaxValue()));

            if (now >= testEndTime && !needToWaitForBacklogDraining) {
                CumulativeLatencies agg = worker.getCumulativeLatencies();
                log.info(
                        "----- Aggregated Pub Latency (ms) avg: {} - 50%: {} - 95%: {} - 99%: {} - 99.9%: {} - 99.99%: {} - Max: {}",
                        dec.format(agg.publishLatency.getMean() / 1000.0),
                        dec.format(agg.publishLatency.getValueAtPercentile(50) / 1000.0),
                        dec.format(agg.publishLatency.getValueAtPercentile(95) / 1000.0),
                        dec.format(agg.publishLatency.getValueAtPercentile(99) / 1000.0),
                        dec.format(agg.publishLatency.getValueAtPercentile(99.9) / 1000.0),
                        dec.format(agg.publishLatency.getValueAtPercentile(99.99) / 1000.0),
                        throughputFormat.format(agg.publishLatency.getMaxValue() / 1000.0));

                result.aggregatedPublishLatencyAvg = agg.publishLatency.getMean() / 1000.0;
                result.aggregatedPublishLatency50pct = agg.publishLatency.getValueAtPercentile(50) / 1000.0;
                result.aggregatedPublishLatency75pct = agg.publishLatency.getValueAtPercentile(75) / 1000.0;
                result.aggregatedPublishLatency95pct = agg.publishLatency.getValueAtPercentile(95) / 1000.0;
                result.aggregatedPublishLatency99pct = agg.publishLatency.getValueAtPercentile(99) / 1000.0;
                result.aggregatedPublishLatency999pct = agg.publishLatency.getValueAtPercentile(99.9) / 1000.0;
                result.aggregatedPublishLatency9999pct = agg.publishLatency.getValueAtPercentile(99.99) / 1000.0;
                result.aggregatedPublishLatencyMax = agg.publishLatency.getMaxValue() / 1000.0;

                result.aggregatedEndToEndLatencyAvg = agg.endToEndLatency.getMean()  / 1000.0;
                result.aggregatedEndToEndLatency50pct = agg.endToEndLatency.getValueAtPercentile(50)  / 1000.0;
                result.aggregatedEndToEndLatency75pct = agg.endToEndLatency.getValueAtPercentile(75)  / 1000.0;
                result.aggregatedEndToEndLatency95pct = agg.endToEndLatency.getValueAtPercentile(95)  / 1000.0;
                result.aggregatedEndToEndLatency99pct = agg.endToEndLatency.getValueAtPercentile(99)  / 1000.0;
                result.aggregatedEndToEndLatency999pct = agg.endToEndLatency.getValueAtPercentile(99.9)  / 1000.0;
                result.aggregatedEndToEndLatency9999pct = agg.endToEndLatency.getValueAtPercentile(99.99)  / 1000.0;
                result.aggregatedEndToEndLatencyMax = agg.endToEndLatency.getMaxValue()  / 1000.0;

                agg.publishLatency.percentiles(100).forEach(value -> {
                    result.aggregatedPublishLatencyQuantiles.put(value.getPercentile(),
                            value.getValueIteratedTo() / 1000.0);
                });

                agg.endToEndLatency.percentiles(100).forEach(value -> {
                    result.aggregatedEndToEndLatencyQuantiles.put(value.getPercentile(),
                            microsToMillis(value.getValueIteratedTo()));
                });

                break;
            }

            oldTime = now;
        }

        return result;
    }

    private static final DecimalFormat rateFormat = new PaddingDecimalFormat("0.0", 7);
    private static final DecimalFormat throughputFormat = new PaddingDecimalFormat("0.0", 4);
    private static final DecimalFormat dec = new PaddingDecimalFormat("0.0", 4);

    private static double microsToMillis(double timeInMicros) {
        return timeInMicros / 1000.0;
    }

    private static double microsToMillis(long timeInMicros) {
        return timeInMicros / 1000.0;
    }

    private static final Logger log = LoggerFactory.getLogger(WorkloadGenerator.class);
}
