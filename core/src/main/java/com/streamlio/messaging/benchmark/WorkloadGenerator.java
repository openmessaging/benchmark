package com.streamlio.messaging.benchmark;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.RateLimiter;
import com.streamlio.messaging.benchmark.driver.BenchmarkConsumer;
import com.streamlio.messaging.benchmark.driver.BenchmarkDriver;
import com.streamlio.messaging.benchmark.driver.BenchmarkProducer;
import com.streamlio.messaging.benchmark.driver.ConsumerCallback;
import com.streamlio.messaging.benchmark.utils.PaddingDecimalFormat;
import com.streamlio.messaging.benchmark.utils.Timer;

import io.netty.util.concurrent.DefaultThreadFactory;

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

    private boolean testCompleted = false;

    public WorkloadGenerator(String driverName, BenchmarkDriver benchmarkDriver, Workload workload) {
        this.driverName = driverName;
        this.benchmarkDriver = benchmarkDriver;
        this.workload = workload;
    }

    public TestResult run() {
        List<String> topics = createTopics();
        List<BenchmarkConsumer> consumers = createConsumers(topics);
        List<BenchmarkProducer> producers = createProducers(topics);

        log.info("----- Starting warm-up traffic ------");
        generateLoad(producers, TimeUnit.MINUTES.toSeconds(1));

        log.info("----- Starting benchmark traffic ------");
        TestResult result = generateLoad(producers, TimeUnit.MINUTES.toSeconds(workload.testDurationMinutes));

        return result;
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
    }

    private List<String> createTopics() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        Timer timer = new Timer();

        String topicPrefix = benchmarkDriver.getTopicNamePrefix();

        List<String> topics = new ArrayList<>();
        for (int i = 0; i < workload.topics; i++) {
            String topic = String.format("%s-%s-%04d", topicPrefix, getRandomString(), i);
            topics.add(topic);
            futures.add(benchmarkDriver.createTopic(topic, workload.partitionsPerTopic));
        }

        futures.forEach(CompletableFuture::join);

        log.info("Created {} topics in {} ms", topics.size(), timer.elapsedMillis());
        return topics;
    }

    private List<BenchmarkConsumer> createConsumers(List<String> topics) {
        List<CompletableFuture<BenchmarkConsumer>> futures = new ArrayList<>();
        Timer timer = new Timer();

        for (int i = 0; i < workload.subscriptionsPerTopic; i++) {
            String subscriptionName = String.format("sub-%03d", i);

            for (String topic : topics) {
                futures.add(benchmarkDriver.createConsumer(topic, subscriptionName, this));
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

    private TestResult generateLoad(List<BenchmarkProducer> producers, long testDurationsSeconds) {
        Recorder recorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);
        Recorder cumulativeRecorder = new Recorder(TimeUnit.SECONDS.toMicros(30), 5);

        long startTime = System.nanoTime();
        this.testCompleted = false;

        executor.submit(() -> {
            try {
                RateLimiter rateLimiter = RateLimiter.create(workload.producerRate);
                byte[] payloadData = new byte[workload.messageSize];

                // Send messages on all topics/producers
                while (!testCompleted) {
                    for (int i = 0; i < producers.size(); i++) {
                        BenchmarkProducer producer = producers.get(i);
                        rateLimiter.acquire();

                        final long sendTime = System.nanoTime();

                        producer.sendAsync(payloadData).thenRun(() -> {
                            messagesSent.increment();
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
                }
            } catch (Throwable t) {
                log.error("Got error", t);
            }
        });

        // Print report stats
        long oldTime = System.nanoTime();

        long testEndTime = startTime + TimeUnit.SECONDS.toNanos(testDurationsSeconds);

        TestResult result = new TestResult();
        result.workload = workload.name;
        result.driver = driverName;

        Histogram reportHistogram = null;

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

            double consumeRate = messagesReceived.sumThenReset() / elapsed;
            double consumeThroughput = bytesReceived.sumThenReset() / elapsed / 1024 / 1024;

            reportHistogram = recorder.getIntervalHistogram(reportHistogram);

            log.info(
                    "Pub rate {} msg/s / {} Mb/s | Cons rate {} msg/s / {} Mb/s | Pub Latency (ms) avg: {} - 50%: {} - 99%: {} - 99.9%: {} - Max: {}",
                    rateFormat.format(publishRate), throughputFormat.format(publishThroughput),
                    rateFormat.format(consumeRate), throughputFormat.format(consumeThroughput),
                    dec.format(reportHistogram.getMean() / 1000.0),
                    dec.format(reportHistogram.getValueAtPercentile(50) / 1000.0),
                    dec.format(reportHistogram.getValueAtPercentile(99) / 1000.0),
                    dec.format(reportHistogram.getValueAtPercentile(99.9) / 1000.0),
                    throughputFormat.format(reportHistogram.getMaxValue() / 1000.0));

            result.publishRate.add(publishRate);
            result.consumeRate.add(consumeRate);
            result.publishLatencyAvg.add(reportHistogram.getMean() / 1000.0);
            result.publishLatency50pct.add(reportHistogram.getValueAtPercentile(50) / 1000.0);
            result.publishLatency75pct.add(reportHistogram.getValueAtPercentile(75) / 1000.0);
            result.publishLatency95pct.add(reportHistogram.getValueAtPercentile(95) / 1000.0);
            result.publishLatency99pct.add(reportHistogram.getValueAtPercentile(99) / 1000.0);
            result.publishLatency999pct.add(reportHistogram.getValueAtPercentile(99.9) / 1000.0);
            result.publishLatency9999pct.add(reportHistogram.getValueAtPercentile(99.99) / 1000.0);
            result.publishLatencyMax.add(reportHistogram.getMaxValue() / 1000.0);

            reportHistogram.reset();

            if (now >= testEndTime) {
                testCompleted = true;
                reportHistogram = cumulativeRecorder.getIntervalHistogram();
                log.info(
                        "----- Aggregated Pub Latency (ms) avg: {} - 50%: {} - 95%: {} - 99%: {} - 99.9%: {} - 99.99%: {} - Max: {}",
                        dec.format(reportHistogram.getMean() / 1000.0),
                        dec.format(reportHistogram.getValueAtPercentile(50) / 1000.0),
                        dec.format(reportHistogram.getValueAtPercentile(95) / 1000.0),
                        dec.format(reportHistogram.getValueAtPercentile(99) / 1000.0),
                        dec.format(reportHistogram.getValueAtPercentile(99.9) / 1000.0),
                        dec.format(reportHistogram.getValueAtPercentile(99.99) / 1000.0),
                        throughputFormat.format(reportHistogram.getMaxValue() / 1000.0));

                result.aggregatedPublishLatencyAvg = reportHistogram.getMean() / 1000.0;
                result.aggregatedPublishLatency50pct = reportHistogram.getValueAtPercentile(50) / 1000.0;
                result.aggregatedPublishLatency75pct = reportHistogram.getValueAtPercentile(75) / 1000.0;
                result.aggregatedPublishLatency95pct = reportHistogram.getValueAtPercentile(95) / 1000.0;
                result.aggregatedPublishLatency99pct = reportHistogram.getValueAtPercentile(99) / 1000.0;
                result.aggregatedPublishLatency999pct = reportHistogram.getValueAtPercentile(99.9) / 1000.0;
                result.aggregatedPublishLatency9999pct = reportHistogram.getValueAtPercentile(99.99) / 1000.0;
                result.aggregatedPublishLatencyMax = reportHistogram.getMaxValue() / 1000.0;
                break;
            }

            oldTime = now;
        }

        return result;
    }

    @Override
    public void messageReceived(byte[] data) {
        messagesReceived.increment();
        bytesReceived.add(data.length);
    }

    private static final DecimalFormat rateFormat = new PaddingDecimalFormat("0.0", 7);
    private static final DecimalFormat throughputFormat = new PaddingDecimalFormat("0.0", 4);
    private static final DecimalFormat dec = new PaddingDecimalFormat("0.0", 4);

    private static final Random random = new Random();

    private static final String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    private static final Logger log = LoggerFactory.getLogger(WorkloadGenerator.class);
}
