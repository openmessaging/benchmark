public_key_path = "~/.ssh/rabbitmq_aws.pub"
region          = "us-west-2"
ami             = "ami-08970fb2e5767e3b8" // RHEL-8

instance_types = {
  "rabbitmq"  = "i3en.6xlarge"
  "client"    = "m5n.8xlarge"
}

num_instances = {
  "rabbitmq"    = 3
  "client"      = 4
}
