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


WARMUP_DURATION=30
TEST_DURATION=60

function generate_workload {
  CKU=$1
  TOPIC_COUNT=$2
  PART_COUNT=$3
  THROUGHPUT_MBPS=$4

  PRODUCER_PER_TOPIC=1
  CONSUMER_PER_GROUP=1
  MSG_SIZE=2   # Kb

  PRODUCER_RATE=$(( ($THROUGHPUT_MBPS * 1024 * 1024) / ($MSG_SIZE * 1024)  ))

  WORKLOAD=confluent-blog-workloads-${CKU}cku-${PRODUCER_PER_TOPIC}p-${CONSUMER_PER_GROUP}c-${MSG_SIZE}Kb-${TOPIC_COUNT}topic-${PART_COUNT}part-${THROUGHPUT_MBPS}MBps
  echo "Generating workload $WORKLOAD"

  cat > workloads/$WORKLOAD.yaml << EOF

name: $WORKLOAD
topics: $TOPIC_COUNT
partitionsPerTopic: $PART_COUNT
messageSize: $(( $MSG_SIZE * 1024 ))
keyDistributor: "NO_KEY"
payloadFile: "payload/payload-${MSG_SIZE}Kb.data"
subscriptionsPerTopic: 3
consumerPerSubscription: $CONSUMER_PER_GROUP
producersPerTopic: $PRODUCER_PER_TOPIC
producerRate: $PRODUCER_RATE
consumerBacklogSizeGB: 0
testDurationMinutes: $TEST_DURATION
warmupDurationMinutes: $WARMUP_DURATION
EOF

}

# generate_workload {CKU} {TOPIC_COUNT} {PARTITION_COUNT_PER_TOPIC} {THROUGHPUT_MBPS}

# 2 CKU
## Variable throughput
generate_workload 2 45 200 10
generate_workload 2 45 200 50
generate_workload 2 45 200 100

## Variable partition count
generate_workload 2 45 40 100
generate_workload 2 45 100 100

# 28 CKU
## Variable throughput
generate_workload 28 500 200 1400
generate_workload 28 500 200 1250
generate_workload 28 500 200 1000
generate_workload 28 500 200 500
generate_workload 28 500 200 250

## Variable partition count
generate_workload 28 500 100 1400
generate_workload 28 500 150 1400
generate_workload 28 500 180 1400
