public_key_path = "~/.ssh/bookkeeper_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4

instance_types = {
  "bookkeeper"  = "i3en.6xlarge"
  "zookeeper"   = "t2.small"
  "client"      = "m5n.8xlarge"
  "prometheus"  = "t2.small"
}

num_instances = {
  "client"      = 4
  "bookkeeper"  = 3
  "zookeeper"   = 3
  "prometheus"  = 1
}
