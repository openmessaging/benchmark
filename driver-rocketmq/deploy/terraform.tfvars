region           = "cn-hangzhou"
private_key_file = "rocketmq_alicloud.pem"
key_name         = "key-pair-from-terraform"
image_id         = "centos_7_04_64_20G_alibase_201701015.vhd"

instance_types = {
  "broker"       = "ecs.mn4.xlarge" #4c16g
  "client"       = "ecs.n4.xlarge" #4c8g
  "namesrv"      = "ecs.n4.xlarge"
}

num_instances = {
  "broker"       = 2
  "namesrv"      = 1
  "client"       = 4
}
