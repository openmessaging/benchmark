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
