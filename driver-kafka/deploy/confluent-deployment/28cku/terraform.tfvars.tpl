region          = "AWS REGION ID"
ami             = "AWS AMI ID"
profile         = "AWS PROFILE"
subnet_cidrs    = ["AWS SUBNET 1", "AWS SUBNET 2", "AWS SUBNET 3"]
azs             = ["AWS AZ 1", "AWS AZ 2", "AWS AZ 3"]

instance_types = {
  "kafka"      = "m5.4xlarge"
  "controller" = "m5.xlarge"
  "client"     = "m5.4xlarge"
  "prometheus" = "m5.4xlarge"
}

num_instances = {
  "client"     = 21
  "kafka"      = 21
  "controller" = 3
  "prometheus" = 1
}

gp3_size_gb       = 2000
gp3_iops          = 12000
gp3_throughput_mb = 640
