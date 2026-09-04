#!/usr/bin/env bash
set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
MYSQL_TOOL_IMAGE="mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
DATABASE_EGRESS_NETWORK="pawcycle-production-database-egress"
RUNTIME_DIR=""; BACKUP_CREDENTIAL_FILE=""; BUCKET=""; PREFIX=""; REGION=""; BACKUP_ID=""
TEMP_DIR=""; MYSQL_OPTION_FILE=""; MYSQL_ROOT_SECRET_FILE=""; MYSQL_CONTAINER=""; MYSQL_VOLUME=""

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage() { printf 'Usage: %s <backup|restore-verify|cleanup> --runtime-dir <path> --backup-credential-file <path> --bucket <name> --prefix <prefix> --region <region> [--backup-id <id>]\n' "${0##*/}" >&2; }
require_command() { command -v "$1" >/dev/null 2>&1 || die "required command is unavailable"; }
validate_path() { [[ "$1" == /* && "$1" != "/" ]] || die "$2 must be an absolute path other than /"; }
validate_region() { [[ "$1" =~ ^[a-z0-9]([a-z0-9-]{0,30}[a-z0-9])?$ ]] || die "region is invalid"; }
validate_bucket() { [[ "$1" =~ ^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$ ]] || die "bucket is invalid"; }
validate_prefix() { [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$ && "$1" != *..* ]] || die "prefix is invalid"; }
validate_backup_id() { [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$ ]] || die "backup id is invalid"; }
validate_datasource_host() {
  local host="$1" octet; local -a octets
  if [[ "$host" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
    IFS=. read -r -a octets <<<"$host"
    for octet in "${octets[@]}"; do ((octet <= 255)) || die "datasource host is not a private IPv4 address"; done
    if ((octets[0] == 10)) || ((octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)) || ((octets[0] == 192 && octets[1] == 168)); then return 0; fi
    die "datasource host is not a private IPv4 address"
  fi
  [[ ${#host} -le 253 && "$host" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] || die "datasource host is not an approved DNS name"
}

read_setting() {
  local file="$1" key="$2" line value matches=0
  local quote="'" prefix="${key}='" encoded
  while IFS= read -r line; do
    if [[ "$line" == "$key="* ]]; then
      matches=$((matches + 1))
      [[ "$line" == "$prefix"*"$quote" ]] || return 1
      value="${line#"$prefix"}"
      value="${value%"$quote"}"
      encoded="$value"
      while [[ "$encoded" == *"$quote"* ]]; do
        [[ "$encoded" == *"\\$quote"* ]] || return 1
        encoded="${encoded//\\$quote/}"
      done
      value="${value//\\$quote/$quote}"
    fi
  done < "$file"
  [[ "$matches" == 1 ]] || return 1
  printf '%s' "$value"
}

validate_backend_env() {
  local bundle env complete host port database ssl url user password line key
  [[ -d "$RUNTIME_DIR" && ! -L "$RUNTIME_DIR" && "$(stat -c '%a' "$RUNTIME_DIR")" == 700 ]] || die "runtime directory is invalid"
  [[ -L "$RUNTIME_DIR/current" ]] || die "runtime current must be a managed symlink"
  bundle="$(readlink -f -- "$RUNTIME_DIR/current")"
  [[ "$bundle" == "$RUNTIME_DIR"/.bundle.* && -d "$bundle" && ! -L "$bundle" && "$(stat -c '%a' "$bundle")" == 700 ]] || die "runtime bundle is invalid"
  env="$bundle/backend.env"
  [[ -f "$env" && ! -L "$env" && "$(stat -c '%a' "$env")" == 600 ]] || die "backend runtime file is invalid"
  complete="$bundle/.complete"
  [[ -f "$complete" && ! -L "$complete" && "$(stat -c '%a' "$complete")" == 600 ]] || die "runtime completion marker is invalid"
  local canonical_re="^([A-Z][A-Z0-9_]*)='.*'$"
  local -A counts=()
  local keys=(PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_DATABASE PAWCYCLE_DATASOURCE_SSL_MODE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS)
  for key in "${keys[@]}"; do counts[$key]=0; done
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ $canonical_re ]] || die "backend runtime file is malformed"
    key="${BASH_REMATCH[1]}"; [[ -n "${counts[$key]+present}" ]] || die "backend runtime file contains an unknown key"; counts[$key]=$((counts[$key] + 1))
  done <"$env"
  for key in "${keys[@]}"; do [[ "${counts[$key]}" == 1 ]] || die "backend runtime key is missing or duplicated"; done
  host="$(read_setting "$env" PAWCYCLE_DATASOURCE_HOST)"; port="$(read_setting "$env" PAWCYCLE_DATASOURCE_PORT)"
  database="$(read_setting "$env" PAWCYCLE_DATASOURCE_DATABASE)"; ssl="$(read_setting "$env" PAWCYCLE_DATASOURCE_SSL_MODE)"
  url="$(read_setting "$env" SPRING_DATASOURCE_URL)"; user="$(read_setting "$env" SPRING_DATASOURCE_USERNAME)"; password="$(read_setting "$env" SPRING_DATASOURCE_PASSWORD)"
  validate_datasource_host "$host"
  [[ "$port" == 3306 && "$ssl" == REQUIRED && "$database" =~ ^[A-Za-z0-9_]{1,64}$ && -n "$user" && -n "$password" && "$user" != *$'\n'* && "$password" != *$'\n'* ]] || die "backend datasource contract is invalid"
  [[ "$url" == "jdbc:mysql://${host}:3306/${database}?sslMode=REQUIRED&serverTimezone=UTC" ]] || die "backend datasource URL contract is invalid"
  PAWCYCLE_DATASOURCE_HOST="$host"; PAWCYCLE_DATASOURCE_PORT="$port"; PAWCYCLE_DATASOURCE_DATABASE="$database"
  BACKEND_ENV_FILE="$env"
}

validate_credentials() {
  local line key
  local canonical_re="^([A-Z][A-Z0-9_]*)='.*'$"
  [[ -f "$BACKUP_CREDENTIAL_FILE" && ! -L "$BACKUP_CREDENTIAL_FILE" ]] || die "backup credential file is invalid"
  [[ "$(stat -c '%a' "$BACKUP_CREDENTIAL_FILE")" == 600 && "$(stat -c '%u' "$BACKUP_CREDENTIAL_FILE")" == "$(id -u)" ]] || die "backup credential file permissions or owner are invalid"
  local -A count=([MYSQL_BACKUP_USERNAME]=0 [MYSQL_BACKUP_PASSWORD]=0)
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ $canonical_re ]] || die "backup credential file is malformed"
    key="${BASH_REMATCH[1]}"; [[ -n "${count[$key]+present}" ]] || die "backup credential file contains an unknown key"; count[$key]=$((count[$key] + 1))
  done < "$BACKUP_CREDENTIAL_FILE"
  [[ "${count[MYSQL_BACKUP_USERNAME]}" == 1 && "${count[MYSQL_BACKUP_PASSWORD]}" == 1 ]] || die "backup credential file is incomplete"
  MYSQL_BACKUP_USERNAME="$(read_setting "$BACKUP_CREDENTIAL_FILE" MYSQL_BACKUP_USERNAME)"; MYSQL_BACKUP_PASSWORD="$(read_setting "$BACKUP_CREDENTIAL_FILE" MYSQL_BACKUP_PASSWORD)"
  [[ -n "$MYSQL_BACKUP_USERNAME" && -n "$MYSQL_BACKUP_PASSWORD" ]] || die "backup credential values are empty"
}

cleanup() {
  local status=$? cleanup_failed=0; trap - EXIT INT TERM; set +e
  [[ -z "$MYSQL_CONTAINER" ]] || docker rm --force "$MYSQL_CONTAINER" >/dev/null 2>&1 || cleanup_failed=1
  [[ -z "$MYSQL_VOLUME" ]] || docker volume rm "$MYSQL_VOLUME" >/dev/null 2>&1 || cleanup_failed=1
  [[ -z "$TEMP_DIR" || ! -d "$TEMP_DIR" ]] || rm -rf -- "$TEMP_DIR" || cleanup_failed=1
  if (( status == 0 && cleanup_failed == 1 )); then status=1; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run_mysql_query() {
  local sql="$1"
  printf '%s\n' "$sql" | docker run --rm --pull never --interactive --network "$DATABASE_EGRESS_NETWORK" --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --pids-limit 128 --log-driver none \
    --volume "$MYSQL_OPTION_FILE:/run/pawcycle/mysql-client.cnf:ro" --entrypoint mysql "$MYSQL_TOOL_IMAGE" \
    --defaults-extra-file=/run/pawcycle/mysql-client.cnf --database="$PAWCYCLE_DATASOURCE_DATABASE" --batch --skip-column-names --raw 2>/dev/null
}

run_restore_query() {
  local sql="$1"
  docker exec --interactive "$MYSQL_CONTAINER" mysql --defaults-extra-file=/run/secrets/mysql-client.cnf \
    --protocol=TCP --batch --skip-column-names --raw --execute="$sql"
}

lookup_mysql_identity() {
  local mysql_uid mysql_gid
  mysql_uid="$(docker run --rm --pull never --network none --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 --log-driver none \
    --entrypoint id "$MYSQL_TOOL_IMAGE" -u mysql 2>/dev/null)" \
    || die "pinned MySQL image mysql uid lookup failed"
  mysql_gid="$(docker run --rm --pull never --network none --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 --log-driver none \
    --entrypoint id "$MYSQL_TOOL_IMAGE" -g mysql 2>/dev/null)" \
    || die "pinned MySQL image mysql gid lookup failed"
  [[ "$mysql_uid" =~ ^[0-9]+$ && "$mysql_gid" =~ ^[0-9]+$ && "$mysql_uid" != 0 ]] \
    || die "pinned MySQL image mysql identity is invalid"
  printf '%s:%s' "$mysql_uid" "$mysql_gid"
}

upload_object() {
  oci os object put --auth instance_principal --bucket-name "$BUCKET" --name "$2" --file "$1" --no-overwrite --verify-checksum --region "$REGION" >/dev/null
}

backup() {
  local dump="$TEMP_DIR/dump.sql.gz" manifest="$TEMP_DIR/manifest.txt" complete="$TEMP_DIR/complete"
  local created dump_sha dump_bytes schema_sha flyway_sha
  created="$(date -u +%Y%m%dT%H%M%SZ)"
  BACKUP_ID="${created}-$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')"
  validate_backup_id "$BACKUP_ID"
  MYSQL_OPTION_FILE="$TEMP_DIR/mysql-client.cnf"; : > "$MYSQL_OPTION_FILE"; chmod 600 "$MYSQL_OPTION_FILE"
  {
    printf '[client]\n'; printf 'host=%s\n' "$PAWCYCLE_DATASOURCE_HOST"; printf 'port=%s\n' "$PAWCYCLE_DATASOURCE_PORT"
    printf 'user=%s\n' "$MYSQL_BACKUP_USERNAME"; printf 'password=%s\n' "$MYSQL_BACKUP_PASSWORD"; printf 'ssl-mode=REQUIRED\n'
  } > "$MYSQL_OPTION_FILE"
  docker run --rm --pull never --interactive --network "$DATABASE_EGRESS_NETWORK" --read-only --tmpfs /tmp:size=16m,mode=1777 \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 --log-driver none \
    --volume "$MYSQL_OPTION_FILE:/run/pawcycle/mysql-client.cnf:ro" --entrypoint mysqldump "$MYSQL_TOOL_IMAGE" \
    --defaults-extra-file=/run/pawcycle/mysql-client.cnf --database="$PAWCYCLE_DATASOURCE_DATABASE" --single-transaction --quick --no-tablespaces --hex-blob | gzip -c > "$dump"
  dump_sha="$(sha256sum "$dump" | awk '{print $1}')"; dump_bytes="$(stat -c '%s' "$dump")"
  schema_sha="$(run_mysql_query "SELECT GROUP_CONCAT(CONCAT(TABLE_NAME, ':', COLUMN_NAME, ':', COLUMN_TYPE, ':', IS_NULLABLE) ORDER BY TABLE_NAME, ORDINAL_POSITION SEPARATOR '|') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE();" | sha256sum | awk '{print $1}')"
  flyway_sha="$(run_mysql_query "SELECT CONCAT(COUNT(*), ':', COALESCE(MAX(installed_rank), 0), ':', COALESCE(SUM(checksum), 0)) FROM flyway_schema_history WHERE success = 1;" | sha256sum | awk '{print $1}')"
  {
    printf 'BACKUP_ID=%s\n' "$BACKUP_ID"; printf 'DUMP_SHA256=%s\n' "$dump_sha"; printf 'DUMP_BYTES=%s\n' "$dump_bytes"
    printf 'SCHEMA_SHA256=%s\n' "$schema_sha"; printf 'FLYWAY_SHA256=%s\n' "$flyway_sha"; printf 'CREATED_AT_UTC=%s\n' "$created"
  } > "$manifest"
  printf 'BACKUP_ID=%s\n' "$BACKUP_ID" > "$complete"; chmod 600 "$manifest" "$complete"
  upload_object "$dump" "$PREFIX/$BACKUP_ID/dump.sql.gz"
  upload_object "$manifest" "$PREFIX/$BACKUP_ID/manifest.txt"
  upload_object "$complete" "$PREFIX/$BACKUP_ID/complete"
  printf 'OCI logical backup completed: %s\n' "$BACKUP_ID"
}

restore_verify() {
  local complete="$TEMP_DIR/complete" manifest="$TEMP_DIR/manifest.txt" dump="$TEMP_DIR/dump.sql.gz"
  local expected actual bytes restored_schema_sha restored_flyway_sha core_tables ready=0 mysql_uid_gid mysql_uid mysql_gid
  oci os object get --auth instance_principal --bucket-name "$BUCKET" --name "$PREFIX/$BACKUP_ID/complete" --file "$complete" --region "$REGION" >/dev/null
  grep -Fxq "BACKUP_ID=$BACKUP_ID" "$complete" || die "completion marker is invalid"
  oci os object get --auth instance_principal --bucket-name "$BUCKET" --name "$PREFIX/$BACKUP_ID/manifest.txt" --file "$manifest" --region "$REGION" >/dev/null
  oci os object get --auth instance_principal --bucket-name "$BUCKET" --name "$PREFIX/$BACKUP_ID/dump.sql.gz" --file "$dump" --region "$REGION" >/dev/null
  expected="$(grep -E '^DUMP_SHA256=[0-9a-f]{64}$' "$manifest" | cut -d= -f2)"; actual="$(sha256sum "$dump" | awk '{print $1}')"
  [[ -n "$expected" && "$actual" == "$expected" ]] || die "backup dump hash does not match manifest"
  bytes="$(stat -c '%s' "$dump")"; [[ "$bytes" == "$(grep -E '^DUMP_BYTES=[0-9]+$' "$manifest" | cut -d= -f2)" ]] || die "backup dump size does not match manifest"
  MYSQL_VOLUME="pawcycle-restore-verify-${BACKUP_ID##*-}"; MYSQL_CONTAINER="$MYSQL_VOLUME"; MYSQL_ROOT_SECRET_FILE="$TEMP_DIR/mysql-root.secret"; MYSQL_OPTION_FILE="$TEMP_DIR/restore-client.cnf"
  printf '%s' "$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')" > "$MYSQL_ROOT_SECRET_FILE"; chmod 600 "$MYSQL_ROOT_SECRET_FILE"
  : > "$MYSQL_OPTION_FILE"; chmod 600 "$MYSQL_OPTION_FILE"
  { printf '[client]\nuser=root\npassword='; cat "$MYSQL_ROOT_SECRET_FILE"; printf '\nssl-mode=REQUIRED\n'; } > "$MYSQL_OPTION_FILE"
  mysql_uid_gid="$(lookup_mysql_identity)"; mysql_uid="${mysql_uid_gid%%:*}"; mysql_gid="${mysql_uid_gid##*:}"
  docker volume create "$MYSQL_VOLUME" >/dev/null
  docker run --detach --name "$MYSQL_CONTAINER" --user mysql:mysql --network none --mount "source=$MYSQL_VOLUME,target=/var/lib/mysql" \
    --mount "type=bind,src=$MYSQL_ROOT_SECRET_FILE,dst=/run/secrets/mysql-root,ro" --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root \
    --mount "type=bind,src=$MYSQL_OPTION_FILE,dst=/run/secrets/mysql-client.cnf,ro" \
    --read-only --tmpfs /tmp:size=64m,mode=1777 --tmpfs "/var/run/mysqld:size=16m,uid=$mysql_uid,gid=$mysql_gid,mode=755" \
    --security-opt no-new-privileges:true --cap-drop ALL --memory 512m --cpus 0.50 --pids-limit 128 --log-driver none "$MYSQL_TOOL_IMAGE" >/dev/null
  for ((attempt=0; attempt<60; attempt++)); do
    if docker exec "$MYSQL_CONTAINER" mysqladmin --defaults-extra-file=/run/secrets/mysql-client.cnf --protocol=TCP ping --silent >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  (( ready == 1 )) || die "temporary MySQL did not become ready before restore"
  gzip -dc "$dump" | docker exec --interactive "$MYSQL_CONTAINER" mysql --defaults-extra-file=/run/secrets/mysql-client.cnf --protocol=TCP --batch >/dev/null
  restored_schema_sha="$(run_restore_query "SELECT GROUP_CONCAT(CONCAT(TABLE_NAME, ':', COLUMN_NAME, ':', COLUMN_TYPE, ':', IS_NULLABLE) ORDER BY TABLE_NAME, ORDINAL_POSITION SEPARATOR '|') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE();" | sha256sum | awk '{print $1}')"
  restored_flyway_sha="$(run_restore_query "SELECT CONCAT(COUNT(*), ':', COALESCE(MAX(installed_rank), 0), ':', COALESCE(SUM(checksum), 0)) FROM flyway_schema_history WHERE success = 1;" | sha256sum | awk '{print $1}')"
  [[ "$restored_schema_sha" == "$(grep -E '^SCHEMA_SHA256=[0-9a-f]{64}$' "$manifest" | cut -d= -f2)" ]] || die "restored schema fingerprint does not match manifest"
  [[ "$restored_flyway_sha" == "$(grep -E '^FLYWAY_SHA256=[0-9a-f]{64}$' "$manifest" | cut -d= -f2)" ]] || die "restored Flyway fingerprint does not match manifest"
  core_tables="$(run_restore_query "SELECT CONCAT((SELECT COUNT(*) FROM members), ':', (SELECT COUNT(*) FROM products), ':', (SELECT COUNT(*) FROM skus), ':', (SELECT COUNT(*) FROM subscriptions));")"
  [[ "$core_tables" =~ ^[0-9]+:[0-9]+:[0-9]+:[0-9]+$ ]] \
    || die "restored core table verification failed"
  printf 'OCI logical backup restore verification completed: %s\n' "$BACKUP_ID"
}

cleanup_backup() {
  local object
  for object in dump.sql.gz manifest.txt complete; do
    oci os object delete --auth instance_principal --bucket-name "$BUCKET" --name "$PREFIX/$BACKUP_ID/$object" --region "$REGION" >/dev/null
  done
  printf 'OCI logical backup cleanup completed: %s\n' "$BACKUP_ID"
}

[[ $# -gt 0 ]] || { usage; exit 64; }
OPERATION="$1"; shift
while (($#)); do
  case "$1" in
    --runtime-dir) RUNTIME_DIR="${2:-}"; shift 2 ;;
    --backup-credential-file) BACKUP_CREDENTIAL_FILE="${2:-}"; shift 2 ;;
    --bucket) BUCKET="${2:-}"; shift 2 ;;
    --prefix) PREFIX="${2:-}"; shift 2 ;;
    --region) REGION="${2:-}"; shift 2 ;;
    --backup-id) BACKUP_ID="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage; die "unknown argument" ;;
  esac
done
[[ "$OPERATION" == backup || "$OPERATION" == restore-verify || "$OPERATION" == cleanup ]] || die "operation is invalid"
validate_path "$RUNTIME_DIR" runtime-dir; validate_path "$BACKUP_CREDENTIAL_FILE" backup-credential-file
validate_bucket "$BUCKET"; validate_prefix "$PREFIX"; validate_region "$REGION"
[[ "$OPERATION" == backup || -n "$BACKUP_ID" ]] || die "--backup-id is required"
[[ -z "$BACKUP_ID" ]] || validate_backup_id "$BACKUP_ID"
for command in date docker gzip mktemp od oci readlink sha256sum stat tr; do
  require_command "$command"
done
validate_backend_env; validate_credentials; TEMP_DIR="$(mktemp -d)"; chmod 700 "$TEMP_DIR"
case "$OPERATION" in
  backup) backup ;;
  restore-verify) restore_verify ;;
  cleanup) cleanup_backup ;;
esac
