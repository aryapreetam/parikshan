#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARIKSHAN_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PARIKSHAN_ROOT}"

echo "============================================================="
echo " PARIKSHAN DOCUMENTATION BUILDER"
echo "============================================================="

# 1. Build MkDocs Site with strict validation
echo "==> 1/3: Building MkDocs Material documentation..."
mkdocs build --strict --clean
touch site/.nojekyll
mkdir -p site/api site/demo

# 2. Build Dokka API Reference
echo "==> 2/3: Generating Dokka Multi-Module API Reference..."
"${PARIKSHAN_ROOT}/gradlew" :parikshan:dokkaGeneratePublicationHtml
cp -r parikshan/build/dokka/html/* site/api/

# 3. Build Showcase Wasm Demo
echo "==> 3/3: Building Wasm Showcase Demo..."
"${PARIKSHAN_ROOT}/gradlew" :samples:multiplatform-showcase:composeApp:wasmJsBrowserDistribution
"${PARIKSHAN_ROOT}/gradlew" --stop
cp -r samples/multiplatform-showcase/composeApp/build/dist/wasmJs/productionExecutable/* site/demo/

echo "============================================================="
echo " Documentation build complete!"
echo " - Landing & Docs : site/index.html"
echo " - API Reference  : site/api/index.html"
echo " - Wasm Demo      : site/demo/index.html"
echo "============================================================="

if [[ "${1:-}" == "--serve" ]]; then
  echo "Serving documentation locally at http://localhost:8000/ (Press Ctrl+C to stop)..."
  echo " - Docs     : http://localhost:8000/"
  echo " - API Docs : http://localhost:8000/api/"
  echo " - Wasm Demo: http://localhost:8000/demo/"
  python3 -m http.server 8000 --directory site
fi
