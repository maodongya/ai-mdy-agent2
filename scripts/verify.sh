#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> mvn clean test"
mvn clean test -Dtest='!OpenAiE2ETest,!OpenAiAppServerE2ETest,!DeepSeekE2ETest,!DeepSeekAppServerE2ETest'

echo "==> verify server starts"
mvn -q -pl anvil-app-server -am -DskipTests package
JAR="anvil-app-server/target/anvil-app-server-0.1.0-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
  echo "ERROR: executable jar not found at $JAR"
  exit 1
fi

java -jar "$JAR" &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT

for i in $(seq 1 45); do
  if curl -sf http://127.0.0.1:7788/api/health >/dev/null; then
    echo "OK: health check passed"
    exit 0
  fi
  sleep 1
done

echo "ERROR: server did not become healthy in 45s"
exit 1
