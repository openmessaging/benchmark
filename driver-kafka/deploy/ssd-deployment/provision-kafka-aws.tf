provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
  version = "3.50"
}

provider "random" {
  version = "3.1"
}

variable "public_key_path" {
  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.

Example: ~/.ssh/kafka_aws.pub
DESCRIPTION
}

resource "random_id" "hash" {
  byte_length = 8
}

variable "key_name" {
  default     = "kafka-benchmark-key"
  description = "Desired name prefix for the AWS key pair"
}

variable "region" {}

variable "profile" {}

variable "ami" {}

variable "az" {}

variable "instance_types" {
  type = map(string)
}

variable "num_instances" {
  type = map(string)
}

# Create a VPC to launch our instances into
resource "aws_vpc" "benchmark_vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "Kafka_Benchmark_VPC_${random_id.hash.hex}"
  }
}

# Create an internet gateway to give our subnet access to the outside world
resource "aws_internet_gateway" "kafka" {
  vpc_id = "${aws_vpc.benchmark_vpc.id}"
}

# Grant the VPC internet access on its main route table
resource "aws_route" "internet_access" {
  route_table_id         = "${aws_vpc.benchmark_vpc.main_route_table_id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.kafka.id}"
}

# Create a subnet to launch our instances into
resource "aws_subnet" "benchmark_subnet" {
  vpc_id                  = "${aws_vpc.benchmark_vpc.id}"
  cidr_block              = "10.0.0.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "${var.az}"
}

resource "aws_security_group" "benchmark_security_group" {
  name   = "terraform-kafka-${random_id.hash.hex}"
  vpc_id = "${aws_vpc.benchmark_vpc.id}"

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port = -1
    to_port = -1
    protocol = "icmp"
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
    Name      = "zk_${count.index}"
    Benchmark = "Kafka"
  }
}

resource "aws_instance" "kafka" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["kafka"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["kafka"]}"

  tags = {
    Name      = "kafka_${count.index}"
    Benchmark = "Kafka"
  }
}

resource "aws_instance" "kafka" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["gateway"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["gateway"]}"

  tags = {
    Name      = "gateway_${count.index}"
    Benchmark = "Gateway"
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
    Name      = "kafka_client_${count.index}"
    Benchmark = "Kafka"
  }
}

output "kafka_ssh_host" {
  value = "${aws_instance.kafka.0.public_ip}"
}

output "client_ssh_host" {
  value = "${aws_instance.client.0.public_ip}"
}

resource "local_file" "inventory" {
  filename = "./inventory.ini"
  content = <<EOF
[all]
${aws_instance.zookeeper.0.public_ip} private_ip=${aws_instance.zookeeper.0.private_ip}
${aws_instance.zookeeper.1.public_ip} private_ip=${aws_instance.zookeeper.1.private_ip}
${aws_instance.zookeeper.2.public_ip} private_ip=${aws_instance.zookeeper.2.private_ip}
${aws_instance.kafka.0.public_ip} private_ip=${aws_instance.kafka.0.private_ip}
${aws_instance.kafka.1.public_ip} private_ip=${aws_instance.kafka.1.private_ip}
${aws_instance.kafka.2.public_ip} private_ip=${aws_instance.kafka.2.private_ip}
${aws_instance.client.0.public_ip} private_ip=${aws_instance.client.0.private_ip}
${aws_instance.client.1.public_ip} private_ip=${aws_instance.client.1.private_ip}
${aws_instance.client.2.public_ip} private_ip=${aws_instance.client.2.private_ip}
${aws_instance.client.3.public_ip} private_ip=${aws_instance.client.3.private_ip}
${aws_instance.gateway.0.public_ip} private_ip=${aws_instance.gateway.0.private_ip}
${aws_instance.gateway.1.public_ip} private_ip=${aws_instance.gateway.1.private_ip}
${aws_instance.gateway.2.public_ip} private_ip=${aws_instance.gateway.2.private_ip}

[zookeeper]
${aws_instance.zookeeper.0.public_ip} private_ip=${aws_instance.zookeeper.0.private_ip}
${aws_instance.zookeeper.1.public_ip} private_ip=${aws_instance.zookeeper.1.private_ip}
${aws_instance.zookeeper.2.public_ip} private_ip=${aws_instance.zookeeper.2.private_ip}

[kafka]
${aws_instance.kafka.0.public_ip} private_ip=${aws_instance.kafka.0.private_ip}
${aws_instance.kafka.1.public_ip} private_ip=${aws_instance.kafka.1.private_ip}
${aws_instance.kafka.2.public_ip} private_ip=${aws_instance.kafka.2.private_ip}

[gateway]
${aws_instance.gateway.0.public_ip} private_ip=${aws_instance.gateway.0.private_ip}
${aws_instance.gateway.1.public_ip} private_ip=${aws_instance.gateway.1.private_ip}
${aws_instance.gateway.2.public_ip} private_ip=${aws_instance.gateway.2.private_ip}

[client]
${aws_instance.client.0.public_ip} private_ip=${aws_instance.client.0.private_ip}
${aws_instance.client.1.public_ip} private_ip=${aws_instance.client.1.private_ip}
${aws_instance.client.2.public_ip} private_ip=${aws_instance.client.2.private_ip}
${aws_instance.client.3.public_ip} private_ip=${aws_instance.client.3.private_ip}
  EOF
}
