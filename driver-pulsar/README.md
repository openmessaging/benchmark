# Apache Pulsar benchmarks

For instructions on running the OpenMessaging benchmarks for Pulsar, see the [official documentation](http://openmessaging.cloud/docs/benchmarks/pulsar/).

## Supplement to the official documentation

Before you run `ansible-playbook` with `terraform-inventory`, you must set the environment variable `TF_STATE`. i.e. the completed command should be:

```bash
TF_STATE=. ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  deploy.yaml
```

### Ansible variable files

The Ansible deployment script supports flexible configuration with a variable file, which is specified by `-e` option like:

```bash
TF_STATE=. ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  -e @extra_vars.yaml \
  deploy.yaml
```

For example, if you changed the AWS instance type, the two SSD device paths might not be `/dev/nvme1n1` and `/dev/nvme2n1`. In this case, you can configure them like

```yaml
disk_dev:
  - /path/to/disk1
  - /path/to/disk2
```

See more explanations in [the example variable file](./deploy/ssd/extra_vars.yaml).

### Enable protocol handlers

With the Ansible variable file, you can enable multiple protocol handlers in `protocol_handlers` variable. For example, given following configurations:

```yaml
protocol_handlers:
  - protocol: kafka
    conf: kop.conf
    url: https://github.com/streamnative/kop/releases/download/v2.9.2.5/pulsar-protocol-handler-kafka-2.9.2.5.nar
  - protocol: mqtt
    conf: mop.conf
    url: https://github.com/streamnative/mop/releases/download/v2.9.2.5/pulsar-protocol-handler-mqtt-2.9.2.5.nar
```

It will download KoP and MoP from the given URLs. Then, the configuration templates will be formatted and appended to the `broker.conf`. The `conf` field is the name of the configuration template, which must be put under `templates` directory.

### Restart the brokers with new configurations

You can change the configuration files and then restart the cluster by executing the following command.

```bash
TF_STATE=. ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  -e @extra_vars.yaml \
  restart-brokers.yaml
```

