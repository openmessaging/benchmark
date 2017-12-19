# Apache Pulsar benchmarks

This folder houses all of the assets necessary to run benchmarks for Apache Pulsar.

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

Once those conditions are in place, you can create the necessary AWS resources using a single Terraform command (from this directory):

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