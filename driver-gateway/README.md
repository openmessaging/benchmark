# Conduktor Gateway benchmarks

NOTE: This is a slightly modified version of Apache Kafka Benchmarks for Conduktor Gateway

## How to run the benchmarks

Pre-requisites:
- terraform 1.8+ (older version may also work)
- ansible 2.16 (older version may also work)
- AWS CLI setup with account with permission on EC2, VPC and API Gateway

1. Create an SSH key named `kafka-aws` in `~/.ssh/` with `ssh-keygen -f ~/.ssh/kafka_aws` **without passphrase**
2. Build the`openmessaging-benchmark` project with `mvn clean install`
3. Go to the `driver-gateway/deploy/ssd-deployment` module
4. Initialize terraform with `terraform init`
5. Create the infrastructure with `terraform apply`
   1. You can change the value of num_instances using -var='num_instances={"client"=5, "gateway"=4, "kafka"=2}'
   2. You might also need to change the AWS profile with -var='aws_profile=your_profile'
   3. With SSO the session does expire so you might need to run `aws sso login --profile your_profile` before running terraform
6. Export your harbor creds https://harbor.cdkt.dev/
   1. Click your name in the top right corner and select User Profile then take your username and CLI secret
   2. export REGISTRY_USERNAME=<registry login>
   3. export REGISTRY_PASSWORD=<registry api token>
7. Setup nodes with `ansible-playbook --user ec2-user --inventory-file inventory.ini deploy.yaml`
8. Connect to one benchmark worker node with `ssh -i ~/.ssh/kafka_aws ec2-user@$(terraform output client_ssh_host | tr -d '"')`
9. Go to benchmark directory with `cd /opt/benchmark`
10. Run the benchmark with `sudo bin/benchmark --drivers driver-gateway/gateway-latency.yaml workloads/100-topic-4-partitions-1kb-4p-4c-500k.yaml;`
11. Download reports from nodes with `scp -i ~/.ssh/kafka_aws ec2-user@$(terraform output client_ssh_host | tr -d '"'):/opt/benchmark/*.json ./reports`

