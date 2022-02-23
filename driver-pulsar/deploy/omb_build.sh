#!/bin/bash

export TF_STATE=.
echo "Create ${1} ${2}"
mkdir -p ${1}
cp ansible.cfg ${1}/.
cp terraform.tfvars ${1}/.
cp provision-pulsar-aws.tf ${1}/.
cp *.yaml ${1}/.
cp -rp templates ${1}/.

cd ${1}
echo `pwd`
ls

terraform init -input=false
terraform apply -input=false -auto-approve
echo Wait ...
sleep 15
echo Playbook ${1} ${2}
ansible-playbook --user ec2-user --inventory `which terraform-inventory` --extra-vars "@${2}" deploy.yaml

