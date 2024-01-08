# OpenMessaging Benchmark Framework adapted for benchmarking on AWS by Ruben te Wierik

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

This repository houses user-friendly, benchmarking suites relying on the AWS cloud platform for the following messaging platforms:

* [Apache Kafka](https://kafka.apache.org)
* [Apache Pulsar](https://pulsar.apache.org)
* [Pravega](https://pravega.io/)
* [RabbitMQ](https://www.rabbitmq.com/)
* [Redis](https://redis.com/)
* [AWS S3](https://aws.amazon.com/s3/)
* [AWS SNS/SQS](https://docs.aws.amazon.com/sns/latest/dg/sns-sqs-as-subscriber.html)

> More details could be found at the [official documentation of the OpenMessaging Benchmark Framework](http://openmessaging.cloud/docs/benchmarks/).

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
