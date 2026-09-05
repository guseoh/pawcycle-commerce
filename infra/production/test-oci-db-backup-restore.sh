#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"; FAKE_BIN="$TEST_ROOT/bin"; OBJECT_ROOT="$TEST_ROOT/objects"; LOG="$TEST_ROOT/oci.log"
REAL_DOCKER="$(command -v docker || true)"
ACTUAL_CONTAINER=""
ACTUAL_VOLUME=""
RUNTIME_DIR="$TEST_ROOT/runtime"; BUNDLE="$RUNTIME_DIR/.bundle.001"; CREDENTIALS="$TEST_ROOT/backup-credentials"
mkdir -p "$FAKE_BIN" "$OBJECT_ROOT" "$BUNDLE"; chmod 700 "$RUNTIME_DIR" "$BUNDLE"
cleanup_actual() {
  if [[ -n "$ACTUAL_CONTAINER" && -n "$REAL_DOCKER" ]]; then
    "$REAL_DOCKER" rm --force "$ACTUAL_CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ -n "$ACTUAL_VOLUME" && -n "$REAL_DOCKER" ]]; then
    "$REAL_DOCKER" volume rm "$ACTUAL_VOLUME" >/dev/null 2>&1 || true
  fi
}
trap 'cleanup_actual; rm -rf -- "$TEST_ROOT"' EXIT
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
if [[ "$1" == run && "$*" == *'--entrypoint id'* && "$*" == *' -u mysql'* ]]; then id -u nobody; exit 0; fi
if [[ "$1" == run && "$*" == *'--entrypoint id'* && "$*" == *' -g mysql'* ]]; then id -g nobody; exit 0; fi
if [[ "$*" == *'--entrypoint mysql'* ]]; then
  sql="$(cat)"
  if [[ "$sql" == *'information_schema.COLUMNS'* ]]; then printf 'schema-fingerprint\n';
  elif [[ "$sql" == *'flyway_schema_history'* ]]; then printf 'flyway-fingerprint\n';
  else printf 'mysql-query-result\n'; fi
  exit 0
fi
case "$1" in
  volume) [[ "$2" == create ]] && printf '%s\n' "$3" && exit 0; [[ "$2" == rm ]] && exit 0 ;;
  run) [[ "$*" == *'--detach'* ]] && printf 'restore-container\n'; exit 0 ;;
  inspect) printf 'healthy\n'; exit 0 ;;
  exec)
    if [[ "$*" == *mysqladmin* ]]; then exit 0;
    elif [[ "$*" == *'information_schema.COLUMNS'* ]]; then printf 'schema-fingerprint\n';
    elif [[ "$*" == *'flyway_schema_history'* ]]; then printf 'flyway-fingerprint\n';
    elif [[ "$*" == *'FROM members'* ]]; then printf '1:1:1:1\n';
    else cat >/dev/null; fi
    exit 0
    ;;
  rm) [[ "${FAKE_DOCKER_CLEANUP_FAIL:-0}" != 1 ]] || exit 1; exit 0 ;;
esac
exit 0
EOF
cat > "$FAKE_BIN/chown" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'chown %s\n' "$*" >> "$FAKE_DOCKER_LOG"
EOF
chmod +x "$FAKE_BIN/oci" "$FAKE_BIN/docker" "$FAKE_BIN/chown"

