#!/bin/bash

echo "Destroy ${1}"
cd ${1}
terraform destroy -input=false -auto-approve
