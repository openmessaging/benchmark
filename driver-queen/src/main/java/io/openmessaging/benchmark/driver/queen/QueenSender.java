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


import io.airlift.compress.zstd.ZstdCompressor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.asynchttpclient.Request;
import org.asynchttpclient.uri.Uri;

/**
 * The worker's producers share a few accumulators ("lanes"): an OMB producer is a small handle on a
 * topic and a lane, and one push carries the messages of every topic and partition its lane holds
 * (binary push, one group per queue and partition). So 400,000 producers on 100,000 topics cost
 * 400,000 small objects and the pushes of a few lanes, not a timer and a push per producer.
 *
 * <p>A lane is Kafka's accumulator: messages go into the open batch; the batch becomes sendable
 * when it holds batchMaxBytes (closed at once) or lingerMs after its first message; a sendable
 * batch goes out while fewer than maxInFlight pushes of the lane are in flight, and until then
 * keeps taking messages (so a slow broker gets bigger batches). sendAsync blocks while the lane
 * holds bufferMaxBytes, for at most maxBlockMs. Without a key a producer writes one partition per
 * batch, the next batch the next partition (Kafka's sticky partitioner); with a key the partition
 * is a stable hash of it. A message's future completes when the push that carried it was answered:
 * successfully, or exceptionally on an HTTP or transport error (no retries).
 *
 * <p>Wire, compression none: {@code "QPB1" | u8 2 | u32 groups | per group: u16 len, queue | u16
 * len, partition | u32 n | n x (u32 len | payload)}, the broker minting the ids. Compression zstd:
 * {@code "QPB1" | u8 8|2 | u32 groups | per group: u16 len, queue | u16 len, partition | u32 n |
 * u32 z_len | z}, z one zstd frame (content size declared) of n frames {@code u32 body_len | u8 0 |
 * u8[16] uuidv7 | u16 36 | the id's text | payload}. Flag 2: the answer is only the counts.
 */
final class QueenSender {
    static final byte[] MAGIC = Wire.ascii("QPB1");
    static final byte FLAG_SHORT = 2;
    static final byte FLAG_FRAMES = 8;
    /** A client frame's body before its payload: flags, id, txn length, txn (the id's text). */
    private static final int FRAME_HEAD = 1 + 16 + 2 + 36;

    private static final byte[] KEY_COUNT = Wire.ascii("\"count\":");
    private static final byte[] KEY_ERROR = Wire.ascii("\"error\":");
    private static final ThreadLocal<ZstdCompressor> COMPRESSOR =
            ThreadLocal.withInitial(ZstdCompressor::new);

    /** A topic as the producers see it: the queue name and its partitions' names. */
    static final class Topic {
        final int id;
        final String name;
        final byte[] queue;
        final int partitions;
        private volatile byte[][] partNames;

        Topic(int id, String name, int partitions) {
            this.id = id;
            this.name = name;
            this.queue = Wire.utf8(name);
            this.partitions = partitions;
        }

        byte[] partName(int p) {
            byte[][] n = partNames;
            if (n == null) {
                synchronized (this) {
                    n = partNames;
                    if (n == null) {
                        n = new byte[partitions][];
                        for (int i = 0; i < partitions; i++) {
                            n[i] = Wire.ascii(Integer.toString(i));
                        }
                        partNames = n;
                    }
                }
            }
            return n[p];
        }

        int partitionOf(String key) {
            int h = key.hashCode();
            h ^= h >>> 16;
            return (h & 0x7fffffff) % partitions;
        }
    }

    private final QueenContext ctx;
    private final Object lanesLock = new Object();
    private Lane[] lanes = new Lane[0];
    private int nextLane;
    private boolean closed;

    QueenSender(QueenContext ctx) {
        this.ctx = ctx;
    }

    /* Makes sure there are at least n lanes (at most producerLanes, or one per core). */
    void ensureLanes(int producers) {
        int cap =
                ctx.cfg.producerLanes > 0
                        ? ctx.cfg.producerLanes
                        : Math.max(1, Runtime.getRuntime().availableProcessors());
        int want = Math.max(1, Math.min(cap, producers));
        synchronized (lanesLock) {
            if (lanes.length >= want) {
                return;
            }
            Lane[] more = Arrays.copyOf(lanes, want);
            for (int i = lanes.length; i < want; i++) {
                more[i] = new Lane(i, ctx.nextUrl());
            }
            lanes = more;
        }
    }

