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


OMB_ENV=confluent-deployment
CMD=$1
FILENAME=$2

[ -n "$OMB_ENV" ] || usage "Missing OMB_ENV"
[ -n "$CMD" ] || usage "Missing command"

function usage {
  echo $1
  echo "Usage: run-cmd.sh {COMMAND}"
  exit 1
}

function restart_client {
  echo "Restarting Benchmark workers"
  terraform-inventory --list ./ | jq -rM '.client[]' | while read ip; do echo "  $ip"; ssh ec2-user@$ip "sudo pkill -9 java" & done;
}

function reset_client_logs {
  echo "Reset logs in all clients"
  terraform-inventory --list ./ | jq -rM '.client[]' | while read ip; do echo "  Deleting logs - $ip"; ssh ec2-user@$ip "sudo find /opt/benchmark -name 'benchmark-worker.log.*' -delete; sudo find /opt/benchmark -name 'java_*' -delete;" & done;
}

function restart_kafka {
  echo "Restarting Apache Kafka brokers"
  terraform-inventory --list ./ | jq -rM '.kafka[]' | while read ip; do echo "  $ip"; ssh ec2-user@$ip "sudo systemctl restart kafka" & done;
}

function status_kafka {
  echo "Status of Apache Kafka service in all brokers"
  terraform-inventory --list ./ | jq -rM '.kafka[]' | while read ip; do echo "  $ip"; ssh ec2-user@$ip "sudo systemctl status kafka" & done;
}

function reset_kafka_logs {
  echo "Reset Apache Kafka logs in all brokers"
  terraform-inventory --list ./ | jq -rM '.kafka[]' | while read ip; do echo "  Deleting kafka logs - $ip"; ssh ec2-user@$ip "sudo rm /opt/kafka/logs/*; sudo truncate -s 0 /var/log/messages;" & done;
}

function copy_driver_kafka_file {
  echo "Copying driver-kafka file"
  for ip in `terraform-inventory --list ./ | jq -rM '.client[]'`
  do
    echo "  Copying $FILENAME to $ip" ;
    scp ../../../driver-kafka/$FILENAME ec2-user@$ip: ;
    ssh ec2-user@$ip "sudo mv $FILENAME /opt/benchmark/driver-kafka/" ;
  done
}

function deploy_benchmark {
  echo "Deploying benchmark jar"
  for ip in `terraform-inventory --list ./ | jq -rM '.client[]'`
  do
    echo "  Deploying to client - $ip" ;
    scp ../../../benchmark-framework/target/benchmark-framework-0.0.1-SNAPSHOT.jar ec2-user@$ip: ;
    scp ../../../driver-kafka/target/driver-kafka-0.0.1-SNAPSHOT.jar ec2-user@$ip: ;
    scp ../../../driver-api/target/driver-api-0.0.1-SNAPSHOT.jar ec2-user@$ip: ;
    ssh ec2-user@$ip "sudo mv benchmark-framework-0.0.1-SNAPSHOT.jar /opt/benchmark/lib/io.openmessaging.benchmark-benchmark-framework-0.0.1-SNAPSHOT.jar; sudo mv driver-kafka-0.0.1-SNAPSHOT.jar /opt/benchmark/lib/io.openmessaging.benchmark-driver-kafka-0.0.1-SNAPSHOT.jar; sudo mv driver-api-0.0.1-SNAPSHOT.jar /opt/benchmark/lib/io.openmessaging.benchmark-driver-api-0.0.1-SNAPSHOT.jar; sudo systemctl restart benchmark-worker;" ;
  done
}

pushd driver-kafka/deploy/$OMB_ENV || usage "Failed pushd"

export TF_STATE=.

case "$CMD" in
  restart-client)
    restart_client
    ;;
  reset-client-logs)
    reset_client_logs
    ;;
  restart-kafka)
    restart_kafka
    ;;
  status-kafka)
    status_kafka
    ;;
  reset-kafka-logs)
    reset_kafka_logs
    ;;
  copy-driver-kafka-file)
    copy_driver_kafka_file
    ;;
  deploy-benchmark)
    deploy_benchmark
    ;;
  *) usage "Invalid command: $CMD"
    ;;
esac
