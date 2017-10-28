package com.streamlio.messaging.benchmark.driver.pulsar;

import org.apache.pulsar.client.api.Consumer;

import com.streamlio.messaging.benchmark.driver.BenchmarkConsumer;

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
