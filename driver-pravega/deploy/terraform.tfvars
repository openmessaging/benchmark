public_key_path = "~/.ssh/pravega_aws.pub"
region          = "us-east-2"
ami             = "ami-0bb2449c2217cb9b0" // RHEL-7.9 us-east-2

instance_types = {
  "controller"   = "m5.large"
  "bookkeeper"   = "i3en.2xlarge"
  "zookeeper"    = "t2.small"
  "client"       = "m5n.xlarge"
  "metrics"      = "t2.large"
}

num_instances = {
  "controller"   = 1
  "bookkeeper"   = 3
  "zookeeper"    = 3
  "client"       = 1
  "metrics"      = 1
}
