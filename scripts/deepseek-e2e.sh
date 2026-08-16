#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
  echo "ERROR: set DEEPSEEK_API_KEY before running DeepSeek E2E"
  exit 1
fi

MODEL="${ANVIL_E2E_MODEL:-deepseek-chat}"
echo "==> Clean build + unit tests (sanity)"
mvn -q clean test -Dtest='!OpenAiE2ETest,!OpenAiAppServerE2ETest,!DeepSeekE2ETest,!DeepSeekAppServerE2ETest'

echo "==> Core DeepSeek E2E (model: $MODEL)"
mvn -q -pl anvil-core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DeepSeekE2ETest test

echo "==> App Server DeepSeek E2E"
mvn -q -pl anvil-app-server -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=DeepSeekAppServerE2ETest test

echo "OK: DeepSeek E2E passed"
