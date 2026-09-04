#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${PARIKSHAN_DOCKER_IMAGE:-parikshan-linux-runner:latest}"

if [ $# -eq 0 ]; then
    echo "Usage: $0 <command...>"
    echo "Examples:"
    echo "  $0 ./gradlew :gradle-plugins:test :parikshan-core:jvmTest"
    echo "  $0 ./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --targets=desktop -PcmpProfile=1.12"
    echo "  $0 ./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --targets=wasm -PcmpProfile=1.12"
    exit 1
fi

mkdir -p "$HOME/.gradle/wrapper" "$HOME/.gradle/caches/modules-2" "$HOME/.gradle/yarn" "$HOME/.gradle/nodejs" "$HOME/.cache/yarn" "$HOME/.cache/ms-playwright-linux"

# Allocate pseudo-TTY only if stdin is a terminal
INTERACTIVE=""
if [ -t 0 ]; then
    INTERACTIVE="-it"
fi

docker run --rm $INTERACTIVE \
    -v "$HOME/.gradle/wrapper":/root/.gradle/wrapper \
    -v "$HOME/.gradle/caches/modules-2":/root/.gradle/caches/modules-2 \
    -v "$HOME/.gradle/yarn":/root/.gradle/yarn \
    -v "$HOME/.gradle/nodejs":/root/.gradle/nodejs \
    -v "$HOME/.cache/yarn":/root/.cache/yarn \
    -v "$HOME/.cache/ms-playwright-linux":/root/.cache/ms-playwright \
    -v "$REPO_ROOT":/workspace \
    -w /workspace \
    "$IMAGE" \
    "$@"
