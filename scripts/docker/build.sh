#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TAG="${1:-parikshan-linux-runner:latest}"

echo "==> Building Docker image: $TAG"
docker build -t "$TAG" -f "$DIR/Dockerfile" "$DIR"
echo "==> Docker image built successfully: $TAG"