    /* The lane for the producer at this index of OMB's list (OMB's thread index = index % cores). */
    Lane lane(int index) {
        synchronized (lanesLock) {
            if (lanes.length == 0) {
                ensureLanes(1);
            }
            return lanes[Math.floorMod(index, lanes.length)];
        }
    }

    /* The next lane, round-robin: a producer created alone. */
    Lane nextLane() {
        synchronized (lanesLock) {
            if (lanes.length == 0) {
                ensureLanes(1);
            }
            return lanes[Math.floorMod(nextLane++, lanes.length)];
        }
    }

    int laneCount() {
        synchronized (lanesLock) {
            return lanes.length;
        }
    }

    void close() {
        Lane[] ls;
        synchronized (lanesLock) {
            if (closed) {
                return;
            }
            closed = true;
            ls = lanes;
        }
        for (Lane l : ls) {
            l.flush();
        }
        for (Lane l : ls) {
            l.close();
        }
    }

    /** One partition's messages in a batch, encoded as they will travel. */
    private static final class Group {
        final Topic topic;
        final int partition;
        /** Room left at the front of buf for the request and group heads (compression none). */
        final int headRoom;

        byte[] buf;
        int len;
        int n;

        Group(Topic topic, int partition, int headRoom, byte[] buf) {
            this.topic = topic;
            this.partition = partition;
            this.headRoom = headRoom;
            this.buf = buf;
            this.len = headRoom;
        }
    }

    private static final class Batch {
        final long id;
        final ArrayList<Group> groups = new ArrayList<>(1);
        HashMap<Long, Group> byKey;
        Group last;
        CompletableFuture<?>[] futures = new CompletableFuture<?>[64];
        int count;
        long bytes;
        boolean sendable;
        Batch nextSend;
        byte[] body;
        int bodyLen;

        Batch(long id) {
            this.id = id;
        }

        void add(CompletableFuture<?> f, int size) {
            if (count == futures.length) {
                futures = Arrays.copyOf(futures, count * 2);
            }
            futures[count++] = f;
            bytes += size;
        }
    }

    /** One accumulator: Kafka's producer batching, for all the topics of its producers. */
    final class Lane {
        final int index;
        private final Uri pushUri;
        private final boolean zstd;
        private final int lingerMs;
        private final int batchMaxBytes;
        private final int maxInFlight;
        private final long bufferMaxBytes;
        private final int maxBlockMs;

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private final ArrayDeque<Batch> ready = new ArrayDeque<>();
        private final ArrayDeque<byte[]> spare = new ArrayDeque<>();
        /** Payload array -> its document tail; OMB sends the same few arrays over and over. */
        private final IdentityHashMap<byte[], byte[]> tails = new IdentityHashMap<>();

        private Batch open;
        private int inFlight;
        private long buffered;
        private int waiters;
        private long batchIds;
        private int idSeq;
        private boolean closed;

        Lane(int index, String baseUrl) {
            this.index = index;
            this.pushUri = Uri.create(baseUrl + "/api/v1/push");
            QueenConfig cfg = ctx.cfg;
            this.zstd = ctx.zstd;
            this.lingerMs = Math.max(0, cfg.lingerMs);
            this.batchMaxBytes = Math.max(1, cfg.batchMaxBytes);
            this.maxInFlight = Math.max(1, cfg.maxInFlight);
            this.bufferMaxBytes = Math.max(cfg.bufferMaxBytes, 2L * batchMaxBytes);
            this.maxBlockMs = Math.max(0, cfg.maxBlockMs);
        }

