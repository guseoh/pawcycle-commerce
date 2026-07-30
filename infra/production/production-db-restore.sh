#!/usr/bin/env bash

set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

COMMAND=""
CANDIDATE_VOLUME=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"
TARGET_SHA=""
ACTIVE_SHA=""

usage() {
  cat <<'EOF'
Usage:
  production-db-restore.sh initialize-volume-state [--state-dir <path>]
  production-db-restore.sh cutover --candidate-volume <name> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]
  production-db-restore.sh revert --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --runtime-dir <path>  Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>    Production state directory (default: /opt/pawcycle/state)

This command never deletes a source or candidate MySQL volume. It does not
modify Flyway history, downgrade schema, copy a raw data directory, or retry
a failed database transition automatically.
EOF
}

parse_args() {
  [[ $# -ge 1 ]] || {
    usage >&2
    exit 2
  }
  COMMAND="$1"
  shift

  while (( $# > 0 )); do
    case "$1" in
      --candidate-volume) CANDIDATE_VOLUME="${2:-}"; shift 2 ;;
      --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
      --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
      --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
      --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
      --help|-h) usage; exit 0 ;;
      *) usage >&2; die "unknown argument: $1" ;;
    esac
  done

  case "$COMMAND" in
    initialize-volume-state)
      [[ -z "$CANDIDATE_VOLUME" && -z "$BACKEND_IMAGE" && -z "$FRONTEND_IMAGE" ]] \
        || die "initialize-volume-state accepts only --state-dir"
      ;;
    cutover)
      validate_mysql_volume "$CANDIDATE_VOLUME"
      [[ "$CANDIDATE_VOLUME" != "$DEFAULT_MYSQL_VOLUME" ]] \
        || die "candidate volume must differ from the default production volume"
      [[ -n "$BACKEND_IMAGE" && -n "$FRONTEND_IMAGE" ]] || die "application image repositories are required"
      ;;
    revert)
      [[ -z "$CANDIDATE_VOLUME" ]] || die "revert reads the candidate from protected state"
      [[ -n "$BACKEND_IMAGE" && -n "$FRONTEND_IMAGE" ]] || die "application image repositories are required"
      ;;
    *) die "command must be initialize-volume-state, cutover, or revert" ;;
  esac
}

record_value() {
  local record="$1"
  local key="$2"
  local value

  value="$(grep -E "^${key}=" "$record" | cut -d= -f2-)"
  [[ -n "$value" ]] || die "database restore state field is missing: $key"
  printf '%s\n' "$value"
}

validate_record_file() {
  local record="$1"

  [[ -e "$record" || -L "$record" ]] || die "required database restore state record is missing"
  [[ ! -L "$record" && -f "$record" ]] || die "database restore state must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$record")" == "600" ]] || die "database restore state mode must be 600"
}

read_state_volume() {
  local name="$1"
  local path="$PAWCYCLE_STATE_DIR/$name"
  local value

  validate_record_file "$path"
  value="$(<"$path")"
  validate_mysql_volume "$value"
  printf '%s\n' "$value"
}

