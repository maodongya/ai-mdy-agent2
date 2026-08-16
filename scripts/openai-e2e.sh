#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "ERROR: set OPENAI_API_KEY before running OpenAI E2E"
  exit 1
fi

MODEL="${ANVIL_E2E_MODEL:-gpt-4o-mini}"
echo "==> Clean build + unit tests (sanity)"
mvn -q clean test -Dtest='!OpenAiE2ETest,!OpenAiAppServerE2ETest,!DeepSeekE2ETest,!DeepSeekAppServerE2ETest'

echo "==> Core OpenAI E2E (model: $MODEL)"
mvn -q -pl anvil-core -am -Dtest=OpenAiE2ETest test

echo "==> App Server OpenAI E2E"
mvn -q -pl anvil-app-server -am -Dtest=OpenAiAppServerE2ETest test

echo "OK: OpenAI E2E passed"
