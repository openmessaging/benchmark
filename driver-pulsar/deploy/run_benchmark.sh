#!/bin/bash

export TF_STATE=.
cd ${1}
echo "Run Benchmark ${1} ${2} ${3}"
export ssh_host=`terraform output client_ssh_host`
export ssh_host=`sed -e 's/^"//' -e 's/"$//' <<<"$ssh_host"`
ssh -i ~/.ssh/pulsar_aws ec2-user@$ssh_host "cd /opt/benchmark;sudo bin/benchmark --drivers ${2} workloads/${3}"
