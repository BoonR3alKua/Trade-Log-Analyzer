#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "JDK 11 or newer is required on PATH."; exit 1; }
mkdir -p build/classes
if [[ "${1:-}" == "test" ]]; then
    javac --release 8 -encoding UTF-8 -d build/classes src/*.java tests/*.java
    java -cp build/classes RegressionTests
else
    javac --release 8 -encoding UTF-8 -d build/classes src/*.java
fi
echo "Build successful."