COMMON=(--runtime-dir "$RUNTIME_DIR" --backup-credential-file "$CREDENTIALS" --bucket pawcycle-backups --prefix production --region ap-tokyo-1)
DOCKER_LOG="$TEST_ROOT/docker.log"
BACKUP_OUTPUT="$(PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" backup "${COMMON[@]}")"
BACKUP_ID="${BACKUP_OUTPUT##*: }"; [[ "$BACKUP_ID" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$ ]]
mapfile -t PUTS < <(grep 'object put' "$LOG"); [[ "${#PUTS[@]}" == 3 ]]
[[ "${PUTS[0]}" == *'dump.sql.gz'* && "${PUTS[1]}" == *'manifest.txt'* && "${PUTS[2]}" == *'complete'* ]]
grep -Fq -- '--auth instance_principal' "$LOG"; grep -Fq -- '--no-overwrite' "$LOG"; grep -Fq -- '--verify-checksum' "$LOG"
grep -Fq -- '--entrypoint mysqldump' "$DOCKER_LOG"
grep -Fq -- '--databases pawcycle' "$DOCKER_LOG"
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
cp "$OBJECT_ROOT/production/$BACKUP_ID/manifest.txt" "$TEST_ROOT/manifest.original"
schema_sha="$(sed -n 's/^SCHEMA_SHA256=//p' "$TEST_ROOT/manifest.original")"
flyway_sha="$(sed -n 's/^FLYWAY_SHA256=//p' "$TEST_ROOT/manifest.original")"
awk -v schema="$schema_sha" -v flyway="$flyway_sha" '
  $0 == "SCHEMA_SHA256=" schema { print "SCHEMA_SHA256=" flyway; next }
  $0 == "FLYWAY_SHA256=" flyway { print "FLYWAY_SHA256=" schema; next }
  { print }
' "$TEST_ROOT/manifest.original" >"$OBJECT_ROOT/production/$BACKUP_ID/manifest.txt"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null 2>&1; then
  printf 'swapped schema and Flyway fingerprints were accepted\n' >&2
  exit 1
fi
cp "$TEST_ROOT/manifest.original" "$OBJECT_ROOT/production/$BACKUP_ID/manifest.txt"
if PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_DOCKER_CLEANUP_FAIL=1 "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null 2>&1; then
  printf 'cleanup failure was reported as success\n' >&2
  exit 1
fi
PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" restore-verify "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null
grep -Fq -- '--network none' "$DOCKER_LOG"
grep -Fq -- '--user mysql:mysql' "$DOCKER_LOG"
grep -Fq -- '--pids-limit 128' "$DOCKER_LOG"
grep -Eq -- '--tmpfs /var/run/mysqld:size=16m,uid=[0-9]+,gid=[0-9]+,mode=755' "$DOCKER_LOG"
grep -Eq -- '^chown [0-9]+:[0-9]+ .*/mysql-root\.secret .*/restore-client\.cnf$' "$DOCKER_LOG"
grep -Fq -- 'mysqladmin --defaults-extra-file=/run/secrets/mysql-client.cnf --protocol=TCP ping --silent' "$DOCKER_LOG"
if grep -Eq -- '(--entrypoint mysql|docker exec .* mysql).*--force' "$DOCKER_LOG"; then
  printf 'restore mysql client still uses --force\n' >&2
  exit 1
fi
PATH="$FAKE_BIN:$PATH" FAKE_LOG="$LOG" FAKE_OBJECTS="$OBJECT_ROOT" FAKE_DOCKER_LOG="$DOCKER_LOG" "$SCRIPT_DIR/oci-db-backup-restore.sh" cleanup "${COMMON[@]}" --backup-id "$BACKUP_ID" >/dev/null
[[ ! -e "$OBJECT_ROOT/production/$BACKUP_ID/complete" && ! -e "$OBJECT_ROOT/production/$BACKUP_ID/manifest.txt" && ! -e "$OBJECT_ROOT/production/$BACKUP_ID/dump.sql.gz" ]]

