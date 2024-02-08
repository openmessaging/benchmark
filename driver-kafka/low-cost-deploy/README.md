# Kafka Deployments

There are two types of deployment found under ssd-deployment and hdd-deployment folders:

- SSD with the i3 instance type that has fast NVMe drives as local instance stores
- HDD with the d2 instance type that has fast HDD sequential IO drives as local instance stores

Customize the instance types in the terraform.tfvars.

If you choose larger instances, they come with more drives. To include those in the benchmarks you must:

- update the Ansible script to include them in the mount and filesystem tasks
- update the server.properties to include them in the logs.dir config

NOTE: When using d2 instances, the instance stores are not automatically generated. You must add them to the provision-kafka-aws.tf file.

For instructions on how to run a benchmark see the [Kafka instructions](http://openmessaging.cloud/docs/benchmarks/kafka/). Run the Terraform and Ansible commands from either the ssd-deployment or hdd-deployment folder.

## AWS Instance Types

| Instance | vCPU | RAM (GB) | Instance Store Drives | Network Baseline | Network Burst |
| -- | -- | -- | -- | -- | -- |
| i3.large | 2 | 15.25 | 1 x 475 NVMe SSD |	0.74 | 10 |
| i3.xlarge | 4 | 30.5 | 1 x 950 NVMe SSD |	1.24 | 10 |
| i3.2xlarge | 8 | 61 | 1 x 1,900 NVMe SSD | 2.48 | 10 |
| i3.4large | 16 | 122 | 2 x 1,900 NVMe SSD | 4.96 | 10 |
| i3.8xlarge | 32 | 244 | 4 x 1,900 NVMe SSD | 10 | 10 |
| d2.xlarge | 4 | 30.5 | 3 x 2 TB HDD |	1.24 | 1.24 |
| d2.2xlarge | 8 | 61 | 6 x 2 TB HDD |	2.48 | 2.48 |
| d2.4large | 16 | 122 | 12 x 2 TB HDD | 4.96 | 4.96 |
| d2.8xlarge | 36 | 244 | 24 x 2 TB HDD | 9.85 | 9.85 |

## Compression

When using 4 client VMs or less you may see lower throughput when using compression. Compression is performed by the producers and consumers only (when using defaults) and clients need to be spread across more VMs to see any throughput gains.

Obviously, throughput may not be your primary goal when using compression.

## SSH keys
Once you’re all set up with AWS and have the necessary tools installed locally, you’ll need to create both a public and a private SSH key at `~/.ssh/kafka_aws` (private) and ~/.ssh/`kafka_aws.pub` (public), respectively.

```
  $ ssh-keygen -f ~/.ssh/kafka_aws
```

When prompted to enter a passphrase, simply hit Enter twice. Then, make sure that the keys have been created:

```
  $ ls ~/.ssh/kafka_aws*
```

## Create resource using Terraform
With SSH keys in place, you can create the necessary AWS resources using just a few Terraform commands:

```
  $ cd driver-kafka/deploy
  $ terraform init
  $ terraform apply
```

Once the installation is complete, you will see a confirmation message listing the resources that have been installed.

## Variables
There’s a handful of configurable parameters related to the Terraform deployment that you can alter by modifying the defaults in the terraform.tfvars file.

| Variable  | Description | Default |
| -- | -- | -- |
| region  | The AWS region in which the Kafka cluster will be deployed  | us-west-2 |
| public_key_path | The path to the SSH public key that you’ve generated  | ~/.ssh/kafka_aws.pub  |
| ami | The Amazon Machine Image (AWI) to be used by the cluster’s machines | ami-9fa343e7  |
| instance_types  | The EC2 instance types used by the various components | i3.4xlarge (Kafka brokers), t2.small (ZooKeeper), c4.8xlarge (benchmarking client)  |

If you modify the public_key_path, make sure that you point to the appropriate SSH key path when running the Ansible playbook.

## Running the Ansible playbook
With the appropriate infrastructure in place, you can install and start the Kafka cluster using Ansible with just one command:

```
$ ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  deploy.yaml
```

If you’re using an SSH private key path different from `~/.ssh/kafka_aws`, you can specify that path using the `--private-key` flag, for example `--private-key=~/.ssh/my_key`.

## SSH'ing into the client host
In the output produced by Terraform, there’s a `client_ssh_host` variable that provides the IP address for the client EC2 host from which benchmarks can be run. You can SSH into that host using this command:

```
$ ssh -i ~/.ssh/kafka_aws ec2-user@$(terraform output client_ssh_host)
```

## Verifying worker health

If benchmark workers did not properly start, they will not respond to API calls. For each of the IP addresses in `/opt/benchmark/workers.yaml`, one can run

```
  curl -o - -I -H "Accept: application/json" -X POST http://${IP_ADDRESS}/stop-all
```

which should yield a `200 OK` response.

## Running the benchmarks from the client hosts
The benchmark scripts can be run from the `/opt/benchmark` working directory.

Once you’ve successfully SSH'ed into the client host, you can run any of the existing benchmarking workloads by specifying the YAML file for that workload when running the benchmark executable. All workloads are in the workloads folder. Here’s an example:

```
$ sudo /opt/benchmark/bin/benchmark \
  --drivers driver-kafka/kafka.yaml \
  workloads/1-topic-16-partitions-1kb.yaml
```

## Specify client hosts
By default, benchmarks will be run from the set of hosts created by Terraform. You can also specify a comma-separated list of client hosts using the `--workers` flag (or `-w` for short):

```
$ sudo bin/benchmark \
  --drivers driver-kafka/kafka-exactly-once.yaml \
  --workers 1.2.3.4:8080,4.5.6.7:8080 \ # or -w 1.2.3.4:8080,4.5.6.7:8080
  workloads/1-topic-16-partitions-1kb.yaml
```

## Debugging failing benchmarks

Benchmarks can fail for a number of reasons. The most common ones observed are related to the infrastructure.

* Insufficient allocation of memory: depending on the chosen EC2 instance size, the configuration for the processes launched on these instances must be modified. The configuration can be found in the `templates` folder, specifically the `*.service` and `*.properties` files.
* Corrupted Ansible deployment: If the Ansible deploy script is executed in part or multiple times, infrastructure can get corrupted. Ensure you are always deploying fresh infrastructure with Terraform and execute the Ansible script in full and only once to observe the most consistent results.

Failing benchmarks can be debugged by running the following commands on the EC2 hosts to analyze the state of the services running on the hosts.

* Printing the logs for a service: `journalctl -u service-name.service (-b)`
* Learning more about the status of a service (e.g. active, failed, initialising): `systemctl -l status benchmark-worker`

## Downloading your benchmarking results
The OpenMessaging benchmarking suite stores results in JSON files in the `/opt/benchmark` folder on the client host from which the benchmarks are run. You can download those results files onto your local machine using scp. You can download all generated JSON results files using this command:

```
$ scp -i ~/.ssh/kafka_aws ec2-user@$(terraform output client_ssh_host):/opt/benchmark/*.json .
```

**NOTE:** On MacOS, the wildcard needs to be escaped.

## Tearing down your benchmarking infrastructure
Once you’re finished running your benchmarks, you should tear down the AWS infrastructure you deployed for the sake of saving costs. You can do that with one command:

```
$ terraform destroy -force
```

Make sure to let the process run to completion (it could take several minutes). Once the tear down is complete, all AWS resources that you created for the Kafka benchmarking suite will have been removed.
