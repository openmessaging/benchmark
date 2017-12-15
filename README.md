
# OpenMessaging Benchmark framework

## Install

```shell
$ mvn install
```

## Usage

Select which drivers to use, which workloads to run and start the load generator

```shell
bin/benchmark --drivers driver-pulsar/pulsar.yaml,driver-kafka/kafka.yaml workloads/*.yaml
```

At the end of the test, there will be a number of Json files in the current directory,
containing the statistics collected during each test run.

Use the chart generator to create charts:

```shell
bin/create_charts.py *.json
```


## Deployment on AWS or bare metal

Each benchmark driver includes scripts to deploy and configure the messaging
system and the benchmark code itself.

For each driver, there's a `driver-XYZ/deploy` directory.

## Provisioning of the VM in AWS

We use [Terraform](https://www.terraform.io/) to quickly provision a set of VMs
in AWS, along with VPC and all required configuration.

Initialize Terraform required plugins:

```shell
terraform init
```

Provision the VMs:

```shell
terraform apply -var 'public_key_path=~/.ssh/id_rsa.pub'
```

After this command, all the VMs should be ready to use.

Once the benchmark is done, you can quickly terminate all the resources with
a single command:

```shell
terraform destroy -var 'public_key_path=~/.ssh/id_rsa.pub'
```

### Using Terraform with Ansible

To use in Ansible the VMs provisioned through Terraform, we need an additional tool that is
required to generate Ansible inventory file. In MacOS you can quickly install the tool with:

```shell
brew install terraform-inventory
```

### Deploy the messaging system

In the `driver-XYZ/deploy` directories, there is also an
[Ansible](https://www.ansible.com/) script, along with a bunch of
configuration templates.

Ansible is used to deploy the particular system and the benchmark code in the
provisioned VMs. It can also be used to deploy on a set of existing
bare metal machines.

Start the deployment:

```shell
ansible-playbook -u ec2-user -i `which terraform-inventory` deploy.yaml
```

This will install all the required software in the VMs and it will start
the messaging system.

At the end, it will print out the IPs of all the machines, by role. Eg:

```
...
TASK [debug] ****************************************************************************************************************************************************************************************
ok: [localhost] => (item=54.191.85.116) => {
    "item": "54.191.85.116",
    "msg": "Pulsar/BookKeeper servers 54.191.85.116"
}
ok: [localhost] => (item=34.223.226.214) => {
    "item": "34.223.226.214",
    "msg": "Pulsar/BookKeeper servers 34.223.226.214"
}
ok: [localhost] => (item=54.213.255.22) => {
    "item": "54.213.255.22",
    "msg": "Pulsar/BookKeeper servers 54.213.255.22"
}

TASK [debug] ****************************************************************************************************************************************************************************************
ok: [localhost] => (item=54.212.205.198) => {
    "item": "54.212.205.198",
    "msg": "Benchmark client 54.212.205.198"
}

```

## Starting the benchmark

You can SSH into the machine where the benchmark client was installed:

```shell
ssh ec2-user@$(terraform output client_ssh_host)
```

Start the load:

```shell
cd /opt/benchmark
sudo bin/benchmark --drivers driver-pulsar/pulsar.yaml workloads/*.yaml
```

All the JSON result files will be written in the current directory. Once
the test is done the results can be aggregated and used with the
`create_charts.py` script.
