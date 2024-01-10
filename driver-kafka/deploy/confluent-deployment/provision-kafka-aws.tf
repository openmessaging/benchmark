terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.1"
    }
  }
}


provider "aws" {
  region  = "${var.region}"
}

resource "random_id" "hash" {
  byte_length = 8
}

variable "owner" {}
variable "region" {}
variable "ami" {}
variable "profile" {}
variable "gp3_size_gb" {}
variable "gp3_throughput_mb" {}
variable "gp3_iops" {}

variable "key_name" {
  default     = "omb-key"
  description = "Desired name prefix for the AWS key pair"
}

variable "public_key_path" {
  default     = "~/.ssh/omb.pub"
  description = "Path to the SSH public key to be used for authentication. Example: ~/.ssh/omb.pub"
}

variable "instance_types" {
  type = map
}

variable "num_instances" {
  type = map
}

variable "subnet_cidrs" {
 type        = list(string)
 description = "Subnet CIDR values"
 default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "azs" {
 type        = list(string)
 description = "Availability Zones"
 default     = ["us-east-2a", "us-east-2b", "us-east-2c"]
}

# Create Key Pair
resource "aws_key_pair" "auth" {
  key_name   = "${var.key_name}-${random_id.hash.hex}"
  public_key = "${file(var.public_key_path)}"
}

# Create a VPC to launch our instances into
resource "aws_vpc" "benchmark_vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "omb-vpc-${random_id.hash.hex}"
    owner = "${var.owner}"
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
  count                   = length(var.subnet_cidrs)
  vpc_id                  = "${aws_vpc.benchmark_vpc.id}"
  cidr_block              = element(var.subnet_cidrs, count.index)
  availability_zone       = element(var.azs, count.index)
  map_public_ip_on_launch = true

  tags = {
    Name = "omb-subnet-${random_id.hash.hex}-${count.index}"
    owner = "${var.owner}"
  }
}

# Get public IP of this machine
data "http" "myip" {
  url = "http://ipv4.icanhazip.com"
}

resource "aws_security_group" "benchmark_security_group" {
  name   = "terraform-kafka-${random_id.hash.hex}"
  vpc_id = "${aws_vpc.benchmark_vpc.id}"

  # All ports open within the VPC
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  # All ports open to this machine
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["${chomp(data.http.myip.response_body)}/32"]
  }

  #Prometheus/Dashboard access
  ingress {
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["${chomp(data.http.myip.response_body)}/32"]
  }
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["${chomp(data.http.myip.response_body)}/32"]
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "omb-sg-${random_id.hash.hex}"
    owner = "${var.owner}"
  }
}

resource "aws_instance" "controller" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["controller"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet[count.index % length(aws_subnet.benchmark_subnet)].id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["controller"]}"
  associate_public_ip_address = true
  monitoring             = true

  root_block_device {
    volume_size           = 50
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  ebs_block_device {
    device_name           = "/dev/xvdb"
    volume_type           = "gp3"
    volume_size           = 250
    delete_on_termination = true
    encrypted             = true
  }

  tags = {
    Name  = "omb-controller-${random_id.hash.hex}-${count.index}"
    owner = "${var.owner}"
  }
}

resource "aws_instance" "kafka" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["kafka"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet[count.index % length(aws_subnet.benchmark_subnet)].id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["kafka"]}"
  associate_public_ip_address = true
  monitoring             = true

  root_block_device {
    volume_size           = 50
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  ebs_block_device {
    device_name           = "/dev/xvdb"
    volume_type           = "gp3"
    volume_size           = "${var.gp3_size_gb}"
    iops                  = "${var.gp3_iops}"
    throughput            = "${var.gp3_throughput_mb}"
    delete_on_termination = true
    encrypted             = true
  }

  tags = {
    Name  = "omb-kafka-${random_id.hash.hex}-${count.index}"
    owner = "${var.owner}"
  }
}

resource "aws_instance" "client" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["client"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet[count.index % length(aws_subnet.benchmark_subnet)].id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["client"]}"
  monitoring             = true

  root_block_device {
    volume_size           = 50
    volume_type           = "gp2"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "omb-client-${random_id.hash.hex}-${count.index}"
    owner = "${var.owner}"
  }
}

resource "aws_instance" "prometheus" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["prometheus"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet[count.index % length(aws_subnet.benchmark_subnet)].id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["prometheus"]}"

  root_block_device {
    volume_size           = 250
    volume_type           = "gp2"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "omb-prometheus-${random_id.hash.hex}-${count.index}"
    owner = "${var.owner}"
  }
}

resource "local_file" "hosts_ini" {
  content = templatefile("${path.module}/hosts_ini.tpl",
    {
      kafka_public_ips             = aws_instance.kafka.*.public_ip
      kafka_private_ips            = aws_instance.kafka.*.private_ip
      controller_public_ips        = aws_instance.controller.*.public_ip
      controller_private_ips       = aws_instance.controller.*.private_ip
      clients_public_ips           = aws_instance.client.*.public_ip
      clients_private_ips          = aws_instance.client.*.private_ip
      prometheus_host_public_ips   = aws_instance.prometheus.*.public_ip
      prometheus_host_private_ips  = aws_instance.prometheus.*.private_ip
      control_public_ips           = aws_instance.client.*.public_ip
      control_private_ips          = aws_instance.client.*.private_ip
      ssh_user                     = "ec2-user"
    }
  )
  filename = "${path.module}/hosts.ini"
}

output "clients" {
  value = {
    for instance in aws_instance.client :
    instance.public_ip => instance.public_ip
  }
}

output "brokers" {
  value = {
    for instance in aws_instance.kafka :
    instance.public_ip => instance.public_ip
  }
}

output "controller" {
  value = {
    for instance in aws_instance.controller :
    instance.public_ip => instance.public_ip
  }
}

output "prometheus_host" {
  value = "${aws_instance.prometheus.0.public_ip}"
}

output "client_ssh_host" {
  value = "${aws_instance.client.0.public_ip}"
}
