#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck disable=SC1091
source "$ROOT/scripts/server-lib.sh"

if [[ -f .env.local ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.local
  set +a
fi

HEALTH_URL="http://127.0.0.1:${ANVIL_SERVER_PORT}/api/health"
JAR="anvil-app-server/target/anvil-app-server-0.1.0-SNAPSHOT.jar"

anvil_stop_server

echo "==> Building app server"
mvn -q -pl anvil-app-server -am -DskipTests package

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found"
  exit 1
fi

echo "==> Starting App Server (foreground, Ctrl+C to stop)"
echo "    Health: $HEALTH_URL"
exec java -jar "$JAR"
