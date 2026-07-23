#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/db-backup-restore.sh"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PRODUCTION_VOLUME="pawcycle-production-mysql-data"
BUCKET="ops013-validation-bucket"
REGION="ap-northeast-2"
PREFIX="production/mysql-backups"
TEMP_DIR="$(mktemp -d)"
FAKE_BIN="$TEMP_DIR/bin"
FAKE_S3_ROOT="$TEMP_DIR/s3"
WORK_ROOT="$TEMP_DIR/work"
LOCK_FILE="$TEMP_DIR/lock/ops013.lock"
SOURCE_SECRET="$TEMP_DIR/source-root-password"
SOURCE_CONTAINER="ops013-source-$RANDOM"
BACKUP_ID=""
PRODUCTION_VOLUME_CREATED=0
REAL_GZIP="$(command -v gzip)"

cleanup() {
  local resolved_base
  local resolved_temp

  set +e
  docker rm --force "$SOURCE_CONTAINER" >/dev/null 2>&1
  if [[ -n "$BACKUP_ID" ]]; then
    docker ps --all --quiet \
      --filter label=com.pawcycle.ops013.scope=restore \
      --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID" \
      | xargs -r docker rm --force >/dev/null 2>&1
    docker volume ls --quiet \
      --filter label=com.pawcycle.ops013.scope=restore \
      --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID" \
      | xargs -r docker volume rm >/dev/null 2>&1
  fi
  if [[ "$PRODUCTION_VOLUME_CREATED" == "1" ]]; then
    docker volume rm "$PRODUCTION_VOLUME" >/dev/null 2>&1
  fi
  resolved_base="$(readlink -f -- "${TMPDIR:-/tmp}")"
  resolved_temp="$(readlink -f -- "$TEMP_DIR")"
  if [[ -n "$resolved_temp" && -d "$resolved_temp" && ! -L "$TEMP_DIR" && "$resolved_temp" == "$resolved_base"/tmp.* ]]; then
    if command -v sudo >/dev/null 2>&1; then
      sudo rm -rf -- "$resolved_temp"
    else
      rm -rf -- "$resolved_temp"
    fi
  fi
}
trap cleanup EXIT

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