validate_candidate_record() {
  local expected_source="${1:-$ACTIVE_MYSQL_VOLUME}"
  local allow_attached="${2:-false}"
  local record="$PAWCYCLE_STATE_DIR/db-restore-candidate"
  local source_volume
  local backup_hash
  local manifest_hash
  local actual
  local key

  validate_record_file "$record"
  [[ "$(record_value "$record" FORMAT_VERSION)" == "1" \
    && "$(record_value "$record" RECORD_KIND)" == "candidate" ]] \
    || die "candidate restore state record is invalid"
  [[ "$(record_value "$record" MYSQL_IMAGE)" == "$MYSQL_IMAGE" ]] \
    || die "candidate MySQL image does not match the pinned production image"
  [[ "$(record_value "$record" CANDIDATE_VOLUME)" == "$CANDIDATE_VOLUME" ]] \
    || die "candidate volume does not match the protected restore state"
  source_volume="$(record_value "$record" SOURCE_VOLUME)"
  validate_mysql_volume "$source_volume"
  [[ "$source_volume" == "$expected_source" && "$source_volume" != "$CANDIDATE_VOLUME" ]] \
    || die "candidate source volume does not match the active production state"
  backup_hash="$(record_value "$record" BACKUP_ID_SHA256)"
  manifest_hash="$(record_value "$record" MANIFEST_SHA256)"
  [[ "$backup_hash" =~ ^[0-9a-f]{64}$ && "$manifest_hash" =~ ^[0-9a-f]{64}$ ]] \
    || die "candidate restore hashes are invalid"
  for key in SCHEMA_SHA256 FLYWAY_SHA256; do
    [[ "$(record_value "$record" "$key")" =~ ^[0-9a-f]{64}$ ]] \
      || die "candidate database fingerprint is invalid"
  done
  for key in FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions; do
    [[ "$(record_value "$record" "$key")" =~ ^[0-9]+$ ]] \
      || die "candidate database manifest count is invalid"
  done

  docker volume inspect "$source_volume" >/dev/null || die "source production MySQL volume is missing"
  docker volume inspect "$CANDIDATE_VOLUME" >/dev/null || die "candidate MySQL volume is missing"
  actual="$(docker volume inspect --format '{{index .Labels "com.pawcycle.ops025.scope"}}' "$CANDIDATE_VOLUME")"
  [[ "$actual" == "candidate" ]] || die "candidate volume ownership label is invalid"
  actual="$(docker volume inspect --format '{{index .Labels "com.pawcycle.ops025.source-volume"}}' "$CANDIDATE_VOLUME")"
  [[ "$actual" == "$source_volume" ]] || die "candidate source-volume label is invalid"
  actual="$(docker volume inspect --format '{{index .Labels "com.pawcycle.ops025.backup-sha256"}}' "$CANDIDATE_VOLUME")"
  [[ "$actual" == "$backup_hash" ]] || die "candidate backup label is invalid"
  actual="$(docker volume inspect --format '{{index .Labels "com.pawcycle.ops025.manifest-sha256"}}' "$CANDIDATE_VOLUME")"
  [[ "$actual" == "$manifest_hash" ]] || die "candidate manifest label is invalid"
  if [[ "$allow_attached" != "true" ]]; then
    [[ -z "$(docker ps --all --quiet --filter "volume=$CANDIDATE_VOLUME")" ]] \
      || die "candidate volume is attached before the approved cutover"
  fi
}

schema_query() {
  cat <<'SQL'
SELECT CONCAT_WS('|', 'TABLE', TABLE_NAME, ENGINE, ROW_FORMAT, TABLE_COLLATION, CREATE_OPTIONS)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME;
SELECT CONCAT_WS('|', 'COLUMN', TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_DEFAULT, '<NULL>'), COLUMN_KEY, EXTRA)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT CONCAT_WS('|', 'INDEX', TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, COLLATION, COALESCE(SUB_PART, ''), INDEX_TYPE)
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
SELECT CONCAT_WS('|', 'CONSTRAINT', TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE)
FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, CONSTRAINT_NAME;
SELECT CONCAT_WS('|', 'KEY', TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION, COLUMN_NAME, COALESCE(REFERENCED_TABLE_NAME, ''), COALESCE(REFERENCED_COLUMN_NAME, ''))
FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;
SQL
}

flyway_query() {
  cat <<'SQL'
SELECT CONCAT_WS('|', installed_rank, COALESCE(version, ''), description, type, script, COALESCE(checksum, ''), success)
FROM flyway_schema_history
ORDER BY installed_rank;
SQL
}

mysql_query() {
  local container="$1"
  local sql="$2"
  local output="$3"
  local error_file="$4"

  : >"$error_file"
  if ! docker exec "$container" sh -eu -c \
    'if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
       export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
     elif [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ] && [ -f "$MYSQL_ROOT_PASSWORD_FILE" ]; then
       export MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"
     else
       exit 1
     fi
     exec mysql --protocol=SOCKET --user=root --batch --skip-column-names --raw "$MYSQL_DATABASE" --execute="$1"' \
    sh "$sql" >"$output" 2>"$error_file"; then
    : >"$error_file"
    return 1
  fi
  : >"$error_file"
}

remove_database_work() {
  local target="$1"
  local resolved_state
  local resolved_target

  [[ -n "$target" && ! -L "$target" && -d "$target" ]] || return 0
  resolved_state="$(readlink -f -- "$PAWCYCLE_STATE_DIR")"
  resolved_target="$(readlink -f -- "$target")"
  [[ "$resolved_target" == "$resolved_state"/.db-restore-* ]] \
    || die "refusing to remove a path outside the database restore state work prefix"
  rm -rf -- "$resolved_target"
}

