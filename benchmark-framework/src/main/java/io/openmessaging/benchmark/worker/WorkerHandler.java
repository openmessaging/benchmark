/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.worker;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;

import io.javalin.Context;
import io.javalin.Javalin;
import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;

@SuppressWarnings("unchecked")
public class WorkerHandler {

    private final Worker localWorker;

    public WorkerHandler(Javalin app, StatsLogger statsLogger) {
        this.localWorker = new LocalWorker(statsLogger);

        app.post("/initialize-driver", this::handleInitializeDriver);
        app.post("/create-topics", this::handleCreateTopics);
        app.post("/create-producers", this::handleCreateProducers);
        app.post("/probe-producers", this::handleProbeProducers);
        app.post("/pause-producers", this::handlePauseProducers);
        app.post("/resume-producers", this::handleResumeProducers);
        app.post("/create-consumers", this::handleCreateConsumers);
        app.post("/pause-consumers", this::handlePauseConsumers);
        app.post("/resume-consumers", this::handleResumeConsumers);
        app.post("/start-load", this::handleStartLoad);
        app.post("/adjust-publish-rate", this::handleAdjustPublishRate);
        app.post("/stop-all", this::handleStopAll);
        app.get("/period-stats", this::handlePeriodStats);
        app.get("/cumulative-latencies", this::handleCumulativeLatencies);
        app.get("/counters-stats", this::handleCountersStats);
        app.post("/reset-stats", this::handleResetStats);
    }

    private void handleInitializeDriver(Context ctx) throws Exception {
        // Save config to temp file
        File tempFile = File.createTempFile("driver-configuration", "conf");
        Files.write(ctx.bodyAsBytes(), tempFile);

        localWorker.initializeDriver(tempFile, null);
        tempFile.delete();
    }

    private void handleCreateTopics(Context ctx) throws Exception {
        TopicsInfo topicsInfo = mapper.readValue(ctx.body(), TopicsInfo.class);
        log.info("Received create topics request for topics: {}", ctx.body());
        List<String> topics = localWorker.createTopics(topicsInfo);
        ctx.result(writer.writeValueAsString(topics));
    }

    private void handleCreateProducers(Context ctx) throws Exception {
        List<String> topics = (List<String>) mapper.readValue(ctx.body(), List.class);
        log.info("Received create producers request for topics: {}", topics);
        localWorker.createProducers(topics);
    }

    private void handleProbeProducers(Context ctx) throws Exception {
        localWorker.probeProducers();
    }

    private void handlePauseProducers(Context ctx) throws Exception {
        localWorker.pauseProducers();
    }

    private void handleResumeProducers(Context ctx) throws Exception {
        localWorker.resumeProducers();
    }

    private void handleCreateConsumers(Context ctx) throws Exception {
        ConsumerAssignment consumerAssignment = mapper.readValue(ctx.body(), ConsumerAssignment.class);

        log.info("Received create consumers request for topics: {}", consumerAssignment.topicsSubscriptions);
        localWorker.createConsumers(consumerAssignment);
    }

    private void handlePauseConsumers(Context ctx) throws Exception {
        localWorker.pauseConsumers();
    }

    private void handleResumeConsumers(Context ctx) throws Exception {
        localWorker.resumeConsumers();
    }

    private void handleStartLoad(Context ctx) throws Exception {
        ProducerWorkAssignment producerWorkAssignment = mapper.readValue(ctx.body(), ProducerWorkAssignment.class);

        log.info("Start load publish-rate: {} msg/s -- payload-size: {}", producerWorkAssignment.publishRate,
                producerWorkAssignment.payloadData.get(0).length);

        localWorker.startLoad(producerWorkAssignment);
    }

    private void handleAdjustPublishRate(Context ctx) throws Exception {
        Double publishRate = mapper.readValue(ctx.body(), Double.class);
        log.info("Adjust publish-rate: {} msg/s", publishRate);
        localWorker.adjustPublishRate(publishRate);
    }

    private void handleStopAll(Context ctx) throws Exception {
        log.info("Stop All");
        localWorker.stopAll();
    }

    private void handlePeriodStats(Context ctx) throws Exception {
        PeriodStats stats = localWorker.getPeriodStats();

        // Serialize histograms
        synchronized (histogramSerializationBuffer) {
            histogramSerializationBuffer.clear();
            stats.publishLatency.encodeIntoCompressedByteBuffer(histogramSerializationBuffer);
            stats.publishLatencyBytes = new byte[histogramSerializationBuffer.position()];
            histogramSerializationBuffer.flip();
            histogramSerializationBuffer.get(stats.publishLatencyBytes);

            histogramSerializationBuffer.clear();
            stats.endToEndLatency.encodeIntoCompressedByteBuffer(histogramSerializationBuffer);
            stats.endToEndLatencyBytes = new byte[histogramSerializationBuffer.position()];
            histogramSerializationBuffer.flip();
            histogramSerializationBuffer.get(stats.endToEndLatencyBytes);
        }

        ctx.result(writer.writeValueAsString(stats));
    }

    private void handleCumulativeLatencies(Context ctx) throws Exception {
        CumulativeLatencies stats = localWorker.getCumulativeLatencies();

        // Serialize histograms
        synchronized (histogramSerializationBuffer) {
            histogramSerializationBuffer.clear();
            stats.publishLatency.encodeIntoCompressedByteBuffer(histogramSerializationBuffer);
            stats.publishLatencyBytes = new byte[histogramSerializationBuffer.position()];
            histogramSerializationBuffer.flip();
            histogramSerializationBuffer.get(stats.publishLatencyBytes);

            histogramSerializationBuffer.clear();
            stats.endToEndLatency.encodeIntoCompressedByteBuffer(histogramSerializationBuffer);
            stats.endToEndLatencyBytes = new byte[histogramSerializationBuffer.position()];
            histogramSerializationBuffer.flip();
            histogramSerializationBuffer.get(stats.endToEndLatencyBytes);
        }

        ctx.result(writer.writeValueAsString(stats));
    }

    private void handleCountersStats(Context ctx) throws Exception {
        ctx.result(writer.writeValueAsString(localWorker.getCountersStats()));
    }

    private void handleResetStats(Context ctx) throws Exception {
        log.info("Reset stats");
        localWorker.resetStats();
    }

    private final ByteBuffer histogramSerializationBuffer = ByteBuffer.allocate(1024 * 1024);

    private static final Logger log = LoggerFactory.getLogger(WorkerHandler.class);

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

}