        CompletableFuture<Void> send(QueenBenchmarkProducer p, Optional<String> key, byte[] payload) {
            final long t = System.currentTimeMillis();
            CompletableFuture<Void> f = new CompletableFuture<>();
            Batch go;
            lock.lock();
            try {
                if (buffered >= bufferMaxBytes && !closed && !awaitRoom()) {
                    ctx.stats.bufferFull.increment();
                    f.completeExceptionally(
                            new QueenException("producer buffer full for " + maxBlockMs + " ms"));
                    return f;
                }
                if (closed) {
                    f.completeExceptionally(new QueenException("producer closed"));
                    return f;
                }
                byte[] tail = tails.get(payload);
                if (tail == null) {
                    if (tails.size() >= 4096) {
                        tails.clear();
                    }
                    tail = Wire.payloadTail(payload);
                    tails.put(payload, tail);
                }
                if (open == null) {
                    open = newBatch();
                }
                Topic topic = p.topic;
                int part;
                if (key.isPresent()) {
                    part = topic.partitionOf(key.get());
                } else {
                    if (p.batchSeen != open.id) {
                        // Sticky: one partition per batch, the next batch the next partition.
                        p.sticky = p.sticky + 1 >= topic.partitions ? 0 : p.sticky + 1;
                        p.batchSeen = open.id;
                    }
                    part = p.sticky;
                }
                int added = append(group(open, topic, part), t, tail);
                open.add(f, added);
                buffered += added;
                if (open.bytes >= batchMaxBytes
                        || (open.sendable && ready.isEmpty() && inFlight < maxInFlight)) {
                    ready.addLast(open);
                    open = null;
                }
                go = takeSendable();
            } finally {
                lock.unlock();
            }
            sendAll(go);
            return f;
        }

