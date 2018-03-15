provider "aws" {
  region  = "${var.region}"
  version = "1.8"
}

provider "random" {
  version = "1.1"
}

resource "random_id" "hash" {
  byte_length = 8
}
