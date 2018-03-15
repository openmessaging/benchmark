output "client_ssh_host" {
  value = "${aws_instance.client.0.public_ip}"
}
