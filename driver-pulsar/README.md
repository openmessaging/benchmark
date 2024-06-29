# Apache Pulsar benchmarks

For instructions on running the OpenMessaging benchmarks for Pulsar, see the [official documentation](http://openmessaging.cloud/docs/benchmarks/pulsar/).

## Supplement to the official documentation
For Ansible to have access to the inventory defined in the Terraform configuration, the Terraform Collection for Ansible is required.
Install it with the following command ([source](https://mdawar.dev/blog/ansible-terraform-inventory)):
```bash
ansible-galaxy collection install cloud.terraform
```

```bash
ansible-playbook \
  --user ec2-user \
  --inventory terraform.yaml \
  deploy.yaml
```

### Ansible variable files

The Ansible deployment script supports flexible configuration with a variable file, which is specified by `-e` option like:

```bash
TF_STATE=. ansible-playbook \
  --user ec2-user \
  --inventory terraform.yaml \
  -e @extra_vars.yaml \
  deploy.yaml
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
ansible-playbook \
  --user ec2-user \
  --inventory terraform.yaml \
  -e @extra_vars.yaml \
  restart-brokers.yaml
```

