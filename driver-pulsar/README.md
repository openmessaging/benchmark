# Apache Pulsar benchmarks

This folder houses all of the assets necessary to run benchmarks for [Apache Pulsar](https://pulsar.incubator.apache.org). In order to run these benchmarks, you'll need to:

* [Create the necessary local artifacts](#creating-local-artifacts)
* [Stand up a Pulsar cluster](#creating-a-pulsar-cluster-on-amazon-web-services-aws-using-terraform-and-ansible) on Amazon Web Services (which includes a client host for running the benchmarks)
* [SSH into the client host](#sshing-into-the-client-host)

## Creating local artifacts

In order to create the local artifacts necessary to run the Pulsar benchmarks in AWS, you'll need to have [Maven](https://maven.apache.org/install.html) installed. Once Maven's installed, you can create the necessary artifacts with a single Maven command:

```bash
$ mvn install
```

## Creating a Pulsar cluster on Amazon Web Services (AWS) using Terraform and Ansible

In order to create an Apache Pulsar cluster on AWS, you'll need to have the following installed:

* [Terraform](https://terraform.io)
* [The `terraform-inventory` plugin for Terraform](https://github.com/adammck/terraform-inventory)
* [Ansible](http://docs.ansible.com/ansible/latest/intro_installation.html)
* The messaging benchmarks repository:

  ```bash
  $ git clone https://github.com/streamlio/messaging-benchmark
  $ cd messaging-benchmark/driver-pulsar/deploy
  ```

In addition, you will need to:

* [Create an AWS account](https://aws.amazon.com/account/) (or use an existing account)
* [Install the `aws` CLI tool](https://aws.amazon.com/cli/)
* [Configure the `aws` CLI tool](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)

Once those conditions are in place, you'll need to create an SSH public and private key at `~/.ssh/aws_pulsar` (private) and `~/.ssh/aws_pulsar.pub` (public), respectively.

```bash
$ ssh-keygen -f ~/.ssh/pulsar_aws
```

When prompted to enter a passphrase, simply hit **Enter** twice. Then, make sure that the keys have been created:

```bash
$ ls ~/.ssh/pulsar_aws*
```

With SSH keys in place, you can create the necessary AWS resources using a single Terraform command (from this directory):

```bash
$ terraform apply
```

That will install the following [EC2](https://aws.amazon.com/ec2) instances (plus some other resources, such as a [Virtual Private Cloud](https://aws.amazon.com/vpc/) (VPC)):

Resource | Description | Count
:--------|:------------|:-----
Pulsar/BookKeeper instances | The VMs on which a Pulsar broker and BookKeeper bookie will run | 3
ZooKeeper instances | The VMs on which a ZooKeeper node will run | 3
Client instance | The VM from which the benchmarking suite itself will be run | 1

When you run `terraform apply`, you will be prompted to type `yes`. Type `yes` to continue with the installation or anything else to quit.

Once the installation is complete, you will see a confirmation message listing the resources that have been installed.

### Variables

There's a handful of configurable parameters related to the Terraform deployment that you can alter by modifying the defaults in the `terraform.tfvars` file.

Variable | Description | Default
:--------|:------------|:-------
`region` | The AWS region in which the Pulsar cluster will be deployed | `us-west-2`
`public_key_path` | The path to the SSH public key that you've generated | `~/.ssh/pulsar_aws.pub`
`ami` | The [Amazon Machine Image](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AMIs.html) (AWI) to be used by the cluster's machines | [`ami-9fa343e7`](https://access.redhat.com/articles/3135091)
`instance_types` | The EC2 instance types used by the various components | `i3.4xlarge` (Pulsar brokers and BookKeeper bookies), `t2.small` (ZooKeeper), `c4.8xlarge` (benchmarking client)

> If you modify the `public_key_path`, make sure that you point to the appropriate SSH key path when running the [Ansible playbook](#running-the-ansible-playbook).

### Running the Ansible playbook

With the appropriate infrastructure in place, you can install and start the Pulsar cluster using Ansible with just one command:

```bash
$ ansible-playbook \
  --user ec2-user \
  --inventory `which terraform-inventory` \
  deploy.yaml
```

> If you're using an SSH private key path different from `~/.ssh/pulsar_aws`, you can specify that path using the `--private-key` flag, for example `--private-key=~/.ssh/my_key`.

## SSHing into the client host

In the [output](https://www.terraform.io/intro/getting-started/outputs.html) produced by Terraform, there's a `client_ssh_host` variable that provides the IP address for the client EC2 host from which benchmarks can be run. You can SSH into that host using this command:

```bash
$ ssh -i ~/.ssh/pulsar_aws ec2-user@$(terraform output client_ssh_host)
```

## Running the benchmarks from the client host

Once you've successfully SSHed into the client host, you can run the benchmarks like this:

```bash
$ cd /opt/benchmark
$ sudo bin/benchmark --drivers driver-pulsar/pulsar.yaml workloads/*.yaml
```

