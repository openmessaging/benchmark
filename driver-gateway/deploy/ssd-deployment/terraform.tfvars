public_key_path = "~/.ssh/kafka_aws.pub"
region          = "us-west-2"
profile         = "benchmark"
az              = "us-west-2a"
ami             = "ami-08970fb2e5767e3b8" // RHEL-8

instance_types = {
  "gateway"   = "m5n.xlarge" # 16.0 GiB	4 vCPUs	EBS only Up to 25 Gigabit
  "kafka"     = "i3en.2xlarge" # 64.0 GiB	8 vCPUs	5000 GB (2 * 2500 GB NVMe SSD)	Up to 25 Gigabit  # "i3en.6xlarge"
  "zookeeper" = "i3en.2xlarge"
  "client"    = "m5n.8xlarge"
}

num_instances = {
  "client"    = 4
  "gateway"   = 3
  "kafka"     = 3
  "zookeeper" = 3
}
