public_key_path = "~/.ssh/kafka_aws.pub"
region          = "us-west-2"
profile         = "benchmark"
az              = "us-west-2a"
ami             = "ami-08970fb2e5767e3b8" // RHEL-8

instance_types = {
  "gateway"   = "m5.xlarge"
  "kafka"     = "i3en.2xlarge" # "i3en.6xlarge"
  "zookeeper" = "i3en.2xlarge"
  "client"    = "m5n.8xlarge"
}

num_instances = {
  "client"    = 4
  "gateway"   = 3
  "kafka"     = 3
  "zookeeper" = 3
}
