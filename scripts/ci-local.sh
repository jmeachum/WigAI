#!/usr/bin/env bash
set -euo pipefail

echo "== WigAI CI Local Mirror =="
echo ""

echo "-- 1/3: Check story status consistency"
"$(dirname "$0")/check-story-status.sh"

echo ""
echo "-- 2/3: Run unit tests"
./gradlew test --warning-mode=none

echo ""
echo "-- 3/3: Build extension (skip tests)"
./gradlew bwextension -x test --warning-mode=none

echo ""
echo "OK"

