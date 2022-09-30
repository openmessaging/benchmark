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
package io.openmessaging.benchmark.worker;

import static io.openmessaging.benchmark.worker.WorkerHandler.ADJUST_PUBLISH_RATE;
import static io.openmessaging.benchmark.worker.WorkerHandler.COUNTERS_STATS;
import static io.openmessaging.benchmark.worker.WorkerHandler.CREATE_CONSUMERS;
import static io.openmessaging.benchmark.worker.WorkerHandler.CREATE_PRODUCERS;
import static io.openmessaging.benchmark.worker.WorkerHandler.CREATE_TOPICS;
import static io.openmessaging.benchmark.worker.WorkerHandler.CUMULATIVE_LATENCIES;
import static io.openmessaging.benchmark.worker.WorkerHandler.INITIALIZE_DRIVER;
import static io.openmessaging.benchmark.worker.WorkerHandler.PAUSE_CONSUMERS;
import static io.openmessaging.benchmark.worker.WorkerHandler.PERIOD_STATS;
import static io.openmessaging.benchmark.worker.WorkerHandler.PROBE_PRODUCERS;
import static io.openmessaging.benchmark.worker.WorkerHandler.RESET_STATS;
import static io.openmessaging.benchmark.worker.WorkerHandler.RESUME_CONSUMERS;
import static io.openmessaging.benchmark.worker.WorkerHandler.START_LOAD;
import static io.openmessaging.benchmark.worker.WorkerHandler.STOP_ALL;
import static org.asynchttpclient.Dsl.asyncHttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CountersStats;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;
import io.openmessaging.benchmark.worker.jackson.ObjectMappers;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpWorkerClient implements Worker {

    private static final byte[] EMPTY_BODY = new byte[0];
    private static final int HTTP_OK = 200;

    private final AsyncHttpClient httpClient;
    private final String host;

    public HttpWorkerClient(String host) {
        this(asyncHttpClient(Dsl.config().setReadTimeout(600000).setRequestTimeout(600000)), host);
    }

    HttpWorkerClient(AsyncHttpClient httpClient, String host) {
        this.httpClient = httpClient;
        this.host = host;
    }

    @Override
    public void initializeDriver(File configurationFile) throws IOException {
        byte[] confFileContent = Files.readAllBytes(Paths.get(configurationFile.toString()));
        sendPost(INITIALIZE_DRIVER, confFileContent);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> createTopics(TopicsInfo topicsInfo) throws IOException {
        return (List<String>) post(CREATE_TOPICS, writer.writeValueAsBytes(topicsInfo), List.class);
    }

    @Override
    public void createProducers(List<String> topics) throws IOException {
        sendPost(CREATE_PRODUCERS, writer.writeValueAsBytes(topics));
    }

    @Override
    public void createConsumers(ConsumerAssignment consumerAssignment) throws IOException {
        sendPost(CREATE_CONSUMERS, writer.writeValueAsBytes(consumerAssignment));
    }

    @Override
    public void probeProducers() throws IOException {
        sendPost(PROBE_PRODUCERS);
    }

    @Override
    public void startLoad(ProducerWorkAssignment producerWorkAssignment) throws IOException {
        log.debug(
                "Setting worker assigned publish rate to {} msgs/sec", producerWorkAssignment.publishRate);
        sendPost(START_LOAD, writer.writeValueAsBytes(producerWorkAssignment));
    }

    @Override
    public void adjustPublishRate(double publishRate) throws IOException {
        log.debug("Adjusting worker publish rate to {} msgs/sec", publishRate);
        sendPost(ADJUST_PUBLISH_RATE, writer.writeValueAsBytes(publishRate));
    }

    @Override
    public void pauseConsumers() throws IOException {
        sendPost(PAUSE_CONSUMERS);
    }

    @Override
    public void resumeConsumers() throws IOException {
        sendPost(RESUME_CONSUMERS);
    }

    @Override
    public CountersStats getCountersStats() throws IOException {
        return get(COUNTERS_STATS, CountersStats.class);
    }

    @Override
    public PeriodStats getPeriodStats() throws IOException {
        return get(PERIOD_STATS, PeriodStats.class);
    }

    @Override
    public CumulativeLatencies getCumulativeLatencies() throws IOException {
        return get(CUMULATIVE_LATENCIES, CumulativeLatencies.class);
    }

    @Override
    public void resetStats() throws IOException {
        sendPost(RESET_STATS);
    }

    @Override
    public void stopAll() {
        sendPost(STOP_ALL);
    }

    @Override
    public String id() {
        return host;
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    private void sendPost(String path) {
        sendPost(path, EMPTY_BODY);
    }

    private void sendPost(String path, byte[] body) {
        httpClient
                .preparePost(host + path)
                .setBody(body)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        response -> {
                            if (response.getStatusCode() != HTTP_OK) {
                                log.error(
                                        "Failed to do HTTP post request to {}{} -- code: {}",
                                        host,
                                        path,
                                        response.getStatusCode());
                            }
                            Preconditions.checkArgument(response.getStatusCode() == HTTP_OK);
                            return (Void) null;
                        })
                .join();
    }

    private <T> T get(String path, Class<T> clazz) {
        return httpClient
                .prepareGet(host + path)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        response -> {
                            try {
                                if (response.getStatusCode() != HTTP_OK) {
                                    log.error(
                                            "Failed to do HTTP get request to {}{} -- code: {}",
                                            host,
                                            path,
                                            response.getStatusCode());
                                }
                                Preconditions.checkArgument(response.getStatusCode() == HTTP_OK);
                                return mapper.readValue(response.getResponseBody(), clazz);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private <T> T post(String path, byte[] body, Class<T> clazz) {
        return httpClient
                .preparePost(host + path)
                .setBody(body)
                .execute()
                .toCompletableFuture()
                .thenApply(
                        response -> {
                            try {
                                if (response.getStatusCode() != HTTP_OK) {
                                    log.error(
                                            "Failed to do HTTP post request to {}{} -- code: {}",
                                            host,
                                            path,
                                            response.getStatusCode());
                                }
                                Preconditions.checkArgument(response.getStatusCode() == HTTP_OK);
                                return mapper.readValue(response.getResponseBody(), clazz);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .join();
    }

    private static final ObjectMapper mapper = ObjectMappers.DEFAULT.mapper();
    private static final ObjectWriter writer = ObjectMappers.DEFAULT.writer();
    private static final Logger log = LoggerFactory.getLogger(HttpWorkerClient.class);
}
