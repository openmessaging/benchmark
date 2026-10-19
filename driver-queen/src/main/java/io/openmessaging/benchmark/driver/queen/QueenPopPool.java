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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.compress.zstd.ZstdDecompressor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.asynchttpclient.Request;
import org.asynchttpclient.uri.Uri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The pops of every consumer on this worker: one pool, no thread per consumer or per pop.
 *
 * <p>A subscription is a (queue, consumer group) with consumers on this worker; their pops are the
 * pool's. Each subscription wants pollers x its local consumers pops in flight, scaled down to its
 * share of maxPops when the pool cannot give every subscription that. While the wants fit in
 * maxPops every subscription keeps its long polls parked on the broker (the old per-poller
 * behaviour, without the threads); past that the pops rotate over the subscriptions in FIFO order,
 * each waiting at most rotationWaitMs on one before it moves on.
 *
 * <p>A pop is {@code GET /api/v1/pop/queue/{queue}?wait=true&timeout=..&consumerGroup=..
 * &autoAck=false&subscriptionMode=..&frames=true&autopilot=true[&leaseSeconds=..]}. Its answer is
 * decoded and handed to OMB on a delivery thread (never on a Netty thread: OMB blocks its callback
 * while it pauses the consumers), then acked through the group's {@link Acker}: acks of one
 * consumer group share requests (the ack body names one group, and partition ids are unique across
 * queues). A batch not acked a third of its lease after its pop gets the lease extended ({@code
 * POST /api/v1/lease/{leaseId}/extend}) until its ack is answered, so a consumer OMB holds still
 * for a backlog keeps its batches and nothing comes back twice.
 *
 * <p>The framed answer: {@code "QPF1" | u8 version (1: u32 len, the answer's own keys as JSON) |
 * u16 len, queue | u16 len, leaseId | u16 len, consumerGroup | u32 claims}; per claim {@code u64
 * partitionId | u16 len, partition | u64 start | u64 end | u32 deliveryAttempt | u32 records}; per
 * record {@code u64 base_offset | i64 created_at_us | u32 count | u8 codec (0 raw, 1 zstd) | u32
 * len | bytes}; the bytes are `count` frames {@code u32 body_len | u8 flags | u8[16] id | [u8[16]
 * trace id, flag 1] | u16 len, transactionId | [u16 len, producer, flag 2] | payload}. Message i of
 * a record has offset base_offset + i and is delivered only within start..=end. A broker that
 * answers JSON instead (an encrypted queue, a conflating group) is read too.
 */
final class QueenPopPool {
    private static final Logger log = LoggerFactory.getLogger(QueenPopPool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final byte[] MAGIC = Wire.ascii("QPF1");
    private static final byte[] ACK_OPEN = Wire.ascii("{\"acknowledgments\":[");
    private static final byte[] ACK_TXN = Wire.ascii("{\"transactionId\":\"");
    private static final byte[] ACK_PID = Wire.ascii("\",\"partitionId\":\"");
    private static final byte[] ACK_LEASE = Wire.ascii("\",\"leaseId\":\"");
    private static final byte[] ACK_DONE = Wire.ascii("\",\"status\":\"completed\"}");
    private static final byte[] KEY_FAILED = Wire.ascii("\"failed\":");
    private static final byte[] KEY_REFUSED_ITEM = Wire.ascii("\"success\":false");
    private static final byte[] KEY_PARTITIONS = Wire.ascii("\"partitions\":");
    private static final byte[] KEY_BATCH = Wire.ascii("\"batch\":");
    /** OMB's subscription names: "sub-%03d-%s", the suffix 5 random bytes in base64url. */
    private static final Pattern OMB_SUBSCRIPTION =
            Pattern.compile("^(sub-\\d{3})-[A-Za-z0-9_-]{7}$");

    private final QueenContext ctx;
    private final QueenConfig cfg;
    private final int maxPops;
    private final int pollers;
    private final String popTail;
    private final int leaseSeconds;

    private final Object lock = new Object();
    private final LinkedHashMap<String, Sub> subs = new LinkedHashMap<>();
    private final ArrayDeque<Sub> ready = new ArrayDeque<>();
    private int running;
    private long totalWant;
    private int share = -1;
    private boolean closed;

    private final ConcurrentHashMap<String, Acker> ackers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Lease> leases = new ConcurrentHashMap<>();
    private final ExecutorService delivery;
    private final ConcurrentLinkedQueue<byte[]> buffers = new ConcurrentLinkedQueue<>();
    private final AtomicLong pooledBytes = new AtomicLong();
    private final ScheduledFuture<?> renewer;
    private final AtomicInteger ackersInFlight = new AtomicInteger();
    private final AtomicLong peakRunning = new AtomicLong();

    private static final ThreadLocal<Decoder> DECODER = ThreadLocal.withInitial(Decoder::new);

    QueenPopPool(QueenContext ctx) {
        this.ctx = ctx;
        this.cfg = ctx.cfg;
        this.maxPops = Math.max(1, cfg.maxPops);
        this.pollers = Math.max(1, cfg.pollers);
        String mode =
                cfg.subscriptionMode == null || cfg.subscriptionMode.isEmpty()
                        ? "all"
                        : cfg.subscriptionMode.trim().toLowerCase(Locale.ROOT);
        StringBuilder tail =
                new StringBuilder("&autoAck=false&subscriptionMode=")
                        .append(enc(mode))
                        .append(
                                cfg.popBatch > 0
                                        ? "&frames=true&batch=" + Math.min(10_000, cfg.popBatch)
                                        : "&frames=true&autopilot=true");
        if (cfg.leaseSeconds > 0) {
            tail.append("&leaseSeconds=").append(cfg.leaseSeconds);
        }
        this.popTail = tail.toString();
        int lease = cfg.leaseSeconds;
        if (lease <= 0
                && cfg.queueOptions != null
                && cfg.queueOptions.get("leaseTime") instanceof Number) {
            lease = ((Number) cfg.queueOptions.get("leaseTime")).intValue();
        }
        this.leaseSeconds = lease > 0 ? lease : 300;
        int threads =
                cfg.deliveryThreads > 0
                        ? cfg.deliveryThreads
                        : Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger n = new AtomicInteger();
        this.delivery =
                Executors.newFixedThreadPool(
                        threads,
                        r -> {
                            Thread t = new Thread(r, "queen-deliver-" + n.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
        this.renewer =
                cfg.renewLeases
                        ? ctx.timer.scheduleWithFixedDelay(this::renewLeases, 1, 1, TimeUnit.SECONDS)
                        : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /*
     * The Queen consumer group of an OMB subscription. OMB names subscriptions "sub-NNN-<random>"
     * because a Kafka group id is cluster-wide; a Queen group is scoped by its queue, so the index
     * alone keeps two subscriptions of one topic apart, and lets one ack request carry the acks of
     * many queues (the ack body names one group).
     */
    static String queenGroup(String subscription) {
        Matcher m = OMB_SUBSCRIPTION.matcher(subscription);
        return m.matches() ? m.group(1) : subscription;
    }

    /** A (queue, group) with consumers on this worker. */
    final class Sub {
        final String queue;
        final String group;
        final String subscription;
        final String base;
        final String query;
        final Acker acker;
        final QueenBenchmarkConsumer.Verifier verifier;
        final ArrayList<QueenBenchmarkConsumer> consumers = new ArrayList<>(1);
        int rr;
        /** The topic's partition count when known (KV or this worker), else 0. */
        int partitions;

        int want;
        int active;
        boolean queued;
        boolean closed;
        int backoffMs;
        volatile String autopilot = "none seen";
        /**
         * Doorbell mode (guarded by the pool lock): the bell, the next offset not seen, the partition's
         * highWatermark.
         */
        Bell bell;

        boolean dirty;
        long hint;
        long hwm;
        String queryNoWait;

        Sub(String queue, String subscription) {
            this.queue = queue;
            this.subscription = subscription;
            this.group = queenGroup(subscription);
            this.base = ctx.nextUrl();
            this.query =
                    base
                            + "/api/v1/pop/queue/"
                            + enc(queue)
                            + "?wait=true&consumerGroup="
                            + enc(group)
                            + popTail
                            + "&timeout=";
            this.acker = ackers.computeIfAbsent(group, Acker::new);
            this.verifier = cfg.verify ? new QueenBenchmarkConsumer.Verifier() : null;
            allSubs.add(this);
        }

        QueenPopPool pool() {
            return QueenPopPool.this;
        }
    }

    /* Registers consumers (one call per OMB createConsumers) and starts their pops. */
    List<QueenBenchmarkConsumer> register(
            List<String[]> topicAndSubscription,
            List<io.openmessaging.benchmark.driver.ConsumerCallback> callbacks,
            Map<String, Integer> partitions) {
        List<QueenBenchmarkConsumer> out = new ArrayList<>(topicAndSubscription.size());
        synchronized (lock) {
            for (int i = 0; i < topicAndSubscription.size(); i++) {
                String[] ts = topicAndSubscription.get(i);
                String key = ts[0] + '\u0000' + ts[1];
                Sub s = subs.get(key);
                if (s == null) {
                    s = new Sub(ts[0], ts[1]);
                    Integer p = partitions.get(ts[0]);
                    s.partitions = p == null ? 0 : p;
                    subs.put(key, s);
                }
                QueenBenchmarkConsumer c = new QueenBenchmarkConsumer(this, s, callbacks.get(i));
                s.consumers.add(c);
                out.add(c);
            }
            rebalance();
        }
        pump();
        return out;
    }

    /** Under the lock: every subscription's share of maxPops, and the ready list. */
    private void rebalance() {
        int n = subs.size();
        boolean useBells = !"off".equalsIgnoreCase(String.valueOf(cfg.doorbell)) && n > maxPops;
        int rotating = 0;
        for (Sub s : subs.values()) {
            if (!(s.bell != null || (useBells && s.partitions == 1))) {
                rotating++;
            }
        }
        share = rotating == 0 ? maxPops : Math.max(1, maxPops / rotating);
        long total = 0;
        for (Sub s : subs.values()) {
            if (s.bell == null && useBells && s.partitions == 1) {
                joinBell(s);
            }
            if (s.bell != null) {
                s.want = 1;
                if (s.dirty && !s.queued && !s.closed && s.active < 1) {
                    s.queued = true;
                    ready.addLast(s);
                }
                continue;
            }
            // A partition is leased to one pop at a time: more pops than partitions only park.
            int cap = s.partitions > 0 ? Math.min(share, s.partitions) : share;
            s.want = Math.max(1, Math.min(pollers * s.consumers.size(), cap));
            total += s.want;
            if (!s.queued && !s.closed && s.active < s.want) {
                s.queued = true;
                ready.addLast(s);
            }
        }
        totalWant = total;
    }

    /* Under the lock: s waits in a doorbell; first one pop to learn where it stands. */
    private void joinBell(Sub s) {
        Bell b = bells.isEmpty() ? null : bells.get(bells.size() - 1);
        if (b == null || b.members.size() >= Math.max(1, Math.min(1024, cfg.doorbellEntries))) {
            b = new Bell(s.base);
            bells.add(b);
        }
        b.members.add(s);
        s.bell = b;
        s.dirty = true;
        s.queryNoWait =
                s.base
                        + "/api/v1/pop/queue/"
                        + enc(s.queue)
                        + "?wait=false&consumerGroup="
                        + enc(s.group)
                        + popTail;
    }

    private final List<Bell> bells = new ArrayList<>();
    private static final byte[] KEY_HWM = Wire.ascii("\"highWatermark\":");
    private static final byte[] KEY_ERROR_FIELD = Wire.ascii("\"error\":");

    private static boolean contains(byte[] a, int len, byte[] key) {
        return occurrences(a, len, key) > 0;
    }

    /**
     * One long-poll POST /api/v1/fetch over up to 1024 single-partition subscriptions at the offsets
     * they have not seen: it answers when one of them has new data (or after doorbellWaitMs), with
     * every entry's highWatermark (a top-level maxBytes of 1 reads at most one record). Each entry
     * past what its subscription has seen gets a pop; the others wait in the next fetch.
     */
    final class Bell {
        final ArrayList<Sub> members = new ArrayList<>();
        final Uri uri;
        boolean inFlight;
        boolean scheduled;
        int backoffMs;

        Bell(String base) {
            this.uri = Uri.create(base + "/api/v1/fetch");
        }

        void kick(int delayMs) {
            synchronized (lock) {
                if (inFlight || scheduled || closed) {
                    return;
                }
                scheduled = true;
            }
            try {
                ctx.timer.schedule(this::issue, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // closing
            }
        }

        private void issue() {
            List<Sub> sent = new ArrayList<>();
            List<long[]> at = new ArrayList<>();
            synchronized (lock) {
                scheduled = false;
                if (closed || inFlight) {
                    return;
                }
                for (Sub s : members) {
                    if (!s.dirty && !s.closed && s.bell == this) {
                        sent.add(s);
                        at.add(new long[] {s.hint});
                    }
                }
                if (sent.isEmpty()) {
                    return;
                }
                inFlight = true;
            }
            Wire.Buf b = new Wire.Buf(64 + sent.size() * 96);
            b.put(Wire.ascii("{\"entries\":["));
            for (int i = 0; i < sent.size(); i++) {
                if (i > 0) {
                    b.put((byte) ',');
                }
                b.put(Wire.ascii("{\"queue\":\""));
                b.putJsonChars(sent.get(i).queue);
                b.put(Wire.ascii("\",\"partition\":\"0\",\"offset\":"));
                b.ensure(20);
                b.n = Wire.putDecimal(b.a, b.n, at.get(i)[0]);
                b.put(Wire.ascii(",\"maxBytes\":1}"));
            }
            b.put(
                    Wire.ascii(
                            "],\"maxWaitMs\":"
                                    + Math.max(0, cfg.doorbellWaitMs)
                                    + ",\"minBytes\":1,\"maxBytes\":1}"));
            Request req;
            try {
                req =
                        ctx.http.post(
                                uri, b.a, b.n, "application/json", cfg.doorbellWaitMs + cfg.requestTimeoutMs);
            } catch (RuntimeException e) {
                answered(sent, null, e);
                return;
            }
            ctx.stats.doorbells.increment();
            ctx.http.execute(req, new QueenHttp.Body(null, (r, err) -> answered(sent, r, err)));
        }

        private void answered(List<Sub> sent, QueenHttp.Body r, Throwable err) {
            long[] h = null;
            String why = null;
            if (err != null) {
                why = err.toString();
            } else if (r.status != 200) {
                why = "HTTP " + r.status + ": " + Wire.excerpt(r.buf, r.len);
            } else {
                h = highWatermarks(r.buf, r.len, sent.size());
                if (h == null) {
                    why = "answer not read: " + Wire.excerpt(r.buf, r.len);
                }
            }
            boolean any = false;
            synchronized (lock) {
                inFlight = false;
                if (h != null) {
                    backoffMs = 0;
                    for (int i = 0; i < sent.size(); i++) {
                        Sub s = sent.get(i);
                        if (h[i] < 0) {
                            // This entry errored (no such queue or offset): the subscription leaves
                            // the doorbell and rotates like any other.
                            members.remove(s);
                            s.bell = null;
                            s.dirty = false;
                            s.want = 1;
                            totalWant += 1;
                            if (!s.queued && !s.closed && s.active < s.want) {
                                s.queued = true;
                                ready.addLast(s);
                            }
                            any = true;
                            continue;
                        }
                        if (h[i] > s.hwm) {
                            s.hwm = h[i];
                        }
                        if (s.hwm > s.hint && !s.dirty && !s.closed) {
                            s.dirty = true;
                            if (!s.queued && s.active < s.want) {
                                s.queued = true;
                                ready.addLast(s);
                            }
                            any = true;
                        }
                    }
                } else {
                    backoffMs = backoffMs == 0 ? 50 : Math.min(backoffMs * 2, 1000);
                }
            }
            if (why != null) {
                ctx.stats.doorbellErrors.increment();
                QueenBenchmarkDriver.warn("doorbell", "doorbell fetch failed: " + why, null);
            }
            if (any) {
                ctx.stats.doorbellRings.increment();
                pump();
            }
            kick(h == null ? backoffMs : any ? Math.max(0, cfg.doorbellLingerMs) : 0);
        }
    }

    /*
     * The highWatermark of every entry of a fetch answer, in order; -1 for an entry that errored;
     * null when the answer cannot be read. Payloads here are OMB's documents (base64 or hex text,
     * never a quote), so the key can only be the answer's own.
     */
    private static long[] highWatermarks(byte[] a, int len, int n) {
        long[] out = new long[n];
        int found = 0;
        outer:
        for (int i = 0; i + KEY_HWM.length <= len && found <= n; i++) {
            for (int k = 0; k < KEY_HWM.length; k++) {
                if (a[i + k] != KEY_HWM[k]) {
                    continue outer;
                }
            }
            if (found == n) {
                return null;
            }
            out[found++] = Wire.jsonLong(a, i, Math.min(32, len - i), KEY_HWM);
            i += KEY_HWM.length;
        }
        if (found == n
                && Wire.jsonLong(a, len, KEY_ERROR_FIELD) == -1
                && !contains(a, len, KEY_ERROR_FIELD)) {
            return out;
        }
        // An entry carries an error (every entry has a highWatermark): read the answer properly.
        try {
            JsonNode root = JSON.readTree(a, 0, len);
            JsonNode es = root.path("entries");
            if (!es.isArray() || es.size() != n) {
                return null;
            }
            for (int i = 0; i < n; i++) {
                JsonNode e = es.get(i);
                out[i] = e.hasNonNull("error") ? -1 : e.path("highWatermark").asLong(0);
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    /* A consumer is closed: its subscription stops popping when it was the last one here. */
    void unregister(QueenBenchmarkConsumer c) {
        synchronized (lock) {
            Sub s = c.sub;
            s.consumers.remove(c);
            if (s.consumers.isEmpty() && !s.closed) {
                s.closed = true;
                subs.remove(s.queue + '\u0000' + s.subscription);
                if (s.bell == null) {
                    totalWant -= s.want; // a doorbell's subscriptions are not in it
                }
            }
        }
    }

    /** Starts pops while there are free slots and subscriptions that want one. */
    private void pump() {
        while (true) {
            Sub s;
            int timeout;
            synchronized (lock) {
                if (closed || running >= maxPops) {
                    return;
                }
                s = ready.pollFirst();
                if (s == null) {
                    return;
                }
                s.queued = false;
                if (s.closed || s.active >= s.want) {
                    continue;
                }
                s.active++;
                running++;
                if (s.active < s.want) {
                    s.queued = true;
                    ready.addLast(s);
                }
                timeout =
                        totalWant > maxPops ? Math.max(1, cfg.rotationWaitMs) : Math.max(1, cfg.popTimeoutMs);
                if (running > peakRunning.get()) {
                    peakRunning.set(running);
                }
            }
            startPop(s, s.bell != null ? 0 : timeout);
        }
    }

    private void startPop(Sub s, int timeout) {
        byte[] buf = buffers.poll();
        if (buf != null) {
            pooledBytes.addAndGet(-buf.length);
        } else {
            buf = new byte[16 * 1024];
        }
        Request req;
        try {
            String url =
                    s.bell != null && s.queryNoWait != null ? s.queryNoWait : s.query + Math.max(1, timeout);
            req = ctx.http.get(Uri.create(url), timeout + cfg.requestTimeoutMs);
        } catch (RuntimeException e) {
            ctx.stats.popErrors.increment();
            QueenBenchmarkDriver.warn("pop-build", "pop " + s.queue + ": request not built: " + e, e);
            finished(s, buf, true);
            return;
        }
        ctx.stats.pops.increment();
        ctx.http.execute(
                req,
                new QueenHttp.Body(
                        buf,
                        (r, err) -> {
                            // The lease is kept alive from the answer on, before the answer waits
                            // for a delivery thread (they may all be held by a paused OMB).
                            if (err == null && r.status == 200 && cfg.renewLeases) {
                                String lease = peekLease(r.buf, r.len);
                                if (lease != null) {
                                    track(lease, s.base);
                                }
                            }
                            try {
                                delivery.execute(() -> answered(s, r, err));
                            } catch (RejectedExecutionException e) {
                                finished(s, r.buf, false);
                            }
                        }));
    }

    /* On a delivery thread: one pop's answer, handed to OMB and acked. */
    private void answered(Sub s, QueenHttp.Body r, Throwable err) {
        boolean failed = false;
        boolean handed = false;
        Decoder d = null;
        try {
            if (err != null) {
                if (!closed) {
                    failed = true;
                    ctx.stats.popErrors.increment();
                    QueenBenchmarkDriver.warn("pop", "pop " + s.queue + " failed: " + err, null);
                }
            } else if (r.status == 204 || r.len == 0) {
                ctx.stats.emptyPops.increment();
                if (s.bell != null) {
                    synchronized (lock) {
                        s.hint = Math.max(s.hint, s.hwm);
                    }
                }
            } else if (r.status != 200) {
                failed = true;
                ctx.stats.popErrors.increment();
                QueenBenchmarkDriver.warn(
                        "pop",
                        "pop " + s.queue + ": HTTP " + r.status + ": " + Wire.excerpt(r.buf, r.len),
                        null);
            } else {
                QueenBenchmarkConsumer c;
                synchronized (lock) {
                    c =
                            s.consumers.isEmpty()
                                    ? null
                                    : s.consumers.get(Math.floorMod(s.rr++, s.consumers.size()));
                }
                if (c == null) {
                    // Closed meanwhile: not handed over, not acked (the lease runs out).
                    ctx.stats.popsAfterClose.increment();
                } else {
                    d = DECODER.get();
                    d.items = 0;
                    d.ack.n = 0;
                    d.lease = null;
                    d.maxEnd = -1;
                    int n;
                    if (isFramed(r.buf, r.len)) {
                        n = d.decodeFrames(s, c, r.buf, r.len);
                    } else {
                        ctx.stats.jsonAnswers.increment();
                        n = d.decodeJson(s, c, r.buf, r.len);
                    }
                    if (n > 0) {
                        ctx.stats.messagesDelivered.add(n);
                        s.acker.add(d.ack.a, d.ack.n, n, d.items, d.lease, s.base);
                        handed = true;
                    } else {
                        ctx.stats.emptyPops.increment();
                    }
                    if (s.bell != null) {
                        synchronized (lock) {
                            if (d.maxEnd >= 0) {
                                s.hint = Math.max(s.hint, d.maxEnd + 1);
                            } else {
                                s.hint = Math.max(s.hint, s.hwm); // someone else has it: wait for newer
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            failed = true;
            ctx.stats.popErrors.increment();
            QueenBenchmarkDriver.warn("pop-decode", "pop " + s.queue + ": answer not readable: " + t, t);
        } finally {
            if (!handed && err == null && r.status == 200) {
                // Nothing acked (closed, empty, unreadable): stop extending; the batch comes back
                // when its lease runs out.
                String lease = d != null && d.lease != null ? d.lease : peekLease(r.buf, r.len);
                if (lease != null) {
                    leases.remove(lease);
                }
            }
            finished(s, r.buf, failed);
        }
    }

    /*
     * A pop is over: its slot and buffer come back, its subscription goes to the back of the line.
     */
    private void finished(Sub s, byte[] buf, boolean failed) {
        // Idle answer buffers are kept for the next pops, at most 256 MiB of them.
        if (buf != null && buf.length <= (8 << 20) && pooledBytes.get() + buf.length <= (256L << 20)) {
            pooledBytes.addAndGet(buf.length);
            buffers.offer(buf);
        }
        int delayMs = 0;
        Bell bell = null;
        synchronized (lock) {
            running--;
            s.active--;
            if (failed) {
                s.backoffMs = s.backoffMs == 0 ? 5 : Math.min(s.backoffMs * 2, 1000);
                delayMs = s.backoffMs;
            } else {
                s.backoffMs = 0;
                if (s.bell != null && s.hint >= s.hwm) {
                    s.dirty = false;
                    bell = s.bell;
                } else if (!s.closed && !s.queued && s.active < s.want) {
                    s.queued = true;
                    ready.addLast(s);
                }
            }
        }
        if (bell != null) {
            bell.kick(0);
        }
        if (delayMs > 0) {
            try {
                ctx.timer.schedule(() -> requeue(s), delayMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // closing
            }
        }
        pump();
    }

    private void requeue(Sub s) {
        synchronized (lock) {
            if (!s.closed && !s.queued && s.active < s.want) {
                s.queued = true;
                ready.addLast(s);
            }
        }
        pump();
    }

    // ------------------------------------------------------------------ leases

    private static final class Lease {
        final String id;
        final String base;
        volatile long renewedAt = System.nanoTime();

        Lease(String id, String base) {
            this.id = id;
            this.base = base;
        }
    }

    /** Every second: extends each lease held for a third of its time without an answered ack. */
    private void renewLeases() {
        if (leases.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        long after = TimeUnit.SECONDS.toNanos(leaseSeconds) / 3;
        byte[] body = Wire.utf8("{\"seconds\":" + leaseSeconds + "}");
        for (Lease l : leases.values()) {
            if (now - l.renewedAt < after) {
                continue;
            }
            l.renewedAt = now;
            Request req;
            try {
                req =
                        ctx.http.post(
                                Uri.create(l.base + "/api/v1/lease/" + enc(l.id) + "/extend"),
                                body,
                                body.length,
                                "application/json",
                                cfg.requestTimeoutMs);
            } catch (RuntimeException e) {
                ctx.stats.renewFailures.increment();
                continue;
            }
            ctx.stats.renewals.increment();
            ctx.http.execute(
                    req,
                    new QueenHttp.Body(
                            null,
                            (r, err) -> {
                                if (err != null || r.status < 200 || r.status > 299) {
                                    ctx.stats.renewFailures.increment();
                                    QueenBenchmarkDriver.warn(
                                            "renew",
                                            "lease "
                                                    + l.id
                                                    + " not extended: "
                                                    + (err != null
                                                            ? err.toString()
                                                            : "HTTP " + r.status + ": " + Wire.excerpt(r.buf, r.len)),
                                            null);
                                }
                            }));
        }
    }

    // ------------------------------------------------------------------ acks

    /** One pop's ack items, kept together. */
    private static final class Chunk {
        final byte[] items;
        /** Messages this pop's items settle. */
        final int n;
        /** Ack items (one per message, or one per claim with popBatch). */
        final int itemCount;

        final String lease;

        Chunk(byte[] items, int n, int itemCount, String lease) {
            this.items = items;
            this.n = n;
            this.itemCount = itemCount;
            this.lease = lease;
        }
    }

    /**
     * The acks of one consumer group on this worker: sent as soon as fewer than ackMaxInFlight are in
     * flight, else they wait and share the next request (at most ackBatchMax items, whole pops).
     */
    final class Acker {
        final String group;
        private final byte[] close;
        private final ArrayDeque<Chunk> pending = new ArrayDeque<>();
        private int inFlight;
        /* Set once, under the lock, by the first add; post reads it outside the lock. */
        private volatile Uri uri;

        Acker(String group) {
            this.group = group;
            Wire.Buf b = new Wire.Buf(64 + group.length());
            b.put(Wire.ascii("],\"consumerGroup\":\""));
            b.putJsonChars(group);
            b.put(Wire.ascii("\",\"answer\":\"counts\"}"));
            this.close = Arrays.copyOf(b.a, b.n);
        }

        void add(byte[] items, int len, int n, int itemCount, String lease, String base) {
            List<Chunk> go;
            synchronized (this) {
                if (uri == null) {
                    uri = Uri.create(base + "/api/v1/ack/batch");
                }
                pending.addLast(new Chunk(Arrays.copyOf(items, len), n, itemCount, lease));
                go = takeLocked();
            }
            if (go != null) {
                post(go);
            }
        }

        private List<Chunk> takeLocked() {
            if (inFlight >= Math.max(1, cfg.ackMaxInFlight) || pending.isEmpty()) {
                return null;
            }
            List<Chunk> go = new ArrayList<>();
            int n = 0;
            while (!pending.isEmpty()
                    && (n == 0 || n + pending.peekFirst().itemCount <= Math.max(1, cfg.ackBatchMax))) {
                Chunk c = pending.pollFirst();
                go.add(c);
                n += c.itemCount;
            }
            inFlight++;
            ackersInFlight.incrementAndGet();
            return go;
        }

        private void post(List<Chunk> go) {
            int size = ACK_OPEN.length + close.length;
            int n = 0;
            for (Chunk c : go) {
                size += c.items.length + 1;
                n += c.n;
            }
            byte[] body = new byte[size];
            int p = 0;
            System.arraycopy(ACK_OPEN, 0, body, p, ACK_OPEN.length);
            p += ACK_OPEN.length;
            for (int i = 0; i < go.size(); i++) {
                if (i > 0) {
                    body[p++] = ',';
                }
                byte[] it = go.get(i).items;
                System.arraycopy(it, 0, body, p, it.length);
                p += it.length;
            }
            System.arraycopy(close, 0, body, p, close.length);
            p += close.length;
            final int items = n;
            Request req;
            try {
                req = ctx.http.post(uri, body, p, "application/json", cfg.requestTimeoutMs);
            } catch (RuntimeException e) {
                done(go, items, null, e);
                return;
            }
            ctx.stats.acks.increment();
            ctx.http.execute(req, new QueenHttp.Body(null, (r, err) -> done(go, items, r, err)));
        }

        private void done(List<Chunk> go, int n, QueenHttp.Body r, Throwable err) {
            try {
                if (err != null) {
                    ctx.stats.ackFailures.increment();
                    QueenBenchmarkDriver.warn(
                            "ack", "ack of " + n + " messages (group " + group + ") failed: " + err, null);
                } else if (r.status < 200 || r.status > 299) {
                    ctx.stats.ackFailures.increment();
                    QueenBenchmarkDriver.warn(
                            "ack",
                            "ack of "
                                    + n
                                    + " messages (group "
                                    + group
                                    + "): HTTP "
                                    + r.status
                                    + ": "
                                    + Wire.excerpt(r.buf, r.len),
                            null);
                } else {
                    long refused = Wire.jsonLong(r.buf, r.len, KEY_FAILED);
                    if (refused < 0) {
                        refused = occurrences(r.buf, r.len, KEY_REFUSED_ITEM);
                    }
                    if (refused > 0) {
                        ctx.stats.acksRefused.add(refused);
                        QueenBenchmarkDriver.warn(
                                "ack-refused",
                                refused
                                        + " of "
                                        + n
                                        + " acks (group "
                                        + group
                                        + ") refused: "
                                        + Wire.excerpt(r.buf, r.len),
                                null);
                    }
                    ctx.stats.messagesAcked.add(n - Math.min(n, Math.max(0, refused)));
                }
            } finally {
                // Answered or not, the lease is left alone from here: a failed ack's batch comes back
                // when it runs out, as at-least-once says.
                for (Chunk c : go) {
                    if (c.lease != null) {
                        leases.remove(c.lease);
                    }
                }
                List<Chunk> next;
                synchronized (this) {
                    inFlight--;
                    ackersInFlight.decrementAndGet();
                    next = takeLocked();
                }
                if (next != null) {
                    post(next);
                }
            }
        }

        synchronized boolean idle() {
            return inFlight == 0 && pending.isEmpty();
        }
    }

    private static long occurrences(byte[] b, int len, byte[] key) {
        if (b == null) {
            return 0;
        }
        long n = 0;
        outer:
        for (int i = 0; i + key.length <= len; i++) {
            for (int k = 0; k < key.length; k++) {
                if (b[i + k] != key[k]) {
                    continue outer;
                }
            }
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ decoding

    private static boolean isFramed(byte[] a, int len) {
        return len > MAGIC.length
                && a[0] == MAGIC[0]
                && a[1] == MAGIC[1]
                && a[2] == MAGIC[2]
                && a[3] == MAGIC[3];
    }

    /** Per delivery thread: the decode buffers and the ack items being built. */
    private static final class Decoder {
        final ZstdDecompressor unzstd = new ZstdDecompressor();
        /** A zstd record, decompressed. */
        byte[] raw = new byte[256 * 1024];
        /** A base64 payload, decoded (OMB reads it before the next message). */
        byte[] bytes = new byte[4096];
        /** The end of every ack item of the claim at hand (partitionId, leaseId, status). */
        final Wire.Buf claimTail = new Wire.Buf(128);
        /** popBatch: the transactionId of the claim's last message so far, JSON-escaped. */
        final Wire.Buf lastTxn = new Wire.Buf(64);
        /** This pop's ack items, comma-separated. */
        final Wire.Buf ack = new Wire.Buf(64 * 1024);

        int items;
        String lease;
        /** The last offset of this pop's claims, -1 for none. */
        long maxEnd;

        /* Hands the framed answer's messages to OMB and adds one ack item per message. */
        int decodeFrames(QueenPopPool.Sub s, QueenBenchmarkConsumer c, byte[] a, int len) {
            int p = MAGIC.length;
            int version = a[p++] & 0xff;
            if (version == 1) {
                int n = Wire.u32(a, p);
                p += 4;
                noteAutopilot(s, a, p, n);
                p += n;
            } else if (version != 0) {
                throw new QueenException("framed pop answer: unknown version " + version);
            }
            p += 2 + Wire.u16(a, p); // queue
            int leaseLen = Wire.u16(a, p);
            int leaseAt = p + 2;
            p = leaseAt + leaseLen;
            p += 2 + Wire.u16(a, p); // consumer group
            int claims = Wire.u32(a, p);
            p += 4;
            if (claims > 0 && leaseLen > 0) {
                lease = new String(a, leaseAt, leaseLen, StandardCharsets.UTF_8);
                s.pool().track(lease, s.base);
            }
            int delivered = 0;
            for (int cl = 0; cl < claims; cl++) {
                long pid = Wire.u64(a, p);
                p += 8;
                p += 2 + Wire.u16(a, p); // partition name
                long start = Wire.u64(a, p);
                long end = Wire.u64(a, p + 8);
                if (end > maxEnd) {
                    maxEnd = end;
                }
                p += 16 + 4; // start, end, deliveryAttempt
                int records = Wire.u32(a, p);
                p += 4;
                Wire.Buf tail = claimTail;
                tail.n = 0;
                tail.put(ACK_PID);
                tail.ensure(20);
                tail.n = Wire.putDecimal(tail.a, tail.n, pid);
                tail.put(ACK_LEASE);
                tail.putJsonChars(a, leaseAt, leaseLen);
                tail.put(ACK_DONE);
                boolean lastOnly = s.pool().cfg.popBatch > 0;
                int claimDelivered = 0;
                lastTxn.n = 0;
                for (int r = 0; r < records; r++) {
                    long base = Wire.u64(a, p);
                    long createdMs = Wire.u64(a, p + 8) / 1000;
                    int count = Wire.u32(a, p + 16);
                    int codec = a[p + 20] & 0xff;
                    int recLen = Wire.u32(a, p + 21);
                    p += 25;
                    if (p + recLen > len) {
                        throw new QueenException("framed pop answer: truncated");
                    }
                    byte[] rb;
                    int q;
                    int qEnd;
                    if (codec == 0) {
                        rb = a;
                        q = p;
                        qEnd = p + recLen;
                    } else if (codec == 1) {
                        long size = ZstdDecompressor.getDecompressedSize(a, p, recLen);
                        if (size < 0 || size > Integer.MAX_VALUE - 64) {
                            throw new QueenException("framed pop answer: a zstd record without its size");
                        }
                        if (raw.length < size) {
                            raw =
                                    new byte[(int) Math.max(size, Math.min(raw.length * 2L, Integer.MAX_VALUE - 64))];
                        }
                        qEnd = unzstd.decompress(a, p, recLen, raw, 0, raw.length);
                        rb = raw;
                        q = 0;
                    } else {
                        throw new QueenException("framed pop answer: unknown codec " + codec);
                    }
                    p += recLen;
                    for (int j = 0; j < count; j++) {
                        int bodyLen = Wire.u32(rb, q);
                        int body = q + 4;
                        q = body + bodyLen;
                        if (bodyLen < 19 || q > qEnd) {
                            throw new QueenException("framed pop answer: a record is shorter than its frames");
                        }
                        int flags = rb[body] & 0xff;
                        if ((flags & 4) != 0) {
                            throw new QueenException("framed pop answer: an encrypted message");
                        }
                        int x = body + 1 + 16;
                        if ((flags & 1) != 0) {
                            x += 16;
                        }
                        int txnLen = Wire.u16(rb, x);
                        int txnAt = x + 2;
                        x = txnAt + txnLen;
                        if ((flags & 2) != 0) {
                            x += 2 + Wire.u16(rb, x);
                        }
                        if (x > q) {
                            throw new QueenException("framed pop answer: a malformed frame");
                        }
                        long off = base + j;
                        if (off < start || off > end) {
                            continue;
                        }
                        deliver(c, rb, x, q - x, createdMs);
                        if (s.verifier != null) {
                            s.verifier.saw(pid, off);
                        }
                        delivered++;
                        claimDelivered++;
                        if (lastOnly) {
                            // popBatch: the claim's last message settles it (kept as a copy: a
                            // zstd record's buffer is reused by the next record).
                            lastTxn.n = 0;
                            lastTxn.putJsonChars(rb, txnAt, txnLen);
                            continue;
                        }
                        // One ack item per message, as the Go client sends them. Only the
                        // last of a claim would settle it too, but the broker's autopilot
                        // reads the items per lease as the consumer's drain and then shrinks
                        // the batches (measured 10-16: batch 1000 -> 238, 3x slower).
                        if (items > 0) {
                            ack.put((byte) ',');
                        }
                        ack.put(ACK_TXN);
                        ack.putJsonChars(rb, txnAt, txnLen);
                        ack.put(tail.a, 0, tail.n);
                        items++;
                    }
                }
                if (lastOnly && claimDelivered > 0) {
                    if (items > 0) {
                        ack.put((byte) ',');
                    }
                    ack.put(ACK_TXN);
                    ack.put(lastTxn.a, 0, lastTxn.n);
                    ack.put(tail.a, 0, tail.n);
                    items++;
                }
            }
            return delivered;
        }

        private void noteAutopilot(QueenPopPool.Sub s, byte[] a, int off, int len) {
            long w = Wire.jsonLong(a, off, len, KEY_PARTITIONS);
            long b = Wire.jsonLong(a, off, len, KEY_BATCH);
            if (w >= 0 || b >= 0) {
                s.autopilot = "partitions " + w + ", batch " + b;
            }
        }

        /* The JSON pop answer (a broker that did not frame this one). */
        int decodeJson(QueenPopPool.Sub s, QueenBenchmarkConsumer c, byte[] a, int len)
                throws IOException {
            JsonNode root = JSON.readTree(a, 0, len);
            JsonNode ap = root.get("autopilot");
            if (ap != null && ap.isObject()) {
                s.autopilot =
                        "partitions "
                                + ap.path("partitions").asLong(-1)
                                + ", batch "
                                + ap.path("batch").asLong(-1);
            }
            String rootLease = root.path("leaseId").asText("");
            if (!rootLease.isEmpty() && root.path("messages").size() > 0) {
                lease = rootLease;
                s.pool().track(lease, s.base);
            }
            int delivered = 0;
            for (JsonNode m : root.path("messages")) {
                deliverJson(c, m.get("data"));
                String pid = m.path("partitionId").asText("");
                if (s.verifier != null && m.has("offset")) {
                    try {
                        s.verifier.saw(Long.parseLong(pid), m.get("offset").asLong());
                    } catch (NumberFormatException ignored) {
                        // not a numeric partition id: nothing to track
                    }
                }
                if (items > 0) {
                    ack.put((byte) ',');
                }
                items++;
                ack.put(ACK_TXN);
                ack.putJsonChars(m.path("transactionId").asText(""));
                ack.put(ACK_PID);
                ack.putJsonChars(pid);
                ack.put(ACK_LEASE);
                ack.putJsonChars(m.path("leaseId").asText(rootLease));
                ack.put(ACK_DONE);
                delivered++;
            }
            return delivered;
        }

        /*
         * Hands one message document b[off, off+len) to OMB with the producer's send time: the fast
         * path reads {"t":<digits>,"v":"..."} or {"t":<digits>,"b":"..."} in place; anything else is
         * parsed as JSON, and a document without "t" gets the time the broker stored it.
         */
        private void deliver(QueenBenchmarkConsumer c, byte[] b, int off, int len, long storedMs) {
            int end = off + len;
            if (len >= 13
                    && b[off] == '{'
                    && b[off + 1] == '"'
                    && b[off + 2] == 't'
                    && b[off + 3] == '"'
                    && b[off + 4] == ':') {
                int p = off + 5;
                long t = 0;
                int digits = 0;
                while (p < end && digits < 18) {
                    int ch = b[p] - '0';
                    if (ch < 0 || ch > 9) {
                        break;
                    }
                    t = t * 10 + ch;
                    p++;
                    digits++;
                }
                if (digits > 0
                        && end - p >= 8
                        && b[p] == ','
                        && b[p + 1] == '"'
                        && b[p + 3] == '"'
                        && b[p + 4] == ':'
                        && b[p + 5] == '"'
                        && b[end - 2] == '"'
                        && b[end - 1] == '}') {
                    int vs = p + 6;
                    int vl = end - 2 - vs;
                    if (b[p + 2] == 'v') {
                        c.callback.messageReceived(ByteBuffer.wrap(b, vs, vl), t);
                        return;
                    }
                    if (b[p + 2] == 'b') {
                        int n = Wire.base64Length(b, vs, vl);
                        if (n >= 0) {
                            if (bytes.length < n) {
                                bytes = new byte[Math.max(n, bytes.length * 2)];
                            }
                            if (Wire.base64Decode(b, vs, vl, bytes) == n) {
                                c.callback.messageReceived(ByteBuffer.wrap(bytes, 0, n), t);
                                return;
                            }
                        }
                    }
                }
            }
            JsonNode doc = null;
            try {
                doc = JSON.readTree(b, off, len);
            } catch (IOException e) {
                // not JSON we can read: handed over as it is
            }
            if (!deliverDoc(c, doc, storedMs)) {
                c.callback.messageReceived(Arrays.copyOfRange(b, off, end), storedMs);
            }
        }

        /* A message of the JSON pop answer. */
        private void deliverJson(QueenBenchmarkConsumer c, JsonNode data) {
            long now = System.currentTimeMillis();
            if (!deliverDoc(c, data, now)) {
                byte[] raw = data == null ? new byte[0] : data.toString().getBytes(StandardCharsets.UTF_8);
                c.callback.messageReceived(raw, now);
            }
        }

        private boolean deliverDoc(QueenBenchmarkConsumer c, JsonNode doc, long fallbackMs) {
            if (doc == null || !doc.isObject()) {
                return false;
            }
            JsonNode t = doc.get("t");
            long ts = t != null && t.canConvertToLong() ? t.asLong() : fallbackMs;
            JsonNode v = doc.get("v");
            if (v != null && v.isTextual()) {
                c.callback.messageReceived(v.asText().getBytes(StandardCharsets.UTF_8), ts);
                return true;
            }
            JsonNode b64 = doc.get("b");
            if (b64 != null && b64.isTextual()) {
                try {
                    c.callback.messageReceived(Base64.getDecoder().decode(b64.asText()), ts);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            return false;
        }
    }

    /* A lease to keep alive until the ack of its batch is answered (first sight counts). */
    void track(String lease, String base) {
        if (cfg.renewLeases) {
            leases.putIfAbsent(lease, new Lease(lease, base));
        }
    }

    /* The leaseId of a framed answer that holds claims, read from its head only; else null. */
    static String peekLease(byte[] a, int len) {
        try {
            if (!isFramed(a, len)) {
                return null;
            }
            int p = MAGIC.length;
            int version = a[p++] & 0xff;
            if (version == 1) {
                p += 4 + Wire.u32(a, p);
            } else if (version != 0) {
                return null;
            }
            p += 2 + Wire.u16(a, p); // queue
            int leaseLen = Wire.u16(a, p);
            int leaseAt = p + 2;
            p = leaseAt + leaseLen;
            p += 2 + Wire.u16(a, p); // consumer group
            if (p + 4 > len || leaseLen == 0 || Wire.u32(a, p) == 0) {
                return null;
            }
            return new String(a, leaseAt, leaseLen, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return null; // a short answer: the decode reports it
        }
    }

    // ------------------------------------------------------------------ close and report

    /** Stops popping, lets the pops and acks in flight land, then reports. */
    void close() throws InterruptedException {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            ready.clear();
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cfg.popTimeoutMs + 5_000L);
        while (System.nanoTime() < deadline) {
            synchronized (lock) {
                if (running == 0) {
                    break;
                }
            }
            Thread.sleep(20);
        }
        long ackDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < ackDeadline && ackersInFlight.get() > 0) {
            Thread.sleep(20);
        }
        if (renewer != null) {
            renewer.cancel(false);
        }
        delivery.shutdown();
        delivery.awaitTermination(5, TimeUnit.SECONDS);
    }

    /*
     * verify: the totals over every subscription this pool served, and the first few with a problem.
     */
    String verifyReport() {
        long parts = 0;
        long distinct = 0;
        long dups = 0;
        long holes = 0;
        int bad = 0;
        StringBuilder worst = new StringBuilder();
        List<Sub> copy;
        synchronized (allSubs) {
            copy = new ArrayList<>(allSubs);
        }
        for (Sub s : copy) {
            if (s.verifier == null) {
                continue;
            }
            long[] t = s.verifier.totals();
            parts += t[0];
            distinct += t[1];
            dups += t[2];
            holes += t[3];
            if ((t[2] > 0 || t[3] > 0) && bad++ < 5) {
                worst
                        .append("; ")
                        .append(s.queue)
                        .append(" / ")
                        .append(s.group)
                        .append(": ")
                        .append(s.verifier.report());
            }
        }
        return String.format(
                "%d subscriptions, %d partitions, %d distinct messages, %d delivered again, %d holes between"
                        + " the first and the last offset delivered; %d subscriptions with duplicates or holes%s",
                copy.size(), parts, distinct, dups, holes, bad, worst);
    }

    Map<String, Object> shape() {
        Map<String, Object> m = new LinkedHashMap<>();
        synchronized (lock) {
            m.put("subscriptions", subs.size());
            m.put("totalWant", totalWant);
            m.put("maxPops", maxPops);
            m.put("share", share);
            m.put("rotating", totalWant > maxPops);
        }
        // The pop sizing the broker's autopilot last reported to the first subscription (closed ones
        // included: OMB closes the consumers before the driver).
        synchronized (allSubs) {
            if (!allSubs.isEmpty()) {
                m.put("autopilot", allSubs.get(0).autopilot);
            }
        }
        m.put("peakPopsInFlight", peakRunning.get());
        synchronized (lock) {
            int members = 0;
            for (Bell b : bells) {
                members += b.members.size();
            }
            m.put("doorbells", bells.size());
            m.put("subscriptionsInDoorbells", members);
        }
        m.put("consumerGroups", ackers.size());
        return m;
    }

    /** Every subscription this pool served (for verify), including closed ones. */
    final List<Sub> allSubs = java.util.Collections.synchronizedList(new ArrayList<>());
}
