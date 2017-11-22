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

import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.RateLimiter;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.utils.PaddingDecimalFormat;
import io.openmessaging.benchmark.utils.Timer;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class WorkloadGenerator implements ConsumerCallback, AutoCloseable {

    private final String driverName;
    private final BenchmarkDriver benchmarkDriver;
    private final Workload workload;

    private static final ExecutorService executor = Executors
            .newCachedThreadPool(new DefaultThreadFactory("messaging-benchmark"));

    private final LongAdder messagesSent = new LongAdder();
    private final LongAdder bytesSent = new LongAdder();

    private final LongAdder messagesReceived = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();

    private final LongAdder totalMessagesSent = new LongAdder();
    // TODO: If we have multiple consumers, then we need to have one for each topic
    private final LongAdder totalMessagesReceived = new LongAdder();

    private final LongAdder missedMessages = new LongAdder();
    private final Recorder e2eLatencyRecorder = new Recorder(5);
    private final Recorder e2eCumulativeLatencyRecorder = new Recorder(5);

    private boolean testCompleted = false;

    public WorkloadGenerator(String driverName, BenchmarkDriver benchmarkDriver, Workload workload) {
        this.driverName = driverName;
        this.benchmarkDriver = benchmarkDriver;
        this.workload = workload;
    }

    public TestResult run() throws InterruptedException {
        List<String> topics = createTopicsIdempotently(workload.producerRate <= 0);
        List<BenchmarkConsumer> consumers = createConsumers(topics, workload.partitionsPerTopic);
        List<BenchmarkProducer> producers = createProducers(topics);

        RateLimiter produceRateLimiter;
        AtomicBoolean runCompleted = new AtomicBoolean();
        if (workload.producerRate > 0) {
            produceRateLimiter = RateLimiter.create(workload.producerRate);
        } else {
            // Producer rate is 0 and we need to discover the sustainable rate
            produceRateLimiter = RateLimiter.create(10000);

            executor.execute(() -> {
                // Run background controller to adjust rate
                findMaximumSustainableRate(produceRateLimiter, runCompleted);
            });
        }
        RateLimiter consumeRateLimiter = RateLimiter.create(workload.consumeRate);

        log.info("----- Starting warm-up traffic ------");

        Recorder produceLatencyRecorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);
        Recorder produceCumulativeRecorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);

        long startTime = System.nanoTime();
        this.testCompleted = false;

        generateProducerLoad(producers, produceRateLimiter, produceLatencyRecorder, produceCumulativeRecorder);
        generateConsumerLoad(consumers, consumeRateLimiter);

        getTestResult(1, produceLatencyRecorder, produceCumulativeRecorder,
                e2eLatencyRecorder, e2eCumulativeLatencyRecorder, startTime);
        e2eLatencyRecorder.reset();
        e2eCumulativeLatencyRecorder.reset();
        runCompleted.set(true);

        log.info("----- Starting benchmark traffic ------");
        runCompleted.set(false);

        /**
         * Only invoke sustainable rate if we configure benchmark to have BOTH producers AND producer rate to be zero
         * In most cases, we want consumers to be on separate physical hosts. The logic below provides extra protection
         * from a producer being launched if you only want consumers on a host.
         */
        /*if (workload.producerRate == 0 && workload.producersPerTopic > 0) {
            // Continue with the feedback system to adjust the publish rate
            executor.execute(() -> {
                // Run background controller to adjust rate
                findMaximumSustainableRate(produceRateLimiter, runCompleted);
            });
        }*/

        produceLatencyRecorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);
        produceCumulativeRecorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);

        startTime = System.nanoTime();
        this.testCompleted = false;

        generateProducerLoad(producers, produceRateLimiter, produceLatencyRecorder, produceCumulativeRecorder);
        generateConsumerLoad(consumers, consumeRateLimiter);

        TestResult result = getTestResult(workload.testDurationMinutes,
                produceLatencyRecorder, produceCumulativeRecorder,
                e2eLatencyRecorder, e2eCumulativeLatencyRecorder, startTime);

        executor.awaitTermination(10, TimeUnit.SECONDS);
        runCompleted.set(true);

        return result;
    }

    /**
     * Adjust the publish rate to a level that is sustainable, meaning that we can consume all the messages that are
     * being produced
     */
    private void findMaximumSustainableRate(RateLimiter rateLimiter, AtomicBoolean testCompleted) {
        double maxRate = Double.MAX_VALUE; // Discovered max sustainable rate
        double currentRate = rateLimiter.getRate(); // Start with a reasonable number
        double minRate = 0.1;

        long localTotalMessagesSentCounter = totalMessagesSent.sum();
        long localTotalMessagesReceivedCounter = totalMessagesReceived.sum();

        int controlPeriodMillis = 3000;

        int successfulPeriods = 0;

        while (!testCompleted.get()) {
            // Check every few seconds and adjust the rate
            try {
                Thread.sleep(controlPeriodMillis);
            } catch (InterruptedException e) {
                return;
            }

            // Consider multiple copies when using mutlple subscriptions
            long totalMessagesSent = this.totalMessagesSent.sum();
            long totalMessagesReceived = this.totalMessagesReceived.sum();
            long messagesPublishedInPeriod = totalMessagesSent - localTotalMessagesSentCounter;
            long messagesReceivedInPeriod = totalMessagesReceived - localTotalMessagesReceivedCounter;
            double publishRateInLastPeriod = messagesPublishedInPeriod / (controlPeriodMillis / 1000.0);
            double receiveRateInLastPeriod = messagesReceivedInPeriod / (controlPeriodMillis / 1000.0);

            localTotalMessagesSentCounter = totalMessagesSent;
            localTotalMessagesReceivedCounter = totalMessagesReceived;

            log.debug("Current rate:  {} -- Publish rate {} -- Consume Rate: {} -- min-rate: {} -- max-rate: {}",
                    dec.format(currentRate), dec.format(publishRateInLastPeriod), dec.format(receiveRateInLastPeriod),
                    dec.format(minRate), dec.format(maxRate));

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
                rateLimiter.setRate(minRate / 10);
                while (true) {
                    long backlog = workload.subscriptionsPerTopic * this.totalMessagesSent.sum()
                            - this.totalMessagesReceived.sum();
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
                rateLimiter.setRate(currentRate);

                try {
                    // Wait some more time for the publish rate to catch up
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }

                localTotalMessagesSentCounter = this.totalMessagesSent.sum();
                localTotalMessagesReceivedCounter = this.totalMessagesReceived.sum();

            } else if (currentRate < maxRate) {
                minRate = currentRate;
                currentRate = Math.min(currentRate * 2, maxRate);
                log.debug("No bottleneck found, increasing the rate to {}", currentRate);
            } else if (++successfulPeriods > 3) {
                minRate = currentRate * 0.95;
                maxRate = currentRate * 1.05;
                successfulPeriods = 0;
            }

            rateLimiter.setRate(currentRate);
        }
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
    }

    private List<String> createTopicsIdempotently(boolean consumerOnly) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        Timer timer = new Timer();

        String topicPrefix = benchmarkDriver.getTopicNamePrefix();

        List<String> topics = new ArrayList<>();
        for (int i = 0; i < workload.topics; i++) {
            String topic = String.format("%s-%s", topicPrefix, i);
            topics.add(topic);
            futures.add(benchmarkDriver.createTopic(topic, workload.partitionsPerTopic, consumerOnly));
        }

        futures.forEach(CompletableFuture::join);

        log.info("Created {} topics in {} ms", topics.size(), timer.elapsedMillis());
        return topics;
    }

    private List<BenchmarkConsumer> createConsumers(List<String> topics, int partitionsPerTopic) {
        List<CompletableFuture<BenchmarkConsumer>> futures = new ArrayList<>();
        Timer timer = new Timer();

        for (int i = 0; i < workload.subscriptionsPerTopic; i++) {
            String subscriptionName = String.format("sub-%03d", i);

            for (String topic : topics) {
                futures.add(benchmarkDriver.createConsumer(topic, subscriptionName, this, partitionsPerTopic));
            }
        }

        List<BenchmarkConsumer> consumers = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        log.info("Created {} consumers in {} ms", consumers.size(), timer.elapsedMillis());
        return consumers;
    }

    private List<BenchmarkProducer> createProducers(List<String> topics) {
        List<CompletableFuture<BenchmarkProducer>> futures = new ArrayList<>();
        Timer timer = new Timer();

        for (int i = 0; i < workload.producersPerTopic; i++) {
            for (String topic : topics) {
                futures.add(benchmarkDriver.createProducer(topic));
            }
        }

        List<BenchmarkProducer> producers = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        log.info("Created {} producers in {} ms", producers.size(), timer.elapsedMillis());
        return producers;
    }

    // Results for consumer are recorded via the callback
    private void generateConsumerLoad(List<BenchmarkConsumer> consumers, RateLimiter rateLimiter) {
        executor.submit(() -> {
            try {
                // Consume messages on all topics/producers
                while (!testCompleted) {
                    consumers.forEach(consumer -> {
                        rateLimiter.acquire();
                        consumer.receiveAsync(this, testCompleted).exceptionally(ex -> {
                            log.warn("Write error on message", ex);
                            System.exit(-1);
                            return null;
                        });
                    });
                }
            } catch (Throwable t) {
                log.error("Got error", t);
            }
        });
    }

    /**
     * Generate a string that is sufficiently large so that we can build messages on uniform text for validation purposes.
     *
     * @return
     */
    private byte[] getComparisonStr(int sizeBytes) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        // Message number (long) + producer hashcode (int) + createTimestamp (long) + trim last char (char)
        int overhead = 8 + 4 + 8;
        int sizeMinusOverhead = sizeBytes - overhead;
        for (int i = 0; i < sizeMinusOverhead; i++) {
            stringBuilder.append(alphabet[i%(alphabet.length-1)]);
        }
        return stringBuilder.toString().getBytes();
    }

    private void generateProducerLoad(List<BenchmarkProducer> producers, RateLimiter rateLimiter,
                                            Recorder recorder, Recorder cumulativeRecorder) {
        if (producers.size() == 0) {
            log.info("Skipping producer load generation because no producers were requested");
            return;
        }
        byte[] comparisonStr = getComparisonStr(workload.messageSize);
        final byte[] payloadData = new byte[workload.messageSize];
        for (int i = 0; i < comparisonStr.length; i++) {
            payloadData[i+20] = comparisonStr[i];
        }

        executor.submit(() -> {
            try {
                // Send messages on all topics/producers
                AtomicLong messageNumber = new AtomicLong(totalMessagesSent.longValue() + 1);
                while (!testCompleted) {
                    for (BenchmarkProducer producer : producers) {
                        rateLimiter.acquire();

                        byte[] messageNumberBytes = Longs.toByteArray(messageNumber.longValue());
                        for (int i = 0; i < 8; i++) {
                            payloadData[i] = messageNumberBytes[i];
                        }
                        byte[] produceHashCodeBytes = ByteBuffer.allocate(4).putInt(producer.hashCode()).array();
                        for (int i = 0; i < 4; i++) {
                            payloadData[i+8] = produceHashCodeBytes[i];
                        }
                        byte[] produceTimestampBytes = Longs.toByteArray(System.nanoTime());
                        for (int i = 0; i < 8; i++) {
                            payloadData[i+12] = produceTimestampBytes[i];
                        }
                        final long sendTime = System.nanoTime();
                        producer.sendAsync(payloadData).thenRun(() -> {
                            messagesSent.increment();
                            totalMessagesSent.increment();
                            bytesSent.add(payloadData.length);

                            long latencyMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - sendTime);
                            recorder.recordValue(latencyMicros);
                            cumulativeRecorder.recordValue(latencyMicros);
                        }).exceptionally(ex -> {
                            log.warn("Write error on message", ex);
                            System.exit(-1);
                            return null;
                        });
                    }
                    messageNumber.incrementAndGet();
                }
            } catch (Throwable t) {
                log.error("Got error", t);
            }
        });
    }

    private TestResult getTestResult(long testDurationMins, Recorder produceLatencyRecorder,
                                     Recorder produceCumulativeLatencyRecorder, Recorder e2eLatencyRecorder,
                                     Recorder e2eCumulativeLatencyRecorder, long startTime) {
        // Print report stats
        long oldTime = System.nanoTime();

        long testEndTime = startTime + TimeUnit.MINUTES.toNanos(testDurationMins);

        TestResult result = new TestResult();
        result.workload = workload.name;
        result.driver = driverName;

        Histogram reportProduceLatencyHistogram = null;
        Histogram reportE2eLatencyHistogram = null;

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                break;
            }

            long now = System.nanoTime();
            double elapsed = (now - oldTime) / 1e9;

            double publishRate = messagesSent.sumThenReset() / elapsed;
            double publishThroughput = bytesSent.sumThenReset() / elapsed / 1024 / 1024;

            double completenessRate = 0;
            long missedMsgCount = missedMessages.longValue();
            if (messagesReceived.longValue() != 0) {
                completenessRate = (1 - (missedMessages.sumThenReset() / messagesReceived.longValue())) * 100;
            }

            double consumeRate = messagesReceived.sumThenReset() / elapsed;
            double consumeThroughput = bytesReceived.sumThenReset() / elapsed / 1024 / 1024;

            reportProduceLatencyHistogram = produceLatencyRecorder.getIntervalHistogram(reportProduceLatencyHistogram);
            reportE2eLatencyHistogram = e2eLatencyRecorder.getIntervalHistogram(reportE2eLatencyHistogram);

            long currentBacklog = workload.subscriptionsPerTopic * totalMessagesSent.sum()
                    - totalMessagesReceived.sum();

            log.info(
                    "Total produced/consumed {}/{} | Pub rate {} msg/s / {} Mb/s |  | Cons rate {} msg/s / {} Mb/s | Ordered completeness {} %: missed message count {} | Backlog: {} K | Pub Latency (ms) avg: {} - 50%: {} - 99%: {} - 99.9%: {} - Max: {} | E2E Latency (ms) avg: {} - 50%: {} - 99%: {} - 99.9%: {} - Max: {}",
                    totalMessagesSent, totalMessagesReceived,
                    rateFormat.format(publishRate), throughputFormat.format(publishThroughput),
                    rateFormat.format(consumeRate), throughputFormat.format(consumeThroughput),
                    completenessRate, missedMsgCount,
                    dec.format(currentBacklog / 1000.0), //
                    dec.format(reportProduceLatencyHistogram.getMean() / 1000.0),
                    dec.format(reportProduceLatencyHistogram.getValueAtPercentile(50) / 1000.0),
                    dec.format(reportProduceLatencyHistogram.getValueAtPercentile(99) / 1000.0),
                    dec.format(reportProduceLatencyHistogram.getValueAtPercentile(99.9) / 1000.0),
                    throughputFormat.format(reportProduceLatencyHistogram.getMaxValue() / 1000.0),
                    dec.format(reportE2eLatencyHistogram.getMean() / 1000.0),
                    dec.format(reportE2eLatencyHistogram.getValueAtPercentile(50) / 1000.0),
                    dec.format(reportE2eLatencyHistogram.getValueAtPercentile(99) / 1000.0),
                    dec.format(reportE2eLatencyHistogram.getValueAtPercentile(99.9) / 1000.0),
                    throughputFormat.format(reportE2eLatencyHistogram.getMaxValue() / 1000.0));

            result.publishRate.add(publishRate);
            result.consumeRate.add(consumeRate);
            result.backlog.add(currentBacklog);
            result.publishLatencyAvg.add(reportProduceLatencyHistogram.getMean() / 1000.0);
            result.publishLatency50pct.add(reportProduceLatencyHistogram.getValueAtPercentile(50) / 1000.0);
            result.publishLatency75pct.add(reportProduceLatencyHistogram.getValueAtPercentile(75) / 1000.0);
            result.publishLatency95pct.add(reportProduceLatencyHistogram.getValueAtPercentile(95) / 1000.0);
            result.publishLatency99pct.add(reportProduceLatencyHistogram.getValueAtPercentile(99) / 1000.0);
            result.publishLatency999pct.add(reportProduceLatencyHistogram.getValueAtPercentile(99.9) / 1000.0);
            result.publishLatency9999pct.add(reportProduceLatencyHistogram.getValueAtPercentile(99.99) / 1000.0);
            result.publishLatencyMax.add(reportProduceLatencyHistogram.getMaxValue() / 1000.0);
            result.e2eLatencyAvg.add(reportE2eLatencyHistogram.getMean() / 1000.0);
            result.e2eLatency50pct.add(reportE2eLatencyHistogram.getValueAtPercentile(50) / 1000.0);
            result.e2eLatency75pct.add(reportE2eLatencyHistogram.getValueAtPercentile(75) / 1000.0);
            result.e2eLatency95pct.add(reportE2eLatencyHistogram.getValueAtPercentile(95) / 1000.0);
            result.e2eLatency99pct.add(reportE2eLatencyHistogram.getValueAtPercentile(99) / 1000.0);
            result.e2eLatency999pct.add(reportE2eLatencyHistogram.getValueAtPercentile(99.9) / 1000.0);
            result.e2eLatency9999pct.add(reportE2eLatencyHistogram.getValueAtPercentile(99.99) / 1000.0);
            result.e2eLatencyMax.add(reportE2eLatencyHistogram.getMaxValue() / 1000.0);

            reportProduceLatencyHistogram.reset();

            if (now >= testEndTime) {
                testCompleted = true;
                reportProduceLatencyHistogram = produceCumulativeLatencyRecorder.getIntervalHistogram();
                reportE2eLatencyHistogram = e2eCumulativeLatencyRecorder.getIntervalHistogram();

                log.info(
                        "----- Aggregated Pub Latency (ms) avg: {} - 50%: {} - 95%: {} - 99%: {} - 99.9%: {} - 99.99%: {} - Max: {}" +
                                " ----- Aggregated E2e Latency (ms) avg: {} - 50%: {} - 95%: {} - 99%: {} - 99.9%: {} - 99.99%: {} - Max: {}",
                        dec.format(reportProduceLatencyHistogram.getMean() / 1000.0),
                        dec.format(reportProduceLatencyHistogram.getValueAtPercentile(50) / 1000.0),
                        dec.format(reportProduceLatencyHistogram.getValueAtPercentile(95) / 1000.0),
                        dec.format(reportProduceLatencyHistogram.getValueAtPercentile(99) / 1000.0),
                        dec.format(reportProduceLatencyHistogram.getValueAtPercentile(99.9) / 1000.0),
                        dec.format(reportProduceLatencyHistogram.getValueAtPercentile(99.99) / 1000.0),
                        throughputFormat.format(reportProduceLatencyHistogram.getMaxValue() / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getMean() / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getValueAtPercentile(50) / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getValueAtPercentile(95) / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getValueAtPercentile(99) / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getValueAtPercentile(99.9) / 1000.0),
                        dec.format(reportE2eLatencyHistogram.getValueAtPercentile(99.99) / 1000.0),
                        throughputFormat.format(reportE2eLatencyHistogram.getMaxValue() / 1000.0));

                result.aggregatedPublishLatencyAvg = reportProduceLatencyHistogram.getMean() / 1000.0;
                result.aggregatedPublishLatency50pct = reportProduceLatencyHistogram.getValueAtPercentile(50) / 1000.0;
                result.aggregatedPublishLatency75pct = reportProduceLatencyHistogram.getValueAtPercentile(75) / 1000.0;
                result.aggregatedPublishLatency95pct = reportProduceLatencyHistogram.getValueAtPercentile(95) / 1000.0;
                result.aggregatedPublishLatency99pct = reportProduceLatencyHistogram.getValueAtPercentile(99) / 1000.0;
                result.aggregatedPublishLatency999pct = reportProduceLatencyHistogram.getValueAtPercentile(99.9) / 1000.0;
                result.aggregatedPublishLatency9999pct = reportProduceLatencyHistogram.getValueAtPercentile(99.99) / 1000.0;
                result.aggregatedPublishLatencyMax = reportProduceLatencyHistogram.getMaxValue() / 1000.0;
                result.aggregatedE2eLatencyAvg = reportE2eLatencyHistogram.getMean() / 1000.0;
                result.aggregatedE2eLatency50pct = reportE2eLatencyHistogram.getValueAtPercentile(50) / 1000.0;
                result.aggregatedE2eLatency75pct = reportE2eLatencyHistogram.getValueAtPercentile(75) / 1000.0;
                result.aggregatedE2eLatency95pct = reportE2eLatencyHistogram.getValueAtPercentile(95) / 1000.0;
                result.aggregatedE2eLatency99pct = reportE2eLatencyHistogram.getValueAtPercentile(99) / 1000.0;
                result.aggregatedE2eLatency999pct = reportE2eLatencyHistogram.getValueAtPercentile(99.9) / 1000.0;
                result.aggregatedE2eLatency9999pct = reportE2eLatencyHistogram.getValueAtPercentile(99.99) / 1000.0;
                result.aggregatedE2eLatencyMax = reportE2eLatencyHistogram.getMaxValue() / 1000.0;

                reportProduceLatencyHistogram.percentiles(100).forEach(value -> {
                    result.aggregatedPublishLatencyQuantiles.put(value.getPercentile(),
                            value.getValueIteratedTo() / 1000.0);
                });
                reportE2eLatencyHistogram.percentiles(100).forEach(value -> {
                    result.aggregatedE2eLatencyQuantiles.put(value.getPercentile(),
                            value.getValueIteratedTo() / 1000.0);
                });
                break;
            }

            oldTime = now;
        }
        return result;
    }

    @Override
    public void messageReceived(byte[] data, long messageReceivedTimestamp) {
        messagesReceived.increment();
        totalMessagesReceived.increment();
        bytesReceived.add(data.length);

        Long messageId = Longs.fromByteArray(Arrays.copyOfRange(data, 0, 8));
        if (messageId != totalMessagesReceived.longValue() + missedMessages.longValue()) {
            long numMessagesMissed = messageId - totalMessagesReceived.longValue();
            missedMessages.add(numMessagesMissed);
        }
        Long messageCreatedTimestamp = Longs.fromByteArray(Arrays.copyOfRange(data, 12, 20));
        Long e2eLatencyNanos = messageReceivedTimestamp - messageCreatedTimestamp;
        Long e2eLatencyMicros = TimeUnit.NANOSECONDS.toMicros(e2eLatencyNanos);
        e2eLatencyRecorder.recordValue(e2eLatencyMicros);
        e2eCumulativeLatencyRecorder.recordValue(e2eLatencyMicros);

        // TODO: Validate message str to ensure no data corruption
    }

    private static final DecimalFormat rateFormat = new PaddingDecimalFormat("0.0", 7);
    private static final DecimalFormat throughputFormat = new PaddingDecimalFormat("0.0", 4);
    private static final DecimalFormat dec = new PaddingDecimalFormat("0.0", 4);

    private static final Logger log = LoggerFactory.getLogger(WorkloadGenerator.class);
}
