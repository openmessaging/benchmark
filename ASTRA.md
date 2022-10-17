## Benchmark Astra Streaming

### Gather Your Information

To run OMB on your Astra Streaming Tenant you need the following information from your Astra Streaming Tenant's Connect tab:

1. Name
2. Pulsar Instance
3. Broker Service URL
4. Web Service URL
5. Auth Token from the Token Manager link
6. Namespace - You also need to decide what namespace in your tenant to use. This name must fit a regex pattern - please use a lowercase letter followed by one or more numerals or lowercase letters.

### Setup your CLI Environment

Setup the following environment variables - here is an example

```
export pulsarServiceUrl="pulsar+ssl://pulsar-aws-useast1.staging.streaming.datastax.com:6651"
export pulsarAdminUrl="https://pulsar-aws-useast1.api.staging.streaming.datastax.com"
export kafkaServiceUrl="kafka-aws-useast1.staging.streaming.datastax.com:9093"
export rabbitmqServiceUrl="rabbitmq-aws-useast1.staging.streaming.datastax.com:5671"
export astraTenant="omb-performance"
export astraNamespace="namespace"
export astraCluster="staging0"
export astraToken="******"
```

### Astra Streaming Drivers and Workloads

OMB 0.0.2 has 2 drivers and 2 workloads ready to use against Astra Streaming within your
rate limited tenant.

#### Drivers

These drivers use the above environment variables to properly access your Astra Streaming Tenant.

1. Pulsar: `driver-pulsar/astra.yaml`
2. Starlight for JMS: `driver-jms/astra-jms.yaml`
3. Starlight for Kafka: `driver-kafka/astra-s4k.yaml`
4. Starlight for RabbitMQ: `driver-rabbitmq/astra-s4r.yaml`

Support for Starlight for Kafka and Starlight for RabbitMQ are yet to be done.

#### Workloads

These are currently set to 1000 msgs/sec.

1. `workloads/1-topic-1-partition-1kb.yaml`
2. `workloads/1-topic-2-partitions-1kb.yaml`

You can create additional workloads as needed.

Please keep in mind current [Astra Streaming Guardrails](https://docs.datastax.com/en/astra-streaming/docs/astream-limits.html) while testing. You may need to clean up your tenant between tests.
