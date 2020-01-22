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
