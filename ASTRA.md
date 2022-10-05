## Benchmark Astra Streaming

### Gather Your Information

For your tenant on Astra Streaming you need the following information from your Astra Streaming Tenant's Connect tab:

1. Name
2. Pulsar Instance
3. Broker Service URL
4. Web Service URL
5. A token from the Token Manager link

### Setup your CLI Environment

Setup the following environment variables:

```
export astraTenant="<1. Name>"
export astraNamespace="<your choice all lowercase or numerals after the first digit>"
export astraCluster="<2. Pulsar Instance>"
export pulsarServiceUrl="<3. Broker Service URL>"
export pulsarAdminUrl="<4. Web Service URL>"
export astraToken="<5. A token from the Token Manager link>"
```

### Astra Streaming Drivers and Workloads

OMB 0.0.2 has 2 drivers and 2 workloads ready to use against Astra Streaming within your
rate limited tenant.

#### Drivers

1. Pulsar: `driver-pulsar/astra.yaml`
2. Starlight for JMS: `driver-jms/astra-jms.yaml`

Support for Starlight for Kafka and Starlight for RabbitMQ are yet to be done.

#### Workloads

These are currently set to 1000 msgs/sec.

1. `workloads/1-topic-1-partition-1kb.yaml`
2. `workloads/1-topic-2-partitions-1kb.yaml`

You can create additional workloads as needed.
