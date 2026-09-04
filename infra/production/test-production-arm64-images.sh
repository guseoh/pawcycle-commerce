#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"

build_arm64_image() {
  local name="$1"
  local dockerfile="$2"

  docker buildx build \
    --progress quiet \
    --platform linux/arm64 \
    --file "$dockerfile" \
    --output type=cacheonly \
    "$REPOSITORY_ROOT"
  printf '%s ARM64 production image build passed\n' "$name"
}

build_arm64_image Backend "$SCRIPT_DIR/backend.Dockerfile"
build_arm64_image Frontend "$SCRIPT_DIR/frontend.Dockerfile"
