variable "public_key_path" {
  default = "~/.ssh/openmessaging-benchmark.pub"

  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.

Example: ~/.ssh/openmessaging-benchmark.pub
DESCRIPTION
}

variable "platform" {
  description = "The name of the messaging platform"
}

variable "key_name" {
  default     = "openmessaging-benchmark-key"
  description = "Desired name prefix for the AWS key pair"
}

variable "region" {
  default = "us-west-2"
}

variable "ami" {
  default = "ami-9fa343e7" // RHEL-7.4
}

variable "instance_types" {
  type = "map"

  default = {
    "messaging"  = "i3.4xlarge"
    "zookeeper"  = "t2.small"
    "client"     = "c5.2xlarge"
    "prometheus" = "t2.small"
  }
}

variable "num_instances" {
  type = "map"

  default = {
    "client"     = 4
    "messaging"  = 3
    "zookeeper"  = 0
    "prometheus" = 0
  }
}
