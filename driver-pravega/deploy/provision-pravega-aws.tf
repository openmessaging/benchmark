provider "aws" {
  region  = "${var.region}"
  version = "~> 2.7"
}

provider "random" {
  version = "~> 2.1"
}

variable "public_key_path" {
  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.

Example: ~/.ssh/pravega_aws.pub
DESCRIPTION
}

resource "random_id" "hash" {
  byte_length = 8
}

variable "key_name" {
  default     = "pravega-benchmark-key"
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

# Create a VPC to launch our instances into
resource "aws_vpc" "benchmark_vpc" {
  cidr_block = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "Pravega-Benchmark-VPC-${random_id.hash.hex}"
  }
}

# Create an internet gateway to give our subnet access to the outside world
resource "aws_internet_gateway" "pravega" {
  vpc_id = "${aws_vpc.benchmark_vpc.id}"
}

# Grant the VPC internet access on its main route table
resource "aws_route" "internet_access" {
  route_table_id         = "${aws_vpc.benchmark_vpc.main_route_table_id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.pravega.id}"
}

# Create a subnet to launch our instances into
resource "aws_subnet" "benchmark_subnet" {
  vpc_id                  = "${aws_vpc.benchmark_vpc.id}"
  cidr_block              = "10.0.0.0/24"
  map_public_ip_on_launch = true
}

resource "aws_security_group" "benchmark_security_group" {
  name   = "terraform-pravega-${random_id.hash.hex}"
  vpc_id = "${aws_vpc.benchmark_vpc.id}"

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
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

  # Prometheus access
  # TODO: 9090 is also used by Pravega Controller. Need to filter below.
  ingress {
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Grafana dashboard access
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Benchmark-Security-Group-${random_id.hash.hex}"
  }
}

resource "aws_key_pair" "auth" {
  key_name   = "${var.key_name}-${random_id.hash.hex}"
  public_key = "${file(var.public_key_path)}"
}

resource "aws_instance" "zookeeper" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["zookeeper"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["zookeeper"]}"

  tags = {
    Name = "zk-${count.index}"
  }
}

resource "aws_instance" "controller" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["controller"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["controller"]}"

  tags = {
    Name = "controller-${count.index}"
  }
}

resource "aws_instance" "bookkeeper" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["bookkeeper"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["bookkeeper"]}"

  tags = {
    Name = "bookkeeper-${count.index}"
  }
}

resource "aws_instance" "client" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["client"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["client"]}"

  tags = {
    Name = "pravega-client-${count.index}"
  }
}

resource "aws_instance" "metrics" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["metrics"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["metrics"]}"

  tags = {
    Name = "metrics-${count.index}"
  }
}

# Change the EFS provisioned TP here
resource "aws_efs_file_system" "tier2" {
  throughput_mode = "provisioned"
  provisioned_throughput_in_mibps = 1000
  tags = {
    Name = "pravega-tier2"
  }
}

resource "aws_efs_mount_target" "tier2" {
  file_system_id  = "${aws_efs_file_system.tier2.id}"
  subnet_id       = "${aws_subnet.benchmark_subnet.id}"
  security_groups = ["${aws_security_group.benchmark_security_group.id}"]
}

output "client_ssh_host" {
  value = "${aws_instance.client.0.public_ip}"
}

output "metrics_host" {
  value = "${aws_instance.metrics.0.public_ip}"
}

output "controller_0_ssh_host" {
  value = "${aws_instance.controller.0.public_ip}"
}

output "bookkeeper_0_ssh_host" {
  value = "${aws_instance.bookkeeper.0.public_ip}"
}

output "zookeeper_0_ssh_host" {
  value = "${aws_instance.zookeeper.0.public_ip}"
}
