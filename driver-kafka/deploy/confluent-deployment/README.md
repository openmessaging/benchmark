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