        /* Under the lock: waits for room in the buffer; false when maxBlockMs passed first. */
        private boolean awaitRoom() {
            long nanos = TimeUnit.MILLISECONDS.toNanos(maxBlockMs);
            waiters++;
            try {
                while (buffered >= bufferMaxBytes && !closed) {
                    if (nanos <= 0) {
                        return false;
                    }
                    try {
                        nanos = changed.awaitNanos(nanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            } finally {
                waiters--;
            }
        }

        /* Under the lock: a new open batch with its linger timer. */
        private Batch newBatch() {
            Batch b = new Batch(++batchIds);
            if (lingerMs == 0) {
                b.sendable = true;
            } else {
                final long id = b.id;
                try {
                    ctx.timer.schedule(() -> lingerExpired(id), lingerMs, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException e) {
                    b.sendable = true; // the driver is closing
                }
            }
            return b;
        }

        private void lingerExpired(long id) {
            Batch go;
            lock.lock();
            try {
                if (open == null || open.id != id || open.count == 0) {
                    return;
                }
                open.sendable = true;
                if (ready.isEmpty() && inFlight < maxInFlight) {
                    ready.addLast(open);
                    open = null;
                }
                go = takeSendable();
            } finally {
                lock.unlock();
            }
            sendAll(go);
        }

        /* Under the lock: the batch's group for a topic's partition, created on first use. */
        private Group group(Batch b, Topic topic, int part) {
            Group g = b.last;
            if (g != null && g.partition == part && g.topic == topic) {
                return g;
            }
            if (b.groups.isEmpty()) {
                g = newGroup(topic, part, true);
                b.groups.add(g);
                b.last = g;
                return g;
            }
            if (b.byKey == null) {
                b.byKey = new HashMap<>();
                Group first = b.groups.get(0);
                b.byKey.put(key(first.topic, first.partition), first);
            }
            long k = key(topic, part);
            g = b.byKey.get(k);
            if (g == null) {
                g = newGroup(topic, part, false);
                b.byKey.put(k, g);
                b.groups.add(g);
            }
            b.last = g;
            return g;
        }

        private long key(Topic topic, int part) {
            return ((long) topic.id << 32) | (part & 0xffffffffL);
        }

        private Group newGroup(Topic topic, int part, boolean first) {
            int headRoom =
                    zstd || !first ? 0 : 9 + 2 + topic.queue.length + 2 + topic.partName(part).length + 4;
            byte[] buf = first ? spare.pollLast() : null;
            if (buf == null) {
                buf = new byte[first ? 64 * 1024 : 2048];
            }
            return new Group(topic, part, headRoom, buf);
        }

        /* Under the lock: appends one message to g; returns the bytes it took. */
        private int append(Group g, long t, byte[] tail) {
            int doc = Wire.T_OPEN.length + Wire.decimalLength(t) + tail.length;
            int need = 4 + (zstd ? FRAME_HEAD : 0) + doc;
            if (g.len + need > g.buf.length) {
                g.buf = Arrays.copyOf(g.buf, Math.max(g.buf.length * 2, g.len + need));
            }
            byte[] b = g.buf;
            int p = g.len;
            if (zstd) {
                Wire.putU32(b, p, FRAME_HEAD + doc);
                p += 4;
                b[p++] = 0;
                int id = p;
                Wire.putUuidV7(b, id, t, idSeq++, ThreadLocalRandom.current().nextLong());
                p += 16;
                Wire.putU16(b, p, 36);
                p += 2;
                Wire.putUuidText(b, id, b, p);
                p += 36;
            } else {
                Wire.putU32(b, p, doc);
                p += 4;
            }
            System.arraycopy(Wire.T_OPEN, 0, b, p, Wire.T_OPEN.length);
            p = Wire.putDecimal(b, p + Wire.T_OPEN.length, t);
            System.arraycopy(tail, 0, b, p, tail.length);
            g.len = p + tail.length;
            g.n++;
            return need;
        }

        /* Under the lock: takes ready batches while there are free slots; returns them chained. */
        private Batch takeSendable() {
            Batch head = null;
            Batch tail = null;
            while (inFlight < maxInFlight && !ready.isEmpty()) {
                Batch b = ready.pollFirst();
                inFlight++;
                b.nextSend = null;
                if (head == null) {
                    head = b;
                } else {
                    tail.nextSend = b;
                }
                tail = b;
            }
            return head;
        }

        private void sendAll(Batch chain) {
            while (chain != null) {
                Batch next = chain.nextSend;
                send(chain);
                chain = next;
            }
        }

        private void send(Batch b) {
            if (zstd) {
                try {
                    ctx.encoder.execute(() -> encodeAndPush(b));
                } catch (RejectedExecutionException e) {
                    finished(b, 0, null, 0, e);
                }
            } else {
                encodeAndPush(b);
            }
        }

        private void encodeAndPush(Batch b) {
            Request req;
            try {
                if (zstd) {
                    encodeFrames(b);
                } else {
                    encodePlain(b);
                }
                req =
                        ctx.http.post(
                                pushUri, b.body, b.bodyLen, "application/octet-stream", ctx.cfg.requestTimeoutMs);
            } catch (Throwable t) {
                finished(b, 0, null, 0, t);
                return;
            }
            ctx.stats.pushes.increment();
            ctx.stats.pushGroups.add(b.groups.size());
            ctx.http.execute(
                    req, new QueenHttp.Body(null, (r, err) -> finished(b, r.status, r.buf, r.len, err)));
        }

        private int requestHead(byte[] out, byte flags, int groups) {
            System.arraycopy(MAGIC, 0, out, 0, 4);
            out[4] = flags;
            Wire.putU32(out, 5, groups);
            return 9;
        }

        private int groupHead(byte[] out, int p, Group g) {
            byte[] queue = g.topic.queue;
            byte[] part = g.topic.partName(g.partition);
            Wire.putU16(out, p, queue.length);
            p += 2;
            System.arraycopy(queue, 0, out, p, queue.length);
            p += queue.length;
            Wire.putU16(out, p, part.length);
            p += 2;
            System.arraycopy(part, 0, out, p, part.length);
            p += part.length;
            Wire.putU32(out, p, g.n);
            return p + 4;
        }

        private int groupHeadLength(Group g) {
            return 2 + g.topic.queue.length + 2 + g.topic.partName(g.partition).length + 4;
        }

        private void encodePlain(Batch b) {
            ArrayList<Group> gs = b.groups;
            if (gs.size() == 1) {
                // In place: the heads go into the room left in front of the messages.
                Group g = gs.get(0);
                int p = requestHead(g.buf, FLAG_SHORT, 1);
                groupHead(g.buf, p, g);
                b.body = g.buf;
                b.bodyLen = g.len;
                return;
            }
            int total = 9;
            for (Group g : gs) {
                total += groupHeadLength(g) + (g.len - g.headRoom);
            }
            byte[] out = new byte[total];
            int p = requestHead(out, FLAG_SHORT, gs.size());
            for (Group g : gs) {
                p = groupHead(out, p, g);
                int n = g.len - g.headRoom;
                System.arraycopy(g.buf, g.headRoom, out, p, n);
                p += n;
            }
            b.body = out;
            b.bodyLen = p;
        }

        private void encodeFrames(Batch b) {
            ZstdCompressor z = COMPRESSOR.get();
            ArrayList<Group> gs = b.groups;
            int bound = 9;
            for (Group g : gs) {
                bound += groupHeadLength(g) + 4;
                bound += z.maxCompressedLength(g.len);
            }
            byte[] out = new byte[bound];
            int p = requestHead(out, (byte) (FLAG_FRAMES | FLAG_SHORT), gs.size());
            for (Group g : gs) {
                p = groupHead(out, p, g);
                int zl = z.compress(g.buf, 0, g.len, out, p + 4, out.length - p - 4);
                Wire.putU32(out, p, zl);
                p += 4 + zl;
            }
            b.body = out;
            b.bodyLen = p;
        }

        /*
         * A push was answered (or failed): frees its slot, sends what is next, completes the futures.
         */
        private void finished(Batch b, int status, byte[] body, int len, Throwable err) {
            int failed = 0;
            Throwable cause = null;
            boolean answered = err == null && status >= 200 && status <= 299;
            if (err != null) {
                failed = b.count;
                cause = err;
            } else if (!answered) {
                failed = b.count;
                cause = new QueenException("push: HTTP " + status + ": " + Wire.excerpt(body, len));
            } else {
                long count = Wire.jsonLong(body, len, KEY_COUNT);
                if (count != b.count) {
                    failed = b.count;
                    cause =
                            new QueenException(
                                    "push of " + b.count + " messages answered: " + Wire.excerpt(body, len));
                } else {
                    long refused = Wire.jsonLong(body, len, KEY_ERROR);
                    if (refused > 0) {
                        failed = (int) Math.min(refused, b.count);
                        cause =
                                new QueenException(
                                        "push: "
                                                + failed
                                                + " of "
                                                + b.count
                                                + " messages refused: "
                                                + Wire.excerpt(body, len));
                    }
                }
            }
            Batch go;
            lock.lock();
            try {
                inFlight--;
                buffered -= b.bytes;
                Group first = b.groups.get(0);
                if (answered && first.headRoom > 0 && spare.size() < maxInFlight + 2) {
                    spare.addLast(first.buf);
                }
                if (ready.isEmpty() && open != null && open.sendable && open.count > 0) {
                    ready.addLast(open);
                    open = null;
                }
                go = takeSendable();
                if (waiters > 0 || closed) {
                    changed.signalAll();
                }
            } finally {
                lock.unlock();
            }
            sendAll(go);

            QueenStats s = ctx.stats;
            s.messagesPushed.add(b.count - failed);
            if (failed > 0) {
                s.messagesPushFailed.add(failed);
                s.pushFailures.increment();
                QueenBenchmarkDriver.warn("push", cause.toString(), null);
            }
            CompletableFuture<?>[] fs = b.futures;
            for (int i = 0; i < b.count; i++) {
                if (i < failed) {
                    fs[i].completeExceptionally(cause);
                } else {
                    fs[i].complete(null);
                }
            }
        }

        /** Sends the open batch now. */
        void flush() {
            Batch go;
            lock.lock();
            try {
                if (open != null && open.count > 0) {
                    ready.addLast(open);
                    open = null;
                }
                go = takeSendable();
            } finally {
                lock.unlock();
            }
            sendAll(go);
        }

        /** Sends what is left, waits up to 10 s for the pushes in flight, fails the rest. */
        void close() {
            Batch go;
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                if (open != null && open.count > 0) {
                    ready.addLast(open);
                }
                open = null;
                go = takeSendable();
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            sendAll(go);
            ArrayList<Batch> left = new ArrayList<>();
            lock.lock();
            try {
                long nanos = TimeUnit.SECONDS.toNanos(10);
                while ((inFlight > 0 || !ready.isEmpty()) && nanos > 0) {
                    go = takeSendable();
                    if (go != null) {
                        lock.unlock();
                        try {
                            sendAll(go);
                        } finally {
                            lock.lock();
                        }
                        continue;
                    }
                    try {
                        nanos = changed.awaitNanos(nanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                left.addAll(ready);
                ready.clear();
            } finally {
                lock.unlock();
            }
            QueenException gone = new QueenException("producer closed before the push was sent");
            for (Batch b : left) {
                for (int i = 0; i < b.count; i++) {
                    b.futures[i].completeExceptionally(gone);
                }
            }
        }
    }
}
