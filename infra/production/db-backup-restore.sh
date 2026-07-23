#!/usr/bin/env bash

set -Eeuo pipefail
set +x

MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PRODUCTION_PROJECT="pawcycle-production"
PRODUCTION_MYSQL_SERVICE="mysql"
PRODUCTION_MYSQL_VOLUME="pawcycle-production-mysql-data"
RESTORE_DATABASE="pawcycle_restore_verify"
WORK_ROOT="/opt/pawcycle/backup-work"
LOCK_FILE="/run/lock/pawcycle/db-backup-restore.lock"
if [[ "${PAWCYCLE_OPS013_TEST_MODE:-}" == "local-validation-only" ]]; then
  WORK_ROOT="${PAWCYCLE_BACKUP_WORK_ROOT:-/tmp/pawcycle-backup-work}"
  LOCK_FILE="${PAWCYCLE_BACKUP_LOCK_FILE:-/tmp/pawcycle-db-backup-restore.lock}"
fi
MIN_AVAILABLE_MEMORY_BYTES=$((256 * 1024 * 1024))
MIN_RESTORE_AVAILABLE_MEMORY_BYTES=$((768 * 1024 * 1024))
MIN_FREE_DISK_BYTES=$((1024 * 1024 * 1024))
MAX_SINGLE_UPLOAD_BYTES=5000000000
MAX_METADATA_OBJECT_BYTES=1048576
CORE_TABLES=(members products skus subscriptions)
export AWS_PAGER=""
export AWS_CLI_AUTO_PROMPT=off

COMMAND=""
S3_BUCKET="${PAWCYCLE_BACKUP_BUCKET:-}"
AWS_REGION="${PAWCYCLE_BACKUP_REGION:-}"
S3_PREFIX="${PAWCYCLE_BACKUP_PREFIX:-}"
BACKUP_ID=""
WORK_DIR=""
AWS_ERROR_FILE=""
MYSQL_ERROR_FILE=""
COMPRESSION_ERROR_FILE=""
TEMP_CONTAINER=""
TEMP_VOLUME=""
CLEANUP_PATHS_FILE=""
SUCCESS_MESSAGE=""

usage() {
  cat <<'EOF'
Usage:
  db-backup-restore.sh backup --bucket <bucket> --region <region> --prefix <prefix>
  db-backup-restore.sh restore-verify --bucket <bucket> --region <region> --prefix <prefix> --backup-id <id>
  db-backup-restore.sh cleanup --backup-id <id>

The bucket, region, and prefix may instead be supplied through
PAWCYCLE_BACKUP_BUCKET, PAWCYCLE_BACKUP_REGION, and PAWCYCLE_BACKUP_PREFIX.
AWS credentials must come from the EC2 instance role; this script never accepts them.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

validate_absolute_directory() {
  [[ "$1" == /* && "$1" != "/" ]] || die "$2 must be an absolute directory other than /"
}

validate_bucket() {
  [[ "$1" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]] \
    || die "bucket must be a valid lowercase general-purpose S3 bucket name"
  [[ "$1" != *..* && "$1" != *.-* && "$1" != *-. ]] \
    || die "bucket contains an invalid adjacent character sequence"
  [[ ! "$1" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] \
    || die "bucket must not use an IPv4-address-shaped name"
}

validate_region() {
  [[ "$1" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]] || die "AWS region format is invalid"
}

validate_prefix() {
  [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*[A-Za-z0-9]$ ]] \
    || die "S3 prefix format is invalid"
  [[ "$1" != /* && "$1" != */ && "$1" != *//* ]] || die "S3 prefix separators are invalid"
  [[ ! "/$1/" =~ /(\.|\.\.)/ ]] || die "S3 prefix must not contain dot path segments"
}

validate_backup_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$ ]] || die "backup ID format is invalid"
}

validate_hash() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]] || die "$2 is not a lowercase SHA-256 value"
}

validate_nonnegative_integer() {
  [[ "$1" =~ ^[0-9]+$ ]] || die "$2 is not a nonnegative integer"
}

