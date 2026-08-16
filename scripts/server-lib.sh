#!/usr/bin/env bash
# Shared helpers for Anvil App Server lifecycle.
set -euo pipefail

ANVIL_SERVER_PORT="${ANVIL_SERVER_PORT:-7788}"
ANVIL_PID_FILE="${ANVIL_PID_FILE:-/tmp/anvil-server.pid}"

anvil_stop_server() {
  if [[ -f "$ANVIL_PID_FILE" ]]; then
    local pid
    pid="$(cat "$ANVIL_PID_FILE")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "==> Stopping App Server (pid $pid)"
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 20); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.25
      done
    fi
    rm -f "$ANVIL_PID_FILE"
  fi

  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti tcp:"$ANVIL_SERVER_PORT" 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "==> Stopping process on port $ANVIL_SERVER_PORT ($pids)"
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null || true
      sleep 1
    fi
  fi
}

anvil_wait_for_health() {
  local url="${1:-http://127.0.0.1:${ANVIL_SERVER_PORT}/api/health}"
  local attempts="${2:-45}"
  for _ in $(seq 1 "$attempts"); do
    if curl -sf "$url" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  return 1
}
