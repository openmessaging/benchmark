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
package io.openmessaging.benchmark.driver.nsq;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.brainlag.nsq.NSQConsumer;
import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.lookup.DefaultNSQLookup;
import com.github.brainlag.nsq.lookup.NSQLookup;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NsqBenchmarkDriver implements BenchmarkDriver {
    private NsqConfig config;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, NsqConfig.class);
        log.info("read config file," + config.toString());
    }

    @Override
    public String getTopicNamePrefix() {
        return "Nsq-Benchmark";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        log.info("create a topic" + topic);
        log.info("ignore partitions");
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(final String topic) {
        NSQProducer nsqProducer = new NSQProducer();
        nsqProducer.addAddress(config.nsqdHost, 4150);
        nsqProducer.start();
        log.info("start a nsq producer");

        return CompletableFuture.completedFuture(new NsqBenchmarkProducer(nsqProducer, topic));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        // Channel can be treat as subscriptionName
        NSQLookup lookup = new DefaultNSQLookup();
        lookup.addLookupAddress(config.lookupHost, 4161);
        NSQConsumer nsqConsumer =
                new NSQConsumer(
                        lookup,
                        topic,
                        subscriptionName,
                        (message) -> {
                            // now mark the message as finished.
                            consumerCallback.messageReceived(
                                    message.getMessage(), message.getTimestamp().getTime());
                            message.finished();

                            // or you could requeue it, which indicates a failure and puts it back on the queue.
                            // message.requeue();
                        });

        nsqConsumer.start();
        log.info("start a nsq consumer");

        return CompletableFuture.completedFuture(new NsqBenchmarkConsumer(nsqConsumer));
    }

    @Override
    public void close() throws Exception {}

    private static final Logger log = LoggerFactory.getLogger(NsqBenchmarkDriver.class);
    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