parse_args() {
  [[ $# -ge 1 ]] || {
    usage >&2
    exit 2
  }
  COMMAND="$1"
  shift

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --bucket)
        [[ $# -ge 2 ]] || die "--bucket requires a value"
        S3_BUCKET="$2"
        shift 2
        ;;
      --region)
        [[ $# -ge 2 ]] || die "--region requires a value"
        AWS_REGION="$2"
        shift 2
        ;;
      --prefix)
        [[ $# -ge 2 ]] || die "--prefix requires a value"
        S3_PREFIX="$2"
        shift 2
        ;;
      --backup-id)
        [[ $# -ge 2 ]] || die "--backup-id requires a value"
        BACKUP_ID="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        die "unknown argument: $1"
        ;;
    esac
  done

  case "$COMMAND" in
    backup)
      [[ -z "$BACKUP_ID" ]] || die "backup generates its own backup ID"
      validate_s3_inputs
      ;;
    restore-verify)
      validate_s3_inputs
      validate_backup_id "$BACKUP_ID"
      ;;
    cleanup)
      validate_backup_id "$BACKUP_ID"
      ;;
    *)
      die "command must be backup, restore-verify, or cleanup"
      ;;
  esac
}

validate_s3_inputs() {
  [[ -n "$S3_BUCKET" && -n "$AWS_REGION" && -n "$S3_PREFIX" ]] \
    || die "bucket, region, and prefix are required"
  validate_bucket "$S3_BUCKET"
  validate_region "$AWS_REGION"
  validate_prefix "$S3_PREFIX"
}

validate_instance_role_boundary() {
  local name

  for name in \
    AWS_ACCESS_KEY_ID \
    AWS_SECRET_ACCESS_KEY \
    AWS_SESSION_TOKEN \
    AWS_PROFILE \
    AWS_WEB_IDENTITY_TOKEN_FILE \
    AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
    AWS_CONTAINER_CREDENTIALS_FULL_URI \
    AWS_ENDPOINT_URL \
    AWS_ENDPOINT_URL_S3; do
    [[ -z "${!name:-}" ]] || die "AWS credentials and endpoint overrides must not come from the ambient environment"
  done
  export AWS_CONFIG_FILE=/dev/null
  export AWS_SHARED_CREDENTIALS_FILE=/dev/null
  export AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true
}

prepare_host() {
  local parent

  [[ "$EUID" -eq 0 ]] || die "this production command must run as root"

  validate_absolute_directory "$WORK_ROOT" "backup work root"
  validate_absolute_directory "$(dirname -- "$LOCK_FILE")" "lock directory"
  [[ ! -e "$WORK_ROOT" || (! -L "$WORK_ROOT" && -d "$WORK_ROOT") ]] \
    || die "backup work root must be a non-symlink directory"
  install -d -m 700 "$WORK_ROOT"
  [[ ! -L "$WORK_ROOT" && -d "$WORK_ROOT" ]] || die "backup work root must be a non-symlink directory"
  chmod 700 "$WORK_ROOT"
  [[ "$(stat -c '%u' "$WORK_ROOT")" == "0" ]] || die "backup work root must be owned by root"

  parent="$(dirname -- "$LOCK_FILE")"
  [[ ! -e "$parent" || (! -L "$parent" && -d "$parent") ]] \
    || die "lock directory must be a non-symlink directory"
  install -d -m 700 "$parent"
  [[ "$(stat -c '%u' "$parent")" == "0" ]] || die "lock directory must be owned by root"
  [[ ! -e "$LOCK_FILE" && ! -L "$LOCK_FILE" ]] \
    || [[ ! -L "$LOCK_FILE" && -f "$LOCK_FILE" ]] \
    || die "lock file must be a regular non-symlink file"
  exec 9>"$LOCK_FILE"
  chmod 600 "$LOCK_FILE"
  [[ "$(stat -c '%u' "$LOCK_FILE")" == "0" ]] || die "lock file must be owned by root"
  flock --nonblock 9 || die "another OPS-013 backup or restore command is running"
}

create_work_dir() {
  WORK_DIR="$(mktemp -d "$WORK_ROOT/ops013-${BACKUP_ID}-XXXXXXXX")"
  [[ ! -L "$WORK_DIR" && -d "$WORK_DIR" ]] || die "temporary work path is invalid"
  chmod 700 "$WORK_DIR"
  AWS_ERROR_FILE="$WORK_DIR/aws-error"
  MYSQL_ERROR_FILE="$WORK_DIR/mysql-error"
  COMPRESSION_ERROR_FILE="$WORK_DIR/compression-error"
  : > "$AWS_ERROR_FILE"
  : > "$MYSQL_ERROR_FILE"
  : > "$COMPRESSION_ERROR_FILE"
  chmod 600 "$AWS_ERROR_FILE" "$MYSQL_ERROR_FILE" "$COMPRESSION_ERROR_FILE"
}

safe_remove_work_dir() {
  local target="$1"
  local resolved_root
  local resolved_target

  [[ -n "$target" && -d "$target" && ! -L "$target" ]] || return 0
  resolved_root="$(readlink -f -- "$WORK_ROOT")"
  resolved_target="$(readlink -f -- "$target")"
  [[ "$resolved_target" == "$resolved_root"/ops013-* ]] \
    || die "refusing to remove a path outside the OPS-013 work root"
  rm -rf -- "$resolved_target"
}

cleanup_trap() {
  local status=$?
  local cleanup_failed=0

  trap - EXIT INT TERM
  set +e
  if [[ -n "$TEMP_CONTAINER" ]]; then
    if ! docker rm --force "$TEMP_CONTAINER" >/dev/null 2>&1; then
      cleanup_failed=1
    elif docker container inspect "$TEMP_CONTAINER" >/dev/null 2>&1; then
      cleanup_failed=1
    fi
  fi
  if [[ -n "$TEMP_VOLUME" ]]; then
    if ! docker volume rm "$TEMP_VOLUME" >/dev/null 2>&1; then
      cleanup_failed=1
    elif docker volume inspect "$TEMP_VOLUME" >/dev/null 2>&1; then
      cleanup_failed=1
    fi
  fi
  if [[ -n "$WORK_DIR" ]]; then
    safe_remove_work_dir "$WORK_DIR"
    if [[ -e "$WORK_DIR" || -L "$WORK_DIR" ]]; then
      cleanup_failed=1
    fi
  fi
  if [[ -n "$CLEANUP_PATHS_FILE" && "$CLEANUP_PATHS_FILE" == "$WORK_ROOT"/ops013-cleanup-*.paths ]]; then
    rm -f -- "$CLEANUP_PATHS_FILE"
    if [[ -e "$CLEANUP_PATHS_FILE" || -L "$CLEANUP_PATHS_FILE" ]]; then
      cleanup_failed=1
    fi
  fi
  if (( cleanup_failed != 0 )); then
    printf 'ERROR: OPS-013 temporary resource cleanup failed\n' >&2
    status=1
  fi
  if (( status == 0 )) && [[ -n "$SUCCESS_MESSAGE" ]]; then
    printf '%s\n' "$SUCCESS_MESSAGE"
  fi
  exit "$status"
}

random_hex() {
  od -An -N4 -tx1 /dev/urandom | tr -d ' \n'
}

new_backup_id() {
  printf '%s-%s\n' "$(date -u +%Y%m%dT%H%M%SZ)" "$(random_hex)"
}

aws_capture() {
  local output_file="$1"
  shift

  : > "$AWS_ERROR_FILE"
  if ! aws "$@" >"$output_file" 2>"$AWS_ERROR_FILE"; then
    : > "$AWS_ERROR_FILE"
    die "AWS request failed; bucket and object identifiers were suppressed"
  fi
  : > "$AWS_ERROR_FILE"
}

verify_bucket_contract() {
  local value_file="$WORK_DIR/aws-value"
  local bucket_region
  local public_block
  local encryption
  local lifecycle_count
  local versioning

  aws_capture "$value_file" s3api get-bucket-location \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --query 'LocationConstraint' --output text
  bucket_region="$(<"$value_file")"
  [[ "$bucket_region" == "$AWS_REGION" ]] || die "S3 bucket region does not match the requested region"

  aws_capture "$value_file" s3api get-public-access-block \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --query '[PublicAccessBlockConfiguration.BlockPublicAcls,PublicAccessBlockConfiguration.IgnorePublicAcls,PublicAccessBlockConfiguration.BlockPublicPolicy,PublicAccessBlockConfiguration.RestrictPublicBuckets]' \
    --output text
  public_block="$(<"$value_file")"
  [[ "$public_block" == $'True\tTrue\tTrue\tTrue' || "$public_block" == "True True True True" ]] \
    || die "all four S3 bucket Public Access Block settings must be enabled"

  aws_capture "$value_file" s3api get-bucket-encryption \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' \
    --output text
  encryption="$(<"$value_file")"
  [[ "$encryption" == "AES256" ]] || die "S3 bucket default encryption must be SSE-S3 AES256"

  aws_capture "$value_file" s3api get-bucket-versioning \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --query 'Status' --output text
  versioning="$(<"$value_file")"
  [[ "$versioning" == "None" ]] || die "S3 bucket versioning must remain disabled for the 14-day retention contract"

  aws_capture "$value_file" s3api get-bucket-lifecycle-configuration \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --query "length(Rules[?Status=='Enabled' && Expiration.Days==\`14\` && Filter.Prefix=='${S3_PREFIX}/'])" \
    --output text
  lifecycle_count="$(<"$value_file")"
  [[ "$lifecycle_count" =~ ^[1-9][0-9]*$ ]] \
    || die "an enabled 14-day expiration lifecycle rule is required for the requested prefix"
}

find_production_mysql() {
  local ids
  local count
  local container
  local image
  local health
  local volume_name
  local destination

  ids="$(docker ps \
    --filter "label=com.docker.compose.project=$PRODUCTION_PROJECT" \
    --filter "label=com.docker.compose.service=$PRODUCTION_MYSQL_SERVICE" \
    --format '{{.ID}}')"
  count="$(grep -c . <<<"$ids" || true)"
  [[ "$count" == "1" ]] || die "exactly one running production MySQL container is required"
  container="$ids"

  image="$(docker inspect --format '{{.Config.Image}}' "$container")"
  [[ "$image" == "$MYSQL_IMAGE" ]] || die "production MySQL is not using the approved pinned image"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
  [[ "$health" == "healthy" ]] || die "production MySQL must be healthy"

  volume_name="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$container")"
  destination="$(docker inspect --format '{{range .Mounts}}{{if eq .Name "pawcycle-production-mysql-data"}}{{.Destination}}{{end}}{{end}}' "$container")"
  [[ "$volume_name" == "$PRODUCTION_MYSQL_VOLUME" && "$destination" == "/var/lib/mysql" ]] \
    || die "production MySQL data volume contract does not match"
  printf '%s\n' "$container"
}

