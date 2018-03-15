resource "aws_instance" "zookeeper" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["zookeeper"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["zookeeper"]}"

  tags {
    Name = "${var.platform}-zk-${count.index}"
  }
}

resource "aws_instance" "messaging" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["messaging"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["messaging"]}"

  tags {
    Name = "${var.platform}-${count.index}"
  }
}

resource "aws_instance" "client" {
  ami                    = "${var.ami}"
  instance_type          = "${var.instance_types["client"]}"
  key_name               = "${aws_key_pair.auth.id}"
  subnet_id              = "${aws_subnet.benchmark_subnet.id}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  count                  = "${var.num_instances["client"]}"

  tags {
    Name = "${var.platform}-client-${count.index}"
  }
}
