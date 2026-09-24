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

YARN_CACHE_DIR="$HOME/.cache/yarn"
if [ -d "$HOME/Library/Caches/Yarn" ]; then
    YARN_CACHE_DIR="$HOME/Library/Caches/Yarn"
fi

mkdir -p "$HOME/.gradle/wrapper" "$HOME/.gradle/caches/modules-2/files-2.1" "$HOME/.gradle/caches/modules-2/metadata-2.107" "$HOME/.gradle/caches/modules-2/resources-2.1" "$HOME/.gradle/yarn" "$HOME/.gradle/nodejs" "$YARN_CACHE_DIR" "$HOME/.cache/ms-playwright-linux"

# Allocate pseudo-TTY only if stdin is a terminal
INTERACTIVE=""
if [ -t 0 ]; then
    INTERACTIVE="-it"
fi

docker run --rm $INTERACTIVE \
    -e YARN_CACHE_FOLDER=/usr/local/share/.cache/yarn \
    -v "$HOME/.gradle/wrapper":/root/.gradle/wrapper \
    -v "$HOME/.gradle/caches/modules-2/files-2.1":/root/.gradle/caches/modules-2/files-2.1 \
    -v "$HOME/.gradle/caches/modules-2/metadata-2.107":/root/.gradle/caches/modules-2/metadata-2.107 \
    -v "$HOME/.gradle/caches/modules-2/resources-2.1":/root/.gradle/caches/modules-2/resources-2.1 \
    -v "$HOME/.gradle/yarn":/root/.gradle/yarn \
    -v "$HOME/.gradle/nodejs":/root/.gradle/nodejs \
    -v "$YARN_CACHE_DIR":/usr/local/share/.cache/yarn \
    -v "$YARN_CACHE_DIR":/root/.cache/yarn \
    -v "$HOME/.cache/ms-playwright-linux":/root/.cache/ms-playwright \
    -v "$REPO_ROOT":/workspace \
    -w /workspace \
    "$IMAGE" \
    "$@"
