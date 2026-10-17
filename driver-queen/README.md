# Queen MQ benchmarks

A driver for [Queen MQ](https://github.com/queen-mq/queen), a transactional message broker that keeps
its state in a raft-replicated log on local disk. The driver speaks Queen's own HTTP protocol: binary
push (`QPB1`), framed pop (`QPF1`) with the broker's pop autopilot, and batched asynchronous acks.
It needs Queen 2.4 or later.

Queen also has a Kafka-compatible facade, which the stock `driver-kafka` can benchmark unchanged
(point `bootstrap.servers` at the facade). This driver measures Queen through its native protocol.

## How OMB maps onto Queen

|        OMB        |                                                         Queen                                                          |
|-------------------|------------------------------------------------------------------------------------------------------------------------|
| Topic             | A queue, created with `POST /api/v1/configure` (the queue defaults plus `dedupWindowSeconds`)                          |
| Partition         | A partition named `"0"` .. `"P-1"`: Queen partitions are names, created by their first push                            |
| Subscription      | A consumer group (OMB's `sub-000-<random>` becomes `sub-000`: a Queen group is scoped by its queue)                    |
| Producer          | A handle on a shared accumulator ("lane"), like a Kafka producer's batches: `lingerMs`, `batchMaxBytes`, `maxInFlight` |
| Consumer          | A share of one pool of long-poll pops per worker: no thread or connection of its own                                   |
| Publish timestamp | Carried in the message, as a Kafka record carries its create time                                                      |

A distributed run creates its topics on the first worker only, so `createTopics` also stores the
partition count in Queen's key-value store (`omb/<prefix>`), where the other workers read it.

## Configuration

Three profiles, alike except where noted:

|           File           |                                                                   What it is for                                                                   |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `queen.yaml`             | The settings we benchmark with; most workloads.                                                                                                    |
| `queen-throughput.yaml`  | `popBatch: 10000`: far more throughput where a consumer works one or a few partitions (the max-rate workloads), at the cost of end-to-end latency. |
| `queen-many-topics.yaml` | `maxPops: 12000`, for the workloads with thousands of topics; start the brokers with `QUEEN_QLOG_SHARDS=16` (below, Many topics).                  |

`queen-local.yaml` is `queen.yaml` pointed at the Docker cluster below. All four pop with
`leaseSeconds: 30`: a pop whose answer is lost on the network holds its partitions for one lease, and
a configured queue's own lease is 300 s. Every key is optional; the table gives the driver's defaults.

|         Key          |          Default          |                                                                                                              Meaning                                                                                                               |
|----------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `urls`               | `[http://localhost:6632]` | The brokers. Producers and pops are spread over them; a follower forwards writes to the raft leader.                                                                                                                               |
| `token`              | none                      | Sent as `Authorization: Bearer <token>` when the broker has authentication on.                                                                                                                                                     |
| `dedupWindowSeconds` | `60`                      | The queues' dedup window. Leave it on: Queen deduplicates every push, and turning that off is not a configuration anyone runs in production.                                                                                       |
| `queueOptions`       | `{}`                      | Further queue options for `/api/v1/configure`, passed as they are.                                                                                                                                                                 |
| `topicPrefix`        | `omb`                     | Prefix of the topic names, plus 6 random characters per driver instance.                                                                                                                                                           |
| `partitionsPerTopic` | `0`                       | Fallback partition count for a producer that cannot read it from the key-value store.                                                                                                                                              |
| `setupConcurrency`   | `64`                      | Configure and key-value calls in flight while the topics are created and the producers look up partition counts.                                                                                                                   |
| `lingerMs`           | `1`                       | As Kafka's `linger.ms`.                                                                                                                                                                                                            |
| `batchMaxBytes`      | `1048576`                 | As Kafka's `batch.size`.                                                                                                                                                                                                           |
| `maxInFlight`        | `5`                       | Pushes in flight per lane.                                                                                                                                                                                                         |
| `bufferMaxBytes`     | `67108864`                | As Kafka's `buffer.memory`, per lane: `sendAsync` blocks above it...                                                                                                                                                               |
| `maxBlockMs`         | `60000`                   | ...for at most this long, then fails the message.                                                                                                                                                                                  |
| `producerLanes`      | `0`                       | Lanes the worker's producers share; `0` is one per producer, at most one per core.                                                                                                                                                 |
| `compression`        | `none`                    | `none` sends the payloads as they are; `zstd` compresses each partition's group on the client, and the broker stores it as it came.                                                                                                |
| `compressionThreads` | `0`                       | Threads that compress the batches when `compression` is `zstd`; `0` is half the cores.                                                                                                                                             |
| `pollers`            | `2`                       | Pops in flight per consumer, at most one per partition of its topic (a partition is leased to one pop at a time).                                                                                                                  |
| `popBatch`           | `0`                       | `0` lets the broker's pop autopilot size each pop, and acks every message. `N > 0` asks for `N` messages per pop (at most 10,000) and acks each claim with its last message.                                                       |
| `maxPops`            | `4096`                    | Pops in flight on the worker, every subscription together. Past it the pops rotate over the subscriptions.                                                                                                                         |
| `rotationWaitMs`     | `200`                     | When the pool holds fewer pops than the worker has subscriptions, the longest a pop waits on one subscription before it moves to the next.                                                                                         |
| `doorbell`           | `auto`                    | `auto`: past `maxPops`, a one-partition subscription is not rotated but waits in a doorbell, a long-poll `POST /api/v1/fetch` over up to `doorbellEntries` partitions that only reports which have new data. `off`: always rotate. |
| `doorbellWaitMs`     | `1000`                    | The doorbell fetch's `maxWaitMs`.                                                                                                                                                                                                  |
| `doorbellLingerMs`   | `5`                       | After a doorbell answer that found data, the wait before the next fetch.                                                                                                                                                           |
| `doorbellEntries`    | `1024`                    | Partitions per doorbell fetch (the broker takes at most 1,024).                                                                                                                                                                    |
| `deliveryThreads`    | `0`                       | Threads that decode the pop answers and hand the messages to OMB; `0` is one per core.                                                                                                                                             |
| `popTimeoutMs`       | `1000`                    | The long poll's timeout.                                                                                                                                                                                                           |
| `leaseSeconds`       | `0`                       | The pops' lease; `0` is the queue's own.                                                                                                                                                                                           |
| `renewLeases`        | `true`                    | Extend the lease of a batch not yet acked, so that a consumer OMB pauses (the backlog workloads) does not get its batches back as duplicates.                                                                                      |
| `ackMaxInFlight`     | `16`                      | Ack requests in flight per consumer group on the worker.                                                                                                                                                                           |
| `ackBatchMax`        | `8192`                    | Ack items in one request.                                                                                                                                                                                                          |
| `subscriptionMode`   | `all`                     | Where a new consumer group starts: `all` (the oldest message) or `new`.                                                                                                                                                            |
| `verify`             | `false`                   | Count, per partition, the messages delivered twice and the offsets missing, and log them at close.                                                                                                                                 |
| `ioThreads`          | `0`                       | The HTTP client's event-loop threads; `0` is its default, twice the cores.                                                                                                                                                         |
| `requestTimeoutMs`   | `30000`                   | The timeout of a push or an ack. A pop gets `popTimeoutMs` on top.                                                                                                                                                                 |
| `connectTimeoutMs`   | `10000`                   | The HTTP connect timeout.                                                                                                                                                                                                          |

## Running it

On one machine, against a three-node cluster in Docker:

```
QUEEN_RAFT_TOKEN=$(openssl rand -hex 16) docker compose -f driver-queen/deploy/docker/compose.yaml up -d
bin/benchmark --drivers driver-queen/queen-local.yaml workloads/simple-workload.yaml
```

On a cluster, deploy the brokers and the benchmark workers as for the other drivers (see
[deploy/README.md](deploy/README.md)), put the brokers in `urls`, and run the workloads with
`--drivers driver-queen/queen.yaml --workers ...` (or one of the other profiles).

## Notes

- **Payloads.** A Queen message is a JSON document, so a payload that is printable ASCII (OMB's
  payload files are hex text) travels as a string, and any other payload, including
  `useRandomizedPayloads`, as base64, about a third more bytes on the wire and on disk. Queen's
  broker compresses the log with zstd; `compression: zstd` moves that work to the client.
- **Many topics.** Each topic is a queue with a log of its own. For the workloads with thousands of
  topics, start the brokers with `QUEEN_QLOG_SHARDS=16` (on a new data directory), which maps every
  queue onto one of 16 shared logs, so a write fsyncs at most 16 files however many queues it
  touches. Each subscription keeps one pop waiting while they fit in `maxPops`; past it, a
  subscription is woken by a long-poll fetch that reports which partitions got data.
  `queen-many-topics.yaml` raises `maxPops` to 12,000.
- **One partition.** A partition is leased to one batch at a time, so one consumer of a one-partition
  topic gets one batch per pop, deliver and ack. `popBatch` sets how big those batches are;
  `queen-throughput.yaml` sets it to 10,000.
- **Durability.** A push is answered once a majority of the brokers have it on disk (fsync), and
  every push is checked against the dedup window. Neither can be turned off from the driver.

