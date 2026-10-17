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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The driver's YAML file (see queen.yaml). Every key is optional. */
public class QueenConfig {
    // Read by OMB itself.
    public String name = "Queen";
    public String driverClass;

    /** Broker base URLs, e.g. http://10.0.0.1:6632. Producers and pollers go round-robin. */
    public List<String> urls = new ArrayList<>(Arrays.asList("http://localhost:6632"));
    /** Optional bearer token, sent as "Authorization: Bearer <token>". */
    public String token;

    // ---- queue setup (POST /api/v1/configure)
    /** The queue's dedup window. Dedup stays on: never default this to 0. */
    public int dedupWindowSeconds = 60;
    /** Further queue options, passed to /api/v1/configure as they are (dedupWindowSeconds wins). */
    public Map<String, Object> queueOptions = new LinkedHashMap<>();
    /** Topic (queue) name prefix. */
    public String topicPrefix = "omb";
    /**
     * Partition count to assume for a topic this worker did not create, when the count the creating
     * worker wrote to Queen KV (namespace "omb", key = topic) cannot be read. 0 = fail.
     */
    public int partitionsPerTopic = 0;

    // ---- producer
    /**
     * How long a batch waits for more messages (Kafka linger.ms). 0 = send as soon as a slot is free.
     */
    public int lingerMs = 1;
    /** A batch is sent as soon as it holds this many bytes (Kafka batch.size). */
    public int batchMaxBytes = 1 << 20;
    /** Pushes in flight per producer (Kafka max.in.flight.requests.per.connection). */
    public int maxInFlight = 5;
    /**
     * Bytes a producer may hold (open, ready and in flight) before sendAsync blocks (Kafka
     * buffer.memory).
     */
    public long bufferMaxBytes = 64L << 20;
    /**
     * How long sendAsync blocks on a full buffer before it fails the message (Kafka max.block.ms).
     */
    public int maxBlockMs = 60_000;
    /**
     * none = binary push (QPB1, flag 2); zstd = client-framed groups, compressed here (flags 8|2).
     */
    public String compression = "none";
    /** Threads that compress batches (compression: zstd). 0 = half the cores. */
    public int compressionThreads = 0;

    /**
     * Shared accumulators ("lanes") the worker's producers append to; one push carries the messages
     * of every topic and partition its lane holds. 0 = one lane per producer, at most one per core.
     */
    public int producerLanes = 0;

    // ---- topic setup
    /**
     * configure / KV calls in flight while topics are created and producers look up partition counts.
     */
    public int setupConcurrency = 64;

    // ---- consumer
    /**
     * Pops in flight per consumer. A worker's consumers share one pool of pops (no thread per
     * consumer or per pop); a subscription gets pollers x its consumers on this worker, scaled down
     * when the pool cannot hold that for every subscription.
     */
    public int pollers = 2;
    /**
     * 0 (default): the broker's pop autopilot sizes each pop, and every message gets its ack item. N
     * > 0: every pop asks for batch=N (at most 10,000, the broker's cap) without the autopilot, and
     * each claim is acked with ONE item, its last message (that settles the earlier ones).
     */
    public int popBatch = 0;
    /** Pops in flight on this worker, all subscriptions together: each is one HTTP connection. */
    public int maxPops = 4096;
    /**
     * The long poll's wait when the pool holds fewer pops than this worker has subscriptions: the
     * pops then rotate over the subscriptions, each waiting at most this long on one before the next.
     */
    public int rotationWaitMs = 200;
    /**
     * auto: when this worker has more subscriptions than maxPops, a single-partition subscription is
     * not rotated through: it waits in a doorbell, a long-poll POST /api/v1/fetch over up to
     * doorbellEntries such partitions (no lease, no cursor: it only tells which have new data), and
     * gets a pop when its partition's highWatermark passes what it has seen. off: always rotate.
     */
    public String doorbell = "auto";
    /** The doorbell fetch's maxWaitMs. */
    public int doorbellWaitMs = 1000;
    /** How long a doorbell waits after an answer that found data before it asks again. */
    public int doorbellLingerMs = 5;
    /** Partitions per doorbell fetch (the broker takes at most 1024). */
    public int doorbellEntries = 1024;
    /** Threads that decode pop answers and hand the messages to OMB. 0 = one per core. */
    public int deliveryThreads = 0;
    /**
     * The pop's leaseSeconds. 0 = the queue's leaseTime (300 s on a queue made by configure, or
     * queueOptions.leaseTime).
     */
    public int leaseSeconds = 0;
    /**
     * Extend the lease of a batch that is not acked after a third of its lease (OMB pauses consumers
     * by blocking the callback, for as long as a backlog takes to build).
     */
    public boolean renewLeases = true;
    /** Ack items in one request. Acks of one consumer group on this worker share requests. */
    public int ackBatchMax = 8192;
    /** Long-poll wait (the pop's timeout parameter, milliseconds). */
    public int popTimeoutMs = 1000;
    /**
     * Ack requests in flight per consumer group on this worker; more items wait and share the next.
     */
    public int ackMaxInFlight = 16;
    /** Where a new consumer group starts: all (the whole queue) or new. */
    public String subscriptionMode = "all";
    /**
     * Count duplicate deliveries (same partition, same offset) and holes in the consumer and log them
     * when it closes. Costs a bit per message; meant for correctness runs.
     */
    public boolean verify = false;

    // ---- HTTP
    /** Netty event-loop threads of the HTTP client. 0 = the client's default (2 x cores). */
    public int ioThreads = 0;
    /** Timeout of a push or an ack (milliseconds). A pop gets popTimeoutMs on top. */
    public int requestTimeoutMs = 30_000;

    public int connectTimeoutMs = 10_000;
}