assert_no_restore_resources() {
  [[ -z "$(docker ps --all --quiet \
    --filter label=com.pawcycle.ops013.scope=restore \
    --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID")" ]] \
    || fail "temporary restore container remained"
  [[ -z "$(docker volume ls --quiet \
    --filter label=com.pawcycle.ops013.scope=restore \
    --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID")" ]] \
    || fail "temporary restore volume remained"
  if [[ -d "$WORK_ROOT" ]]; then
    [[ -z "$(sudo find "$WORK_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'ops013-*' -print -quit 2>/dev/null)" ]] \
      || fail "temporary restore work file remained"
  fi
}

run_ops013() {
  sudo env \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_S3_ROOT="$FAKE_S3_ROOT" \
    FAKE_TEST_UID="$(id -u)" \
    FAKE_TEST_GID="$(id -g)" \
    FAKE_AWS_FAIL_OPERATION="${FAKE_AWS_FAIL_OPERATION:-}" \
    FAKE_AWS_ENCRYPTION="${FAKE_AWS_ENCRYPTION:-}" \
    FAKE_AWS_PUBLIC_BLOCK="${FAKE_AWS_PUBLIC_BLOCK:-}" \
    FAKE_AWS_VERSIONING="${FAKE_AWS_VERSIONING:-}" \
    FAKE_AWS_LIFECYCLE_COUNT="${FAKE_AWS_LIFECYCLE_COUNT:-}" \
    FAKE_AWS_HEAD_SIZE="${FAKE_AWS_HEAD_SIZE:-}" \
    FAKE_GZIP_FAIL="${FAKE_GZIP_FAIL:-}" \
    FAKE_GZIP_FAIL_DECOMPRESS="${FAKE_GZIP_FAIL_DECOMPRESS:-}" \
    PAWCYCLE_OPS013_TEST_MODE=local-validation-only \
    PAWCYCLE_BACKUP_WORK_ROOT="$WORK_ROOT" \
    PAWCYCLE_BACKUP_LOCK_FILE="$LOCK_FILE" \
    "$SCRIPT" "$@"
}

wait_source_mysql() {
  local elapsed=0

  while (( elapsed < 180 )); do
    if docker exec "$SOURCE_CONTAINER" sh -eu -c \
      'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=SOCKET --user=root --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT 1;"' \
      >/dev/null 2>&1 \
      && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$SOURCE_CONTAINER")" == "healthy" ]]; then
      return 0
    fi
    if [[ "$(docker inspect --format '{{.State.Status}}' "$SOURCE_CONTAINER")" == "exited" ]]; then
      return 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  return 1
}

mkdir -p "$FAKE_BIN" "$FAKE_S3_ROOT"
cat >"$FAKE_BIN/aws" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

operation="${2:-}"
shift 2

argument() {
  local wanted="$1"
  shift
  while [[ $# -gt 0 ]]; do
    if [[ "$1" == "$wanted" ]]; then
      printf '%s\n' "$2"
      return 0
    fi
    shift
  done
  return 1
}

if [[ "${FAKE_AWS_FAIL_OPERATION:-}" == "$operation" ]]; then
  printf 'simulated AWS failure\n' >&2
  exit 1
fi

case "$operation" in
  get-bucket-location)
    printf '%s\n' 'ap-northeast-2'
    ;;
  get-public-access-block)
    printf '%s\n' "${FAKE_AWS_PUBLIC_BLOCK:-True	True	True	True}"
    ;;
  get-bucket-encryption)
    printf '%s\n' "${FAKE_AWS_ENCRYPTION:-AES256}"
    ;;
  get-bucket-versioning)
    printf '%s\n' "${FAKE_AWS_VERSIONING:-None}"
    ;;
  get-bucket-lifecycle-configuration)
    printf '%s\n' "${FAKE_AWS_LIFECYCLE_COUNT:-1}"
    ;;
  put-object)
    key="$(argument --key "$@")"
    body="$(argument --body "$@")"
    mkdir -p -- "$FAKE_S3_ROOT/$(dirname -- "$key")"
    cp -- "$body" "$FAKE_S3_ROOT/$key"
    chown -R "$FAKE_TEST_UID:$FAKE_TEST_GID" "$FAKE_S3_ROOT"
    printf '{}\n'
    ;;
  head-object)
    key="$(argument --key "$@")"
    if [[ -n "${FAKE_AWS_HEAD_SIZE:-}" && "$key" == *.sql.gz ]]; then
      size="$FAKE_AWS_HEAD_SIZE"
    else
      size="$(stat -c '%s' "$FAKE_S3_ROOT/$key")"
    fi
    printf '%s\tAES256\n' "$size"
    ;;
  get-object)
    key="$(argument --key "$@")"
    target="${@: -1}"
    cp -- "$FAKE_S3_ROOT/$key" "$target"
    printf '{}\n'
    ;;
  *)
    printf 'unsupported fake AWS operation\n' >&2
    exit 1
    ;;
esac
EOF
cat >"$FAKE_BIN/gzip" <<EOF
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "\${FAKE_GZIP_FAIL:-}" == "1" ]]; then
  exit 1
fi
if [[ "\${FAKE_GZIP_FAIL_DECOMPRESS:-}" == "1" ]]; then
  for argument in "\$@"; do
    if [[ "\$argument" == "--decompress" ]]; then
      exit 1
    fi
  done
fi
exec "$REAL_GZIP" "\$@"
EOF
chmod +x "$FAKE_BIN/aws" "$FAKE_BIN/gzip" "$SCRIPT"

command -v docker >/dev/null || fail "docker is required"
command -v sudo >/dev/null || fail "sudo is required for root-only OPS-013 path validation"
docker info >/dev/null 2>&1 || fail "Docker engine is required"
[[ -z "$(docker ps --all --quiet \
  --filter label=com.docker.compose.project=pawcycle-production \
  --filter label=com.docker.compose.service=mysql)" ]] \
  || fail "a production-labeled MySQL container already exists; refusing destructive test setup"
