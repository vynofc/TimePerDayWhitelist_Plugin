#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "[1/2] Cleaning old build..."
mvn clean

echo "[2/2] Building plugin..."
mvn package

echo
echo "Build finished. Artifact(s):"
ls -1 target/*.jar
