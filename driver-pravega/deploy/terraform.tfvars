public_key_path = "~/.ssh/pravega_aws.pub"
region          = "us-west-2"
ami             = "ami-9fa343e7" // RHEL-7.4 us-west-2

instance_types = {
  "controller"   = "m5.large"       //  2 cpu,   8 GiB,   78-1250 MB/sec net
  "bookkeeper"   = "i3.4xlarge"     // 16 cpu, 122 GiB,  625-1250 MB/sec net, 2 x 1,900 NVMe SSD
  "zookeeper"    = "t3.small"       //  2 cpu,   2 GiB
  "client"       = "c5.4xlarge"     //  16 cpu,  32 GiB,  277-1250 MB/sec net
  "metrics"      = "t3.large"       //  2 cpu,   8 GiB
}

num_instances = {
  "controller"   = 1
  "bookkeeper"   = 3
  "zookeeper"    = 3
  "client"       = 2
  "metrics"      = 1
}