source_mysql_query() {
  local container="$1"
  local sql="$2"
  local output_file="$3"

  : >"$MYSQL_ERROR_FILE"
  if ! docker exec "$container" sh -eu -c \
    'if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
       export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
     elif [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ] && [ -f "$MYSQL_ROOT_PASSWORD_FILE" ]; then
       export MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"
     else
       exit 1
     fi
     exec mysql --protocol=SOCKET --user=root --batch --skip-column-names --raw "$MYSQL_DATABASE" --execute="$1"' \
    sh "$sql" >"$output_file" 2>"$MYSQL_ERROR_FILE"; then
    : >"$MYSQL_ERROR_FILE"
    die "source MySQL metadata query failed"
  fi
  : >"$MYSQL_ERROR_FILE"
}

restore_mysql_query() {
  local sql="$1"
  local output_file="$2"

  : >"$MYSQL_ERROR_FILE"
  if ! docker exec "$TEMP_CONTAINER" sh -eu -c \
    'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=SOCKET --user=root --batch --skip-column-names --raw "$MYSQL_DATABASE" --execute="$1"' \
    sh "$sql" >"$output_file" 2>"$MYSQL_ERROR_FILE"; then
    : >"$MYSQL_ERROR_FILE"
    die "isolated restore metadata query failed"
  fi
  : >"$MYSQL_ERROR_FILE"
}

schema_query() {
  cat <<'SQL'
SELECT CONCAT_WS('|', 'TABLE', TABLE_NAME, ENGINE, ROW_FORMAT, TABLE_COLLATION, CREATE_OPTIONS)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

SELECT CONCAT_WS('|', 'COLUMN', TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_DEFAULT, '<NULL>'), COLUMN_KEY, EXTRA)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT CONCAT_WS('|', 'INDEX', TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, COLLATION, COALESCE(SUB_PART, ''), INDEX_TYPE)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT CONCAT_WS('|', 'CONSTRAINT', TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE)
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

SELECT CONCAT_WS('|', 'KEY', TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION, COLUMN_NAME, COALESCE(REFERENCED_TABLE_NAME, ''), COALESCE(REFERENCED_COLUMN_NAME, ''))
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;
SQL
}

