#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/server-lib.sh"

anvil_stop_server
echo "OK: App Server stopped (port ${ANVIL_SERVER_PORT})"
