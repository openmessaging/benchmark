package io.openmessaging.benchmark.driver.tdengine;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class TDengineBenchmarkDriver implements BenchmarkDriver {
    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {

    }

    @Override
    public String getTopicNamePrefix() {
        return null;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return null;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return null;
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
