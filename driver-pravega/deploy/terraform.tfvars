public_key_path = "~/.ssh/pravega_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4 us-west-2

instance_types = {
  "controller"   = "m5.large"
  "bookkeeper"   = "i3.4xlarge"
  "zookeeper"    = "t2.small"
  "client"       = "c5.2xlarge"
  "metrics"      = "t2.large"
}

num_instances = {
  "controller"   = 1
  "bookkeeper"   = 3
  "zookeeper"    = 3
  "client"       = 2
  "metrics"      = 1
}
