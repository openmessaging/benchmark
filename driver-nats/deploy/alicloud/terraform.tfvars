region            = "cn-shenzhen"
availability_zone = "cn-shenzhen-c"
private_key_file  = "alicloud.pem"
key_name          = "key-pair-from-terraform-nats"
image_id          = "centos_7_04_64_20G_alibase_201701015.vhd"

instance_types = {
  "nats"      = "ecs.se1.4xlarge" #4c16g
  "client"        = "ecs.n4.4xlarge"
}

num_instances = {
  "nats"        = 1
  "client"      = 2
}