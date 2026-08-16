#!/usr/bin/env bash
# Stop App Server + JavaFX UI, then start fresh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/server-lib.sh"

echo "==> Stopping Anvil UI processes"
if command -v pgrep >/dev/null; then
  pgrep -f 'com.anvil.ui.AnvilUiApp' | while read -r pid; do
    echo "    kill UI pid $pid"
    kill "$pid" 2>/dev/null || true
  done
fi

anvil_stop_server
sleep 1

echo "==> Starting Anvil (server + UI)"
exec bash "$ROOT/scripts/start-ui.sh"
