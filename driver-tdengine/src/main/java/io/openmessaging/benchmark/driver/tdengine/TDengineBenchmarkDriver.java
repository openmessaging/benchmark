/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.openmessaging.benchmark.driver.tdengine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

public class TDengineBenchmarkDriver implements BenchmarkDriver {
    private Config config;
    private Connection conn;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, Config.class);

        try {
            conn = DriverManager.getConnection(config.jdbcURL);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try (Statement stmt = conn.createStatement()) {
            String q = "drop database if exists " + config.database;
            log.info(q);
            stmt.executeUpdate(q);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return "td";
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        CompletableFuture future = new CompletableFuture();
        try (Statement stmt = conn.createStatement()) {
            String q = "create database if not exists " + config.database + " precision 'ns' vgroups " + partitions;
            log.info(q);
            stmt.executeUpdate(q);
            stmt.executeUpdate("use " + config.database);
            String stable = topic.replaceAll("-", "_");
            q = "create stable if not exists " + stable + "(ts timestamp, payload binary(" + config.varcharLen + ")) tags(id bigint)";
            log.info(q);
            stmt.executeUpdate(q);
            /*
            q = "create topic `" + topic + "` as select ts, payload from " + stable;
            q = "create topic `" + topic + "` as database " + config.database;
            */
            q = "create topic `" + topic + "` as stable " + stable;
            log.info(q);
            stmt.executeUpdate(q);
            future.complete(null);
        } catch (SQLException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return CompletableFuture.completedFuture(new TDengineBenchmarkProducer(new TDengineProducer(topic, config)));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return CompletableFuture.completedFuture(new TDengineBenchmarkConsumer(topic, subscriptionName, consumerCallback));
    }

    @Override
    public void close() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TDengineBenchmarkDriver.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
