/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;

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
     * @param statsLogger stats logger to collect stats from benchmark driver
     * @throws IOException
     */
    void initialize(File configurationFile, StatsLogger statsLogger) throws IOException;

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
    CompletableFuture<BenchmarkConsumer> createConsumer(
        String topic,
        String subscriptionName,
        ConsumerCallback consumerCallback);
}
