package com.streamlio.messaging.benchmark.driver.pulsar.config;

public class PulsarProducerConfig {
    public boolean batchingEnabled = true;
    public boolean blockIfQueueFull = true;
    public int batchingMaxPublishDelayMs = 1;
}
