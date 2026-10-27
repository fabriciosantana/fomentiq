#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="fomentiq-curation-e2e-$$${RANDOM}"
COMPOSE_FILE="$ROOT_DIR/compose.yaml"
POSTGRES_PORT="${E2E_POSTGRES_PORT:-25432}"
S3_PORT="${E2E_S3_PORT:-29000}"
S3_CONSOLE_PORT="${E2E_S3_CONSOLE_PORT:-29001}"
BACKEND_PORT="${E2E_BACKEND_PORT:-18080}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-14173}"
export COMPOSE_PROJECT_NAME="$PROJECT_NAME" POSTGRES_PORT S3_PORT S3_CONSOLE_PORT HOST_BIND_ADDRESS=127.0.0.1
compose_example_value() {
  awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ROOT_DIR/.env.example"
}
export POSTGRES_DB="fomentiq_e2e"
export POSTGRES_USER="fomentiq_e2e"
export POSTGRES_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
export S3_ACCESS_KEY="e2e-$(python3 -c 'import secrets; print(secrets.token_hex(8))')"
export S3_SECRET_KEY="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"

backend_pgid=""
BACKEND_LOG="/tmp/${PROJECT_NAME}-backend.log"
CLASSPATH_FILE="/tmp/${PROJECT_NAME}-test-classpath"
PLAYWRIGHT_OUTPUT_DIR="/tmp/${PROJECT_NAME}-playwright-results"
TLS_DIRECTORY="/tmp/${PROJECT_NAME}-tls"
export PLAYWRIGHT_OUTPUT_DIR PLAYWRIGHT_WEB_PORT="$FRONTEND_PORT"
export PLAYWRIGHT_API_PROXY_TARGET="http://127.0.0.1:${BACKEND_PORT}" PLAYWRIGHT_WEB_PROTOCOL=https
export VITE_DEV_HTTPS_KEY="${TLS_DIRECTORY}/localhost-key.pem" VITE_DEV_HTTPS_CERT="${TLS_DIRECTORY}/localhost-cert.pem"
safe_to_cleanup=0
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  if [[ "$exit_code" != 0 && -s "$BACKEND_LOG" ]]; then
    echo "Sanitized E2E backend error summary:" >&2
    grep -E 'ERROR|Exception|Caused by' "$BACKEND_LOG" | tail -n 40 \
      | sed -E 's/(password|secret|token|authorization)[=: ]+[^ ,]+/\1=[REDACTED]/Ig' >&2 || true
  fi
  if [[ -n "$backend_pgid" ]]; then
    kill -- "-$backend_pgid" 2>/dev/null || true
    wait "$backend_pgid" 2>/dev/null || true
  fi
  if [[ "$safe_to_cleanup" == 1 ]]; then
    docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" down \
      --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
  fi
  rm -f "$BACKEND_LOG" "$CLASSPATH_FILE"
  rm -rf "$TLS_DIRECTORY"
  node -e 'require("node:fs").rmSync(process.argv[1], { recursive: true, force: true })' "$PLAYWRIGHT_OUTPUT_DIR"
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$ROOT_DIR"
for port in "$POSTGRES_PORT" "$S3_PORT" "$S3_CONSOLE_PORT" "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if ! python3 - "$port" <<'PY'
import socket, sys
sock = socket.socket()
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    sock.bind(("127.0.0.1", int(sys.argv[1])))
except OSError:
    sys.exit(1)
finally:
    sock.close()
PY
  then
    echo "Required isolated E2E port ${port} is already occupied; refusing to reuse or stop it." >&2
    exit 1
  fi
done
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT_NAME}")" ]] \
  || docker volume inspect "${PROJECT_NAME}_postgres_data" >/dev/null 2>&1 \
  || docker volume inspect "${PROJECT_NAME}_object_storage_data" >/dev/null 2>&1 \
  || docker network inspect "${PROJECT_NAME}_default" >/dev/null 2>&1; then
  echo "Generated Compose identity already has containers, volumes, or a network; refusing to reuse it." >&2
  exit 1
fi
safe_to_cleanup=1
mkdir -m 700 "$TLS_DIRECTORY"
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$VITE_DEV_HTTPS_KEY" \
  -out "$VITE_DEV_HTTPS_CERT" -days 1 -subj '/CN=127.0.0.1' >/dev/null 2>&1
docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --wait postgres object-storage

export DATABASE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
export DATABASE_USERNAME="$POSTGRES_USER" DATABASE_PASSWORD="$POSTGRES_PASSWORD"
export S3_ENABLED=true S3_ENDPOINT="http://127.0.0.1:${S3_PORT}"
export S3_REGION=us-east-1
export OPENAI_API_KEY=''
export SPRING_PROFILES_ACTIVE=manual-demo FOMENTIQ_ADMIN_DEMO_ENABLED=true
export FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT="$PROJECT_NAME"
export FOMENTIQ_ADMIN_DEMO_POSTGRES_PORT="$POSTGRES_PORT" FOMENTIQ_ADMIN_DEMO_S3_PORT="$S3_PORT"
export FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT="$S3_CONSOLE_PORT"
export FOMENTIQ_ADMIN_USERNAME=offline-reviewer
export FOMENTIQ_ADMIN_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
export S3_BUCKET="$PROJECT_NAME"

mvn -B -f backend/pom.xml -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test "-Dmdep.outputFile=${CLASSPATH_FILE}"
APP_CLASSPATH="${ROOT_DIR}/backend/target/test-classes:${ROOT_DIR}/backend/target/classes:$(cat "$CLASSPATH_FILE")"
setsid java -cp "$APP_CLASSPATH" com.fomentiq.ingestion.OfflineE2eApplication \
  --server.port="$BACKEND_PORT" \
  --server.address=127.0.0.1 \
  > "$BACKEND_LOG" 2>&1 &
backend_pgid=$!

for _ in $(seq 1 90); do
  if curl --silent --fail "http://127.0.0.1:${BACKEND_PORT}/actuator/health" >/dev/null; then break; fi
  if ! kill -0 "$backend_pgid" 2>/dev/null; then
    sed -E 's/(password|secret|token|authorization)[=: ]+[^ ,]+/\1=[REDACTED]/Ig' "$BACKEND_LOG"
    exit 1
  fi
  sleep 2
done
curl --silent --fail "http://127.0.0.1:${BACKEND_PORT}/actuator/health" >/dev/null
grep -E '^OFFLINE_FIXTURE_INGEST source=(FINEP|CNPQ) ' "$BACKEND_LOG"

cd "$ROOT_DIR/frontend"
npm run test:e2e

echo "Offline E2E passed. This run's backend, Compose project, network, and named volumes are removed on exit."
