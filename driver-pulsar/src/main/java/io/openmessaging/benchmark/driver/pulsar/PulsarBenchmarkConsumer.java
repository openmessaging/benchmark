package io.openmessaging.benchmark.driver.pulsar;

import org.apache.pulsar.client.api.Consumer;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;

public class PulsarBenchmarkConsumer implements BenchmarkConsumer {

    private final Consumer consumer;

    public PulsarBenchmarkConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void close() throws Exception {
        consumer.close();
    }

}