if docker volume inspect "$PRODUCTION_VOLUME" >/dev/null 2>&1; then
  fail "the production MySQL volume name already exists; refusing destructive test setup"
fi

printf '%s\n' 'local-validation-root-password' >"$SOURCE_SECRET"
chmod 600 "$SOURCE_SECRET"
docker volume create "$PRODUCTION_VOLUME" >/dev/null
PRODUCTION_VOLUME_CREATED=1
docker run --detach \
  --name "$SOURCE_CONTAINER" \
  --label com.docker.compose.project=pawcycle-production \
  --label com.docker.compose.service=mysql \
  --network none \
  --mount "type=volume,source=$PRODUCTION_VOLUME,destination=/var/lib/mysql" \
  --mount "type=bind,source=$SOURCE_SECRET,destination=/run/secrets/mysql-root-password,readonly" \
  --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root-password \
  --env MYSQL_DATABASE=ops013_source \
  --health-cmd='MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" mysqladmin --protocol=SOCKET --user=root ping --silent' \
  --health-interval=3s \
  --health-timeout=3s \
  --health-retries=30 \
  "$MYSQL_IMAGE" \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci >/dev/null
wait_source_mysql || fail "source MySQL did not become ready"

docker exec --interactive "$SOURCE_CONTAINER" sh -eu -c \
  'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=SOCKET --user=root "$MYSQL_DATABASE"' <<'SQL'
CREATE TABLE flyway_schema_history (
  installed_rank INT NOT NULL PRIMARY KEY,
  version VARCHAR(50),
  description VARCHAR(200) NOT NULL,
  type VARCHAR(20) NOT NULL,
  script VARCHAR(1000) NOT NULL,
  checksum INT,
  success BOOLEAN NOT NULL
);
INSERT INTO flyway_schema_history VALUES
  (1, '1', 'validation schema', 'SQL', 'V1__validation.sql', 12345, TRUE);
CREATE TABLE members (id BIGINT PRIMARY KEY);
CREATE TABLE products (id BIGINT PRIMARY KEY);
CREATE TABLE skus (id BIGINT PRIMARY KEY);
CREATE TABLE subscriptions (id BIGINT PRIMARY KEY);
INSERT INTO members VALUES (1);
INSERT INTO products VALUES (1);
INSERT INTO skus VALUES (1);
INSERT INTO subscriptions VALUES (1);
SQL

backup_output="$(run_ops013 backup --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX")"
BACKUP_ID="$(sed -n 's/^Backup completed: //p' <<<"$backup_output")"
[[ -n "$BACKUP_ID" ]] || fail "backup ID was not returned"
backup_root="$FAKE_S3_ROOT/$PREFIX"
for suffix in sql.gz sql.gz.sha256 verify verify.sha256 complete; do
  [[ -s "$backup_root/${BACKUP_ID}.${suffix}" ]] || fail "backup object set is incomplete"
done
baseline="$TEMP_DIR/baseline"
mkdir -p "$baseline"
cp -a -- "$backup_root/." "$baseline/"

run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  >/dev/null
assert_no_restore_resources

oversized_failure="$(FAKE_AWS_HEAD_SIZE=5000000001 run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  2>&1 >/dev/null || true)"
[[ "$oversized_failure" == *"S3 object exceeds the approved download size limit"* ]] \
  || fail "oversized S3 object was not rejected before download"
assert_no_restore_resources

cp -a -- "$baseline/." "$backup_root/"
dump_hash="$(sha256sum "$backup_root/${BACKUP_ID}.sql.gz" | awk '{print $1}')"
printf '%s  /dev/zero\n' "$dump_hash" >"$backup_root/${BACKUP_ID}.sql.gz.sha256"
checksum_target_failure="$(run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  2>&1 >/dev/null || true)"
[[ "$checksum_target_failure" == *"checksum target filename is invalid"* ]] \
  || fail "untrusted checksum target filename was not rejected"
