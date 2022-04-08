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

set -ex

: ${DOCKER_REPOSITORY?"You must export DOCKER_REPOSITORY"}
: ${IMAGE_TAG?"You must export IMAGE_TAG"}

ROOT_DIR=$(dirname $0)/..
NAMESPACE=${NAMESPACE:-examples}

#${ROOT_DIR}/driver-pravega/uninstall.sh

helm upgrade --install --timeout 600 --wait --debug \
${NAMESPACE}-openmessaging-benchmarking \
--namespace ${NAMESPACE} \
--set image=${DOCKER_REPOSITORY}/openmessaging-benchmark:${IMAGE_TAG} \
${ROOT_DIR}/deployment/kubernetes/helm/benchmark \
$@
