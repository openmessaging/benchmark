# Confluent deployment

This README provides you with step-by-step instructions for running OpenMessaging Benchmark (OMB) with Confluent deployment.

## 0) Prerequisites

> :warning: The results reported in the blog were achieved with a Dedicated Confluent Cloud cluster in AWS with
> VPC-peered connection to the OMB client VPC. Please follow the steps below to recreate that setup.
> Once it is created, follow the guide below to create the OMB client VPC.
> - [Details about Dedicated Cluster in Confluent Cloud](https://docs.confluent.io/cloud/current/clusters/cluster-types.html#types-dedicated-clusters)
> - [Create VPC peering connection with Confluent Cloud in AWS](https://docs.confluent.io/cloud/current/networking/peering/aws-peering.html)
>
> :warning: Confluent Cloud limits the rate of topic creation and deletion to prevent DDOS from Kafka client. In the real
> world, this is necessary and is not an issue. However, this slows down the preparation stage when running OpenMessaging
> Benchmark (OMB), especially when creating large number of topics. If you run this test against Confluent Cloud and the
> preparation stage is too slow, Confluent Support can increase the limit for your cluster.

1. Have an AWS account and set up your profile.
2. Determine the region to run the test.
3. Create a dedicated Confluent Cloud cluster with API Key and Secret and VPC Peering connection in the chosen region.
4. Install `ansible`.
5. Install `terraform`.
6. Install `terraform-inventory`.

Following are example commands in Mac OSX:

```bash
brew install ansible
brew install terraform
brew install terraform-inventory
```

7. Create SSH key pair (only need to do this once).

```bash
ssh-keygen -f ~/.ssh/omb
```

8. Checkout [Confluent fork of OpenMessaging Benchmark](https://github.com/confluentinc/benchmark)
9. Switch to branch `10x-performance-blog`
10. Compile OMB libraries.

```bash
git clone https://github.com/confluentinc/benchmark
cd benchmark
git checkout 10x-performance-blog
mvn clean install -Dlicense.skip=true
```

## 1) Create a deployment setup with Terraform

1. Start from the `driver-kafka/deploy/confluent-deployment` directory.

```bash
cd driver-kafka/deploy/confluent-deployment
```

2. Copy the `terraform.tfvars.tpl` file and the `ansible-config.yaml` file from one of the `*cku` directory you want to test.
   There is `2cku` and `28cku` setup available. You can only use one setup at a time. The instance
   types and number of instances are _equivalent_ in processing power and memory size to the ones used in Confluent Cloud.

```bash
cp 2cku/* .
vi terraform.tfvars.tpl
```

3. Then edit `terraform.tfvars.tpl` the following variable values.

```text
region          = "AWS REGION ID"
ami             = "AWS AMI ID"
profile         = "AWS PROFILE"
subnet_cidrs    = ["AWS SUBNET 1", "AWS SUBNET 2", "AWS SUBNET 3"]
azs             = ["AWS AZ 1", "AWS AZ 2", "AWS AZ 3"]
```

Example values:

```text
region          = "eu-central-2"
ami             = "ami-0f0fa69ebc3b199bb"
profile         = "test-profile"
keypair_id      = "test-omb-eu-central-2"
subnet_cidrs    = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
azs             = ["eu-central-2a", "eu-central-2b", "eu-central-2c"]
```

> :warning: Please use ami `ami-0f0fa69ebc3b199bb` in `eu-central-2` to reproduce the result.

4. Rename `terraform.tfvars.tpl` to `terraform.tfvars` when you have filled in the details.

```bash
mv terraform.tfvars.tpl terraform.tfvars
```

5. Copy `ccloud.properties.tpl` file to `ccloud.properties` and update the `bootstrap.server` and `sasl.jaas.config` values with your Confluent Cloud
   details

```bash
cp ccloud.properties.tpl ccloud.properties
vi ccloud.properties
```

6. Run Terraform

If you haven't previously done so, run the following:

```bash
terraform init
```

Now run Terraform apply to provision the EC2 instances.

```bash
terraform apply
```

7. Run Ansible

Generate SSL certificates, if you have not done so (only need to do this once).

```bash
ansible-playbook setup-ssl-certs.yaml
```

Deploy the clusters.

```bash
ansible-playbook deploy.yaml --extra-vars "@ansible-config.yaml"
```

If Ansible fails, check why it fails. Most of the time it fails because of connection failure. If this is the case, just
run Ansible again and again until it shows no failure.

Ansible can take a while to complete (up to 1 hour) depending on your deployment. The higher the CKU, the longer it takes.

Once Ansible has finished all steps successfully, you can start running test workload.

## 2) Start a Workload

> :warning: Before running any test workloads, you have to setup SSH agent because many commands use SSH. You have to do
> the following command once per shell where you plan to run test workloads.

```bash
eval $(ssh-agent)
ssh-add ~/.ssh/omb
```

> :warning: Then ensure there is network connection between OMB client hosts and Confluent Cloud cluster by following
> [Networking in Confluent Cloud](https://docs.confluent.io/cloud/current/networking/overview.html) documentation. In
> short, you can do this by `ssh` into one of the client node and run `openssl` as follows:

```bash
ssh ec2-user@$(terraform-inventory --list . | jq -rM '.client_0[]')
openssl s_client -connect pkc-xxxx.confluent.cloud:9092 -debug
```

> If it prints the key and the connection details, then it has successfully established connection to the Confluent Cloud
> cluster. Otherwise, please check if the network configurations (e.g. routing table, security group, etc) are correct.

1. Start from the root of OMB directory. If you are in `confluent-deployment` directory, you need to do:

```bash
cd ../../..
```

2. Generate Confluent workloads.

```bash
./confluent-bin/create-blog-workloads.sh
```

3. Identify a workload file you'd like to run for the CKU size you created under the `workloads` directory.

4. Determine whether you want to run the workload against Apache Kafka or Confluent Cloud.

5. Run the workload. Following examples uses `confluent-blog-workloads-2cku-1p-1c-2Kb-45topic-200part-100MBps.yaml`
   workload against a 2 CKU cluster.

- To run against Apache Kafka:

```bash
./confluent-bin/run-workloads.sh ak workloads/confluent-blog-workloads-2cku-1p-1c-2Kb-45topic-200part-100MBps.yaml
```

- To run against Confluent Cloud:

```bash
./confluent-bin/run-workloads.sh cc workloads/confluent-blog-workloads-2cku-1p-1c-2Kb-45topic-200part-100MBps.yaml
```

Note: Connection to Confluent Cloud will use the details specified in `ccloud.properties` in the previous step.

## 3) Monitor progress with Prometheus

You can follow the progress from the command line and also looking at the Prometheus dashboards. You can get the IP of the
Prometheus server from the `hosts.ini` file or with the following command:

```bash
terraform-inventory --list . | jq -rM '.prometheus[]'
```

The EC2 security group only allows access from your IP. And prometheus port is 9090.

## 4) Collect the results

At the end the OMB benchmark program will write summarized results to a json file. The `run-workload.sh` will
automatically download this and the output of the run into a directory under `results/`, under the OMB root directory.

## 5) Tear down your environments!

If you use temporary credentials, remember you may need to refresh them first.

From the same Terraform directory that you ran the `apply` command, run: `terraform destroy --auto-approve` and use the
same owner tag value when prompted.

```bash
cd driver-kafka/deploy/confluent-deployment
terraform destroy
```

# Troubleshooting

## Could not find matching plugin: ''sudo''

```
task path: /Users/myuser/git/omb-internal-10x/driver-kafka/deploy/confluent-deployment/deploy.yaml:15
fatal: [18.117.187.18]: FAILED! => {
    "msg": "Invalid become method specified, could not find matching plugin: ''sudo''. Use `ansible-doc -t become -l` to list available plugins."
}
```

This error means that ansible can't find the local (ie on the machine running the ansible script) sudo plugin.

To resulve this, update the `driver-kafka/deploy/confluent-deployment/ansible.cfg` as described in the comments in that file, taking into account the version of the sudo plugin that you have available in your installation.

You can find the plugin version using `ansible-doc -t become -l`.  If the output contains `sudo` then the version is the older style and the config file should look like:

```
[defaults]
host_key_checking=false
private_key_file=~/.ssh/omb
timeout=60
inventory = hosts.ini

[privilege_escalation]
become=true
become_method='sudo'
become_user='root'

```

If the output only contains `ansible.builtin.sudo` and not `sudo`, then the version is the newer style, and the configuration needs updating to be:

```
[defaults]
host_key_checking=false
private_key_file=~/.ssh/omb
timeout=60
inventory = hosts.ini

[privilege_escalation]
become=true
become_exe=sudo
become_user='root'

[sudo_become_plugin]
executable = sudo
user = root
```

If you still see `Invalid become method specified, could not find matching plugin: ''sudo''.` it may be because your python and ansible environments are misconfigured.  The following steps, to run using `virtualenv` can be followed to set up a consistent environment:

```
pyenv install 3.9.15
virtualenv venv --python=/Users/myuser/.pyenv/versions/3.9.15/bin/python
source venv/bin/activate
python --version
pip install ansible
```

## Could not find or access...

The step `TASK [Copy benchmark code]` throws the following error when running `ansible-playbook deploy.yaml --extra-vars "@ansible-config.yaml"`

```
fatal: [52.15.93.212]: FAILED! => {"changed": false, "msg": "Could not find or access '../../../package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz'\nSearched in:\n\t/Users/myuser/Coding/omb-internal-10x/driver-kafka/deploy/confluent-deployment/files/../../../package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz\n\t/Users/myuser/Coding/omb-internal-10x/driver-kafka/deploy/confluent-deployment/../../../package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz\n\t/Users/myuser/Coding/omb-internal-10x/driver-kafka/deploy/confluent-deployment/files/../../../package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz\n\t/Users/myuser/Coding/omb-internal-10x/driver-kafka/deploy/confluent-deployment/../../../package/target/openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz on the Ansible Controller.\nIf you are using a module and expect the file to exist on the remote, see the remote_src option"}
An exception occurred during task execution. To see the full traceback, use -vvv. The error was: If you are using a module and expect the file to exist on the remote, see the remote_src option

```

Run

```
mvn spotless:apply (may not be needed)
mvn clean install -Dlicense.skip=true
```

and then rerun `ansible-playbook deploy.yaml --extra-vars "@ansible-config.yaml"`

## Error unpacking rpm package

```
"msg": "Error unpacking rpm package 2000:jdk-17-17.0.10-11.x86_64\n",
"rc": 1,
"results": [
    "Loaded plugins: priorities, update-motd, versionlock\nExamining /home/ec2-user/.ansible/tmp/ansible-tmp-1705925072.712148-70493-89997865592259/jdk-17_linux-x64_binxocyZ4.rpm: 2000:jdk-17-17.0.10-11.x86_64\nMarking /home/ec2-user/.ansible/tmp/ansible-tmp-1705925072.712148-70493-89997865592259/jdk-17_linux-x64_binxocyZ4.rpm to be installed\nResolving Dependencies\n--> Running transaction check\n---> Package jdk-17.x86_64 2000:17.0.10-11 will be installed\n--> Finished Dependency Resolution\n\nDependencies Resolved\n\n================================================================================\n Package   Arch      Version               Repository                      Size\n================================================================================\nInstalling:\n jdk-17    x86_64    2000:17.0.10-11       /jdk-17_linux-x64_binxocyZ4    303 M\n\nTransaction Summary\n================================================================================\nInstall  1 Package\n\nTotal size: 303 M\nInstalled size: 303 M\nDownloading packages:\nRunning transaction check\nRunning transaction test\nTransaction test succeeded\nRunning transaction\n  Installing : 2000:jdk-17-17.0.10-11.x86_64                                1/1 \nerror: unpacking of archive failed on file /usr/lib/jvm/jdk-17-oracle-x64/jmods/java.base.jmod;65ae59d5: cpio: read\n  Verifying  : 2000:jdk-17-17.0.10-11.x86_64                                1/1 \n\nFailed:\n  jdk-17.x86_64 2000:17.0.10-11                                                 \n\nComplete!\n"
]
```

This is a transient error - try re-running `ansible-playbook deploy.yaml --extra-vars "@ansible-config.yaml"`
