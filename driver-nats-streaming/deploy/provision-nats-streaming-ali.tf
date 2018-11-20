provider "alicloud" {
  region     = "${var.region}"
}

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

resource "alicloud_vpc" "vpc" {
  name = "vpc_for_nats_streaming"
  cidr_block = "172.16.0.0/12"
}

resource "alicloud_vswitch" "vswitch" {
  name = "subnet_for_nats_streaming"
  vpc_id = "${alicloud_vpc.vpc.id}"
  cidr_block = "172.16.0.0/21"
  availability_zone = "${var.availability_zone}"
}

# Create security group
resource "alicloud_security_group" "default" {
  name        = "group_for_nats_streaming"
  provider    = "alicloud"
  description = "default"
  vpc_id = "${alicloud_vpc.vpc.id}"
}

resource "alicloud_security_group_rule" "allow_all_tcp" {
  type              = "ingress"
  ip_protocol       = "tcp"
  nic_type          = "intranet"
  policy            = "accept"
  port_range        = "1/65535"
  priority          = 1
  security_group_id = "${alicloud_security_group.default.id}"
  cidr_ip           = "0.0.0.0/0"
}

resource "alicloud_instance" "nats-streaming-server" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"
  availability_zone          = "${var.availability_zone}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["nats-streaming-server"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  vswitch_id = "${alicloud_vswitch.vswitch.id}"
  internet_max_bandwidth_out = "1"
  system_disk_category = "cloud_ssd"
  system_disk_size = "500"
  count = "${var.num_instances["nats-streaming-server"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
  instance_name        = "nats-streaming-server-${count.index}"
  password = "Ali123456"
}

resource "alicloud_instance" "client" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"
  internet_charge_type  = "PayByBandwidth"
  availability_zone          = "${var.availability_zone}"
  instance_type        = "${var.instance_types["client"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  vswitch_id = "${alicloud_vswitch.vswitch.id}"
  internet_max_bandwidth_out = "1"
  count = "${var.num_instances["client"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
  system_disk_category = "cloud_ssd"
  instance_name        = "nats-streaming-client-${count.index}"
}


resource "alicloud_key_pair" "key_pair" {
  key_name = "${var.key_name}"
  key_file = "${var.private_key_file}"
}

output "broker_host" {
  value = "${alicloud_instance.nats-streaming-server.*.public_ip}"
}


output "client_host" {
  value = "${alicloud_instance.client.*.public_ip}"
}

output "client_ssh_host" {
  value = "${alicloud_instance.client.0.public_ip}"
}
