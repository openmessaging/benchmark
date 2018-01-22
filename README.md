# The OpenMessaging Benchmark Framework

This repository houses user-friendly, cloud-ready benchmarking suites for the following messaging platforms:

* [Apache Kafka](https://kafka.apache.org)
* [Apache Pulsar (incubating)](https://pulsar.incubator.apache.org)

> A benchmarking suite for [RocketMQ](https://rocketmq.apache.org) and [RabbitMQ](https://www.rabbitmq.com) will be available soon.

<a id="documentation"></a>
For each platform, the benchmarking suite includes easy-to-use scripts for deploying that platform on [Amazon Web Services](https://aws.amazon.com) (AWS) and then running benchmarks upon deployment. For end-to-end instructions, see platform-specific docs for:

* [Kafka](driver-kafka/README.md)
* [RocketMQ](driver-rocketmq/README.md)
* [Pulsar](driver-pulsar/README.md)
* [RabbitMQ](driver-rabbitmq/README.md)

## Project goals

The goal of the OpenMessaging Benchmark Framework is to provide benchmarking suites for an ever-expanding variety of messaging platforms. These suites are intended to be:

* **Cloud friendly** &mdash; All benchmarks are run on cloud infrastructure, not on your laptop
* **Easy to use** &mdash; Just a few CLI commands get you from zero to completed benchmarks
* **Transparent** &mdash; All benchmarking code is open source, with pull requests very welcome
* **Realistic** &mdash; Benchmarks should be largely oriented toward standard use cases rather than bizarre edge cases

## Benchmarking workloads

Benchmarking workloads are specified in [YAML](http://yaml.org/) configuration files that are available in the [`workloads`](workloads) directory. The table below describes each workload in terms of the following parameters:

* The number of topics
* The size of the messages being produced and consumed
* The number of subscriptions per topic
* The number of producers per topic
* The rate at which producers produce messages (per second). **Note**: a value of 0 means that messages are produced as quickly as possible, with no rate limiting.
* The size of the consumer's backlog (in gigabytes)
* The total duration of the test (in minutes)

Workload | Topics | Partitions per topic | Message size | Subscriptions per topic | Producers per topic | Producer rate (per second) | Consumer backlog size (GB) | Test duration (minutes)
:--------|:-------|:---------------------|:-------------|:------------------------|:--------------------|:---------------------------|:---------------------------|:-----------------------
[`simple-workload.yaml`](workloads/simple-workload.yaml) | 1 | 10 | 1 kB | 1 | 1 | 10000 | 0 | 5
[`1-topic-1-partition-1kb.yaml`](workloads/1-topic-1-partition-1kb.yaml) | 1 | 1 | 1 kB | 1 | 1 | 50000 | 0 | 15
[`1-topic-1-partition-100b.yaml`](workloads/1-topic-1-partition-100b.yaml) | 1 | 1 | 100 bytes | 1 | 1 | 50000 | 0 | 15
[`1-topic-16-partitions-1kb.yaml`](workloads/1-topic-16-partitions-1kb.yaml) | 1 | 16 | 1 kB | 1 | 1 | 50000 | 0 | 15
[`backlog-1-topic-1-partition-1kb.yaml`](workloads/backlog-1-topic-1-partition-1kb.yaml) | 1 | 1 | 1 kB | 1 | 1 | 100000 | 100 | 5
[`backlog-1-topic-16-partitions-1kb.yaml`](workloads/backlog-1-topic-16-partitions-1kb.yaml) | 1 | 16 | 1 kB | 1 | 1 | 100000 | 100 | 5
[`max-rate-1-topic-1-partition-1kb.yaml`](workloads/max-rate-1-topic-1-partition-1kb.yaml) | 1 | 1 | 1 kB | 1 | 1 | 0 | 0 | 5
[`max-rate-1-topic-1-partition-100b.yaml`](workloads/max-rate-1-topic-1-partition-100b.yaml) | 1 | 1 | 100 bytes | 1 | 1 | 0 | 0 | 5
[`max-rate-1-topic-16-partitions-1kb.yaml`](workloads/max-rate-1-topic-16-partitions-1kb.yaml) | 1 | 16 | 1 kB | 1 | 1 | 0 | 0 | 5
[`max-rate-1-topic-16-partitions-100b.yaml`](workloads/max-rate-1-topic-16-partitions-100b.yaml) | 1 | 16 | 100 bytes | 1 | 1 | 0 | 0 | 5
[`max-rate-1-topic-100-partitions-1kb.yaml`](workloads/max-rate-1-topic-100-partitions-1kb.yaml) | 1 | 100 | 1 kB | 1 | 1 | 0 | 0 | 5
[`max-rate-1-topic-100-partitions-100b.yaml`](workloads/max-rate-1-topic-100-partitions-100b.yaml) | 1 | 100 | 100 bytes | 1 | 1 | 0 | 0 | 5

> Instructions for running specific workloads—or all workloads sequentially—can be found in the platform-specific [documentation](#documentation).

## Adding a new platform

In order to add a new platform for benchmarking, you need to provide the following:

* A [Terraform](https://terraform.io) configuration for creating the necessary AWS resources ([example](https://github.com/streamlio/messaging-benchmark/blob/lperkins/readme-changes/driver-kafka/deploy/provision-kafka-aws.tf))
* An [Ansible playbook](http://docs.ansible.com/ansible/latest/playbooks.html) for installing and starting the platform on AWS ([example](https://github.com/streamlio/messaging-benchmark/blob/lperkins/readme-changes/driver-pulsar/deploy/deploy.yaml))
* An implementation of the Java [`driver-api`](https://github.com/streamlio/messaging-benchmark/tree/master/driver-api) library ([example](https://github.com/streamlio/messaging-benchmark/tree/lperkins/readme-changes/driver-kafka/src/main/java/io/openmessaging/benchmark/driver/kafka))
* A YAML configuration file that provides any necessary client configuration info ([example](https://github.com/streamlio/messaging-benchmark/blob/master/driver-pulsar/pulsar.yaml))
