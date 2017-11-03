package io.openmessaging.benchmark.driver.pulsar;

import java.util.concurrent.CompletableFuture;

import org.apache.pulsar.client.api.Producer;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class PulsarBenchmarkProducer implements BenchmarkProducer {

    private final Producer producer;

    public PulsarBenchmarkProducer(Producer producer) {
        this.producer = producer;
    }

    @Override
    public void close() throws Exception {
        producer.close();
    }

    @Override
    public CompletableFuture<Void> sendAsync(byte[] message) {
        return producer.sendAsync(message).thenApply(msgId -> null);
    }

}
