package io.openmessaging.benchmark.driver.pulsar.config;

public class PulsarClientConfig {
    public String serviceUrl;

    public String httpUrl;

    public int ioThreads = 8;

    public int connectionsPerBroker = 8;

    public String topicPrefix;
}
