#!/usr/bin/env bash
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


REPOSITORY="mihkels/open-messaging-benchmark"
VERSION=${1:-latest}
PLATFORM=${2:-linux/amd64}

docker buildx build  --progress=plain \
  --attest type=sbom \
  --platform ${PLATFORM} \
  -t ${REPOSITORY}:${VERSION}-kafka-3.9-java24 . \
  -f docker/Dockerfile.build  \
  --cache-to type=registry,ref=mihkels/open-messaging-benchmark:cache \
  --push

