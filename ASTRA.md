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
export astraTenant="njdeluxeaws"
export astraNamespace="namespace0"
export astraCluster="staging0"
export pulsarServiceUrl="pulsar+ssl://pulsar-aws-useast1.staging.streaming.datastax.com:6651"
export pulsarAdminUrl="https://pulsar-aws-useast1.api.staging.streaming.datastax.com"
export astraToken="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2NjQ0NzQyMDIsImlzcyI6ImRhdGFzdGF4Iiwic3ViIjoiY2xpZW50O2YyOTMyN2U2LTM0ZGItNDkyNy04ODA4LTFjMTdhZjFkMGJmMTtaMmxuWVc5dDswZWRmYmZlZmY3IiwidG9rZW5pZCI6IjBlZGZiZmVmZjcifQ.TrdCiT-YUn5Q7cQH4ks9d62tVR6PClCAjnurOiXvcG8roTOjFtqh0dZ4CNaYqp5SuXN5Tup5ND5NXmRcxiCCSXMRv8zj0M03UFddAmIiWbQtprRXsofrkmvNP_cdoH8cwvzMQq8UerHSO81ZRTeDsoRG8StwqFQPa--XxQfLgjuFCt-vWLI51_Guh-Y8IMT0dsC8eWsOSTnqe5xweT_d6EGFbnOExV1JS07ubaloePP1yLw5OhkA2BFtPRscf5NjDT3IdDqXvdZkGLUVcQ2ELXpjjiF85RPxu574H5Ow2cXvEnmIn9WrfAadzjM2bsA3XJPtKzz4m7vG_PtYl1inug"
```

### Astra Streaming Drivers and Workloads

OMB 0.0.2 has 2 drivers and 2 workloads ready to use against Astra Streaming within your
rate limited tenant.

#### Drivers

These drivers use the above environment variables to properly access your Astra Streaming Tenant.

1. Pulsar: `driver-pulsar/astra.yaml`
2. Starlight for JMS: `driver-jms/astra-jms.yaml`

Support for Starlight for Kafka and Starlight for RabbitMQ are yet to be done.

#### Workloads

These are currently set to 1000 msgs/sec.

1. `workloads/1-topic-1-partition-1kb.yaml`
2. `workloads/1-topic-2-partitions-1kb.yaml`

You can create additional workloads as needed.

Please keep in mind current [Astra Streaming Guardrails](https://docs.datastax.com/en/astra-streaming/docs/astream-limits.html) while testing. You may need to clean up your tenant between tests.
