# COLLECTING LOGS AND METRICS

This will download logs and metrics from the cluster to your local machine.

```
cd deploy
ansible-playbook --user ec2-user --inventory `which terraform-inventory` collect_logs_and_metrics.yaml
```

Example output:

```
ls ../../data/pravega_logs/pravega_logs_20191004T034401
benchmark-worker-10.0.0.119.log.gz
benchmark-worker-10.0.0.13.log.gz
benchmark-worker-10.0.0.42.log.gz
benchmark-worker-10.0.0.82.log.gz
bookkeeper-10.0.0.145.log.gz
bookkeeper-10.0.0.186.log.gz
bookkeeper-10.0.0.247.log.gz
influxdb.tgz
pravega-controller-10.0.0.242.log.gz
pravega-segmentstore-10.0.0.121.log.gz
pravega-segmentstore-10.0.0.253.log.gz
pravega-segmentstore-10.0.0.80.log.gz
prometheus.tgz
zookeeper-10.0.0.185.log.gz
zookeeper-10.0.0.202.log.gz
zookeeper-10.0.0.219.log.gz
```

## Viewing Previously Collected Metrics

This will run local instances of InfluxDB, Prometheus, and Grafana loaded with previously
collected metrics.

```
cd deploy
open_saved_metrics/open_saved_metrics.sh ../../data/pravega_logs/pravega_logs_20191004T034401
```

Open Grafana at http://localhost:3000.
Login using user name "admin" and any password.

Configure Grafana with the following data sources:

- Prometheus
  - Name: Prometheus
  - HTTP URL: http://prometheus:9090
- InfluxDB
  - Name: pravega-influxdb
  - HTTP URL: http://influxdb:8086
  - InfluxDB Details Database: pravega

Load dashboards from [deploy/templates/dashboards](../deploy/templates/dashboards).
