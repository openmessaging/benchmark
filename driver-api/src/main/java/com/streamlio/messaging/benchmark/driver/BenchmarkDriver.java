package com.streamlio.messaging.benchmark.driver;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Base driver interface
 */
public interface BenchmarkDriver extends AutoCloseable {
    /**
     * Driver implementation can use this method to initialize the client libraries, with the provided configuration
     * file.
     * <p>
     * The format of the configuration file is specific to the driver implementation.
     * 
     * @param configurationFile
     * @throws IOException
     */
    void initialize(File configurationFile) throws IOException;

    /**
     * Get a driver specific prefix to be used in creating multiple topic names
     */
    String getTopicNamePrefix();

    /**
     * Create a new topic with a given number of partitions
     */
    CompletableFuture<Void> createTopic(String topic, int partitions);

    /**
     * Create a producer for a given topic
     */
    CompletableFuture<BenchmarkProducer> createProducer(String topic);

    /**
     * Create a benchmark consumer relative to one particular topic and subscription.
     * 
     * It is responsibility of the driver implementation to invoke the <code>consumerCallback</code> each time a message
     * is received.
     * 
     * @param topic
     * @param subscriptionName
     * @param consumerCallback
     * @return
     */
    CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
            ConsumerCallback consumerCallback);
}
