#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"; FAKE_BIN="$TEST_ROOT/bin"; OBJECT_ROOT="$TEST_ROOT/objects"; LOG="$TEST_ROOT/oci.log"
RUNTIME_DIR="$TEST_ROOT/runtime"; BUNDLE="$RUNTIME_DIR/.bundle.001"; CREDENTIALS="$TEST_ROOT/backup-credentials"
mkdir -p "$FAKE_BIN" "$OBJECT_ROOT" "$BUNDLE"; chmod 700 "$RUNTIME_DIR" "$BUNDLE"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
ln -s .bundle.001 "$RUNTIME_DIR/current"
printf '%s\n' \
  "PAWCYCLE_DATASOURCE_HOST='db.example.com'" \
  "PAWCYCLE_DATASOURCE_PORT='3306'" \
  "PAWCYCLE_DATASOURCE_DATABASE='pawcycle'" \
  "PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'" \
  "SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'" \
  "SPRING_DATASOURCE_USERNAME='pawcycle_app'" \
  "SPRING_DATASOURCE_PASSWORD='application-password'" \
  "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'" \
  "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='25'" \
  "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='60000'" > "$BUNDLE/backend.env"
printf 'RUNTIME_ENV_FORMAT=1\n' > "$BUNDLE/.complete"; chmod 600 "$BUNDLE/backend.env" "$BUNDLE/.complete"
printf '%s\n' "MYSQL_BACKUP_USERNAME='backup_user'" "MYSQL_BACKUP_PASSWORD='backup-password'" > "$CREDENTIALS"; chmod 600 "$CREDENTIALS"

cat > "$FAKE_BIN/oci" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$FAKE_LOG"
if [[ "${1:-}" == os && "${2:-}" == object && "${3:-}" == put ]]; then
  [[ "${FAKE_OBJECT_EXISTS:-0}" != 1 ]] || exit 1
  name=""; file=""; shift 3
  while (($#)); do case "$1" in --name) name="$2"; shift 2 ;; --file) file="$2"; shift 2 ;; *) shift ;; esac; done
  mkdir -p "$FAKE_OBJECTS/$(dirname "$name")"; cp "$file" "$FAKE_OBJECTS/$name"
elif [[ "${1:-}" == os && "${2:-}" == object && "${3:-}" == get ]]; then
  name=""; file=""; shift 3
  while (($#)); do case "$1" in --name) name="$2"; shift 2 ;; --file) file="$2"; shift 2 ;; *) shift ;; esac; done
  cp "$FAKE_OBJECTS/$name" "$file"
elif [[ "${1:-}" == os && "${2:-}" == object && "${3:-}" == delete ]]; then
  name=""; shift 3
  while (($#)); do case "$1" in --name) name="$2"; shift 2 ;; *) shift ;; esac; done
  rm -f "$FAKE_OBJECTS/$name"
fi
EOF
cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
if [[ "$*" == *'mysqldump'* ]]; then printf 'CREATE TABLE example (id INT);\n'; exit 0; fi
if [[ "$*" == *'--entrypoint mysql'* ]]; then cat >/dev/null; printf 'schema-fingerprint\n'; exit 0; fi
case "$1" in
  volume) [[ "$2" == create ]] && printf '%s\n' "$3" && exit 0; [[ "$2" == rm ]] && exit 0 ;;
  run) [[ "$*" == *'--detach'* ]] && printf 'restore-container\n'; exit 0 ;;
  inspect) printf 'healthy\n'; exit 0 ;;
  exec)
    if [[ "$*" == *'information_schema.COLUMNS'* || "$*" == *'flyway_schema_history'* ]]; then printf 'schema-fingerprint\n';
    elif [[ "$*" == *'FROM members'* ]]; then printf '1:1:1:1\n';
    else cat >/dev/null; fi
    exit 0
    ;;
  rm) [[ "${FAKE_DOCKER_CLEANUP_FAIL:-0}" != 1 ]] || exit 1; exit 0 ;;
esac
exit 0
EOF
chmod +x "$FAKE_BIN/oci" "$FAKE_BIN/docker"

COMMON=(--runtime-dir "$RUNTIME_DIR" --backup-credential-file "$CREDENTIALS" --bucket pawcycle-backups --prefix production --region ap-tokyo-1)
DOCKER_LOG="$TEST_ROOT/docker.log"
BACKUP_OUTPUT="$(PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" backup "${COMMON[@]}")"
BACKUP_ID="${BACKUP_OUTPUT##*: }"; [[ "$BACKUP_ID" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$ ]]
mapfile -t PUTS < <(grep 'object put' "$LOG"); [[ "${#PUTS[@]}" == 3 ]]
[[ "${PUTS[0]}" == *'dump.sql.gz'* && "${PUTS[1]}" == *'manifest.txt'* && "${PUTS[2]}" == *'complete'* ]]
grep -Fq -- '--auth instance_principal' "$LOG"; grep -Fq -- '--no-overwrite' "$LOG"; grep -Fq -- '--verify-checksum' "$LOG"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_OBJECT_EXISTS=1 "$SCRIPT_DIR/oci-db-backup-restore.sh" backup "${COMMON[@]}" >/dev/null 2>&1; then
  printf 'object already exists was reported as success\n' >&2
  exit 1
fi
cp "$OBJECT_ROOT/production/$BACKUP_ID/dump.sql.gz" "$TEST_ROOT/dump.sql.gz.original"
printf 'tampered' >"$OBJECT_ROOT/production/$BACKUP_ID/dump.sql.gz"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null 2>&1; then
  printf 'hash mismatch was reported as success\n' >&2
  exit 1
fi
cp "$TEST_ROOT/dump.sql.gz.original" "$OBJECT_ROOT/production/$BACKUP_ID/dump.sql.gz"
rm -f "$OBJECT_ROOT/production/$BACKUP_ID/complete"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null 2>&1; then
  printf 'missing completion was reported as success\n' >&2
  exit 1
fi
printf 'BACKUP_ID=%s\n' "$BACKUP_ID" >"$OBJECT_ROOT/production/$BACKUP_ID/complete"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_DOCKER_CLEANUP_FAIL=1 "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null 2>&1; then
  printf 'cleanup failure was reported as success\n' >&2
  exit 1
fi
PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null
grep -Fq -- '--network none' "$DOCKER_LOG"
PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" cleanup "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null
[[ ! -e "$OBJECT_ROOT/production/$BACKUP_ID/complete" && ! -e "$OBJECT_ROOT/production/$BACKUP_ID/manifest.txt" && ! -e "$OBJECT_ROOT/production/$BACKUP_ID/dump.sql.gz" ]]
printf 'OCI Object Storage backup, restore-verify, cleanup, and credential-boundary fake tests passed\n'
