terraform {
  backend "s3" {
    key    = "kafka-aws-low-cost"
    region = "eu-west-1"
  }
}