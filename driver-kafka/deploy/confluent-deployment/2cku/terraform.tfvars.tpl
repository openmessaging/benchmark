region          = "AWS REGION ID"
ami             = "AWS AMI ID"
profile         = "AWS PROFILE"
subnet_cidrs    = ["AWS SUBNET 1", "AWS SUBNET 2", "AWS SUBNET 3"]
azs             = ["AWS AZ 1", "AWS AZ 2", "AWS AZ 3"]

instance_types = {
  "kafka"      = "m5.xlarge"
  "controller" = "m5.xlarge"
  "client"     = "m5.xlarge"
  "prometheus" = "m5.xlarge"
}

num_instances = {
  "client"     = 6
  "kafka"      = 6
  "controller" = 3
  "prometheus" = 1
}

gp3_size_gb       = 500
gp3_iops          = 3000
gp3_throughput_mb = 125
