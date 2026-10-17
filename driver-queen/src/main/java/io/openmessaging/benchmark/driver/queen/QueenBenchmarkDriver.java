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
package io.openmessaging.benchmark.driver.queen;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.bookkeeper.stats.StatsLogger;
import org.asynchttpclient.uri.Uri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenMessaging Benchmark driver for Queen MQ over its native HTTP protocol: binary push, framed
 * pop, pop autopilot. A topic is a Queen queue, its partitions are the partition names "0" ..
 * "P-1", a subscription is a consumer group.
 */
public class QueenBenchmarkDriver implements BenchmarkDriver {
    private static final Logger log = LoggerFactory.getLogger(QueenBenchmarkDriver.class);
    private static final ObjectMapper YAML =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Queen KV namespace where the creating worker leaves each topic's partition count. */
    private static final String KV_NAMESPACE = "omb";

    private QueenConfig cfg;
    private QueenContext ctx;
    private final Map<String, Integer> partitionsByTopic = new ConcurrentHashMap<>();
    private final Map<String, QueenSender.Topic> topics = new ConcurrentHashMap<>();
    private final AtomicInteger topicIds = new AtomicInteger();
    /** The partition count stored for a topic-name prefix (one KV read per prefix). */
    private final Map<String, CompletableFuture<Integer>> byPrefix = new ConcurrentHashMap<>();
    /** This driver's topic prefix: topicPrefix plus 6 random characters, the key of its KV entry. */
    private String prefix;

