public_key_path = "~/.ssh/rabbitmq_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4

instance_types = {
  "rabbitmq"  = "i3.4xlarge"
  "client"    = "c4.8xlarge"
}

num_instances = {
  "rabbitmq"    = 3
  "client"      = 1
}