region            = "cn-shenzhen"
availability_zone = "cn-shenzhen-b"
private_key_file  = "benchmark_message_alicloud.pem"
key_name          = "key-pair-from-terraform-benchmark-rabbitmq"
image_id          = "centos_7_04_64_20G_alibase_201701015.vhd"

instance_types = {
  "rabbitmq"    = "ecs.se1.xlarge"
  "client"      = "ecs.se1.xlarge"
}

num_instances = {
  "rabbitmq"    = 3
  "client"      = 4
}
