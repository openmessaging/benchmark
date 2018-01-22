public_key_path = "~/.ssh/pulsar_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4

instance_types = {
  "pulsar"    = "i3.4xlarge"
  "zookeeper" = "t2.small"
  "client"    = "c4.8xlarge"
}

num_instances = {
  "pulsar"    = 3
  "zookeeper" = 3
}