variable "public_key_path" {
  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.

Example: ~/.ssh/kafka_aws.pub
DESCRIPTION
}

variable "platform" {
  description = "The name of the messaging platform"
}

variable "key_name" {
  default     = "openmessaging-benchmark-key"
  description = "Desired name prefix for the AWS key pair"
}

variable "region" {}

variable "ami" {}

variable "instance_types" {
  type = "map"
}

variable "num_instances" {
  type = "map"
}
