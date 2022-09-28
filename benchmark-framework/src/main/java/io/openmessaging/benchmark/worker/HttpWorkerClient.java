package io.openmessaging.benchmark.worker;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CountersStats;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpWorkerClient implements Worker {

    private final AsyncHttpClient httpClient;
    private final String host;

    public HttpWorkerClient(String host) {
        this.host = host;
        DefaultAsyncHttpClientConfig.Builder clientBuilder = Dsl.config()
                .setReadTimeout(600000)
                .setRequestTimeout(600000);
        httpClient = asyncHttpClient(clientBuilder);
    }

    public HttpWorkerClient(AsyncHttpClient httpClient, String host) {
        this.httpClient = httpClient;
        this.host = host;
    }

    @Override
    public void initializeDriver(File configurationFile) throws IOException {
        byte[] confFileContent = Files.readAllBytes(Paths.get(configurationFile.toString()));
        sendPost("/initialize-driver", confFileContent).join();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> createTopics(TopicsInfo topicsInfo) throws IOException {
        return (List<String>) post( "/create-topics", writer.writeValueAsBytes(topicsInfo), List.class)
                .join();
    }

    @Override
    public void createProducers(List<String> topics) throws IOException {
        sendPost("/create-producers", writer.writeValueAsBytes(topics)).join();
    }

    @Override
    public void createConsumers(ConsumerAssignment consumerAssignment) throws IOException {
        sendPost("/create-consumers", writer.writeValueAsBytes(consumerAssignment));
    }

    @Override
    public void probeProducers() throws IOException {
        sendPost("/probe-producers", new byte[0]).join();
    }

    @Override
    public void startLoad(ProducerWorkAssignment producerWorkAssignment) throws IOException {
        log.debug("Setting worker assigned publish rate to {} msgs/sec", producerWorkAssignment.publishRate);
        sendPost("/start-load", writer.writeValueAsBytes(producerWorkAssignment)).join();
    }

    @Override
    public void adjustPublishRate(double publishRate) throws IOException {
        log.debug("Adjusting worker publish rate to {} msgs/sec", publishRate);
        sendPost("/adjust-publish-rate", writer.writeValueAsBytes(publishRate)).join();
    }

    @Override
    public void pauseConsumers() throws IOException {
        sendPost("/pause-consumers", new byte[0]).join();
    }

    @Override
    public void resumeConsumers() throws IOException {
        sendPost("/resume-consumers", new byte[0]).join();
    }

    @Override
    public CountersStats getCountersStats() throws IOException {
        return get("/counters-stats", CountersStats.class).join();
    }

    @Override
    public PeriodStats getPeriodStats() throws IOException {
        return get("/period-stats", PeriodStats.class).join();
    }

    @Override
    public CumulativeLatencies getCumulativeLatencies() throws IOException {
        return get("/cumulative-latencies", CumulativeLatencies.class).join();
    }

    @Override
    public void resetStats() throws IOException {
        sendPost("/reset-stats", new byte[0]).join();
    }

    @Override
    public void stopAll() {
        sendPost("/stop-all", new byte[0]).join();
    }

    @Override
    public String id() {
        return host;
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
    }

    private CompletableFuture<Void> sendPost(String path, byte[] body) {
        return httpClient.preparePost(host + path).setBody(body).execute().toCompletableFuture().thenApply(x -> {
            if (x.getStatusCode() != 200) {
                log.error("Failed to do HTTP post request to {}{} -- code: {}", host, path, x.getStatusCode());
            }
            Preconditions.checkArgument(x.getStatusCode() == 200);
            return (Void) null;
        });
    }

    private <T> CompletableFuture<T> get(String path, Class<T> clazz) {
        return httpClient.prepareGet(host + path).execute().toCompletableFuture().thenApply(response -> {
            try {
                if (response.getStatusCode() != 200) {
                    log.error("Failed to do HTTP get request to {}{} -- code: {}", host, path, response.getStatusCode());
                }
                Preconditions.checkArgument(response.getStatusCode() == 200);
                return mapper.readValue(response.getResponseBody(), clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private <T> CompletableFuture<T> post(String path, byte[] body, Class<T> clazz) {
        return httpClient.preparePost(host + path).setBody(body).execute().toCompletableFuture().thenApply(response -> {
            try {
                if (response.getStatusCode() != 200) {
                    log.error("Failed to do HTTP post request to {}{} -- code: {}", host, path, response.getStatusCode());
                }
                Preconditions.checkArgument(response.getStatusCode() == 200);
                return mapper.readValue(response.getResponseBody(), clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    private static final Logger log = LoggerFactory.getLogger(HttpWorkerClient.class);
}
