# Define variables

variable "region" {}
variable "private_key_file" {}
variable "key_name" {}
variable "image_id" {}
variable "availability_zone" {}
variable "instance_types" {
  type = "map"
}

variable "num_instances" {
  type = "map"
}

# Configure the Alicloud Provider
provider "alicloud" {
  region     = "${var.region}"
}


# Create zookeeper
resource "alicloud_instance" "zookeeper" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"
  availability_zone          = "${var.availability_zone}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["zookeeper"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  instance_name        = "zookeeper"
  internet_max_bandwidth_out = "1"
  system_disk_category = "cloud_ssd"
  system_disk_size = "64"
  description          = "developer_native_compare=message"
  count = "${var.num_instances["zookeeper"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
  tags {
    Name = "zk-${count.index}"
  }
}

# Create kafka
resource "alicloud_instance" "kafka" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["kafka"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  instance_name        = "kafka"
  internet_max_bandwidth_out = "1"
  description          = "developer_native_compare=message"
  count = "${var.num_instances["kafka"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
  tags {
    Name = "kafka-${count.index}"
  }
}

# Create client
resource "alicloud_instance" "client" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["client"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  instance_name        = "client"
  internet_max_bandwidth_out = "1"
  description          = "developer_native_compare=message"
  count = "${var.num_instances["client"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
  tags {
    Name = "kafka-client-${count.index}"
  }
}

# Create security group
resource "alicloud_security_group" "default" {
  name        = "default"
  provider    = "alicloud"
  description = "default"
}

resource "alicloud_security_group_rule" "allow_all_tcp" {
  type              = "ingress"
  ip_protocol       = "tcp"
  nic_type          = "internet"
  policy            = "accept"
  port_range        = "1/65535"
  priority          = 1
  security_group_id = "${alicloud_security_group.default.id}"
  cidr_ip           = "0.0.0.0/0"
}

resource "alicloud_key_pair" "key_pair" {
  key_name = "${var.key_name}"
  key_file = "${var.private_key_file}"
}

output "client_ssh_host" {
  value = "${alicloud_instance.client.0.public_ip}"
}