flyway_query() {
  cat <<'SQL'
SELECT CONCAT_WS('|', installed_rank, COALESCE(version, ''), description, type, script, COALESCE(checksum, ''), success)
FROM flyway_schema_history
ORDER BY installed_rank;
SQL
}

check_source_tools_and_database() {
  local container="$1"
  local value_file="$WORK_DIR/source-check"
  local table_count

  : >"$MYSQL_ERROR_FILE"
  if ! docker exec "$container" sh -eu -c \
    'command -v mysql >/dev/null
     command -v mysqldump >/dev/null
     test -n "$MYSQL_DATABASE"
     if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
       exit 0
     fi
     test -n "${MYSQL_ROOT_PASSWORD_FILE:-}"
     test -f "$MYSQL_ROOT_PASSWORD_FILE"' \
    >/dev/null 2>"$MYSQL_ERROR_FILE"; then
    : >"$MYSQL_ERROR_FILE"
    die "source MySQL tools or runtime database contract is unavailable"
  fi
  : >"$MYSQL_ERROR_FILE"
  source_mysql_query "$container" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('members','products','skus','subscriptions');" \
    "$value_file"
  table_count="$(<"$value_file")"
  [[ "$table_count" == "${#CORE_TABLES[@]}" ]] || die "one or more required core tables are missing"
  source_mysql_query "$container" \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history';" \
    "$value_file"
  [[ "$(<"$value_file")" == "1" ]] || die "Flyway history table is missing"
}

check_capacity() {
  local container="$1"
  local value_file="$WORK_DIR/source-size"
  local database_bytes
  local available_disk
  local required_disk
  local available_memory

  source_mysql_query "$container" \
    "SELECT COALESCE(SUM(DATA_LENGTH + INDEX_LENGTH), 0) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();" \
    "$value_file"
  database_bytes="$(<"$value_file")"
  validate_nonnegative_integer "$database_bytes" "database size"
  available_disk="$(df -PB1 "$WORK_ROOT" | awk 'NR==2 {print $4}')"
  validate_nonnegative_integer "$available_disk" "available disk"
  required_disk=$((database_bytes * 2 + 512 * 1024 * 1024))
  if (( required_disk < MIN_FREE_DISK_BYTES )); then
    required_disk="$MIN_FREE_DISK_BYTES"
  fi
  (( available_disk >= required_disk )) || die "insufficient free disk for a compressed logical backup and verification copy"

  available_memory="$(awk '/^MemAvailable:/ {print $2 * 1024}' /proc/meminfo)"
  validate_nonnegative_integer "$available_memory" "available memory"
  (( available_memory >= MIN_AVAILABLE_MEMORY_BYTES )) || die "available memory is below the 256 MiB safety floor"
  printf 'Production MySQL health, database tools, disk, and memory preflight passed\n'
}

