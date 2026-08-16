#!/usr/bin/env bash
# Compare golden protocol trace with a run trace CSV export.
# Usage: bash scripts/compare-trace.sh fixtures/protocol/read-add.golden.json anvil-trace-run.csv
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -pl anvil-core exec:java \
  -Dexec.mainClass=com.anvil.core.trace.TraceCompareCli \
  -Dexec.args="$*"
