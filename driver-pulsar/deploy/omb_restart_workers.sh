#!/bin/bash

export TF_STATE=.
echo "Restart Workerd ${1}"
cd ${1}
echo `pwd`
ls

echo Playbook ${1}
ansible-playbook --user ec2-user --inventory `which terraform-inventory` client-restart.yaml

