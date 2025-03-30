# Apache Kafka benchmarks

For instructions on running the OpenMessaging bencmarks for Kafka, see the [official documentation](http://openmessaging.cloud/docs/benchmarks/kafka).

NOTE: This is a slightly modified version with two key differences:

- workloads have extra arguments for sending randomized payloads rather than the same payload over and over.
- there is a new argument that converts all output result json files into a single csv.

TODO: Document these changes.

## Features

### Zone-aware workers

To pass a zone/rack ID (e.g. cloud region availability zone name) to the Kafka clients (producer, consumer) client-id configuration, use the system property `zone.id`, and use the template `{zone.id}` on the `client.id` or `client.rack` configs, either on the `commonConfig`, `producerConfig`, or `consumerConfig` Driver values.

When running workers, pass the `zone.id`:

```bash
export JVM_OPTS=-Dzone.id=az0
/opt/benchmark/bin/benchmark-worker
```

Then pass the `client.id` template:
```yaml
commonConfig: |
  bootstrap.servers=localhost:9092
  client.id=omb-client_az={zone.id}
  client.rack=omb-client_az={zone.id}
```

This generates producer and consumer `client.id=omb-client_az=value` and `client.rack=omb-client_az=value`

```yaml
producerConfig: |
  client.id=omb-producer_az={zone.id}
  client.rack=omb-producer_az={zone.id}
consumerConfig: |
  auto.offset.reset=earliest
  client.id=omb-consumer_az={zone.id}
  client.rack=omb-consumer_az={zone.id}
```

This generates producer `client.id=omb-producer_az=value` and `client.rack=omb-producer_az=value`; consumer `client.id=omb-consumer_az=value` and `client.rack=omb-consumer_az=value`