write_database_record() {
  local target="$1"
  local volume="$2"
  local application_sha="$3"
  local record_kind="${4:-source}"
  local replace="${5:-false}"
  local work
  local container
  local schema_file
  local flyway_file
  local error_file
  local candidate
  local table
  local count
  local volume_key

  case "$record_kind" in
    source) volume_key="SOURCE_VOLUME" ;;
    candidate-current) volume_key="CANDIDATE_VOLUME" ;;
    *) return 1 ;;
  esac
  if [[ -e "$target" || -L "$target" ]]; then
    [[ "$replace" == "true" ]] || return 1
    [[ ! -L "$target" && -f "$target" && "$(stat -c '%a' "$target")" == "600" ]] \
      || return 1
  fi
  work="$(mktemp -d "$PAWCYCLE_STATE_DIR/.db-restore-source.XXXXXXXX")" || return 1
  chmod 700 "$work" || {
    remove_database_work "$work"
    return 1
  }
  schema_file="$work/schema"
  flyway_file="$work/flyway"
  count_file="$work/count"
  error_file="$work/mysql-error"
  candidate="$work/record"
  container="$(compose ps --quiet mysql)"
  [[ -n "$container" ]] || {
    remove_database_work "$work"
    return 1
  }
  mysql_query "$container" "$(schema_query)" "$schema_file" "$error_file" || {
    remove_database_work "$work"
    return 1
  }
  mysql_query "$container" "$(flyway_query)" "$flyway_file" "$error_file" || {
    remove_database_work "$work"
    return 1
  }
  for table in members products skus subscriptions; do
    mysql_query "$container" "SELECT COUNT(*) FROM \`${table}\`;" \
      "$work/count-$table" "$error_file" || {
      remove_database_work "$work"
      return 1
    }
    count="$(<"$work/count-$table")"
    [[ "$count" =~ ^[0-9]+$ ]] || {
      remove_database_work "$work"
      return 1
    }
  done
  {
    printf 'FORMAT_VERSION=1\n'
    printf 'RECORD_KIND=%s\n' "$record_kind"
    printf '%s=%s\n' "$volume_key" "$volume"
    printf 'APPLICATION_SHA=%s\n' "$application_sha"
    printf 'MYSQL_IMAGE=%s\n' "$MYSQL_IMAGE"
    printf 'SCHEMA_SHA256=%s\n' "$(sha256sum "$schema_file" | awk '{print $1}')"
    printf 'FLYWAY_SHA256=%s\n' "$(sha256sum "$flyway_file" | awk '{print $1}')"
    printf 'FLYWAY_COUNT=%s\n' "$(wc -l <"$flyway_file" | tr -d ' ')"
    for table in members products skus subscriptions; do
      printf 'TABLE_%s=%s\n' "$table" "$(<"$work/count-$table")"
    done
  } >"$candidate" || {
    remove_database_work "$work"
    return 1
  }
  chmod 600 "$candidate" || {
    remove_database_work "$work"
    return 1
  }
  if ! mv -f -- "$candidate" "$target"; then
    remove_database_work "$work"
    return 1
  fi
  remove_database_work "$work"
}

validate_current_candidate_record() {
  local record="$1"
  local volume="$2"
  local application_sha="$3"
  local key

  validate_record_file "$record"
  [[ "$(record_value "$record" FORMAT_VERSION)" == "1" \
    && "$(record_value "$record" RECORD_KIND)" == "candidate-current" \
    && "$(record_value "$record" CANDIDATE_VOLUME)" == "$volume" \
    && "$(record_value "$record" APPLICATION_SHA)" == "$application_sha" \
    && "$(record_value "$record" MYSQL_IMAGE)" == "$MYSQL_IMAGE" ]] \
    || die "current candidate recovery record is invalid"
  for key in SCHEMA_SHA256 FLYWAY_SHA256; do
    [[ "$(record_value "$record" "$key")" =~ ^[0-9a-f]{64}$ ]] \
      || die "current candidate database fingerprint is invalid"
  done
  for key in FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions; do
    [[ "$(record_value "$record" "$key")" =~ ^[0-9]+$ ]] \
      || die "current candidate database manifest count is invalid"
  done
}

