public_key_path = "~/.ssh/pulsar_aws.pub"
region          = "us-west-2"
az              = "us-west-2a"
ami             = "ami-08970fb2e5767e3b8" // RHEL-8

instance_types = {
  "pulsar"     = "i3en.6xlarge"
  "zookeeper"  = "t2.xlarge"
  "client"     = "m5n.8xlarge"
  "prometheus" = "t2.large"
}

num_instances = {
  "client"     = 4
  "pulsar"     = 3
  "zookeeper"  = 3
  "prometheus" = 1
}
