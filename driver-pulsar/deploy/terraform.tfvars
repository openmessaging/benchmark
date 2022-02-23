public_key_path = "~/.ssh/pulsar_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4
s3_bucket	= "lunastreaming-omb-testing-1"

instance_types = {
  "pulsar"      = "i3en.6xlarge"
  "zookeeper"   = "t3.small"
  "client"      = "c5n.4xlarge"
  "prometheus"  = "t3.small"
}

num_instances = {
  "client"      = 6
  "pulsar"      = 3
  "zookeeper"   = 3
  "prometheus"  = 1
}
