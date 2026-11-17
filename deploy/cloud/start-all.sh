#!/usr/bin/env bash
# ============================================================================
# Cloud startup for TranslationSystem (demo profile)
#
# Usage:
#   cd /opt/ecommerce/TraslationSystem/deploy/cloud
#   set -a && source cloud.env && set +a
#   ./start-all.sh
#
# Stop:
#   ./stop-all.sh
#
# The script starts services in dependency order, waits for each port,
# and writes logs to $APP_ROOT/deploy/cloud/logs/.
# Re-running is safe: a service whose port is already listening is skipped.
# ============================================================================
set -euo pipefail

: "${APP_ROOT:?APP_ROOT not set - source cloud.env first}"
: "${DB_PASSWORD:?DB_PASSWORD not set}"
: "${JWT_SECRET_KEY:?JWT_SECRET_KEY not set}"
: "${LLM_API_KEY:?LLM_API_KEY not set}"

LOG_DIR="$APP_ROOT/deploy/cloud/logs"
JAR_DIR="$APP_ROOT/backend-java"
mkdir -p "$LOG_DIR" "$UPLOAD_DIR" "$(dirname "$ASSISTANT_INDEX_DIR")"

JAVA_BIN="${JAVA_BIN:-java}"
PY_BIN="${PY_BIN:-python3}"

# ---------- helpers ----------
port_open() {
  ss -ltn 2>/dev/null | grep -qE "[:.]$1[[:space:]]" || \
  netstat -ltn 2>/dev/null | grep -qE "[:.]$1[[:space:]]"
}

wait_port() {
  local port="$1" name="$2" i=0
  while ! port_open "$port"; do
    i=$((i+1))
    if [ "$i" -ge 60 ]; then
      echo "  [FAIL] $name did not open :$port in 60s - see $LOG_DIR/${name}.log"
      return 1
    fi
    sleep 1
  done
  echo "  [OK]   $name listening on :$port"
}

start_java() {
  local module="$1" port="$2" extra="${3:-}"
  if port_open "$port"; then
    echo "  [SKIP] $module already on :$port"
    return 0
  fi
  echo "  [START] $module -> :$port"
  # shellcheck disable=SC2086
  nohup $JAVA_BIN $extra -jar "$JAR_DIR/$module/build/libs/$module-1.0.0-SNAPSHOT.jar" \
    --server.port="$port" >"$LOG_DIR/$module.log" 2>&1 &
  echo $! >"$LOG_DIR/$module.pid"
  wait_port "$port" "$module"
}

# ---------- preflight ----------
echo "=== Preflight ==="
command -v "$JAVA_BIN" >/dev/null || { echo "Java not found: $JAVA_BIN"; exit 1; }
"$JAVA_BIN" -version 2>&1 | head -1
command -v ss >/dev/null || command -v netstat >/dev/null || {
  echo "Need 'ss' or 'netstat' for port checks: apt install iproute2"; exit 1;
}

if [ "${DB_PASSWORD}" = "CHANGE_ME_DB_PASSWORD" ]; then
  echo "ERROR: edit cloud.env and set a real DB_PASSWORD"; exit 1
fi
if [ "${JWT_SECRET_KEY}" = "CHANGE_ME_TO_A_LONG_RANDOM_STRING_32_CHARS_MIN" ]; then
  echo "ERROR: edit cloud.env and set a real JWT_SECRET_KEY"; exit 1
fi
if [ "${LLM_API_KEY}" = "CHANGE_ME_DEEPSEEK_KEY" ]; then
  echo "ERROR: edit cloud.env and set a real LLM_API_KEY"; exit 1
fi

echo
echo "=== Profile ==="
echo "  MySQL    : local (localhost:3306)"
echo "  ES       : $([ "$ES_ENABLED" = true ] && echo enabled || echo 'DISABLED -> MySQL ngram')"
echo "  Canal    : $([ "$CANAL_ENABLED" = true ] && echo enabled || echo DISABLED)"
echo "  Redis    : $([ "$REDIS_ENABLED" = true ] && echo enabled || echo DISABLED)"
echo "  Embedding: $([ "$EMBEDDING_ENABLED" = true ] && echo enabled || echo 'DISABLED -> RAG unavailable')"
echo

# ---------- 0. embedding (optional) ----------
if [ "${EMBEDDING_ENABLED:-false}" = true ]; then
  echo "=== [0] Python embedding ==="
  if port_open 10683; then
    echo "  [SKIP] embedding already on :10683"
  else
    echo "  [START] embedding -> :10683"
    nohup "$PY_BIN" -m uvicorn --app-dir "$APP_ROOT" backend.ai_service:app \
      --host 127.0.0.1 --port 10683 >"$LOG_DIR/embedding.log" 2>&1 &
    echo $! >"$LOG_DIR/embedding.pid"
    wait_port 10683 embedding
  fi
fi

# ---------- 1-6. Java services ----------
echo "=== Java services ==="
start_java auth-service             8081
start_java catalog-service          8082
start_java translation-service      8083
start_java recommendation-service   8084
start_java platform-service         8085
start_java gateway-service          "${GATEWAY_PORT:-10682}"

echo
echo "=== Done ==="
echo "  Gateway : ${GATEWAY_PORT:-10682}"
echo "  Logs    : $LOG_DIR"
echo "  Health  : curl http://localhost:${GATEWAY_PORT:-10682}/api/products?per_page=1"
echo
echo "  Stop with: $APP_ROOT/deploy/cloud/stop-all.sh"
