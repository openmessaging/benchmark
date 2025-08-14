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
# If no platform is provided, default to multi-platform on macOS (Darwin), single-platform elsewhere
if [[ -z "${2:-}" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    PLATFORMS="linux/arm64,linux/amd64"
  else
    PLATFORMS="linux/amd64"
  fi
else
  PLATFORMS="${2}"
fi

# Optional push behavior (default: no push).
# - 3rd arg: push | --push | true | 1  -> push
#            load | --load              -> load into local Docker (single-platform only)
# - env DOCKER_PUSH=1 can also enable push
PUSH_ARG="${3:-}"
if [[ -n "${DOCKER_PUSH:-}" && "${DOCKER_PUSH}" != "0" ]]; then
  DO_PUSH=1
elif [[ "${PUSH_ARG}" =~ ^(push|--push|true|1)$ ]]; then
  DO_PUSH=1
else
  DO_PUSH=0
fi
DO_LOAD=0
if [[ "${PUSH_ARG}" =~ ^(load|--load)$ ]]; then
  DO_LOAD=1
fi

# Ensure a buildx builder is available
if ! docker buildx ls >/dev/null 2>&1; then
  echo "docker buildx not available or not configured"
  exit 1
fi
if ! docker buildx ls | grep -q '\*'; then
  docker buildx create --use >/dev/null
fi

echo "Building for platforms: ${PLATFORMS}"
if [[ "${DO_PUSH}" -eq 1 ]]; then
  echo "Images will be pushed to ${REPOSITORY}"
elif [[ "${DO_LOAD}" -eq 1 ]]; then
  echo "Image will be loaded into local Docker (single-platform only)"
else
  if [[ "${PLATFORMS}" == *","* ]]; then
    echo "No push/load selected for multi-platform build. Will export an OCI tarball locally."
  else
    echo "No push/load selected. Image will be loaded into local Docker."
    DO_LOAD=1
  fi
fi

# Decide output flags
OUTPUT_FLAGS=()
if [[ "${DO_PUSH}" -eq 1 ]]; then
  OUTPUT_FLAGS+=(--push)
elif [[ "${DO_LOAD}" -eq 1 ]]; then
  OUTPUT_FLAGS+=(--load)
else
  # Multi-platform or explicit no load/push: save to local tarball
  TARBALL="omb-image-${VERSION}-kafka-3.9-java24.oci.tar"
  OUTPUT_FLAGS+=(--output "type=oci,dest=${TARBALL}")
  echo "Will export image to ${TARBALL}"
fi

export DOCKER_BUILDKIT=1
docker buildx build  --progress=plain \
  --attest type=sbom \
  --platform "${PLATFORMS}" \
  -t "${REPOSITORY}:${VERSION}-kafka-3.9-java24" . \
  -f docker/Dockerfile.build  \
  --build-context host-m2=$HOME/.m2 \
  --cache-to type=registry,ref=mihkels/open-messaging-benchmark:cache \
  "${OUTPUT_FLAGS[@]}"

