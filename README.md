
# OpenMessaging Benchmark framework

## Install

```shell
$ mvn install
```

## Usage

Select which drivers to use, which workloads to run and start the load generator 

```shell
bin/benchmark --drivers pulsar/pulsar.yaml,kafka/kafka.yaml workloads/*.yaml
```

At the end of the test, there will be a number of Json files in the current directory, 
containing the statistics collected during each test run.

Use the chart generator to create charts:

```shell
bin/create_charts.py *.json
```



