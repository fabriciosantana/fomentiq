#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="fomentiq-cycle7-e2e-$$-${RANDOM}"
COMPOSE_FILE="$ROOT_DIR/compose.yaml"
POSTGRES_PORT="${POSTGRES_PORT:-15432}"
S3_PORT="${S3_PORT:-19000}"
S3_CONSOLE_PORT="${S3_CONSOLE_PORT:-19001}"
export COMPOSE_PROJECT_NAME="$PROJECT_NAME" POSTGRES_PORT S3_PORT S3_CONSOLE_PORT
compose_example_value() {
  awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ROOT_DIR/.env.example"
}
export POSTGRES_DB="${POSTGRES_DB:-$(compose_example_value POSTGRES_DB)}"
export POSTGRES_USER="${POSTGRES_USER:-$(compose_example_value POSTGRES_USER)}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(compose_example_value POSTGRES_PASSWORD)}"
export S3_ACCESS_KEY="${S3_ACCESS_KEY:-$(compose_example_value S3_ACCESS_KEY)}"
export S3_SECRET_KEY="${S3_SECRET_KEY:-$(compose_example_value S3_SECRET_KEY)}"

backend_pgid=""
BACKEND_LOG="/tmp/${PROJECT_NAME}-backend.log"
CLASSPATH_FILE="/tmp/${PROJECT_NAME}-test-classpath"
PLAYWRIGHT_OUTPUT_DIR="/tmp/${PROJECT_NAME}-playwright-results"
export PLAYWRIGHT_OUTPUT_DIR
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  if [[ -n "$backend_pgid" ]]; then
    kill -- "-$backend_pgid" 2>/dev/null || true
    wait "$backend_pgid" 2>/dev/null || true
  fi
  docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" down \
    --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
  rm -f "$BACKEND_LOG" "$CLASSPATH_FILE"
  node -e 'require("node:fs").rmSync(process.argv[1], { recursive: true, force: true })' "$PLAYWRIGHT_OUTPUT_DIR"
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$ROOT_DIR"
if curl --silent --fail http://127.0.0.1:8080/actuator/health >/dev/null; then
  echo "Port 8080 already serves a healthy application; stop it before this isolated E2E run." >&2
  exit 1
fi
docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --wait postgres object-storage

export DATABASE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
export DATABASE_USERNAME="$POSTGRES_USER" DATABASE_PASSWORD="$POSTGRES_PASSWORD"
export S3_ENABLED=true S3_ENDPOINT="http://127.0.0.1:${S3_PORT}" S3_BUCKET="fomentiq-cycle7"
export S3_REGION=us-east-1
export OPENAI_API_KEY=''

mvn -B -f backend/pom.xml -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test "-Dmdep.outputFile=${CLASSPATH_FILE}"
APP_CLASSPATH="${ROOT_DIR}/backend/target/test-classes:${ROOT_DIR}/backend/target/classes:$(cat "$CLASSPATH_FILE")"
setsid java -cp "$APP_CLASSPATH" com.fomentiq.ingestion.OfflineE2eApplication \
  > "$BACKEND_LOG" 2>&1 &
backend_pgid=$!

for _ in $(seq 1 90); do
  if curl --silent --fail http://127.0.0.1:8080/actuator/health >/dev/null; then break; fi
  if ! kill -0 "$backend_pgid" 2>/dev/null; then
    sed -E 's/(password|secret|token|authorization)[=: ]+[^ ,]+/\1=[REDACTED]/Ig' "$BACKEND_LOG"
    exit 1
  fi
  sleep 2
done
curl --silent --fail http://127.0.0.1:8080/actuator/health >/dev/null
grep -E '^OFFLINE_FIXTURE_INGEST ' "$BACKEND_LOG"

cd "$ROOT_DIR/frontend"
npm run test:e2e

echo "Offline E2E passed. This run's backend, Compose project, network, and named volumes are removed on exit."
