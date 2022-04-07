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

set -x

ROOT_DIR=$(dirname $0)/..
NAMESPACE=${NAMESPACE:-examples}

helm  del --purge \
${NAMESPACE}-openmessaging-benchmarking

kubectl wait --for=delete --timeout=300s statefulset/${NAMESPACE}-openmessaging-benchmarking-worker -n ${NAMESPACE}
kubectl wait --for=delete --timeout=300s pod/${NAMESPACE}-openmessaging-benchmarking-driver -n ${NAMESPACE}

true
