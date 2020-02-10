region            = "cn-hangzhou"
availability_zone = "cn-hangzhou-b"
private_key_file  = "mqtt_alicloud.pem"
key_name          = "key-pair-from-terraform"
image_id          = "centos_7_04_64_20G_alibase_201701015.vhd"

instance_types = {
  "mqtt-broker"        = "ecs.mn4.xlarge" #4c16g
  "mqtt-client"        = "ecs.mn4.xlarge"
}

num_instances = {
  "mqtt-broker"        = 1
  "mqtt-client"        = 4
}
