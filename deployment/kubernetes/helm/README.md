Users can deploy the helm chart:

```bash
$ helm install ./benchmark --name benchmark
```

After the chart has started, users can exec into the pod name "benchmark-driver" and run the benchmark from there.

For example, once inside the "benchmark-driver" pod, users can execute:

```bash
bin/benchmark --drivers driver-pulsar/pulsar.yaml --workers $WORKERS workloads/1-topic-16-partitions-1kb.yaml
```

All workers that has configured to startup will be set in the "$WORKERS" env variable

To tear down the chart:

```bash
$ helm delete benchmark --purge
```

