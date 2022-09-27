# Tool

## `WorkloadGenerationTool`

Generates a set of `Workload` definition files from a `WorkloadSetTemplate` file.

### Example

Template:

```yaml
nameFormat: "${topics}-topics-${partitionsPerTopic}-partitions-${messageSize}b-${producersPerTopic}p-${consumerPerSubscription}c-${producerRate}"
topics: [1]
partitionsPerTopic: [1]
messageSize: [10000]
payloadFile: "payload/payload-100b.data"
subscriptionsPerTopic: [1]
consumerPerSubscription: [1, 2, 4, 8, 16, 32, 64]
producersPerTopic: [1, 2, 4, 8, 16, 32, 64]
producerRate: [50000]
consumerBacklogSizeGB: 0
testDurationMinutes: 15
```

Usage:

```
mkdir my-workloads
<java + cp> io.openmessaging.benchmark.tool.workload.WorkloadGenerationTool \
  --templateFile=template.yaml \
  --outputFolder=my-workloads
```

Output:

```
Starting benchmark with config: templateFile: "template.yaml"
outputFolder: "my-workloads"

Generated: 1-topics-1-partitions-10kb-1p-1c-50k
Generated: 1-topics-1-partitions-10kb-1p-2c-50k
Generated: 1-topics-1-partitions-10kb-1p-4c-50k
...
Generated: 1-topics-1-partitions-10kb-64p-64c-50k
Generated 49 workloads.
```

Example generated workload:

```yaml
name: "1-topics-1-partitions-10kb-64p-2c-50k"
topics: 1
partitionsPerTopic: 1
keyDistributor: "NO_KEY"
messageSize: 10000
useRandomizedPayloads: false
randomBytesRatio: 0.0
randomizedPayloadPoolSize: 0
payloadFile: "payload/payload-100b.data"
subscriptionsPerTopic: 1
producersPerTopic: 64
consumerPerSubscription: 2
producerRate: 50000
consumerBacklogSizeGB: 0
testDurationMinutes: 5
warmupDurationMinutes: 1
```