assert_no_restore_resources

cp -a -- "$baseline/." "$backup_root/"
decompression_failure="$(FAKE_GZIP_FAIL_DECOMPRESS=1 run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  2>&1 >/dev/null || true)"
[[ "$decompression_failure" == *"restore-decompression-failed"* ]] \
  || fail "restore decompression failure stage was not reported"
assert_no_restore_resources

if FAKE_GZIP_FAIL=1 run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "backup failure was reported as success"
fi

if FAKE_AWS_FAIL_OPERATION=put-object run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "upload failure was reported as success"
fi

if FAKE_AWS_ENCRYPTION=aws:kms run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "bucket encryption mismatch was reported as success"
fi
if FAKE_AWS_PUBLIC_BLOCK='True True False True' run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "bucket public access mismatch was reported as success"
fi
if FAKE_AWS_VERSIONING=Enabled run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "bucket versioning mismatch was reported as success"
fi
if FAKE_AWS_LIFECYCLE_COUNT=0 run_ops013 backup \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" >/dev/null 2>&1; then
  fail "bucket lifecycle mismatch was reported as success"
fi

cp -a -- "$baseline/." "$backup_root/"
printf 'tampered\n' >>"$backup_root/${BACKUP_ID}.sql.gz"
if run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  >/dev/null 2>&1; then
  fail "checksum mismatch was reported as success"
fi
assert_no_restore_resources

cp -a -- "$baseline/." "$backup_root/"
gzip --decompress --stdout "$backup_root/${BACKUP_ID}.sql.gz" >"$TEMP_DIR/invalid.sql"
printf '\nTHIS IS NOT VALID SQL;\n' >>"$TEMP_DIR/invalid.sql"
gzip --stdout "$TEMP_DIR/invalid.sql" >"$backup_root/${BACKUP_ID}.sql.gz"
(
  cd "$backup_root"
  sha256sum "${BACKUP_ID}.sql.gz" >"${BACKUP_ID}.sql.gz.sha256"
)
dump_hash="$(sha256sum "$backup_root/${BACKUP_ID}.sql.gz" | awk '{print $1}')"
sed -i "s/^DUMP_SHA256=.*/DUMP_SHA256=$dump_hash/" "$backup_root/${BACKUP_ID}.complete"
restore_failure="$(run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  2>&1 >/dev/null || true)"
[[ "$restore_failure" == *"restore-sql-import-failed"* ]] \
  || fail "restore SQL import failure stage was not reported"
assert_no_restore_resources

cp -a -- "$baseline/." "$backup_root/"
sed -i 's/^TABLE_members=.*/TABLE_members=999/' "$backup_root/${BACKUP_ID}.verify"
(
  cd "$backup_root"
  sha256sum "${BACKUP_ID}.verify" >"${BACKUP_ID}.verify.sha256"
)
manifest_hash="$(sha256sum "$backup_root/${BACKUP_ID}.verify" | awk '{print $1}')"
sed -i "s/^MANIFEST_SHA256=.*/MANIFEST_SHA256=$manifest_hash/" "$backup_root/${BACKUP_ID}.complete"
if run_ops013 restore-verify \
  --bucket "$BUCKET" --region "$REGION" --prefix "$PREFIX" --backup-id "$BACKUP_ID" \
  >/dev/null 2>&1; then
  fail "verification mismatch was reported as success"
fi
assert_no_restore_resources

docker exec "$SOURCE_CONTAINER" sh -eu -c \
  'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; test "$(mysql --protocol=SOCKET --user=root --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COUNT(*) FROM members;")" = "1"' \
  || fail "source production fixture changed during backup or restore verification"
docker volume inspect "$PRODUCTION_VOLUME" >/dev/null \
  || fail "source production volume was removed"

run_ops013 cleanup --backup-id "$BACKUP_ID" >/dev/null
assert_no_restore_resources
printf 'OPS-013 isolated backup and restore lifecycle tests passed\n'
