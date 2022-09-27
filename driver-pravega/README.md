# PRAVEGA BENCHMARKS

This tutorial shows you how to run OpenMessaging benchmarks for [Pravega](https://pravega.io/).
You can currently deploy to the following platforms:

* [Amazon Web Services (AWS)](#deploy-a-pravega-cluster-on-amazon-web-services)

# INITIAL SETUP

To begin with, you will need to clone the benchmark repo from the Pravega organization on GitHub:

```
$ git clone https://github.com/openmessaging/openmessaging-benchmark.git
$ cd openmessaging-benchmark
```

You will also need to have [Maven](https://maven.apache.org/install.html) installed.

# CREATE LOCAL ARTIFACTS

Once you have the repo cloned locally, you can create all the artifacts necessary to run the benchmarks with a single
Maven command:

```
$ mvn install
```

If you want to use the pre-release version of Pravega or the master branch of Pravega, please
check [how to build Pravega](doc/build_pravega.md).

# DEPLOY A PRAVEGA CLUSTER ON AMAZON WEB SERVICES

You can deploy a Pravega cluster on AWS (for benchmarking purposes) using [Terraform 0.12.20](https://www.terraform.io/) and [Ansible 2.8.5](https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html).
You’ll need to have both of those tools installed as well as the `terraform-inventory` [plugin](https://github.com/adammck/terraform-inventory) for Terraform.

You also need to install an Ansible modules to support metrics.

```
ansible-galaxy install cloudalchemy.node-exporter
```

In addition, you’ll need to:

* [Create an AWS account](https://aws.amazon.com/account/) (or use an existing account)
* [Install the `aws` CLI tool](https://aws.amazon.com/cli/)
* [Configure the `aws` CLI tool](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html)

# SSH KEYS

Once you’re all set up with AWS and have the necessary tools installed locally, you’ll need to create both a public and a private SSH key at `~/.ssh/pravega_aws` (private) and `~/.ssh/pravega_aws.pub` (public), respectively.

```
$ ssh-keygen -f ~/.ssh/pravega_aws
```

When prompted to enter a passphrase, simply hit `Enter` twice. Then, make sure that the keys have been created:

```
$ ls ~/.ssh/pravega_aws*
```

# CREATE RESOURCES USING TERRAFORM

With SSH keys in place, you can create the necessary AWS resources using just a few Terraform commands:

```
$ cd driver-pravega/deploy
$ terraform init
$ echo "yes" | terraform apply
```

This will install the following [EC2](https://aws.amazon.com/ec2) instances (plus some other resources, such as a [Virtual Private Cloud](https://aws.amazon.com/vpc/) (VPC)):

|       Resource       |                         Description                         | Count |
|----------------------|-------------------------------------------------------------|-------|
| Controller instances | The VMs on which a Pravega controller will run              | 1     |
| Bookkeeper instances | The VMs on which a Bookkeeper and Segmentstore will run     | 3     |
| ZooKeeper instances  | The VMs on which a ZooKeeper node will run                  | 3     |
| Client instance      | The VM from which the benchmarking suite itself will be run | 2     |

When you run `terraform apply`, you will be prompted to type `yes`. Type `yes` to continue with the installation or anything else to quit.

# VARIABLES

There’s a handful of configurable parameters related to the Terraform deployment that you can alter by modifying the defaults in the `terraform.tfvars` file.

|     Variable      |                                                             Description                                                              |                                                        Default                                                        |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `region`          | The AWS region in which the Pravega cluster will be deployed                                                                         | `us-west-2`                                                                                                           |
| `public_key_path` | The path to the SSH public key that you’ve generated                                                                                 | `~/.ssh/pravega_aws.pub`                                                                                              |
| `ami`             | The [Amazon Machine Image (AWI)](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AMIs.html) to be used by the cluster’s machines | `ami-9fa343e7`                                                                                                        |
| `instance_types`  | The EC2 instance types used by the various components                                                                                | `i3.4xlarge` (BookKeeper bookies), `m5.large`(Controller), `t3.small` (ZooKeeper), `c5.4xlarge` (benchmarking client) |

If you modify the `public_key_path`, make sure that you point to the appropriate SSH key path when running the [Ansible playbook](#_RUNNING_THE_ANSIBLE_PLAYBOOK).

# RUNNING THE ANSIBLE PLAYBOOK

With the appropriate infrastructure in place, you can install and start the Pravega cluster using Ansible with just one command:

```
# Fixes "terraform-inventory had an execution error: Error reading tfstate file: 0.12 format error"
$ export TF_STATE=./
$ ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  deploy.yaml
```

If you’re using an SSH private key path different from `~/.ssh/pravega_aws`, you can specify that path using the `--private-key` flag, for example `--private-key=~/.ssh/my_key`.

# SSHING INTO THE CLIENT HOST

In the [output](https://learn.hashicorp.com/terraform/getting-started/outputs.html) produced by Terraform, there’s a `client_ssh_host` variable that provides the IP address for the client EC2 host from which benchmarks can be run. You can SSH into that host using this command:

```
$ ssh -i ~/.ssh/pravega_aws ec2-user@$(terraform output client_ssh_host)
```

# RUNNING THE BENCHMARKS FROM THE CLIENT HOSTS

> The benchmark scripts can be run from the /opt/benchmark working directory.

Once you’ve successfully SSHed into the client host, you can run any of the [existing benchmarking workloads](http://openmessaging.cloud/docs/benchmarks/#benchmarking-workloads) by specifying the YAML file for that workload when running the `benchmark` executable. All workloads are in the `workloads` folder. Here’s an example:

```
$ sudo bin/benchmark \
  --drivers driver-pravega/pravega.yaml \
  workloads/1-topic-16-partitions-1kb.yaml
```

> Although benchmarks are run from a specific client host, the benchmarks are run in distributed mode, across multiple client hosts.

There are multiple Pravega “modes” for which you can run benchmarks. Each mode has its own YAML configuration file in the driver-pravega folder.

|     Mode     |                         Description                         |                       Config file                        |
|--------------|-------------------------------------------------------------|----------------------------------------------------------|
| Standard     | Pravega with transaction disabled (at-least-once semantics) | [pravega.yaml](./pravega.yaml)                           |
| Exactly Once | Pravega with transaction enabled (exactly-once semantics)   | [pravega-exactly-once.yaml](./pravega-exactly-once.yaml) |

The example used the “standard” mode as configured in `driver-pravega/pravega.yaml`. Here’s an example of running a benchmarking workload in exactly-once mode:

```
$ sudo bin/benchmark \
  --drivers driver-pravega/pravega-exactly-once.yaml \
  workloads/1-topic-16-partitions-1kb.yaml
```

# SPECIFY CLIENT HOSTS

By default, benchmarks will be run from the set of hosts created by Terraform. You can also specify a comma-separated list of client hosts using the `--workers` flag (or `-w` for short):

```
$ sudo bin/benchmark \
  --drivers driver-pravega/pravega-exactly-once.yaml \
  --workers 1.2.3.4:8080,4.5.6.7:8080 \ # or -w 1.2.3.4:8080,4.5.6.7:8080
  workloads/1-topic-16-partitions-1kb.yaml
```

# DOWNLOADING YOUR BENCHMARKING RESULTS

The OpenMessaging benchmarking suite stores results in JSON files in the `/opt/benchmark` folder on the client host from which the benchmarks are run. You can download those results files onto your local machine using `scp`. You can download all generated JSON results files using this command:

```
$ scp -i ~/.ssh/pravega_aws ec2-user@$(terraform output client_ssh_host):/opt/benchmark/*.json .
```

# COLLECTING METRICS AND LOGS

See [metrics and logs](doc/metrics_and_logs.md).

# TEARING DOWN YOUR BENCHMARKING INFRASTRUCTURE

Once you’re finished running your benchmarks, you should tear down the AWS infrastructure you deployed for the sake of saving costs. You can do that with one command:

```
$ terraform destroy -force
```

Make sure to let the process run to completion (it could take several minutes). Once the tear down is complete, all AWS resources that you created for the Pravega benchmarking suite will have been removed.

# RUN IN KUBERNETES

See [run in Kubernetes](doc/run_in_k8s.md).

# P3 Test Driver

[P3 Test Driver](https://github.com/pravega/p3_test_driver) can be used to run multiple tests automatically and plot the results.

# TROUBLESHOOTING

See [troubleshooting](doc/troubleshooting.md).
