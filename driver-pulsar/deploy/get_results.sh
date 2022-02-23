#!/bin/bash

export here=`pwd`
export TF_STATE=.
cd ${1}
echo "Get Results ${1} ${2}"
export ssh_host=`terraform output client_ssh_host`
export ssh_host=`sed -e 's/^"//' -e 's/"$//' <<<"$ssh_host"`
cd ${here}
export tdate=`date -u "+%Y-%m-%d"`
export results=${2}/${tdate}
mkdir -p ${results}
scp -i ~/.ssh/pulsar_aws ec2-user@${ssh_host}:/opt/benchmark/\*.json ${results}
cd ${results}
python3 /opt/openmessaging-benchmark/bin/create_charts.py  *.json
