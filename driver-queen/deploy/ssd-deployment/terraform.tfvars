public_key_path = "~/.ssh/queen_aws.pub"
region          = "us-west-2"
az              = "us-west-2a"
ami             = "ami-08970fb2e5767e3b8" // RHEL-8

// The same machines as driver-kafka's ssd-deployment, so the two are compared on equal hardware.
instance_types = {
  "queen"  = "i3en.6xlarge"
  "client" = "m5n.8xlarge"
}

num_instances = {
  "client" = 4
  "queen"  = 3
}
