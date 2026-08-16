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

start_server() {
  echo "==> Building app server"
  mvn -q -pl anvil-app-server -am -DskipTests package

  JAR="anvil-app-server/target/anvil-app-server-0.1.0-SNAPSHOT.jar"
  if [[ ! -f "$JAR" ]]; then
    echo "ERROR: $JAR not found"
    exit 1
  fi

  anvil_stop_server

  echo "==> Starting App Server on port ${ANVIL_SERVER_PORT}"
  nohup java -jar "$JAR" > /tmp/anvil-server.log 2>&1 &
  echo $! > "$ANVIL_PID_FILE"

  if anvil_wait_for_health "$HEALTH_URL"; then
    echo "OK: App Server healthy — $(curl -sf "$HEALTH_URL")"
    return
  fi
  echo "ERROR: server did not start — see /tmp/anvil-server.log"
  tail -30 /tmp/anvil-server.log
  exit 1
}

start_server
exec bash "$ROOT/scripts/run-ui.sh"
