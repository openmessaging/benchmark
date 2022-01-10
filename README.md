# The OpenMessaging Benchmark Framework

**Notice：** We may release test results based on our fork this standard. For reference, you can purchase your benchmark tests on the cloud by yourself. 

This repository houses user-friendly, cloud-ready benchmarking suites for the following messaging platforms:

* [Apache Kafka](https://kafka.apache.org)
* [Apache RocketMQ](https://rocketmq.apache.org)
* [RabbitMQ](https://www.rabbitmq.com/)
* [Apache Pulsar](https://pulsar.apache.org)
* [NATS Streaming](https://nats.io/)
* [Redis](https://redis.com/)
* [Pravega](https://pravega.io/)

## DataStax Starlight for X

* JMS
* Apache Kafka

> More details could be found at the [official documentation](http://openmessaging.cloud/docs/benchmarks/).

Both the benchmark program and the workloads are enhanced from [OpenMessaging Benchmark](https://github.com/openmessaging/benchmark/)

## Benchmark Options

```
$ sudo bin/benchmark \
  --drivers driver-kafka/kafka-exactly-once.yaml \
  --workers 1.2.3.4:8080,4.5.6.7:8080 \ # or -w 1.2.3.4:8080,4.5.6.7:8080
  workloads/1-topic-16-partitions-1kb.yaml
```

|  Options  | Description |
| ---- | ---- |
|  -d, --drivers | Drivers list. eg.: pulsar/pulsar.yaml, kafka/kafka.yaml. The workload is run for each driver provided. |
|  -cd, --consumer-driver | Path to an alternative driver file for consumption |
|  -w, --workers | List of worker nodes. eg: http://1.2.3.4:8080,http://4.5.6.7:8080 |
|  -wf, --workers-file | Path to a YAML file containing the list of workers addresses. The dafult is look for `./workers.yaml` |
|  -c, --csv |  Print results from this directory to a csv file |
|  -x, --extra |  Allocate extra consumer workers when your backlog builds. Essentially averages twice as many consumer workers as producers. |

### Examples

1. Produce with Pulsar driver and consume with Starlight for Kafka.

   ```
   $ sudo bin/benchmark \
     --drivers driver-pulsar/pulsar.yaml \
     --consumer-driver driver-kafka/kafka-s4k.yaml \
     workloads/1m-10-topics-3-partitions-100b.yaml
   ```

2. Benchmark using Starlight for JMS with twice as many consumer benchmark workers.

   ```
   $ sudo bin/benchmark \
     --drivers driver-jms/pulsar-jms.yaml \
     --extra workloads/1m-10-topics-3-partitions-100b.yaml 
   ```


## Workload parameters

The following parametes are available for a workload file:

| Data Type | Key | Description |
| ---- | ---- | ---- |
| String | name | The name of this workload |
| int | topics |  Number of topics to create in the test |
| int | partitionsPerTopic |  Number of partitions each topic will contain |
| KeyDistributorType | keyDistributor | KeyDistributorType.NO_KEY |
| int | messageSize | Thye size of each message in bytes |
| boolean | useRandomizedPayloads | Use reandom payloads |
| double | randomBytesRatio | Ratio of random payloads |
| int | randomizedPayloadPoolSize | Number of random payloads to randomly choose |
| String | payloadFile | File containing random payloads |
| int | subscriptionsPerTopic | subscriptions per topic |
| int | producersPerTopic | producers per topic |
| int | consumerPerSubscription | number of consumers per subscription |
| int | producerRate | If the producerRate = 0, the generator will slowly grow producerRate to find the maximum balanced rate.  This starts at 10000. Add an optional parameter to start the value closer to the balanced rate. The rates we are attempting may require an acceptable backlog on the order of 0.5 seconds of production. So, we also provide a way to set that value |
| int | producerStartRate | If prodcuerRate = 0 then start finding the rate using this value. Default is 10000. |
| int | acceptableBacklog | When draining the backlog it may not be possible to get to 0. Set the minim acceptable backlog. The default value is 10000. |
| long | consumerBacklogSizeGB | If the consumer backlog is > 0, the generator will accumulate messages until the requested amount of storage is retained and then it will start the consumers to drain it. The testDurationMinutes will be overruled to allow the test to complete when the consumer has drained all the backlog and it's on par with the producer |
| long | producerPauseBeforeDrain | If doing a backlog draining test and producerPauseBeforeDrain > 0 then once the desired backlog is achieved then pause production for the indicated number of seconds. During the pause you can take whatever action you wish to take on your cluster like restarting the brokers. |
|  int | testDurationMinutes | How long to run the benchmark test. |

### Examples

1. Produce at 1,000,000 message per second.

   ```
   name: 1m-10-topics-3-partitions-100b

   topics: 10
   partitionsPerTopic: 3
   messageSize: 100
   useRandomizedPayloads: true
   randomBytesRatio: 0.5
   randomizedPayloadPoolSize: 1000

   subscriptionsPerTopic: 1
   consumerPerSubscription: 1
   producersPerTopic: 1

   producerRate: 1000000

   consumerBacklogSizeGB: 0
   testDurationMinutes: 15
   ```

2. Build a 128GB backlog and then drain

   ```
   name: 1m-10-topics-3-partitions-100b-128g-backlog

   topics: 10
   partitionsPerTopic: 3
   messageSize: 100
   useRandomizedPayloads: true
   randomBytesRatio: 0.5
   randomizedPayloadPoolSize: 1000

   subscriptionsPerTopic: 1
   consumerPerSubscription: 1
   producersPerTopic: 1

   producerRate: 1000000

   consumerBacklogSizeGB: 128
   acceptableBacklog: 30000
   testDurationMinutes: 30
   ```
