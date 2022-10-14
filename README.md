# OpenMessaging Benchmark Framework

[![Build](https://github.com/openmessaging/benchmark/actions/workflows/pr-build-and-test.yml/badge.svg)](https://github.com/openmessaging/benchmark/actions/workflows/pr-build-and-test.yml)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

**Notice：** We do not consider or plan to release any unilateral test results based on this standard. For reference, you can purchase server tests on the cloud by yourself.

This repository houses user-friendly, cloud-ready benchmarking suites for the following messaging platforms:

* [Apache ActiveMQ Artemis](https://activemq.apache.org/components/artemis/)
* [Apache Bookkeeper](https://bookkeeper.apache.org)
* [Apache Kafka](https://kafka.apache.org)
* [Apache Pulsar](https://pulsar.apache.org)
* [Apache RocketMQ](https://rocketmq.apache.org)
* Generic [JMS](https://javaee.github.io/jms-spec/)
* [KoP (Kafka-on-Pulsar)](https://github.com/streamnative/kop)
* [NATS JetStream](https://docs.nats.io/nats-concepts/jetstream)
* [NATS Streaming (STAN)](https://docs.nats.io/legacy/stan/intro)
* [NSQ](https://nsq.io)
* [Pravega](https://pravega.io/)
* [RabbitMQ](https://www.rabbitmq.com/)
* [Redis](https://redis.com/)

> More details could be found at the [official documentation](http://openmessaging.cloud/docs/benchmarks/).

## Build

Requirements:

* JDK 8
* Maven 3.8.6+

Common build actions:

|             Action              |                 Command                  |
|---------------------------------|------------------------------------------|
| Full build and test             | `mvn clean verify`                       |
| Skip tests                      | `mvn clean verify -DskipTests`           |
| Skip Jacoco test coverage check | `mvn clean verify -Djacoco.skip`         |
| Skip Checkstyle standards check | `mvn clean verify -Dcheckstyle.skip`     |
| Skip Spotless formatting check  | `mvn clean verify -Dspotless.check.skip` |
| Format code                     | `mvn spotless:apply`                     |
| Generate license headers        | `mvn license:format`                     |

## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
