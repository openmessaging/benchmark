public_key_path = "~/.ssh/pulsar_aws.pub"
region          = "us-east-1"
#region          = "us-west-2"
ami             = "ami-26ebbc5c" // RHEL-7.4

instance_types = {
  "pulsar"      = "i3.4xlarge"
  "zookeeper"   = "t2.small"
  "client"      = "c5.2xlarge"
  "prometheus"  = "t2.small"
}

num_instances = {
  "client"      = 4
  "pulsar"      = 3
  "zookeeper"   = 3
  "prometheus"  = 1
}
