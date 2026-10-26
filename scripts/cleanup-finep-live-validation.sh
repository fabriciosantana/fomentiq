#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <manifest.json> <ingestion-run-uuid>" >&2
  exit 2
fi

MANIFEST_PATH=$1
INGESTION_RUN_ID=$2
REPOSITORY_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd "$REPOSITORY_ROOT"

if [[ ! "$INGESTION_RUN_ID" =~ ^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$ ]]; then
  echo "cleanup refused: invalid run identifier" >&2
  exit 2
fi

PROJECT_NAME=$(python3 - "$MANIFEST_PATH" <<'PY'
import json, pathlib, re, sys
try:
    value = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    project = value["isolation"]["composeProject"]
except (OSError, ValueError, KeyError, TypeError):
    raise SystemExit("cleanup refused: approved manifest is unavailable")
if not re.fullmatch(r"fomentiq-finep-live-[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", project):
    raise SystemExit("cleanup refused: compose project is not a dedicated Finep validation project")
print(project)
PY
)

if [[ -z "${FOMENTIQ_AUDIT_ARCHIVE_DIR:-}" || ! -d "$FOMENTIQ_AUDIT_ARCHIVE_DIR" ]]; then
  echo "cleanup refused: private audit archive directory is unavailable" >&2
  exit 3
fi
java -jar backend/target/fomentiq-backend-0.1.0-SNAPSHOT.jar \
  "--fomentiq.audit.export-run=$INGESTION_RUN_ID"
java -jar backend/target/fomentiq-backend-0.1.0-SNAPSHOT.jar \
  "--fomentiq.audit.verify=$FOMENTIQ_AUDIT_ARCHIVE_DIR/packages/$INGESTION_RUN_ID.zip"
python3 - "$FOMENTIQ_AUDIT_ARCHIVE_DIR/packages/$INGESTION_RUN_ID.zip" "$INGESTION_RUN_ID" "$PROJECT_NAME" <<'PY'
import json, sys, zipfile
try:
    with zipfile.ZipFile(sys.argv[1]) as package:
        manifest = json.loads(package.read("manifest.json"))
        approval = json.loads(package.read("authorization/manifest-sanitized.json"))
        run_link = json.loads(package.read("authorization/run-manifest-link.json"))
except (OSError, KeyError, ValueError, zipfile.BadZipFile):
    raise SystemExit("cleanup refused: verified package manifest is unavailable")
authorized_project = approval.get("approvedManifest", {}).get("isolation", {}).get("composeProject")
if (manifest.get("complete") is not True or manifest.get("ingestionRunId") != sys.argv[2]
        or run_link.get("ingestionRunId") != sys.argv[2]
        or run_link.get("manifestRunId") != approval.get("manifestRunId")
        or authorized_project != sys.argv[3]):
    raise SystemExit("cleanup refused: package is incomplete or belongs to another run")
PY

mapfile -t CONTAINERS < <(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT_NAME")
mapfile -t VOLUMES < <(docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT_NAME")
if (( ${#CONTAINERS[@]} == 0 )) || (( ${#VOLUMES[@]} != 2 )); then
  echo "cleanup refused: expected dedicated PostgreSQL and object-storage resources were not found" >&2
  exit 4
fi

for container_id in "${CONTAINERS[@]}"; do
  service=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$container_id")
  if [[ "$service" != "postgres" && "$service" != "object-storage" ]]; then
    echo "cleanup refused: unexpected service in the dedicated Compose project" >&2
    exit 4
  fi
done

EXPECTED_VOLUMES=$(printf '%s\n' "${PROJECT_NAME}_postgres_data" "${PROJECT_NAME}_object_storage_data" | sort)
ACTUAL_VOLUMES=$(printf '%s\n' "${VOLUMES[@]}" | sort)
if [[ "$EXPECTED_VOLUMES" != "$ACTUAL_VOLUMES" ]]; then
  echo "cleanup refused: Compose volumes do not match the dedicated project" >&2
  exit 4
fi

docker compose --project-name "$PROJECT_NAME" -f compose.yaml down --volumes --remove-orphans
echo "Dedicated Finep validation Compose resources removed after verified audit export."
