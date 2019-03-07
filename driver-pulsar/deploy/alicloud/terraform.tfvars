region            = "cn-shenzhen"
availability_zone = "cn-shenzhen-b"
private_key_file  = "benchmark_message_alicloud.pem"
key_name          = "key-pair-from-terraform-benchmark-pulsar"
image_id          = "centos_7_04_64_20G_alibase_201701015.vhd"



instance_types = {
  "pulsar"      = "ecs.i1.xlarge"
  "zookeeper"   = "ecs.se1.xlarge"
  "client"      = "ecs.se1.xlarge"
  "prometheus"  = "ecs.se1.xlarge"
}

num_instances = {
  "client"      = 4
  "pulsar"      = 3
  "zookeeper"   = 3
  "prometheus"  = 1
}
