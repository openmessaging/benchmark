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
package io.openmessaging.benchmark.driver.bookkeeper;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dlshade.org.apache.bookkeeper.stats.CachingStatsLogger;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.bookkeeper.stats.StatsLoggerAdaptor;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.distributedlog.DistributedLogConfiguration;
import org.apache.distributedlog.api.DistributedLogManager;
import org.apache.distributedlog.api.namespace.Namespace;
import org.apache.distributedlog.api.namespace.NamespaceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Benchmark driver testing distributedlog. */
public class DlogBenchmarkDriver implements BenchmarkDriver {

    private static final Logger log = LoggerFactory.getLogger(DlogBenchmarkProducer.class);
    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Config config;
    private Namespace namespace;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, Config.class);

        PropertiesConfiguration propsConf = new PropertiesConfiguration();
        DistributedLogConfiguration conf = new DistributedLogConfiguration();
        try {
            propsConf.load(new StringReader(config.dlogConf));
            conf.loadConf(propsConf);
        } catch (ConfigurationException e) {
            log.error("Failed to load dlog configuration : \n{}\n", config.dlogConf, e);
            throw new IOException("Failed to load configuration : \n" + config.dlogConf + "\n", e);
        }
        URI dlogUri = URI.create(config.dlogUri);

        dlshade.org.apache.bookkeeper.stats.StatsLogger dlStatsLogger =
                new CachingStatsLogger(new StatsLoggerAdaptor(statsLogger.scope("dlog")));

        namespace =
                NamespaceBuilder.newBuilder().conf(conf).uri(dlogUri).statsLogger(dlStatsLogger).build();

        log.info("Initialized distributedlog namespace at {}", dlogUri);
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-stream";
    }

    private static String getFullyQualifiedPartitionedStreamName(String topic, int partitionIdx) {
        return String.format("%s/%06d", topic, partitionIdx);
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        namespace.createLog(topic);
                        if (partitions > 1) {
                            for (int i = 0; i < partitions; i++) {
                                namespace.createLog(getFullyQualifiedPartitionedStreamName(topic, i));
                            }
                        }
                        log.info("Successfully create topic {} with {} partitions", topic, partitions);
                    } catch (IOException ioe) {
                        log.error("Failed to create topic {} with {} partitions", topic, partitions, ioe);
                        throw new RuntimeException(ioe);
                    }
                });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                DistributedLogManager dlm = namespace.openLog(topic);
                                log.info("Open stream {} for producer", topic);
                                return dlm;
                            } catch (IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                        })
                .thenCompose(dlm -> dlm.openAsyncLogWriter())
                .thenApply(writer -> new DlogBenchmarkProducer(writer));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                DistributedLogManager dlm = namespace.openLog(topic);
                                log.info("Open stream {} for consumer", topic);
                                return dlm;
                            } catch (IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                        })
                .thenApply(dlm -> new DlogBenchmarkConsumer(dlm, consumerCallback));
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down BookKeeper benchmark driver");

        if (null != namespace) {
            namespace.close();
        }

        log.info("BookKeeper benchmark driver successfully shut down");
    }
}
