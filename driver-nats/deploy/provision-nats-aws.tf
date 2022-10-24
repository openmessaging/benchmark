terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
    }
    random = {
      source  = "hashicorp/random"
    }
  }
}

variable "public_key_path" {
  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.

Example: ~/.ssh/nats_aws.pub
DESCRIPTION
}

resource "random_id" "hash" {
  byte_length = 8
}

variable "key_name" {
  default     = "benchmark-key-nats"
  description = "Desired name of AWS key pair"
}

variable "instance_types" {
  type = map(string)
}

variable "num_instances" {
  type = map(string)
}

variable "region" {}
variable "az" {}
variable "ami" {}

provider "aws" {
  region = var.region
}

# Create a VPC to launch our instances into
resource "aws_vpc" "benchmark_vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "Benchmark-VPC-NATS-${random_id.hash.hex}"
  }
}

# Create an internet gateway to give our subnet access to the outside world
resource "aws_internet_gateway" "default" {
  vpc_id = aws_vpc.benchmark_vpc.id
}

# Grant the VPC internet access on its main route table
resource "aws_route" "internet_access" {
  route_table_id         = aws_vpc.benchmark_vpc.main_route_table_id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.default.id
}

# Create a subnet to launch our instances into
resource "aws_subnet" "benchmark_subnet" {
  vpc_id                  = aws_vpc.benchmark_vpc.id
  cidr_block              = "10.0.0.0/24"
  map_public_ip_on_launch = true
  availability_zone       = var.az
}

resource "aws_security_group" "benchmark_security_group" {
  name   = "terraform-nats-${random_id.hash.hex}"
  vpc_id = aws_vpc.benchmark_vpc.id

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Prometheus access
  ingress {
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Grafana access
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # All ports open within the VPC
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Benchmark-Security-Group-NATS-${random_id.hash.hex}"
  }
}

resource "aws_key_pair" "auth" {
  key_name   = "${var.key_name}-${random_id.hash.hex}"
  public_key = file(var.public_key_path)
}

resource "aws_instance" "nats" {
  ami                    = var.ami
  instance_type          = var.instance_types["nats"]
  key_name               = aws_key_pair.auth.id
  subnet_id              = aws_subnet.benchmark_subnet.id
  vpc_security_group_ids = [aws_security_group.benchmark_security_group.id]
  count                  = var.num_instances["nats"]

  tags = {
    Name = "nats_${count.index}"
  }
}

resource "aws_instance" "client" {
  ami                    = var.ami
  instance_type          = var.instance_types["client"]
  key_name               = aws_key_pair.auth.id
  subnet_id              = aws_subnet.benchmark_subnet.id
  vpc_security_group_ids = [aws_security_group.benchmark_security_group.id]
  count                  = var.num_instances["client"]

  tags = {
    Name = "nats_client_${count.index}"
  }
}

resource "aws_instance" "prometheus" {
  ami                    = var.ami
  instance_type          = var.instance_types["prometheus"]
  key_name               = aws_key_pair.auth.id
  subnet_id              = aws_subnet.benchmark_subnet.id
  vpc_security_group_ids = [
    aws_security_group.benchmark_security_group.id]
  count = var.num_instances["prometheus"]

  tags = {
    Name = "prometheus_${count.index}"
  }
}

output "prometheus_host" {
  value = aws_instance.prometheus.0.public_ip
}

output "client_ssh_host" {
  value = aws_instance.client[0].public_ip
}
