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
package io.openmessaging.benchmark.driver.pravega;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.pravega.config.PravegaConfig;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.StreamConfiguration;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PravegaBenchmarkDriver implements BenchmarkDriver {
    private static final Logger log = LoggerFactory.getLogger(PravegaBenchmarkDriver.class);

    private static final ObjectWriter objectWriter =
            new ObjectMapper().writerWithDefaultPrettyPrinter();

    private PravegaConfig config;
    private ClientConfig clientConfig;
    private String scopeName;
    private StreamManager streamManager;
    private ReaderGroupManager readerGroupManager;
    private EventStreamClientFactory clientFactory;
    private final List<String> createdTopics = new ArrayList<>();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = readConfig(configurationFile);
        log.info("Pravega driver configuration: {}", objectWriter.writeValueAsString(config));

        clientConfig =
                ClientConfig.builder().controllerURI(URI.create(config.client.controllerURI)).build();
        scopeName = config.client.scopeName;
        streamManager = StreamManager.create(clientConfig);
        readerGroupManager = ReaderGroupManager.withScope(scopeName, clientConfig);
        clientFactory = EventStreamClientFactory.withScope(scopeName, clientConfig);
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static PravegaConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, PravegaConfig.class);
    }

    /**
     * Clean Pravega stream name to only allow alpha-numeric and "-".
     *
     * @param name
     * @return the cleaned name
     */
    private String cleanName(String name) {
        return name.replaceAll("[^A-Za-z0-9-]", "");
    }

    @Override
    public String getTopicNamePrefix() {
        return "openmessaging-benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        topic = cleanName(topic);
        log.info("createTopic: topic={}, partitions={}", topic, partitions);
        synchronized (createdTopics) {
            createdTopics.add(topic);
        }
        if (config.createScope) {
            streamManager.createScope(scopeName);
        }
        ScalingPolicy scalingPolicy;
        // Create a fixed or auto-scaling Stream based on user configuration.
        if (config.enableStreamAutoScaling
                && (config.eventsPerSecond != PravegaConfig.DEFAULT_STREAM_AUTOSCALING_VALUE
                        || config.kbytesPerSecond != PravegaConfig.DEFAULT_STREAM_AUTOSCALING_VALUE)) {
            scalingPolicy =
                    config.eventsPerSecond != PravegaConfig.DEFAULT_STREAM_AUTOSCALING_VALUE
                            ? ScalingPolicy.byEventRate(config.eventsPerSecond, 2, partitions)
                            : ScalingPolicy.byDataRate(config.kbytesPerSecond, 2, partitions);
        } else {
            scalingPolicy = ScalingPolicy.fixed(partitions);
        }
        streamManager.createStream(
                scopeName, topic, StreamConfiguration.builder().scalingPolicy(scalingPolicy).build());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        topic = cleanName(topic);
        BenchmarkProducer producer = null;
        if (config.enableTransaction) {
            producer =
                    new PravegaBenchmarkTransactionProducer(
                            topic,
                            clientFactory,
                            config.includeTimestampInEvent,
                            config.writer.enableConnectionPooling,
                            config.eventsPerTransaction);
        } else {
            producer =
                    new PravegaBenchmarkProducer(
                            topic,
                            clientFactory,
                            config.includeTimestampInEvent,
                            config.writer.enableConnectionPooling);
        }
        return CompletableFuture.completedFuture(producer);
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        topic = cleanName(topic);
        subscriptionName = cleanName(subscriptionName);
        BenchmarkConsumer consumer =
                new PravegaBenchmarkConsumer(
                        topic,
                        scopeName,
                        subscriptionName,
                        consumerCallback,
                        clientFactory,
                        readerGroupManager,
                        config.includeTimestampInEvent);
        return CompletableFuture.completedFuture(consumer);
    }

    private void deleteTopics() {
        synchronized (createdTopics) {
            for (String topic : createdTopics) {
                log.info("deleteTopics: topic={}", topic);
                streamManager.sealStream(scopeName, topic);
                if (config.deleteStreams) {
                    streamManager.deleteStream(scopeName, topic);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        log.info("close: clientConfig={}", clientConfig);
        if (clientFactory != null) {
            clientFactory.close();
        }
        if (readerGroupManager != null) {
            readerGroupManager.close();
        }
        if (streamManager != null) {
            deleteTopics();
            streamManager.close();
        }
    }
}