verify_active_database() {
  local record="$1"
  local work
  local container
  local actual
  local expected
  local table

  validate_record_file "$record"
  work="$(mktemp -d "$PAWCYCLE_STATE_DIR/.db-restore-verify.XXXXXXXX")"
  chmod 700 "$work"
  container="$(compose ps --quiet mysql)"
  [[ -n "$container" ]] || {
    remove_database_work "$work"
    return 1
  }
  mysql_query "$container" "$(schema_query)" "$work/schema" "$work/mysql-error" || {
    remove_database_work "$work"
    return 1
  }
  actual="$(sha256sum "$work/schema" | awk '{print $1}')"
  expected="$(record_value "$record" SCHEMA_SHA256)"
  [[ "$actual" == "$expected" ]] || {
    remove_database_work "$work"
    return 1
  }
  mysql_query "$container" "$(flyway_query)" "$work/flyway" "$work/mysql-error" || {
    remove_database_work "$work"
    return 1
  }
  actual="$(sha256sum "$work/flyway" | awk '{print $1}')"
  expected="$(record_value "$record" FLYWAY_SHA256)"
  [[ "$actual" == "$expected" && "$(wc -l <"$work/flyway" | tr -d ' ')" == "$(record_value "$record" FLYWAY_COUNT)" ]] || {
    remove_database_work "$work"
    return 1
  }
  for table in members products skus subscriptions; do
    mysql_query "$container" "SELECT COUNT(*) FROM \`${table}\`;" "$work/count" "$work/mysql-error" || {
      remove_database_work "$work"
      return 1
    }
    [[ "$(<"$work/count")" == "$(record_value "$record" "TABLE_${table}")" ]] || {
      remove_database_work "$work"
      return 1
    }
  done
  remove_database_work "$work"
  printf 'Active database schema, Flyway history, and core table manifest match protected state\n'
}

initialize_volume_state() {
  local ids
  local container
  local volume
  local destination

  [[ "$EUID" -eq 0 ]] || die "this production command must run as root"
  require_command docker
  require_command flock
  require_command install
  require_command stat
  validate_absolute_directory "$PAWCYCLE_STATE_DIR" "state directory"
  install -d -m 700 "$PAWCYCLE_STATE_DIR"
  [[ "$(stat -c '%u' "$PAWCYCLE_STATE_DIR")" == "0" ]] || die "state directory must be owned by root"
  exec 9>"$PAWCYCLE_STATE_DIR/deploy.lock"
  chmod 600 "$PAWCYCLE_STATE_DIR/deploy.lock"
  flock --nonblock 9 || die "another production release or database restore command is running"
  [[ ! -e "$PAWCYCLE_STATE_DIR/active-mysql-volume" \
    && ! -L "$PAWCYCLE_STATE_DIR/active-mysql-volume" ]] \
    || die "active MySQL volume state already exists"

  ids="$(docker ps \
    --filter label=com.docker.compose.project=pawcycle-production \
    --filter label=com.docker.compose.service=mysql \
    --format '{{.ID}}')"
  [[ "$(grep -c . <<<"$ids" || true)" == "1" ]] \
    || die "exactly one running production MySQL container is required"
  container="$ids"
  [[ "$(docker inspect --format '{{.Config.Image}}' "$container")" == "$MYSQL_IMAGE" ]] \
    || die "production MySQL is not using the pinned image"
  [[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")" == "healthy" ]] \
    || die "production MySQL must be healthy"
  volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$container")"
  destination="$(docker inspect --format '{{range .Mounts}}{{if eq .Name "pawcycle-production-mysql-data"}}{{.Destination}}{{end}}{{end}}' "$container")"
  [[ "$volume" == "$DEFAULT_MYSQL_VOLUME" && "$destination" == "/var/lib/mysql" ]] \
    || die "initial production MySQL volume contract does not match"
  docker volume inspect "$DEFAULT_MYSQL_VOLUME" >/dev/null || die "initial production MySQL volume is missing"
  write_state active-mysql-volume "$DEFAULT_MYSQL_VOLUME"
  printf 'Active MySQL volume state initialized after pinned-image, health, and mount verification\n'
}

