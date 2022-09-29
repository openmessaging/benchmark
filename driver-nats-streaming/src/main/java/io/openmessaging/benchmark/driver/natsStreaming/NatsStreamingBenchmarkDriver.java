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
package io.openmessaging.benchmark.driver.natsStreaming;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.BaseEncoding;
import io.nats.streaming.AckHandler;
import io.nats.streaming.Message;
import io.nats.streaming.MessageHandler;
import io.nats.streaming.NatsStreaming;
import io.nats.streaming.Options;
import io.nats.streaming.StreamingConnection;
import io.nats.streaming.Subscription;
import io.nats.streaming.SubscriptionOptions;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.LoggerFactory;

public class NatsStreamingBenchmarkDriver implements BenchmarkDriver {
    private final String defaultClusterId = "test-cluster";
    private String clusterId;
    private NatsStreamingClientConfig config;
    private StreamingConnection natsStreamingPublisher;
    private SubscriptionOptions.Builder subBuilder = new SubscriptionOptions.Builder();
    private Options.Builder optsBuilder = new Options.Builder();

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, NatsStreamingClientConfig.class);
        log.info("read config file," + config.toString());
        if (config.clusterId != null) {
            clusterId = config.clusterId;
        } else {
            clusterId = defaultClusterId;
        }
        if (config.natsHostUrl != null) {
            optsBuilder.natsUrl(config.natsHostUrl);
            log.info("natUrl {}", config.natsHostUrl);
        }
        optsBuilder.maxPubAcksInFlight(config.maxPubAcksInFlight);

        subBuilder.maxInFlight(config.maxInFlight);
    }

    @Override
    public String getTopicNamePrefix() {
        return "Nats-streaming-benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        log.info("nats streaming create a topic" + topic);
        log.info("ignore partitions");
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        if (natsStreamingPublisher == null) {
            String clientId = "ProducerInstance" + getRandomString();
            try {
                natsStreamingPublisher = NatsStreaming.connect(clusterId, clientId, optsBuilder.build());
            } catch (Exception e) {
                log.warn("nats streaming create producer exception", e);
                return null;
            }
        }

        return CompletableFuture.completedFuture(
                new NatsStreamingBenchmarkProducer(natsStreamingPublisher, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        Subscription sub;
        StreamingConnection streamingConnection;
        String clientId = "ConsumerInstance" + getRandomString();
        try {
            streamingConnection = NatsStreaming.connect(clusterId, clientId, optsBuilder.build());
            streamingConnection.subscribe(
                    topic,
                    subscriptionName,
                    new MessageHandler() {
                        @Override
                        public void onMessage(Message message) {
                            consumerCallback.messageReceived(message.getData(), message.getTimestamp());
                        }
                    },
                    subBuilder.build());
        } catch (Exception e) {
            log.warn("nats streaming create consumer exception", e);
            return null;
        }
        return CompletableFuture.completedFuture(
                new NatsStreamingBenchmarkConsumer(streamingConnection));
    }

    @Override
    public void close() throws Exception {
        if (natsStreamingPublisher != null) {
            natsStreamingPublisher.close();
            natsStreamingPublisher = null;
        }
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(NatsStreamingBenchmarkDriver.class);

    private static final Random random = new Random();

    private static String getRandomString() {
        byte[] buffer = new byte[5];
        random.nextBytes(buffer);
        return BaseEncoding.base64Url().omitPadding().encode(buffer);
    }

    public static void main(String[] args) throws Exception {
        try {
            Options opts = new Options.Builder().natsUrl("nats://0.0.0.0:4222").build();
            SubscriptionOptions.Builder builder = new SubscriptionOptions.Builder();
            StreamingConnection streamingConnection =
                    NatsStreaming.connect("mycluster", "benchmark-sub", opts);
            Subscription sub =
                    streamingConnection.subscribe(
                            "topicTest",
                            "subscription",
                            new MessageHandler() {
                                @Override
                                public void onMessage(Message message) {
                                    System.out.println(message.toString());
                                }
                            },
                            builder.build());
            StreamingConnection natsStreamingPublisher =
                    NatsStreaming.connect("mycluster", "benchmark-pub", opts);

            final String[] guid = new String[1];
            AckHandler acb =
                    new AckHandler() {
                        @Override
                        public void onAck(String s, Exception e) {
                            if ((e != null) || !guid[0].equals(s)) {
                                System.out.println("pub error");
                            } else {
                                System.out.println("pub success");
                            }
                        }
                    };

            guid[0] = natsStreamingPublisher.publish("topicTest", "HelloStreaming".getBytes(UTF_8), acb);

            if (guid[0].isEmpty()) {
                System.out.println("Expected non-empty guid to be returned.");
            }

            Thread.sleep(1000);
            sub.unsubscribe();
            natsStreamingPublisher.close();
            streamingConnection.close();
        } catch (Exception e) {
            log.warn("test exception", e);
        }
    }
}
