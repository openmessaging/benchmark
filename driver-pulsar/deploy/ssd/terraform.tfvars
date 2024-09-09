public_key_path = "~/.ssh/pulsar_aws.pub"
region          = "us-east-2"
az              = "us-east-2a"
ami             = "ami-012e6364f6bd17628"
user            = "ubuntu"
spot            = false

instance_types = {
  "pulsar"     = "i3en.6xlarge"
  "zookeeper"  = "i3en.2xlarge"
  "client"     = "m5n.8xlarge"
  "prometheus" = "t2.large"
}

num_instances = {
  "client"     = 4
  "pulsar"     = 5
  "zookeeper"  = 3
  "prometheus" = 1
}