initialize_transition_context() {
  validate_absolute_directory "$PAWCYCLE_STATE_DIR" "state directory"
  prepare_release_context
  acquire_release_lock
  TARGET_SHA="$(read_state_sha current-sha)"
  validate_sha "$TARGET_SHA"
  ACTIVE_SHA="$TARGET_SHA"
  load_active_mysql_volume
  load_runtime_contract
}

verify_active_mysql_identity() {
  local container_id
  local mysql_volume

  container_id="$(compose ps --quiet mysql)" || return 1
  [[ -n "$container_id" ]] || return 1
  [[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")" == "healthy" ]] \
    || return 1
  [[ "$(docker inspect --format '{{.Config.Image}}' "$container_id")" == "$MYSQL_IMAGE" ]] \
    || return 1
  mysql_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$container_id")"
  [[ "$mysql_volume" == "$ACTIVE_MYSQL_VOLUME" ]]
}

activate_database_release() {
  local record="$1"
  local sha="$2"
  local service

  ACTIVE_SHA="$sha"
  export ACTIVE_SHA
  compose up --detach --pull never --remove-orphans mysql || return 1
  wait_healthy mysql || return 1
  verify_active_mysql_identity || return 1
  verify_active_database "$record" || return 1

  compose up --detach --pull never --remove-orphans backend frontend || return 1
  for service in backend frontend; do
    wait_healthy "$service" || return 1
  done
  # Backend startup can run Flyway. Recheck the protected database contract
  # before any user traffic is admitted through the proxy.
  verify_active_database "$record" || return 1

  compose up --detach --pull never --no-deps --force-recreate proxy || return 1
  wait_healthy proxy || return 1
  verify_running_release || return 1
  smoke_release || return 1
  if https_enabled; then
    verify_https_release || return 1
  fi
}

restore_source_after_failed_cutover() {
  local source_volume="$1"
  local source_record="$2"
  local recovery_state_written=1

  compose stop proxy frontend backend mysql >/dev/null 2>&1 || true
  if ! write_state active-mysql-volume "$source_volume"; then
    recovery_state_written=0
  fi
  ACTIVE_MYSQL_VOLUME="$source_volume"
  if (activate_database_release "$source_record" "$TARGET_SHA") \
    && [[ "$recovery_state_written" == "1" ]]; then
    die "candidate cutover failed; source volume and application state were restored"
  fi
  die "candidate cutover and source recovery both failed; both MySQL volumes were preserved"
}

restore_candidate_after_failed_revert() {
  local candidate_volume="$1"
  local candidate_record="$2"
  local recovery_state_written=1

  compose stop proxy frontend backend mysql >/dev/null 2>&1 || true
  if ! write_state active-mysql-volume "$candidate_volume"; then
    recovery_state_written=0
  fi
  ACTIVE_MYSQL_VOLUME="$candidate_volume"
  if (activate_database_release "$candidate_record" "$TARGET_SHA") \
    && [[ "$recovery_state_written" == "1" ]]; then
    die "source database revert failed; candidate volume and application state were restored"
  fi
  die "source database revert and candidate recovery both failed; both MySQL volumes were preserved"
}

resume_candidate_before_revert() {
  local candidate_volume="$1"
  local reason="$2"

  ACTIVE_MYSQL_VOLUME="$candidate_volume"
  if activate_release "$TARGET_SHA"; then
    die "$reason; candidate volume and application state were restored without database transition"
  fi
  die "$reason and candidate release restoration both failed; both MySQL volumes were preserved"
}

