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

set -e

: ${1?"Usage: $0 PRAVEGA_LOGS_DIRECTORY"}

pravega_logs_dir=$(readlink -f "$1")
echo Loading metrics from: ${pravega_logs_dir}
export INFLUXDB_BACKUP_DIR="${pravega_logs_dir}/influxdb"
export PROMETHEUS_DATA_DIR="${pravega_logs_dir}/prometheus"
docker-compose -p open_saved_metrics -f $(dirname $0)/docker-compose.yml rm --stop --force influxdb

#
# InfluxDB
#

influxdb_tgz="${pravega_logs_dir}/influxdb.tgz"
ls -lh ${influxdb_tgz}
rm -rf "${INFLUXDB_BACKUP_DIR}"
mkdir "${INFLUXDB_BACKUP_DIR}"
tar -xzvf "${influxdb_tgz}" -C "${INFLUXDB_BACKUP_DIR}"
docker-compose -p open_saved_metrics -f $(dirname $0)/docker-compose.yml up -d influxdb
docker-compose -p open_saved_metrics -f $(dirname $0)/docker-compose.yml exec influxdb influxd restore -portable /tmp/backup

#
# Prometheus
#

prometheus_tgz="${pravega_logs_dir}/prometheus.tgz"
ls -lh ${prometheus_tgz}
rm -rf "${PROMETHEUS_DATA_DIR}"
mkdir "${PROMETHEUS_DATA_DIR}"
tar -xzvf "${prometheus_tgz}" --strip-components=3 -C "${PROMETHEUS_DATA_DIR}" opt/prometheus/data
chmod -R a+rwX "${PROMETHEUS_DATA_DIR}"
docker-compose -p open_saved_metrics -f $(dirname $0)/docker-compose.yml up -d prometheus

#
# Grafana
#

docker-compose -p open_saved_metrics -f $(dirname $0)/docker-compose.yml up -d

echo
echo Grafana URL: http://localhost:3000
echo