if [[ -n "$REAL_DOCKER" ]] && "$REAL_DOCKER" info >/dev/null 2>&1; then
  ACTUAL_IMAGE="mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
  if ! "$REAL_DOCKER" image inspect "$ACTUAL_IMAGE" >/dev/null 2>&1; then
    "$REAL_DOCKER" pull "$ACTUAL_IMAGE" >/dev/null
  fi
  ACTUAL_VOLUME="oci-restore-regression-volume-${RANDOM}"
  ACTUAL_CONTAINER="oci-restore-regression-${RANDOM}"
  mysql_uid="$($REAL_DOCKER run --rm --pull never --network none --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 \
    --entrypoint id "$ACTUAL_IMAGE" -u mysql)"
  mysql_gid="$($REAL_DOCKER run --rm --pull never --network none --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 \
    --entrypoint id "$ACTUAL_IMAGE" -g mysql)"
  [[ "$mysql_uid" =~ ^[0-9]+$ && "$mysql_gid" =~ ^[0-9]+$ && "$mysql_uid" != 0 ]]
  "$REAL_DOCKER" volume create "$ACTUAL_VOLUME" >/dev/null
  "$REAL_DOCKER" run --detach --name "$ACTUAL_CONTAINER" --user mysql:mysql --network none \
    --mount "source=$ACTUAL_VOLUME,target=/var/lib/mysql" \
    --env MYSQL_ALLOW_EMPTY_PASSWORD=yes \
    --read-only --tmpfs /tmp:size=64m,mode=1777 --tmpfs "/var/run/mysqld:size=16m,uid=$mysql_uid,gid=$mysql_gid,mode=755" \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 \
    --log-driver none "$ACTUAL_IMAGE" >/dev/null
  ready=0
  for ((attempt=0; attempt<60; attempt++)); do
    if "$REAL_DOCKER" exec --user mysql:mysql "$ACTUAL_CONTAINER" mysqladmin --user=root --password= --protocol=TCP ping --silent >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  (( ready == 1 )) || { printf 'actual MySQL restore-container readiness failed\n' >&2; exit 1; }
  [[ "$("$REAL_DOCKER" exec --user mysql:mysql "$ACTUAL_CONTAINER" id -u)" != 0 ]]
  "$REAL_DOCKER" exec --user mysql:mysql "$ACTUAL_CONTAINER" mysql --user=root --password= --protocol=TCP --batch \
    --execute='CREATE DATABASE restore_verify_test;' >/dev/null
  if "$REAL_DOCKER" exec --user mysql:mysql "$ACTUAL_CONTAINER" mysql --user=root --password= --protocol=TCP --batch \
    --execute='THIS IS INVALID SQL;' >/dev/null 2>&1; then
    printf 'actual MySQL restore SQL failure was swallowed\n' >&2
    exit 1
  fi
  [[ "$("$REAL_DOCKER" inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$ACTUAL_CONTAINER")" == true ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{.HostConfig.NetworkMode}}' "$ACTUAL_CONTAINER")" == none ]]
  actual_ports="$("$REAL_DOCKER" inspect --format '{{json .NetworkSettings.Ports}}' "$ACTUAL_CONTAINER")"
  [[ "$actual_ports" == null || "$actual_ports" == '{}' ]]
  mounts="$("$REAL_DOCKER" inspect --format '{{json .Mounts}}' "$ACTUAL_CONTAINER")"
  [[ "$mounts" == *"$ACTUAL_VOLUME"* && "$mounts" == *'"Destination":"/var/lib/mysql"'* ]]
  tmpfs="$("$REAL_DOCKER" inspect --format '{{json .HostConfig.Tmpfs}}' "$ACTUAL_CONTAINER")"
  [[ "$tmpfs" == *'"/tmp"'* && "$tmpfs" == *'"/var/run/mysqld"'* ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{json .HostConfig.SecurityOpt}}' "$ACTUAL_CONTAINER")" == *'no-new-privileges:true'* ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{json .HostConfig.CapDrop}}' "$ACTUAL_CONTAINER")" == *'ALL'* ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{.HostConfig.Memory}}' "$ACTUAL_CONTAINER")" == 536870912 ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{.HostConfig.NanoCpus}}' "$ACTUAL_CONTAINER")" == 500000000 ]]
  [[ "$("$REAL_DOCKER" inspect --format '{{.HostConfig.PidsLimit}}' "$ACTUAL_CONTAINER")" == 128 ]]
  "$REAL_DOCKER" rm --force "$ACTUAL_CONTAINER" >/dev/null
  ACTUAL_CONTAINER=""
  "$REAL_DOCKER" volume rm "$ACTUAL_VOLUME" >/dev/null
  ACTUAL_VOLUME=""
  printf 'Actual pinned MySQL restore-container regression passed\n'
else
  printf 'Actual pinned MySQL restore-container regression skipped: Docker engine unavailable\n'
fi
printf 'OCI Object Storage backup, restore-verify, cleanup, and credential-boundary fake tests passed\n'
