#!/usr/bin/env bash
# Stop all services started by start-all.sh, using the recorded PIDs.
set -uo pipefail

: "${APP_ROOT:?APP_ROOT not set - source cloud.env first}"
LOG_DIR="$APP_ROOT/deploy/cloud/logs"

if [ ! -d "$LOG_DIR" ]; then
  echo "No pid files at $LOG_DIR - nothing to stop"
  exit 0
fi

for pidf in "$LOG_DIR"/*.pid; do
  [ -f "$pidf" ] || continue
  name=$(basename "$pidf" .pid)
  pid=$(cat "$pidf")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" && echo "  [STOP] $name (pid $pid)"
  else
    echo "  [SKIP] $name already stopped"
  fi
  rm -f "$pidf"
done

echo
echo "Remaining service ports:"
ss -ltn 2>/dev/null | grep -E ':808[1-5]|:1068[23]' || echo "  none"
