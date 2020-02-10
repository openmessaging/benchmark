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


# Create mqtt broker
resource "alicloud_instance" "mqtt-broker" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"
  availability_zone          = "${var.availability_zone}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["mqtt-broker"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  instance_name        = "mqtt-broker"
  internet_max_bandwidth_out = "1"
  system_disk_category = "cloud_ssd"
  system_disk_size = "64"
  count = "${var.num_instances["mqtt-broker"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
}

# Create mqtt clients
resource "alicloud_instance" "mqtt-client" {
  provider          = "alicloud"
  image_id          = "${var.image_id}"

  internet_charge_type  = "PayByBandwidth"

  instance_type        = "${var.instance_types["mqtt-client"]}"
  security_groups      = ["${alicloud_security_group.default.id}"]
  instance_name        = "mqtt-client"
  internet_max_bandwidth_out = "1"
  count = "${var.num_instances["mqtt-client"]}"
  key_name = "${alicloud_key_pair.key_pair.id}"
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

output "broker_host" {
  value = "${alicloud_instance.mqtt-broker.*.public_ip}"
}

output "client_host" {
  value = "${alicloud_instance.mqtt-client.*.public_ip}"
}

output "client_ssh_host" {
  value = "${alicloud_instance.mqtt-client.0.public_ip}"
}
