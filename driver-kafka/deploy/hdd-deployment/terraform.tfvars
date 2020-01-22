public_key_path = "~/.ssh/kafka_aws.pub"
region          = "us-west-2"
az              = "us-west-2a"
ami             = "ami-9fa343e7" // RHEL-7.4

instance_types = {
  "kafka"     = "d2.4xlarge"
  "zookeeper" = "t2.small"
  "client"    = "c5.4xlarge"
}

num_instances = {
  "client"    = 4
  "kafka"     = 3
  "zookeeper" = 3
}