check_restore_capacity() {
  local dump="$WORK_DIR/${BACKUP_ID}.sql.gz"
  local size_file="$WORK_DIR/${BACKUP_ID}.uncompressed-size"
  local docker_root
  local available_disk
  local required_disk
  local uncompressed_size
  local available_memory

  docker_root="$(docker info --format '{{.DockerRootDir}}')"
  [[ "$docker_root" == /* && -d "$docker_root" ]] || die "Docker root directory is unavailable"
  available_disk="$(df -PB1 "$docker_root" | awk 'NR==2 {print $4}')"
  validate_nonnegative_integer "$available_disk" "Docker free disk"

  : >"$COMPRESSION_ERROR_FILE"
  if ! gzip --decompress --stdout "$dump" 2>"$COMPRESSION_ERROR_FILE" | wc -c >"$size_file"; then
    : >"$COMPRESSION_ERROR_FILE"
    die "unable to measure uncompressed logical dump size"
  fi
  : >"$COMPRESSION_ERROR_FILE"
  uncompressed_size="$(tr -d ' ' <"$size_file")"
  validate_nonnegative_integer "$uncompressed_size" "uncompressed logical dump size"
  required_disk=$((uncompressed_size * 2 + 1024 * 1024 * 1024))
  if (( required_disk < 2 * MIN_FREE_DISK_BYTES )); then
    required_disk=$((2 * MIN_FREE_DISK_BYTES))
  fi
  (( available_disk >= required_disk )) || die "insufficient Docker disk for isolated restore verification"

  available_memory="$(awk '/^MemAvailable:/ {print $2 * 1024}' /proc/meminfo)"
  validate_nonnegative_integer "$available_memory" "available memory"
  (( available_memory >= MIN_RESTORE_AVAILABLE_MEMORY_BYTES )) \
    || die "available memory is below the 768 MiB isolated restore safety floor"
  printf 'Isolated restore disk and memory preflight passed\n'
}

write_source_manifest() {
  local container="$1"
  local manifest="$2"
  local schema_file="$WORK_DIR/source-schema"
  local flyway_file="$WORK_DIR/source-flyway"
  local schema_hash
  local flyway_hash
  local flyway_count
  local table
  local count_file="$WORK_DIR/source-count"
  local count

  source_mysql_query "$container" "$(schema_query)" "$schema_file"
  source_mysql_query "$container" "$(flyway_query)" "$flyway_file"
  schema_hash="$(sha256sum "$schema_file" | awk '{print $1}')"
  flyway_hash="$(sha256sum "$flyway_file" | awk '{print $1}')"
  flyway_count="$(wc -l <"$flyway_file" | tr -d ' ')"

  {
    printf 'FORMAT_VERSION=1\n'
    printf 'MYSQL_IMAGE=%s\n' "$MYSQL_IMAGE"
    printf 'SCHEMA_SHA256=%s\n' "$schema_hash"
    printf 'FLYWAY_SHA256=%s\n' "$flyway_hash"
    printf 'FLYWAY_COUNT=%s\n' "$flyway_count"
    for table in "${CORE_TABLES[@]}"; do
      source_mysql_query "$container" "SELECT COUNT(*) FROM \`${table}\`;" "$count_file"
      count="$(<"$count_file")"
      validate_nonnegative_integer "$count" "$table row count"
      printf 'TABLE_%s=%s\n' "$table" "$count"
    done
  } >"$manifest"
  chmod 600 "$manifest"
}

create_dump() {
  local container="$1"
  local dump="$2"

  : >"$MYSQL_ERROR_FILE"
  : >"$COMPRESSION_ERROR_FILE"
  if ! docker exec "$container" sh -eu -c \
    'if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
       export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
     elif [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ] && [ -f "$MYSQL_ROOT_PASSWORD_FILE" ]; then
       export MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"
     else
       exit 1
     fi
     exec mysqldump --protocol=SOCKET --user=root --single-transaction --quick --skip-lock-tables --routines --events --triggers --hex-blob --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
    2>"$MYSQL_ERROR_FILE" | gzip 2>"$COMPRESSION_ERROR_FILE" >"$dump"; then
    rm -f -- "$dump"
    : >"$MYSQL_ERROR_FILE"
    : >"$COMPRESSION_ERROR_FILE"
    die "logical dump failed"
  fi
  : >"$MYSQL_ERROR_FILE"
  : >"$COMPRESSION_ERROR_FILE"
  [[ -s "$dump" ]] || die "compressed logical dump is empty"
  chmod 600 "$dump"
  if ! gzip --test "$dump" 2>"$COMPRESSION_ERROR_FILE"; then
    : >"$COMPRESSION_ERROR_FILE"
    die "compressed logical dump failed the local gzip integrity check"
  fi
  : >"$COMPRESSION_ERROR_FILE"
}

write_checksum() {
  local file="$1"
  local directory
  local basename

  directory="$(dirname -- "$file")"
  basename="$(basename -- "$file")"
  (
    cd "$directory"
    sha256sum "$basename" >"${basename}.sha256"
    chmod 600 "${basename}.sha256"
  )
}

verify_local_checksum() {
  local file="$1"
  local checksum_file="$2"
  local basename
  local expected_hash
  local checksum_name
  local extra
  local actual_hash
  local line_count

  basename="$(basename -- "$file")"
  [[ "$(basename -- "$checksum_file")" == "${basename}.sha256" ]] || die "checksum filename does not match its payload"
  line_count="$(wc -l <"$checksum_file" | tr -d ' ')"
  [[ "$line_count" == "1" ]] || die "checksum file must contain exactly one entry"
  read -r expected_hash checksum_name extra <"$checksum_file" || die "checksum file could not be parsed"
  validate_hash "$expected_hash" "downloaded checksum"
  [[ "$checksum_name" == "$basename" && -z "$extra" ]] || die "checksum target filename is invalid"
  actual_hash="$(sha256sum "$file" | awk '{print $1}')"
  [[ "$actual_hash" == "$expected_hash" ]] || die "downloaded backup checksum does not match"
}

put_object() {
  local key="$1"
  local file="$2"
  local response="$WORK_DIR/aws-response"
  local size

  size="$(stat -c '%s' "$file")"
  (( size <= MAX_SINGLE_UPLOAD_BYTES )) \
    || die "backup object exceeds the approved single-request S3 upload limit"

  aws_capture "$response" s3api put-object \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" \
    --key "$key" --body "$file" \
    --storage-class STANDARD \
    --server-side-encryption AES256
}

head_object_size() {
  local key="$1"
  local maximum_size="$2"
  local response="$WORK_DIR/aws-response"
  local actual_size
  local encryption

  aws_capture "$response" s3api head-object \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" --key "$key" \
    --query '[ContentLength,ServerSideEncryption]' --output text
  read -r actual_size encryption <"$response"
  validate_nonnegative_integer "$actual_size" "S3 object size"
  [[ "$encryption" == "AES256" ]] || die "S3 object encryption metadata does not match"
  (( actual_size <= maximum_size )) || die "S3 object exceeds the approved download size limit"
  printf '%s\n' "$actual_size"
}

head_object() {
  local key="$1"
  local expected_size="$2"
  local actual_size

  actual_size="$(head_object_size "$key" "$MAX_SINGLE_UPLOAD_BYTES")"
  [[ "$actual_size" == "$expected_size" ]] || die "uploaded S3 object size does not match"
}

get_object() {
  local key="$1"
  local target="$2"
  local response="$WORK_DIR/aws-response"

  aws_capture "$response" s3api get-object \
    --bucket "$S3_BUCKET" --region "$AWS_REGION" --key "$key" "$target"
  chmod 600 "$target"
}

upload_and_verify() {
  local key="$1"
  local file="$2"
  local verification_file="$WORK_DIR/verify-$(basename -- "$file")"
  local size
  local expected_hash
  local actual_hash

  size="$(stat -c '%s' "$file")"
  put_object "$key" "$file"
  head_object "$key" "$size"
  get_object "$key" "$verification_file"
  actual_hash="$(sha256sum "$verification_file" | awk '{print $1}')"
  expected_hash="$(sha256sum "$file" | awk '{print $1}')"
  [[ "$actual_hash" == "$expected_hash" ]] || die "uploaded S3 object checksum verification failed"
  rm -f -- "$verification_file"
}

backup_command() {
  local container
  local dump
  local manifest
  local complete
  local base_key
  local dump_hash
  local manifest_hash

  BACKUP_ID="$(new_backup_id)"
  validate_backup_id "$BACKUP_ID"
  create_work_dir
  printf 'Backup attempt ID: %s\n' "$BACKUP_ID"
  verify_bucket_contract
  container="$(find_production_mysql)"
  check_source_tools_and_database "$container"
  check_capacity "$container"

  dump="$WORK_DIR/${BACKUP_ID}.sql.gz"
  manifest="$WORK_DIR/${BACKUP_ID}.verify"
  complete="$WORK_DIR/${BACKUP_ID}.complete"
  base_key="${S3_PREFIX}/${BACKUP_ID}"

  create_dump "$container" "$dump"
  write_source_manifest "$container" "$manifest"
  write_checksum "$dump"
  write_checksum "$manifest"
  dump_hash="$(sha256sum "$dump" | awk '{print $1}')"
  manifest_hash="$(sha256sum "$manifest" | awk '{print $1}')"
  {
    printf 'FORMAT_VERSION=1\n'
    printf 'BACKUP_ID=%s\n' "$BACKUP_ID"
    printf 'DUMP_SHA256=%s\n' "$dump_hash"
    printf 'MANIFEST_SHA256=%s\n' "$manifest_hash"
  } >"$complete"
  chmod 600 "$complete"

  upload_and_verify "${base_key}.sql.gz" "$dump"
  upload_and_verify "${base_key}.sql.gz.sha256" "${dump}.sha256"
  upload_and_verify "${base_key}.verify" "$manifest"
  upload_and_verify "${base_key}.verify.sha256" "${manifest}.sha256"
  [[ "$(find_production_mysql)" == "$container" ]] \
    || die "production MySQL changed during backup verification"
  upload_and_verify "${base_key}.complete" "$complete"
  SUCCESS_MESSAGE="Backup completed: $BACKUP_ID"
}

manifest_value() {
  local manifest="$1"
  local key="$2"
  local value

  value="$(grep -E "^${key}=" "$manifest" | cut -d= -f2-)"
  [[ -n "$value" ]] || die "verification manifest field is missing: $key"
  printf '%s\n' "$value"
}

verify_completion_marker() {
  local complete="$1"
  local dump="$2"
  local manifest="$3"
  local format
  local marker_id
  local dump_hash
  local manifest_hash

  [[ ! -L "$complete" && -f "$complete" ]] || die "completion marker must be a regular file"
  format="$(manifest_value "$complete" FORMAT_VERSION)"
  marker_id="$(manifest_value "$complete" BACKUP_ID)"
  dump_hash="$(manifest_value "$complete" DUMP_SHA256)"
  manifest_hash="$(manifest_value "$complete" MANIFEST_SHA256)"
  [[ "$format" == "1" && "$marker_id" == "$BACKUP_ID" ]] || die "completion marker identity is invalid"
  validate_hash "$dump_hash" "dump completion checksum"
  validate_hash "$manifest_hash" "manifest completion checksum"
  [[ "$(sha256sum "$dump" | awk '{print $1}')" == "$dump_hash" ]] || die "dump does not match the completion marker"
  [[ "$(sha256sum "$manifest" | awk '{print $1}')" == "$manifest_hash" ]] || die "manifest does not match the completion marker"
}

download_backup_set() {
  local base_key="${S3_PREFIX}/${BACKUP_ID}"
  local dump="$WORK_DIR/${BACKUP_ID}.sql.gz"
  local dump_checksum="${dump}.sha256"
  local manifest="$WORK_DIR/${BACKUP_ID}.verify"
  local manifest_checksum="${manifest}.sha256"
  local complete="$WORK_DIR/${BACKUP_ID}.complete"
  local dump_size
  local dump_checksum_size
  local manifest_size
  local manifest_checksum_size
  local complete_size
  local total_size
  local available_disk

  complete_size="$(head_object_size "${base_key}.complete" "$MAX_METADATA_OBJECT_BYTES")"
  dump_size="$(head_object_size "${base_key}.sql.gz" "$MAX_SINGLE_UPLOAD_BYTES")"
  dump_checksum_size="$(head_object_size "${base_key}.sql.gz.sha256" "$MAX_METADATA_OBJECT_BYTES")"
  manifest_size="$(head_object_size "${base_key}.verify" "$MAX_METADATA_OBJECT_BYTES")"
  manifest_checksum_size="$(head_object_size "${base_key}.verify.sha256" "$MAX_METADATA_OBJECT_BYTES")"
  total_size=$((complete_size + dump_size + dump_checksum_size + manifest_size + manifest_checksum_size))
  available_disk="$(df -PB1 "$WORK_ROOT" | awk 'NR==2 {print $4}')"
  validate_nonnegative_integer "$available_disk" "backup download free disk"
  (( available_disk >= total_size + MIN_FREE_DISK_BYTES )) \
    || die "insufficient free disk to download the verified backup object set"

  get_object "${base_key}.complete" "$complete"
  [[ "$(stat -c '%s' "$complete")" == "$complete_size" ]] || die "downloaded completion marker size does not match"
  get_object "${base_key}.sql.gz" "$dump"
  [[ "$(stat -c '%s' "$dump")" == "$dump_size" ]] || die "downloaded dump size does not match"
  get_object "${base_key}.sql.gz.sha256" "$dump_checksum"
  [[ "$(stat -c '%s' "$dump_checksum")" == "$dump_checksum_size" ]] || die "downloaded dump checksum size does not match"
  get_object "${base_key}.verify" "$manifest"
  [[ "$(stat -c '%s' "$manifest")" == "$manifest_size" ]] || die "downloaded manifest size does not match"
  get_object "${base_key}.verify.sha256" "$manifest_checksum"
  [[ "$(stat -c '%s' "$manifest_checksum")" == "$manifest_checksum_size" ]] || die "downloaded manifest checksum size does not match"

  verify_local_checksum "$dump" "$dump_checksum"
  verify_local_checksum "$manifest" "$manifest_checksum"
  verify_completion_marker "$complete" "$dump" "$manifest"
  if ! gzip --test "$dump" 2>"$COMPRESSION_ERROR_FILE"; then
    : >"$COMPRESSION_ERROR_FILE"
    die "downloaded logical dump failed gzip integrity validation"
  fi
  : >"$COMPRESSION_ERROR_FILE"
}

wait_restore_mysql() {
  local elapsed=0
  local interval=2
  local consecutive_successes=0
  local container_status

  while (( elapsed < 180 )); do
    container_status="$(docker inspect --format '{{.State.Status}}' "$TEMP_CONTAINER" 2>/dev/null || true)"
    case "$container_status" in
      exited|dead|"")
        return 1
        ;;
    esac

    if docker exec "$TEMP_CONTAINER" sh -eu -c \
      'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=TCP --host=127.0.0.1 --user=root --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT 1;"' \
      >/dev/null 2>&1; then
      consecutive_successes=$((consecutive_successes + 1))
      if (( consecutive_successes >= 2 )); then
        return 0
      fi
    else
      consecutive_successes=0
    fi

    container_status="$(docker inspect --format '{{.State.Status}}' "$TEMP_CONTAINER" 2>/dev/null || true)"
    if [[ "$container_status" == "exited" || "$container_status" == "dead" || -z "$container_status" ]]; then
      return 1
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done
  return 1
}

assert_restore_isolation() {
  local network_mode
  local port_bindings
  local production_mount
  local restore_mount

  network_mode="$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$TEMP_CONTAINER")"
  port_bindings="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$TEMP_CONTAINER")"
  production_mount="$(docker inspect --format '{{range .Mounts}}{{if eq .Name "pawcycle-production-mysql-data"}}found{{end}}{{end}}' "$TEMP_CONTAINER")"
  restore_mount="$(docker inspect --format "{{range .Mounts}}{{if eq .Name \"$TEMP_VOLUME\"}}{{.Destination}}{{end}}{{end}}" "$TEMP_CONTAINER")"
  [[ "$network_mode" == "none" ]] || die "restore container must use the none network"
  [[ "$port_bindings" == "{}" || "$port_bindings" == "null" ]] || die "restore container must not publish host ports"
  [[ -z "$production_mount" ]] || die "restore container must not mount the production MySQL volume"
  [[ "$restore_mount" == "/var/lib/mysql" ]] || die "restore container does not use its isolated temporary volume"
}

create_restore_mysql() {
  local secret_file="$WORK_DIR/mysql-root-password"
  local suffix

  suffix="$(random_hex)"
  TEMP_CONTAINER="pawcycle-restore-verify-${BACKUP_ID}-${suffix}"
  TEMP_VOLUME="pawcycle-restore-verify-${BACKUP_ID}-${suffix}"
  if docker container inspect "$TEMP_CONTAINER" >/dev/null 2>&1; then
    die "temporary restore container already exists"
  fi
  if docker volume inspect "$TEMP_VOLUME" >/dev/null 2>&1; then
    die "temporary restore volume already exists"
  fi

  head -c 48 /dev/urandom | base64 >"$secret_file"
  chmod 600 "$secret_file"
  docker volume create \
    --label com.pawcycle.ops013.scope=restore \
    --label "com.pawcycle.ops013.backup-id=$BACKUP_ID" \
    "$TEMP_VOLUME" >/dev/null
  docker create \
    --name "$TEMP_CONTAINER" \
    --label com.pawcycle.ops013.scope=restore \
    --label "com.pawcycle.ops013.backup-id=$BACKUP_ID" \
    --network none \
    --mount "type=volume,source=$TEMP_VOLUME,destination=/var/lib/mysql" \
    --mount "type=bind,source=$secret_file,destination=/run/secrets/mysql-root-password,readonly" \
    --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root-password \
    --env "MYSQL_DATABASE=$RESTORE_DATABASE" \
    --memory 640m \
    --cpus 0.70 \
    --pids-limit 256 \
    --log-driver none \
    "$MYSQL_IMAGE" \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_0900_ai_ci >/dev/null
  docker start "$TEMP_CONTAINER" >/dev/null
  wait_restore_mysql || die "isolated restore MySQL did not become ready"
  assert_restore_isolation
}

import_dump() {
  local dump="$WORK_DIR/${BACKUP_ID}.sql.gz"
  local pipeline_status
  local gzip_status
  local mysql_status

  : >"$MYSQL_ERROR_FILE"
  : >"$COMPRESSION_ERROR_FILE"
  set +e
  gzip --decompress --stdout "$dump" 2>"$COMPRESSION_ERROR_FILE" \
    | docker exec --interactive "$TEMP_CONTAINER" sh -eu -c \
      'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"; exec mysql --protocol=SOCKET --user=root "$MYSQL_DATABASE"' \
      > /dev/null 2>"$MYSQL_ERROR_FILE"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  gzip_status="${pipeline_status[0]}"
  mysql_status="${pipeline_status[1]}"
  : >"$MYSQL_ERROR_FILE"
  : >"$COMPRESSION_ERROR_FILE"

  if (( gzip_status != 0 && gzip_status != 141 )); then
    die "restore-decompression-failed"
  fi
  if (( mysql_status != 0 )); then
    die "restore-sql-import-failed"
  fi
  if (( gzip_status != 0 )); then
    die "restore-decompression-failed"
  fi
}

verify_restored_database() {
  local manifest="$WORK_DIR/${BACKUP_ID}.verify"
  local schema_file="$WORK_DIR/restore-schema"
  local flyway_file="$WORK_DIR/restore-flyway"
  local count_file="$WORK_DIR/restore-count"
  local expected
  local actual
  local table

  [[ "$(manifest_value "$manifest" FORMAT_VERSION)" == "1" ]] || die "verification manifest version is unsupported"
  [[ "$(manifest_value "$manifest" MYSQL_IMAGE)" == "$MYSQL_IMAGE" ]] || die "verification manifest MySQL image does not match"

  restore_mysql_query "$(schema_query)" "$schema_file"
  actual="$(sha256sum "$schema_file" | awk '{print $1}')"
  expected="$(manifest_value "$manifest" SCHEMA_SHA256)"
  validate_hash "$expected" "expected schema checksum"
  [[ "$actual" == "$expected" ]] || die "restored schema does not match the backup-time source schema"

  restore_mysql_query "$(flyway_query)" "$flyway_file"
  actual="$(sha256sum "$flyway_file" | awk '{print $1}')"
  expected="$(manifest_value "$manifest" FLYWAY_SHA256)"
  validate_hash "$expected" "expected Flyway checksum"
  [[ "$actual" == "$expected" ]] || die "restored Flyway history does not match the backup-time source"
  actual="$(wc -l <"$flyway_file" | tr -d ' ')"
  expected="$(manifest_value "$manifest" FLYWAY_COUNT)"
  validate_nonnegative_integer "$expected" "expected Flyway history count"
  [[ "$actual" == "$expected" ]] || die "restored Flyway history count does not match"

  for table in "${CORE_TABLES[@]}"; do
    restore_mysql_query "SELECT COUNT(*) FROM \`${table}\`;" "$count_file"
    actual="$(<"$count_file")"
    expected="$(manifest_value "$manifest" "TABLE_${table}")"
    validate_nonnegative_integer "$expected" "expected $table row count"
    [[ "$actual" == "$expected" ]] || die "restored core table count does not match the backup-time source"
  done
  printf 'Schema, Flyway history, and core table counts match the backup-time source\n'
}

restore_verify_command() {
  local production_container

  docker volume inspect "$PRODUCTION_MYSQL_VOLUME" >/dev/null \
    || die "production MySQL volume is missing; stop before isolated verification"
  production_container="$(find_production_mysql)"
  create_work_dir
  verify_bucket_contract
  download_backup_set
  check_restore_capacity
  create_restore_mysql
  import_dump
  verify_restored_database
  assert_restore_isolation
  [[ "$(find_production_mysql)" == "$production_container" ]] \
    || die "production MySQL changed during isolated restore verification"
  SUCCESS_MESSAGE="Isolated restore verification completed: $BACKUP_ID"
}

cleanup_labeled_resources() {
  local containers
  local volumes
  local container
  local volume
  local scope
  local network_mode
  local production_mount

  containers="$(docker ps --all --quiet \
    --filter label=com.pawcycle.ops013.scope=restore \
    --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID")" \
    || die "unable to enumerate isolated restore containers"
  while IFS= read -r container; do
    [[ -n "$container" ]] || continue
    scope="$(docker inspect --format '{{index .Config.Labels "com.pawcycle.ops013.scope"}}' "$container")"
    network_mode="$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$container")"
    production_mount="$(docker inspect --format '{{range .Mounts}}{{if eq .Name "pawcycle-production-mysql-data"}}found{{end}}{{end}}' "$container")"
    [[ "$scope" == "restore" && "$network_mode" == "none" && -z "$production_mount" ]] \
      || die "refusing to remove a container outside the isolated OPS-013 contract"
    docker rm --force "$container" >/dev/null
  done <<<"$containers"

  volumes="$(docker volume ls --quiet \
    --filter label=com.pawcycle.ops013.scope=restore \
    --filter "label=com.pawcycle.ops013.backup-id=$BACKUP_ID")" \
    || die "unable to enumerate isolated restore volumes"
  while IFS= read -r volume; do
    [[ -n "$volume" ]] || continue
    [[ "$volume" == pawcycle-restore-verify-"$BACKUP_ID"-* ]] \
      || die "refusing to remove a volume outside the isolated OPS-013 naming contract"
    docker volume rm "$volume" >/dev/null
  done <<<"$volumes"
}

cleanup_work_dirs() {
  local path

  CLEANUP_PATHS_FILE="$WORK_ROOT/ops013-cleanup-${BACKUP_ID}.paths"
  [[ ! -e "$CLEANUP_PATHS_FILE" && ! -L "$CLEANUP_PATHS_FILE" ]] || die "cleanup path list already exists"
  find "$WORK_ROOT" -mindepth 1 -maxdepth 1 -type d \
    -name "ops013-${BACKUP_ID}-*" -print0 >"$CLEANUP_PATHS_FILE"
  chmod 600 "$CLEANUP_PATHS_FILE"
  while IFS= read -r -d '' path; do
    safe_remove_work_dir "$path"
  done <"$CLEANUP_PATHS_FILE"
  rm -f -- "$CLEANUP_PATHS_FILE"
  CLEANUP_PATHS_FILE=""
}

cleanup_command() {
  cleanup_labeled_resources
  cleanup_work_dirs
  SUCCESS_MESSAGE="OPS-013 temporary resources are absent for backup ID: $BACKUP_ID"
}

main() {
  umask 077
  parse_args "$@"
  for command in awk aws base64 basename chmod cut date df dirname docker find flock grep gzip head install mktemp od readlink rm sha256sum sleep stat tr wc; do
    require_command "$command"
  done
  prepare_host
  trap cleanup_trap EXIT INT TERM

  case "$COMMAND" in
    backup)
      validate_instance_role_boundary
      backup_command
      ;;
    restore-verify)
      validate_instance_role_boundary
      restore_verify_command
      ;;
    cleanup) cleanup_command ;;
  esac
}

main "$@"
