provider "aws" {
  region = "${var.region}"
}

provider "random" {}

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "3.50"
    }
    random = {
      version = "3.1"
    }
  }
}