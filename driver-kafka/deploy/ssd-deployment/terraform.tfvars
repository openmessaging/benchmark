public_key_path = "~/.ssh/kafka_aws.pub"
region          = "us-west-2"
az              = "us-west-2a"
ami             = "ami-9fa343e7" // RHEL-7.4

instance_types = {
  "kafka"     = "i3en.6xlarge"
  "zookeeper" = "t2.small"
  "client"    = "m5n.8xlarge"
}

num_instances = {
  "client"    = 4
  "kafka"     = 3
  "zookeeper" = 3
}