    private static final AtomicInteger CONFIGURED_LOGGED = new AtomicInteger();
    private final List<QueenBenchmarkProducer> producers =
            Collections.synchronizedList(new ArrayList<>());
    private final List<QueenBenchmarkConsumer> consumers =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        cfg = YAML.readValue(configurationFile, QueenConfig.class);
        List<String> urls = new ArrayList<>();
        if (cfg.urls != null) {
            for (String u : cfg.urls) {
                if (u != null && !u.trim().isEmpty()) {
                    String s = u.trim();
                    while (s.endsWith("/")) {
                        s = s.substring(0, s.length() - 1);
                    }
                    urls.add(s);
                }
            }
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException(configurationFile + ": urls is empty");
        }
        String compression =
                cfg.compression == null ? "none" : cfg.compression.trim().toLowerCase(Locale.ROOT);
        boolean zstd;
        switch (compression) {
            case "":
            case "none":
                zstd = false;
                break;
            case "zstd":
                zstd = true;
                break;
            default:
                throw new IllegalArgumentException(
                        configurationFile + ": compression is none or zstd, not " + cfg.compression);
        }
        if (cfg.dedupWindowSeconds <= 0) {
            log.warn(
                    "dedupWindowSeconds {}: the queues are created without dedup", cfg.dedupWindowSeconds);
        }
        QueenHttp http = new QueenHttp(cfg);
        ScheduledThreadPoolExecutor timer =
                new ScheduledThreadPoolExecutor(
                        Math.max(2, Runtime.getRuntime().availableProcessors() / 4), daemon("queen-linger"));
        ExecutorService encoder = null;
        if (zstd) {
            int n =
                    cfg.compressionThreads > 0
                            ? cfg.compressionThreads
                            : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
            encoder = Executors.newFixedThreadPool(n, daemon("queen-zstd"));
        }
        String base = cfg.topicPrefix == null || cfg.topicPrefix.isEmpty() ? "omb" : cfg.topicPrefix;
        StringBuilder rnd = new StringBuilder(base).append('-');
        java.util.concurrent.ThreadLocalRandom tr = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            rnd.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(tr.nextInt(36)));
        }
        prefix = rnd.toString();
        ctx = new QueenContext(cfg, http, Collections.unmodifiableList(urls), timer, encoder, zstd);
        ctx.sender = new QueenSender(ctx);
        ctx.pool = new QueenPopPool(ctx);
        for (String u : urls) {
            health(u);
        }
        log.info(
                "Queen driver: brokers {}; push: lanes {}, linger {} ms, batch {} B, {} in flight per lane,"
                        + " compression {}; consume: {} pops per consumer, {} pops on this worker,"
                        + " pop timeout {} ms ({} ms when rotating),"
                        + " {} acks in flight per group, lease {} s{}; queue dedup window {} s",
                urls,
                cfg.producerLanes > 0 ? cfg.producerLanes : "auto",
                cfg.lingerMs,
                cfg.batchMaxBytes,
                cfg.maxInFlight,
                zstd ? "zstd (client-framed)" : "none",
                cfg.pollers,
                cfg.maxPops,
                cfg.popTimeoutMs,
                cfg.rotationWaitMs,
                cfg.ackMaxInFlight,
                cfg.leaseSeconds > 0 ? cfg.leaseSeconds : "of the queue",
                cfg.renewLeases ? ", extended while a batch is not acked" : ", never extended",
                cfg.dedupWindowSeconds);
    }

    private void health(String base) {
        try {
            QueenHttp.Body r =
                    ctx.http
                            .call(ctx.http.get(Uri.create(base + "/health"), 10_000))
                            .get(15, TimeUnit.SECONDS);
            log.info("Queen {} /health: HTTP {} {}", base, r.status, Wire.excerpt(r.buf, r.len));
        } catch (Exception e) {
            log.warn("Queen {} /health failed: {}", base, e.toString());
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return prefix;
    }

    /*
     * The prefix of a name OMB built as prefix + "-%07d-" + 7 random base64url characters, or null.
     * The random part may hold '-', so it is cut by length, not at a dash.
     */
    static String prefixOf(String topic) {
        int n = topic.length();
        if (n < 17 || topic.charAt(n - 8) != '-' || topic.charAt(n - 16) != '-') {
            return null;
        }
        for (int i = n - 15; i < n - 8; i++) {
            if (!Character.isDigit(topic.charAt(i))) {
                return null;
            }
        }
        return topic.substring(0, n - 16);
    }

    /**
     * The topics, setupConcurrency at a time (OMB's default starts every createTopic at once: a
     * million requests for a million topics).
     */
    @Override
    public CompletableFuture<Void> createTopics(List<TopicInfo> topicInfos) {
        long t0 = System.nanoTime();
        Bounded run = new Bounded(cfg.setupConcurrency);
        AtomicInteger done = new AtomicInteger();
        // One KV entry for the whole batch when its topics share this driver's prefix and a partition
        // count (OMB's createTopics: one count, names prefix-%07d-random): KV takes 100 writes/s per
        // tenant (QUEEN_KV_WRITE_RATE), so one entry per topic would take 1,000 s at 100,000 topics.
        int common = topicInfos.isEmpty() ? 0 : topicInfos.get(0).getPartitions();
        boolean shared = common > 0;
        for (TopicInfo ti : topicInfos) {
            if (ti.getPartitions() != common || !prefix.equals(prefixOf(ti.getTopic()))) {
                shared = false;
                break;
            }
        }
        final boolean perTopicKv = !shared;
        for (TopicInfo ti : topicInfos) {
            run.submit(
                    () ->
                            configureQueue(ti.getTopic(), ti.getPartitions(), perTopicKv)
                                    .whenComplete(
                                            (v, e) -> {
                                                int n = done.incrementAndGet();
                                                if (n % 10_000 == 0) {
                                                    log.info("Queen: {} of {} queues configured", n, topicInfos.size());
                                                }
                                            }));
        }
        CompletableFuture<Void> all = run.all();
        if (shared) {
            all = all.thenCompose(v -> putPartitionCount(ctx.urls.get(0), prefix, common, 0));
        }
        final boolean one = shared;
        return all.thenRun(
                () ->
                        log.info(
                                "Queen: {} queues configured in {} ms; partition count {} in KV {}/{}",
                                topicInfos.size(),
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                                one ? common : "per topic",
                                KV_NAMESPACE,
                                one ? prefix : "<topic>"));
    }

    /**
     * Configures the queue (POST /api/v1/configure: the queue defaults, queueOptions, the dedup
     * window), then leaves the partition count in Queen KV for the producers of other workers.
     */
    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return configureQueue(topic, partitions, true);
    }

    private CompletableFuture<Void> configureQueue(String topic, int partitions, boolean kv) {
        if (partitions <= 0) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("partitions " + partitions));
            return f;
        }
        partitionsByTopic.put(topic, partitions);
        Map<String, Object> options = new LinkedHashMap<>();
        if (cfg.queueOptions != null) {
            options.putAll(cfg.queueOptions);
        }
        options.put("dedupWindowSeconds", cfg.dedupWindowSeconds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("queue", topic);
        body.put("options", options);
        String base = ctx.urls.get(0);
        byte[] json;
        try {
            json = JSON.writeValueAsBytes(body);
        } catch (IOException e) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        return ctx.http
                .call(
                        ctx.http.post(
                                Uri.create(base + "/api/v1/configure"),
                                json,
                                json.length,
                                "application/json",
                                cfg.requestTimeoutMs))
                .thenCompose(
                        r -> {
                            if (r.status < 200 || r.status > 299) {
                                throw new QueenException(
                                        "configure "
                                                + topic
                                                + ": HTTP "
                                                + r.status
                                                + ": "
                                                + Wire.excerpt(r.buf, r.len));
                            }
                            if (CONFIGURED_LOGGED.getAndIncrement() < 5) {
                                log.info(
                                        "Queen queue {} configured ({} partitions): {}",
                                        topic,
                                        partitions,
                                        Wire.excerpt(r.buf, r.len));
                            }
                            return kv
                                    ? putPartitionCount(base, topic, partitions, 0)
                                    : CompletableFuture.completedFuture((Void) null);
                        });
    }

    /*
     * Writes omb/<key> = {"partitions":P}; a 429 (the tenant's KV rate) is retried for up to a
     * minute.
     */
    private CompletableFuture<Void> putPartitionCount(
            String base, String key, int partitions, int attempt) {
        byte[] body =
                Wire.utf8("{\"value\":{\"partitions\":" + partitions + "},\"ttlSeconds\":2592000}");
        return ctx.http
                .call(
                        ctx.http.put(
                                Uri.create(base + "/api/v1/kv/" + KV_NAMESPACE + "/" + key),
                                body,
                                "application/json",
                                cfg.requestTimeoutMs))
                .handle(
                        (r, err) ->
                                err == null && (r.status == 429 || r.status == 503) && attempt < 60
                                        ? null
                                        : new Object[] {r, err})
                .thenCompose(
                        o -> {
                            if (o == null) {
                                CompletableFuture<Void> later = new CompletableFuture<>();
                                ctx.timer.schedule(
                                        () ->
                                                putPartitionCount(base, key, partitions, attempt + 1)
                                                        .whenComplete((v, e) -> later.complete(null)),
                                        1000,
                                        TimeUnit.MILLISECONDS);
                                return later;
                            }
                            QueenHttp.Body r = (QueenHttp.Body) o[0];
                            Throwable err = (Throwable) o[1];
                            if (err != null || r.status < 200 || r.status > 299) {
                                log.warn(
                                        "Queen KV {}/{}: the partition count was not stored ({}); producers on other"
                                                + " workers need partitionsPerTopic",
                                        KV_NAMESPACE,
                                        key,
                                        err != null
                                                ? err.toString()
                                                : "HTTP " + r.status + ": " + Wire.excerpt(r.buf, r.len));
                            }
                            return CompletableFuture.completedFuture((Void) null);
                        });
    }

    /*
     * The partition count another worker stored in KV (tried for up to 10 s), or partitionsPerTopic,
     * or 0.
     */
    private CompletableFuture<Integer> lookUpPartitions(String topic, int attempt) {
        String pre = prefixOf(topic);
        if (pre == null) {
            return lookUpKey(topic, attempt);
        }
        return byPrefix
                .computeIfAbsent(pre, k -> lookUpKey(k, 0))
                .thenCompose(p -> p > 0 ? CompletableFuture.completedFuture(p) : lookUpKey(topic, attempt));
    }

    /*
     * omb/<key>'s partition count, retried every 500 ms until attempt 19, then partitionsPerTopic.
     */
    private CompletableFuture<Integer> lookUpKey(String topic, int attempt) {
        String base = ctx.urls.get(0);
        Uri uri = Uri.create(base + "/api/v1/kv/" + KV_NAMESPACE + "/" + topic);
        return ctx.http
                .call(ctx.http.get(uri, cfg.requestTimeoutMs))
                .handle(
                        (r, err) -> {
                            if (err == null && r.status == 200 && r.len > 0) {
                                try {
                                    JsonNode n = JSON.readTree(r.buf, 0, r.len);
                                    int p = n.path("value").path("partitions").asInt(0);
                                    if (n.path("found").asBoolean(false) && p > 0) {
                                        return p;
                                    }
                                } catch (IOException e) {
                                    log.debug("KV read of {}: {}", topic, e.toString());
                                }
                            }
                            return 0;
                        })
                .thenCompose(
                        p -> {
                            if (p > 0) {
                                return CompletableFuture.completedFuture(p);
                            }
                            if (attempt >= 19) {
                                return CompletableFuture.completedFuture(Math.max(0, cfg.partitionsPerTopic));
                            }
                            CompletableFuture<Integer> later = new CompletableFuture<>();
                            ctx.timer.schedule(
                                    () ->
                                            lookUpKey(topic, attempt + 1)
                                                    .whenComplete(
                                                            (v, e) -> {
                                                                if (e != null) {
                                                                    later.completeExceptionally(e);
                                                                } else {
                                                                    later.complete(v);
                                                                }
                                                            }),
                                    500,
                                    TimeUnit.MILLISECONDS);
                            return later;
                        });
    }

    /* The topic as the producers see it, its partition count from this worker, KV or the config. */
    private CompletableFuture<QueenSender.Topic> topic(String name) {
        QueenSender.Topic t = topics.get(name);
        if (t != null) {
            return CompletableFuture.completedFuture(t);
        }
        Integer known = partitionsByTopic.get(name);
        CompletableFuture<Integer> partitions =
                known != null ? CompletableFuture.completedFuture(known) : lookUpPartitions(name, 0);
        return partitions.thenApply(
                p -> {
                    if (p <= 0) {
                        throw new QueenException(
                                "the partition count of "
                                        + name
                                        + " is unknown: this worker did not create it, Queen KV "
                                        + KV_NAMESPACE
                                        + "/"
                                        + name
                                        + " has none, and partitionsPerTopic is not set");
                    }
                    return topics.computeIfAbsent(
                            name, k -> new QueenSender.Topic(topicIds.incrementAndGet(), k, p));
                });
    }

    /**
     * Every producer of this worker at once: the topics' partition counts are looked up
     * setupConcurrency at a time, the producers spread over the lanes in OMB's order (OMB gives
     * producer i to its thread i % cores, so with one lane per core a thread has its own lane).
     */
    @Override
    public CompletableFuture<List<BenchmarkProducer>> createProducers(List<ProducerInfo> infos) {
        long t0 = System.nanoTime();
        ctx.sender.ensureLanes(infos.size());
        Bounded run = new Bounded(cfg.setupConcurrency);
        Map<String, Boolean> distinct = new LinkedHashMap<>();
        for (ProducerInfo pi : infos) {
            distinct.put(pi.getTopic(), Boolean.TRUE);
        }
        for (String name : distinct.keySet()) {
            run.submit(() -> topic(name));
        }
        return run.all()
                .thenApply(
                        v -> {
                            List<BenchmarkProducer> out = new ArrayList<>(infos.size());
                            int i = 0;
                            for (ProducerInfo pi : infos) {
                                QueenBenchmarkProducer p =
                                        new QueenBenchmarkProducer(ctx.sender.lane(i++), topics.get(pi.getTopic()));
                                producers.add(p);
                                out.add(p);
                            }
                            log.info(
                                    "Queen: {} producers on {} topics, {} lanes, in {} ms",
                                    infos.size(),
                                    distinct.size(),
                                    ctx.sender.laneCount(),
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
                            return out;
                        });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return topic(topic)
                .thenApply(
                        t -> {
                            ctx.sender.ensureLanes(producers.size() + 1);
                            QueenBenchmarkProducer p = new QueenBenchmarkProducer(ctx.sender.nextLane(), t);
                            producers.add(p);
                            return p;
                        });
    }

    /**
     * Every consumer of this worker at once, into the shared pop pool. The topics' partition counts
     * (this worker's, or KV's, one read each, setupConcurrency at a time) cap a subscription's pops:
     * a partition is leased to one pop at a time, so more pops than partitions would only park.
     */
    @Override
    public CompletableFuture<List<BenchmarkConsumer>> createConsumers(List<ConsumerInfo> infos) {
        long t0 = System.nanoTime();
        List<String[]> ts = new ArrayList<>(infos.size());
        List<ConsumerCallback> cbs = new ArrayList<>(infos.size());
        Map<String, Integer> parts = new ConcurrentHashMap<>();
        for (ConsumerInfo ci : infos) {
            ts.add(new String[] {ci.getTopic(), ci.getSubscriptionName()});
            cbs.add(ci.getConsumerCallback());
            Integer p = partitionsByTopic.get(ci.getTopic());
            parts.put(ci.getTopic(), p != null ? p : 0);
        }
        Bounded run = new Bounded(cfg.setupConcurrency);
        for (Map.Entry<String, Integer> e : parts.entrySet()) {
            if (e.getValue() == 0) {
                String name = e.getKey();
                run.submit(() -> lookUpPartitions(name, 19).thenAccept(p -> parts.put(name, p)));
            }
        }
        return run.all()
                .thenApply(
                        v -> {
                            List<QueenBenchmarkConsumer> cs = ctx.pool.register(ts, cbs, parts);
                            consumers.addAll(cs);
                            log.info(
                                    "Queen: {} consumers on {} topics in {} ms; pop pool {}",
                                    cs.size(),
                                    parts.size(),
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                                    ctx.pool.shape());
                            return new ArrayList<BenchmarkConsumer>(cs);
                        });
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        List<String[]> ts = new ArrayList<>(1);
        ts.add(new String[] {topic, subscriptionName});
        List<ConsumerCallback> cbs = new ArrayList<>(1);
        cbs.add(consumerCallback);
        Integer known = partitionsByTopic.get(topic);
        CompletableFuture<Integer> p =
                known != null ? CompletableFuture.completedFuture(known) : lookUpPartitions(topic, 19);
        return p.thenApply(
                n -> {
                    Map<String, Integer> parts = new ConcurrentHashMap<>();
                    parts.put(topic, n);
                    QueenBenchmarkConsumer c = ctx.pool.register(ts, cbs, parts).get(0);
                    consumers.add(c);
                    return c;
                });
    }

    /** Runs at most n of the submitted calls at a time; all() completes when every one has. */
    private static final class Bounded {
        private final java.util.concurrent.Semaphore slots;
        private final List<CompletableFuture<?>> all = new ArrayList<>();
        private final java.util.concurrent.atomic.AtomicReference<Throwable> first =
                new java.util.concurrent.atomic.AtomicReference<>();

        Bounded(int n) {
            slots = new java.util.concurrent.Semaphore(Math.max(1, n));
        }

        void submit(java.util.function.Supplier<CompletableFuture<?>> call) {
            slots.acquireUninterruptibly();
            CompletableFuture<?> f;
            try {
                f = call.get();
            } catch (Throwable t) {
                slots.release();
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                f = failed;
            }
            f.whenComplete(
                    (v, e) -> {
                        if (e != null) {
                            first.compareAndSet(null, e);
                        }
                        slots.release();
                    });
            all.add(f);
        }

        CompletableFuture<Void> all() {
            return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]));
        }
    }

    @Override
    public void close() throws Exception {
        if (ctx == null) {
            return;
        }
        producers.clear();
        ctx.sender.close();
        List<QueenBenchmarkConsumer> cs;
        synchronized (consumers) {
            cs = new ArrayList<>(consumers);
            consumers.clear();
        }
        Map<String, Object> shape = ctx.pool.shape();
        for (QueenBenchmarkConsumer c : cs) {
            c.close();
        }
        ctx.pool.close();
        log.info("Queen driver closed: {}; pop pool {}", ctx.stats, shape);
        if (cfg.verify) {
            log.info("Queen verify: {}", ctx.pool.verifyReport());
        }
        ctx.timer.shutdownNow();
        if (ctx.encoder != null) {
            ctx.encoder.shutdown();
            ctx.encoder.awaitTermination(5, TimeUnit.SECONDS);
        }
        ctx.http.close();
        ctx = null;
    }

    private static ThreadFactory daemon(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static final ConcurrentHashMap<String, long[]> WARNED = new ConcurrentHashMap<>();

    /* A warning at most every 5 s per kind, with the count of the ones held back. */
    static void warn(String kind, String message, Throwable t) {
        long now = System.nanoTime();
        long[] st =
                WARNED.computeIfAbsent(kind, k -> new long[] {now - TimeUnit.SECONDS.toNanos(60), 0});
        long held;
        synchronized (st) {
            if (now - st[0] < TimeUnit.SECONDS.toNanos(5)) {
                st[1]++;
                return;
            }
            held = st[1];
            st[0] = now;
            st[1] = 0;
        }
        String more = held > 0 ? " (and " + held + " more like it in the last seconds)" : "";
        if (t != null) {
            log.warn("{}{}", message, more, t);
        } else {
            log.warn("{}{}", message, more);
        }
    }
}
