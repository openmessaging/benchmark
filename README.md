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

## How to run experiments

This section covers how to run experiments.

### Building the project into a deployable Docker container

Run the following commands in the root directory.

```
  mvn package
  export BENCHMARK_TARBALL=package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
  docker build -t rubentewierik/benchmark-framework --build-arg BENCHMARK_TARBALL . -f docker/Dockerfile
```

### Deploying the workers to Kubernetes

The first step is to deploy the Helm chart.

```bash
$ cd deployment/kubernetes/helm
$ helm install ./benchmark --name-template benchmark
```

### Exec'ing into the driver pod to run the benchmark

After the chart has started, one can exec into the pod name `benchmark-driver` and run the benchmark from there.

```
  kubectl exec --stdin --tty benchmark-driver -- /bin/bash
```

Once inside the `benchmark-driver` pod, one can run the following example command to execute one of the benchmarks.

```bash
bin/benchmark --drivers driver-pulsar/pulsar.yaml --workers $WORKERS workloads/1-topic-16-partitions-1kb.yaml
```

The started workers are referenced in the "$WORKERS" environment variable.

### Cleaning up deployed resources

The chart can be destroyed by running the following command.

```bash
$ helm delete benchmark
```

### Troubleshooting the deployed Helm chart

One can run the following command to list all deployed pods to verify presence of resources.

```
  kubectl get pod
```

Depending on how many workers were configured in the `values.yaml` file in `deployment/kubernetes/helm/benchmark`, one should be able to observe a pod named `benchmark-driver` and one or more pods named `benchmark-worker-{index}` where index is the zero-indexed identifier of one of the worker pods.

One can run the following command to list all deployed services to verify presence of resources.

```
  kubectl get service
```



## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
