#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
TEMP_ROOT=$(mktemp -d)
trap 'rm -rf "$TEMP_ROOT"' EXIT
ARCHIVE="$TEMP_ROOT/archive"
BIN="$TEMP_ROOT/bin"
mkdir -p "$ARCHIVE/packages" "$BIN"
RUN_ID=11111111-2222-3333-4444-555555555555
PROJECT_ID=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
MANIFEST="$TEMP_ROOT/manifest.json"
python3 - "$MANIFEST" "$ARCHIVE/packages/$RUN_ID.zip" "$RUN_ID" "$PROJECT_ID" <<'PY'
import json, pathlib, sys, zipfile
manifest_run_id = "99999999-8888-7777-6666-555555555555"
pathlib.Path(sys.argv[1]).write_text(json.dumps({"isolation": {
    "composeProject": "fomentiq-finep-live-" + sys.argv[4]}}), encoding="utf-8")
with zipfile.ZipFile(sys.argv[2], "w") as package:
    package.writestr("manifest.json", json.dumps({"complete": True, "ingestionRunId": sys.argv[3]}))
    package.writestr("authorization/manifest-sanitized.json", json.dumps({
        "manifestRunId": manifest_run_id,
        "approvedManifest": {"runId": manifest_run_id, "isolation": {
            "composeProject": "fomentiq-finep-live-different-run"}}}))
    package.writestr("authorization/run-manifest-link.json", json.dumps({
        "manifestRunId": manifest_run_id, "ingestionRunId": sys.argv[3]}))
PY

cat > "$BIN/java" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_COMMAND_LOG"
case "${FAKE_JAVA_MODE}:${*}" in
  export-fail:*audit.export-run*) exit 9 ;;
  verify-fail:*audit.verify*) exit 3 ;;
  *) exit 0 ;;
esac
SH
cat > "$BIN/docker" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_COMMAND_LOG"
exit 0
SH
chmod 750 "$BIN/java" "$BIN/docker"

for mode in export-fail verify-fail identity-mismatch; do
  LOG="$TEMP_ROOT/$mode.log"
  if PATH="$BIN:$PATH" FOMENTIQ_AUDIT_ARCHIVE_DIR="$ARCHIVE" FAKE_COMMAND_LOG="$LOG" \
      FAKE_JAVA_MODE="$mode" "$ROOT/scripts/cleanup-finep-live-validation.sh" "$MANIFEST" "$RUN_ID" \
      >/dev/null 2>&1; then
    echo "cleanup gate test failed: $mode unexpectedly succeeded" >&2
    exit 1
  fi
  if grep -q 'compose .*down' "$LOG" 2>/dev/null; then
    echo "cleanup gate test failed: cleanup ran after $mode" >&2
    exit 1
  fi
done

echo "PASS: export, verification, and manifest/project identity failures block Compose cleanup"
