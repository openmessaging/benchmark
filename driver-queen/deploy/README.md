# Queen deployments

- `docker`: a three-node cluster on one machine, to try the driver. The header of
  [docker/compose.yaml](docker/compose.yaml) has the commands.
- `ssd-deployment`: Terraform and Ansible for AWS, three brokers and four benchmark clients on the same
  instance types as `driver-kafka`'s ssd-deployment, so the two run on equal hardware.

## AWS

You need [Terraform](https://terraform.io), the
[`terraform-inventory`](https://github.com/adammck/terraform-inventory) plugin, and
[Ansible](https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html) (the
full `ansible` package: the playbook uses modules from `ansible.posix` and `community.general`).

Build the benchmark package first: the playbook copies
`package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz` to the clients.

```bash
mvn install -DskipTests
ssh-keygen -f ~/.ssh/queen_aws -N ''
cd driver-queen/deploy/ssd-deployment
terraform init
terraform apply
TF_STATE=. ansible-playbook --user ec2-user --inventory `which terraform-inventory` deploy.yaml
```

Then run the workloads from the first client:

```bash
ssh -i ~/.ssh/queen_aws ec2-user@$(terraform output -raw client_ssh_host)
cd /opt/benchmark
sudo bin/benchmark --drivers driver-queen/queen.yaml workloads/1-topic-100-partitions-1kb-4p-4c-500k.yaml
```

`/opt/benchmark/workers.yaml` lists the four clients, and `bin/benchmark` reads it by default.

|       Role       |   Instance   | vCPU | RAM (GiB) |               Disks                | Count |
|------------------|--------------|------|-----------|------------------------------------|-------|
| Queen broker     | i3en.6xlarge | 24   | 192       | 2 x 7,500 GB NVMe, one RAID 0, XFS | 3     |
| Benchmark client | m5n.8xlarge  | 32   | 128       | EBS                                | 4     |

Change them in `terraform.tfvars`. A broker with more NVMe disks needs them added to the RAID 0 task
in `deploy.yaml`.

What the playbook sets up on the brokers:

- the `latency-performance` tuned profile, `vm.dirty_background_bytes` at 1 GiB (writeback starts
  early, so an fsync of the log does not wait behind a pile of dirty pages), and the raft directory
  on the array at `/mnt/queen`;
- `ghcr.io/queen-mq/queen:2.5.0` under podman with host networking, as the systemd unit `queen`,
  with its settings in `/etc/queen.env`: the three voters, and a raft token made once on the machine
  that runs Ansible. Change the version with `queenVersion` in `deploy.yaml`.

For the workloads with thousands of topics, uncomment `QUEEN_QLOG_SHARDS=16` in
`templates/queen.env` before the first deploy (it applies to a new data directory only), and run
them with `driver-queen/queen-many-topics.yaml`.

On the clients: Java 17, the benchmark package with the driver's three profiles (`queen.yaml`,
`queen-throughput.yaml`, `queen-many-topics.yaml`) pointed at the three brokers (their `urls`
rewritten, every other setting as in the package), chrony, and the `benchmark-worker` service.

Restart the brokers with:

```bash
TF_STATE=. ansible-playbook --user ec2-user --inventory `which terraform-inventory` deploy.yaml --tags restart-queen
```

`terraform destroy` removes everything.
