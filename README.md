
# The OpenMessaging Benchmark framework

This repository houses user-friendly, cloud-ready benchmarking suites for the following messaging platforms:

* [Apache Kafka](https://kafka.apache.org)
* [Apache Pulsar (incubating)](https://pulsar.incubator.apache.org)

For each platform, the suite includes easy-to-use scripts for deploying the platform on [Amazon Web Services](https://aws.amazon.com) (AWS). For instructions on running the benchmarks, see platform-specific docs for:

* [Kafka](driver-kafka/README.md)
* [Pulsar](driver-pulsar/README.md)

## Adding a new platform

In order to add a new platform for benchmarking, you need to provide the following:

* A [Terraform](https://terraform.io) configuration for creating the necessary AWS resources
* An [Ansible playbook](http://docs.ansible.com/ansible/latest/playbooks.html) for installing and starting the platform on AWS
* An implementation of the Java [`driver-api`](https://github.com/streamlio/messaging-benchmark/tree/master/driver-api) library 
* A YAML configuration file like [this one](https://github.com/streamlio/messaging-benchmark/blob/master/driver-pulsar/pulsar.yaml) that provides any necessary client configuration info