cutover() {
  local source_volume
  local source_record="$PAWCYCLE_STATE_DIR/db-restore-source"
  local candidate_record="$PAWCYCLE_STATE_DIR/db-restore-candidate"

  initialize_transition_context
  source_volume="$ACTIVE_MYSQL_VOLUME"
  [[ ! -e "$source_record" && ! -L "$source_record" ]] \
    || die "source recovery record already exists; preserve it and stop"
  validate_candidate_record
  preflight_release "$TARGET_SHA"
  verify_running_release || die "running release identity is invalid before database cutover"
  smoke_release || die "running release smoke failed before database cutover"
  if https_enabled; then
    verify_https_release || die "running HTTPS verification failed before database cutover"
  fi

  if ! compose stop proxy frontend backend; then
    ACTIVE_MYSQL_VOLUME="$source_volume"
    activate_release "$TARGET_SHA" || true
    die "application write-path stop failed; source release reactivation was attempted without cutover"
  fi
  if ! write_database_record "$source_record" "$source_volume" "$TARGET_SHA"; then
    activate_release "$TARGET_SHA" || true
    die "source database recovery record failed; application restart was attempted without cutover"
  fi
  if ! compose stop mysql; then
    restore_source_after_failed_cutover "$source_volume" "$source_record"
  fi
  if ! (validate_candidate_record); then
    restore_source_after_failed_cutover "$source_volume" "$source_record"
  fi
  if ! write_state previous-mysql-volume "$source_volume" \
    || ! write_state db-restore-application-sha "$TARGET_SHA" \
    || ! write_state active-mysql-volume "$CANDIDATE_VOLUME"; then
    restore_source_after_failed_cutover "$source_volume" "$source_record"
  fi
  ACTIVE_MYSQL_VOLUME="$CANDIDATE_VOLUME"

  if ! (activate_database_release "$candidate_record" "$TARGET_SHA"); then
    restore_source_after_failed_cutover "$source_volume" "$source_record"
  fi
  compose ps || printf 'WARNING: database cutover succeeded, but final compose ps failed\n' >&2
  printf 'Production database candidate activated; source and candidate volumes preserved\n'
}

revert() {
  local candidate_volume
  local source_volume
  local restore_sha
  local source_record="$PAWCYCLE_STATE_DIR/db-restore-source"
  local current_candidate_record="$PAWCYCLE_STATE_DIR/db-restore-revert-candidate"

  initialize_transition_context
  candidate_volume="$ACTIVE_MYSQL_VOLUME"
  source_volume="$(read_state_volume previous-mysql-volume)"
  restore_sha="$(read_state_sha db-restore-application-sha)"
  validate_mysql_volume "$source_volume"
  [[ "$source_volume" != "$candidate_volume" && "$TARGET_SHA" == "$restore_sha" ]] \
    || die "database revert state does not match the active application transition"
  validate_record_file "$source_record"
  [[ "$(record_value "$source_record" SOURCE_VOLUME)" == "$source_volume" \
    && "$(record_value "$source_record" APPLICATION_SHA)" == "$restore_sha" ]] \
    || die "source recovery record does not match revert state"
  CANDIDATE_VOLUME="$candidate_volume"
  validate_candidate_record "$source_volume" true
  docker volume inspect "$source_volume" >/dev/null || die "source recovery volume is missing"
  preflight_release "$restore_sha"

  if ! compose stop proxy frontend backend; then
    resume_candidate_before_revert "$candidate_volume" "database revert write-path stop failed"
  fi
  if ! write_database_record \
    "$current_candidate_record" "$candidate_volume" "$restore_sha" candidate-current true; then
    resume_candidate_before_revert "$candidate_volume" "current candidate recovery record failed"
  fi
  if ! (validate_current_candidate_record \
    "$current_candidate_record" "$candidate_volume" "$restore_sha"); then
    resume_candidate_before_revert "$candidate_volume" "current candidate recovery record validation failed"
  fi
  if ! compose stop mysql; then
    restore_candidate_after_failed_revert "$candidate_volume" "$current_candidate_record"
  fi
  if ! write_state active-mysql-volume "$source_volume"; then
    restore_candidate_after_failed_revert "$candidate_volume" "$current_candidate_record"
  fi
  ACTIVE_MYSQL_VOLUME="$source_volume"
  if ! (activate_database_release "$source_record" "$restore_sha"); then
    restore_candidate_after_failed_revert "$candidate_volume" "$current_candidate_record"
  fi
  compose ps || printf 'WARNING: database revert succeeded, but final compose ps failed\n' >&2
  printf 'Original production database volume and application state restored; both volumes preserved\n'
}

main() {
  umask 077
  parse_args "$@"
  if [[ "$COMMAND" == "initialize-volume-state" ]]; then
    initialize_volume_state
    return
  fi
  for command in awk chmod curl cut docker flock git grep install mktemp mv readlink rm sha256sum stat tr wc; do
    require_command "$command"
  done
  case "$COMMAND" in
    cutover) cutover ;;
    revert) revert ;;
  esac
}

main "$@"
