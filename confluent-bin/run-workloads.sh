#!/bin/bash
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


function usage {
  echo $1
  echo "Usage: run-workloads.sh {ak|cc} workloads/x.yaml"
  exit 1
}

function restart_clients {
  terraform-inventory --list ./ | jq -rM '.client[]' | while read ip; do ssh ec2-user@$ip sudo pkill -9 java & done;
  sleep 30
}

trap "restart_clients" INT

OMB_ENV=confluent-deployment
[ -d ./driver-kafka/deploy/$OMB_ENV ] || usage "Should be executed in OMB root directory"

AK_CC=$1
WORKLOAD_PATH=$2

[ -n "$OMB_ENV" ] || usage "Missing omb_env"
[ -n "$AK_CC" ] || usage "Missing ak|cc switch"
[ -n "$WORKLOAD_PATH" ] || usage "Missing workload"

DATE=$(date -u +"%Y%m%d-%H%M%SU")
WORKLOAD=$(basename $WORKLOAD_PATH)

pushd "driver-kafka/deploy/$OMB_ENV" || usage "Failed pushd"
export TF_STATE="."
CLIENT0_IP=$(terraform-inventory --list ./ | jq -rM '.client_0[]')

case "$AK_CC" in
  ak)
    DRIVER_PATH="driver-kafka/ssl-kafka-all-background-commit.yaml"
    ;;
  cc)
    DRIVER_PATH="driver-kafka/ccloud-background-commit.yaml"
    ;;
  *)
    usage "Invalid ak|cc switch: $AK_CC"
    ;;
esac

echo "Restart all clients..."
restart_clients

echo "Copying workloads..."
(cd ../../..; scp $WORKLOAD_PATH ec2-user@$CLIENT0_IP:)
ssh ec2-user@$CLIENT0_IP "sudo mv -f $WORKLOAD /opt/benchmark/workloads"

echo "Start workload against $AK_CC with driver=$DRIVER_PATH and workload=$WORKLOAD_PATH..."
DATA_DIR_NAME="$DATE-$WORKLOAD-$AK_CC"
TMP_PATH="/tmp/$DATA_DIR_NAME"
ssh ec2-user@$CLIENT0_IP "mkdir -p $TMP_PATH; cd /opt/benchmark; sudo bin/benchmark -d $DRIVER_PATH -o $TMP_PATH/output.txt $WORKLOAD_PATH 2>&1 | sudo tee $TMP_PATH/stdout.txt"

popd || usage "Failed popd"

RESULTS_DIR="./results/"
echo "Copying outputs to '$RESULTS_DIR' directory"
mkdir -p $RESULTS_DIR
scp -r ec2-user@$CLIENT0_IP:$TMP_PATH $RESULTS_DIR
