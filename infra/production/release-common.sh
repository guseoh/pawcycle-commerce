#!/usr/bin/env bash

set -Eeuo pipefail

PRODUCTION_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CONTROL_WORKTREE_ROOT="$(cd -- "$PRODUCTION_DIR/../.." && pwd -P)"
COMPOSE_FILE="$PRODUCTION_DIR/compose.yaml"
PROJECT_NAME="pawcycle-production"
HEALTH_TIMEOUT_SECONDS="${PAWCYCLE_HEALTH_TIMEOUT_SECONDS:-240}"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
DEFAULT_MYSQL_VOLUME="pawcycle-production-mysql-data"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
CERTBOT_IMAGE="certbot/certbot:v5.7.0@sha256:d07bd043d61d6bee1114235ac12c2e9a5c54b6931b3ccf5e1174d6c8c4afaa95"
CERTIFICATE_NAME="pawcycle-production"
CERTBOT_WEBROOT_VOLUME="pawcycle-production-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-production-letsencrypt"
HTTPS_MARKER_NAME="https-enabled"
HTTPS_DOMAIN_NAME="https-domain"
HTTPS_NGINX_CONFIG_NAME="nginx.https.conf"
HTTPS_MIN_CERT_VALIDITY_SECONDS="86400"
MIGRATION_BUNDLE_PATH=':(top)backend/src/main/resources/db/migration'
HTTPS_DOMAIN=""
RELEASE_CONTRACT_PATHS=(
  ':(top)infra/production/compose.yaml'
  ':(top)infra/production/nginx.conf'
  ':(top)infra/production/nginx.https.conf'
)
CONTROL_WORKTREE_PATHS=(
  "${RELEASE_CONTRACT_PATHS[@]}"
  ':(top)infra/production/release-common.sh'
  ':(top)infra/production/deploy.sh'
  ':(top)infra/production/rollback.sh'
  ':(top)infra/production/subscription-automation-control.sh'
  ':(top)infra/production/subscription-automation-preflight.sh'
  ':(top)infra/production/production-db-restore.sh'
  ':(top)infra/production/materialize-ssm-env.sh'
  ':(top)infra/production/rds-read-only-preflight.sh'
  ':(top)infra/production/rds-transition-gate.sh'
)
CONTRACT_SHA=""
PENDING_CONTRACT_SHA=""
ACTIVE_MYSQL_VOLUME=""
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=""
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=""
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=""
PAWCYCLE_DATASOURCE_HOST=""
PAWCYCLE_DATASOURCE_PORT=""
PAWCYCLE_DATASOURCE_SSL_MODE=""
declare -A RUNTIME_LOCK_FDS=()

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

validate_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "release SHA must be 40 lowercase hexadecimal characters"
}

validate_image_repository() {
  [[ "$1" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._/-]*$ ]] \
    || die "image repository must be an untagged lowercase ghcr.io path"
}

validate_absolute_directory() {
  [[ "$1" == /* && "$1" != "/" ]] || die "$2 must be an absolute directory other than /"
}

read_runtime_setting() {
  local file="$1"
  local key="$2"
  local destination="$3"
  local line
  local matches=0
  local value=""
  local encoded
  local quote="'"
  local prefix="${key}=${quote}"

  while IFS= read -r line; do
    if [[ "$line" == "$key="* ]]; then
      matches=$((matches + 1))
      [[ "$line" == "$prefix"*"$quote" ]] \
        || die "runtime setting must use the materialized single-quoted format: $key"
      value="${line#"$prefix"}"
      value="${value%"$quote"}"
      encoded="$value"
      while [[ "$encoded" == *"$quote"* ]]; do
        [[ "$encoded" == *"\\$quote"* ]] \
          || die "runtime setting contains an unescaped quote: $key"
        encoded="${encoded//\\$quote/}"
      done
      value="${value//\\$quote/$quote}"
    fi
  done < "$file"
  [[ "$matches" -eq 1 ]] || die "runtime setting must appear exactly once: $key"
  printf -v "$destination" '%s' "$value"
}

validate_subscription_automation_settings() {
  [[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" == "true" \
    || "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" == "false" ]] \
    || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED must be exactly true or false"
  [[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE" =~ ^[1-9][0-9]*$ ]] \
    || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE must be a positive explicit integer"
  [[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS" =~ ^[1-9][0-9]*$ ]] \
    || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS must be a positive explicit integer"
}

validate_datasource_settings() {
  local database="$1"
  local user="$2"
  local password="$3"
  local backend_password="$4"
  local datasource_url="$5"
  local expected_url

  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || die "MYSQL_DATABASE has an unsafe identifier shape"
  [[ "$user" =~ ^[A-Za-z0-9_]{1,32}$ ]] || die "MYSQL_USER has an unsafe identifier shape"
  [[ -n "$password" && "$password" != *$'\n'* && "$password" != *$'\r'* ]] || die "MYSQL_PASSWORD has an unsafe runtime shape"
  [[ "$backend_password" == "$password" ]] || die "backend datasource password must match the MySQL user password"
  [[ "$PAWCYCLE_DATASOURCE_PORT" == "3306" ]] || die "datasource port must be exactly 3306"
  [[ "$PAWCYCLE_DATASOURCE_SSL_MODE" == "DISABLED" || "$PAWCYCLE_DATASOURCE_SSL_MODE" == "REQUIRED" ]] || die "datasource ssl mode is invalid"
  if [[ "$PAWCYCLE_DATASOURCE_HOST" == "mysql" && "$PAWCYCLE_DATASOURCE_SSL_MODE" == "DISABLED" ]]; then
    expected_url="jdbc:mysql://mysql:3306/${database}?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  elif [[ "$PAWCYCLE_DATASOURCE_HOST" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+ap-northeast-2\.rds\.amazonaws\.com$ && ${#PAWCYCLE_DATASOURCE_HOST} -le 253 && "$PAWCYCLE_DATASOURCE_SSL_MODE" == "REQUIRED" ]]; then
    expected_url="jdbc:mysql://${PAWCYCLE_DATASOURCE_HOST}:3306/${database}?sslMode=REQUIRED&serverTimezone=UTC"
  else
    die "datasource runtime combination is not approved"
  fi
  [[ "$datasource_url" == "$expected_url" ]] || die "datasource URL does not exactly match the validated runtime fields"
}

acquire_runtime_read_lock() {
  local runtime_dir="$1"
  local lock="$runtime_dir/.materialize.lock"

  local canonical_dir
  local fd
  canonical_dir="$(realpath -e "$runtime_dir")" || die "unable to resolve runtime directory: $runtime_dir"
  if [[ -n "${RUNTIME_LOCK_FDS[$canonical_dir]:-}" ]]; then
    return 0
  fi
  require_command flock
  [[ -f "$lock" && ! -L "$lock" && "$(stat -c '%a' "$lock")" == "600" ]] \
    || die "materialize lock must be a regular mode-600 file: $lock"
  exec {fd}<"$lock"
  flock --shared --nonblock "$fd" || die "runtime materialization is in progress"
  RUNTIME_LOCK_FDS[$canonical_dir]="$fd"
}

validate_runtime_key_set() {
  local file="$1"
  shift
  local line key
  local -A allowed=()
  local -A count=()
  for key in "$@"; do allowed[$key]=1; count[$key]=0; done
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=\'.*\'$ ]] \
      || die "runtime file must contain only canonical single-quoted key=value lines: $file"
    key="${BASH_REMATCH[1]}"
    [[ -n "${allowed[$key]:-}" ]] || die "runtime file contains an unknown or cross-file key: $key"
    count[$key]=$((count[$key] + 1))
  done < "$file"
  for key in "$@"; do [[ "${count[$key]}" == "1" ]] || die "runtime key must appear exactly once: $key"; done
}

require_subscription_automation_mode() {
  local expected="$1"

  [[ "$expected" == "true" || "$expected" == "false" ]] \
    || die "internal automation mode expectation is invalid"
  [[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" == "$expected" ]] \
    || die "subscription automation runtime must be explicitly $expected for this command"
}

validate_runtime_bundle() {
  local runtime_dir="$1"
  local current="$runtime_dir/current"
  local file

  validate_absolute_directory "$runtime_dir" "runtime directory"
  acquire_runtime_read_lock "$runtime_dir"
  [[ -d "$current" ]] || die "materialized runtime bundle is missing: $current"
  [[ -f "$current/.complete" ]] || die "runtime bundle completion marker is missing"

  for file in "$current/mysql.env" "$current/backend.env" "$current/.complete"; do
    [[ ! -L "$file" && -f "$file" ]] || die "runtime file must be a regular non-symlink file: $file"
    [[ "$(stat -c '%a' "$file")" == "600" ]] || die "runtime file mode must be 600: $file"
  done

  validate_runtime_key_set "$current/mysql.env" MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
  validate_runtime_key_set "$current/backend.env" PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_SSL_MODE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS

  local MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
  local SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
  for key in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD; do
    read_runtime_setting "$current/mysql.env" "$key" "$key"
  done
  for key in PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_SSL_MODE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
    read_runtime_setting "$current/backend.env" "$key" "$key"
  done
  if grep -Eq '^MYSQL_ROOT_PASSWORD=' "$current/backend.env"; then
    die "Backend runtime file must not contain the MySQL root password"
  fi
  if grep -Eq '^PAWCYCLE_DATASOURCE_' "$current/mysql.env"; then
    die "MySQL runtime file must not contain Backend-only datasource fields"
  fi
  [[ "$SPRING_DATASOURCE_USERNAME" == "$MYSQL_USER" ]] || die "backend datasource username must match MYSQL_USER"
  validate_datasource_settings "$MYSQL_DATABASE" "$MYSQL_USER" "$MYSQL_PASSWORD" "$SPRING_DATASOURCE_PASSWORD" "$SPRING_DATASOURCE_URL"

  read_runtime_setting "$current/backend.env" \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED
  read_runtime_setting "$current/backend.env" \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE
  read_runtime_setting "$current/backend.env" \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS
  validate_subscription_automation_settings

  PAWCYCLE_MYSQL_ENV_FILE="$current/mysql.env"
  PAWCYCLE_BACKEND_ENV_FILE="$current/backend.env"
  export PAWCYCLE_MYSQL_ENV_FILE PAWCYCLE_BACKEND_ENV_FILE \
    PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_SSL_MODE \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS
}

prepare_state_directory() {
  validate_absolute_directory "$PAWCYCLE_STATE_DIR" "state directory"
  install -d -m 700 "$PAWCYCLE_STATE_DIR"
}

read_state_sha() {
  local name="$1"
  local path="$PAWCYCLE_STATE_DIR/$name"
  local value

  [[ -e "$path" || -L "$path" ]] || die "$name state is missing"
  [[ ! -L "$path" && -f "$path" ]] || die "$name state must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$path")" == "600" ]] || die "$name state mode must be 600"
  value="$(<"$path")"
  validate_sha "$value"
  printf '%s\n' "$value"
}

validate_mysql_volume() {
  [[ "$1" == "$DEFAULT_MYSQL_VOLUME" || "$1" =~ ^pawcycle-production-mysql-candidate-[0-9a-f]{16}$ ]] \
    || die "active MySQL volume state is invalid"
}

load_active_mysql_volume() {
  local path="$PAWCYCLE_STATE_DIR/active-mysql-volume"

  [[ -e "$path" || -L "$path" ]] || die "active MySQL volume state is missing"
  [[ ! -L "$path" && -f "$path" ]] || die "active MySQL volume state must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$path")" == "600" ]] || die "active MySQL volume state mode must be 600"
  ACTIVE_MYSQL_VOLUME="$(<"$path")"
  validate_mysql_volume "$ACTIVE_MYSQL_VOLUME"
}

current_control_sha() {
  local changes
  local sha

  if ! changes="$(git status --porcelain --untracked-files=all -- "${CONTROL_WORKTREE_PATHS[@]}")"; then
    die "unable to inspect production control contract worktree"
  fi
  [[ -z "$changes" ]] || die "production control contract worktree is not clean"

  if ! sha="$(git rev-parse --verify HEAD)"; then
    die "unable to resolve production control HEAD"
  fi
  validate_sha "$sha"
  printf '%s\n' "$sha"
}

current_clean_control_sha() {
  local changes
  local sha

  if ! changes="$(git -C "$CONTROL_WORKTREE_ROOT" status --porcelain --untracked-files=all)"; then
    die "unable to inspect Production control worktree"
  fi
  [[ -z "$changes" ]] || die "Production control worktree is not clean"

  if ! sha="$(git -C "$CONTROL_WORKTREE_ROOT" rev-parse --verify HEAD)"; then
    die "unable to resolve Production control HEAD"
  fi
  validate_sha "$sha"
  printf '%s\n' "$sha"
}

validate_https_domain() {
  [[ "$1" =~ ^([a-z0-9]|[a-z0-9][a-z0-9-]{0,61}[a-z0-9])\.duckdns\.org$ ]] \
    || die "domain must be one lowercase single-label duckdns.org hostname"
}

load_https_domain() {
  local domain_file="$PAWCYCLE_STATE_DIR/$HTTPS_DOMAIN_NAME"

  [[ -e "$domain_file" || -L "$domain_file" ]] || die "HTTPS domain state is missing"
  [[ ! -L "$domain_file" && -f "$domain_file" ]] \
    || die "HTTPS domain state must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$domain_file")" == "600" ]] || die "HTTPS domain state mode must be 600"
  HTTPS_DOMAIN="$(<"$domain_file")"
  validate_https_domain "$HTTPS_DOMAIN"
}

validate_https_nginx_state() {
  local config="$PAWCYCLE_STATE_DIR/$HTTPS_NGINX_CONFIG_NAME"

  [[ -e "$config" || -L "$config" ]] || die "generated HTTPS Nginx configuration is missing"
  [[ ! -L "$config" && -f "$config" ]] \
    || die "generated HTTPS Nginx configuration must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$config")" == "600" ]] || die "generated HTTPS Nginx configuration mode must be 600"
  grep -Fq "server_name $HTTPS_DOMAIN;" "$config" \
    || die "generated HTTPS Nginx configuration does not contain the approved domain"
  grep -Fq "return 301 https://$HTTPS_DOMAIN\$request_uri;" "$config" \
    || die "generated HTTPS redirect does not use the approved domain"
  if grep -Fq '__PAWCYCLE_DOMAIN__' "$config"; then
    die "generated HTTPS Nginx configuration contains an unresolved domain placeholder"
  fi
}

https_enabled() {
  local marker="$PAWCYCLE_STATE_DIR/$HTTPS_MARKER_NAME"

  [[ -e "$marker" || -L "$marker" ]] || return 1
  [[ ! -L "$marker" && -f "$marker" ]] || die "HTTPS marker must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$marker")" == "600" ]] || die "HTTPS marker mode must be 600"
  [[ "$(<"$marker")" == "enabled" ]] || die "HTTPS marker content is invalid"
  load_https_domain
  validate_https_nginx_state
}

compose() {
  local nginx_config="$PRODUCTION_DIR/nginx.conf"

  if https_enabled; then
    nginx_config="$PAWCYCLE_STATE_DIR/$HTTPS_NGINX_CONFIG_NAME"
  fi

  RELEASE_SHA="$ACTIVE_SHA" \
  BACKEND_IMAGE="$BACKEND_IMAGE" \
  FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  PAWCYCLE_MYSQL_ENV_FILE="$PAWCYCLE_MYSQL_ENV_FILE" \
  PAWCYCLE_BACKEND_ENV_FILE="$PAWCYCLE_BACKEND_ENV_FILE" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED="$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE="$PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS="$PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS" \
  PAWCYCLE_MYSQL_VOLUME="$ACTIVE_MYSQL_VOLUME" \
  PAWCYCLE_EDGE_NETWORK="pawcycle-production-edge" \
  PAWCYCLE_APP_NETWORK="pawcycle-production-app" \
  PAWCYCLE_DATA_NETWORK="pawcycle-production-data" \
  PAWCYCLE_DATABASE_EGRESS_NETWORK="pawcycle-production-database-egress" \
  PAWCYCLE_CERTBOT_WEBROOT_VOLUME="$CERTBOT_WEBROOT_VOLUME" \
  PAWCYCLE_LETSENCRYPT_VOLUME="$LETSENCRYPT_VOLUME" \
  PAWCYCLE_NGINX_CONFIG="$nginx_config" \
  PAWCYCLE_HTTP_PORT="80" \
  PAWCYCLE_HTTPS_PORT="443" \
    docker compose --project-name "$PROJECT_NAME" --file "$COMPOSE_FILE" "$@"
}

base_image_digest() {
  local reference="$1"
  local repository="${reference%%:*}"
  local expected_digest="${reference##*@}"
  local digest

  [[ "$reference" == *@sha256:* && "$expected_digest" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || die "base image must be pinned by sha256 digest: $reference"

  docker pull "$reference" >/dev/null
  digest="$(docker image inspect --format '{{ range .RepoDigests }}{{ println . }}{{ end }}' "$reference" \
    | grep -Fx "${repository}@${expected_digest}" | head -n 1 || true)"
  [[ -n "$digest" ]] || die "pinned base image digest is missing or drifted: $reference"
  printf '%s\n' "$digest"
}

image_digest() {
  local repository="$1"
  local sha="$2"
  local reference="${repository}:${sha}"
  local revision
  local digest

  docker pull "$reference" >/dev/null
  revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$reference")"
  [[ "$revision" == "$sha" ]] || die "image revision label does not match release SHA: $reference"

  digest="$(docker image inspect --format '{{ range .RepoDigests }}{{ println . }}{{ end }}' "$reference" \
    | grep -F "${repository}@sha256:" | head -n 1 || true)"
  [[ -n "$digest" ]] || die "registry digest is missing: $reference"
  printf '%s\n' "$digest"
}

preflight_release() {
  local sha="$1"
  local record_images="${2:-${PAWCYCLE_PREFLIGHT_RECORD_IMAGES:-true}}"
  local record="$PAWCYCLE_STATE_DIR/${sha}.images"
  local backend_digest
  local frontend_digest
  local mysql_digest
  local proxy_digest
  local candidate_record="${record}.tmp"

  validate_sha "$sha"
  [[ "$record_images" == "true" || "$record_images" == "false" ]] \
    || die "internal preflight image-recording mode is invalid"
  ACTIVE_SHA="$sha"
  export ACTIVE_SHA

  compose config --quiet
  mysql_digest="$(base_image_digest "$MYSQL_IMAGE")"
  proxy_digest="$(base_image_digest "$PROXY_IMAGE")"
  backend_digest="$(image_digest "$BACKEND_IMAGE" "$sha")"
  frontend_digest="$(image_digest "$FRONTEND_IMAGE" "$sha")"

  if [[ "$record_images" == "true" ]]; then
    {
      printf 'RELEASE_SHA=%s\n' "$sha"
      printf 'BACKEND_DIGEST=%s\n' "$backend_digest"
      printf 'FRONTEND_DIGEST=%s\n' "$frontend_digest"
      printf 'MYSQL_DIGEST=%s\n' "$mysql_digest"
      printf 'PROXY_DIGEST=%s\n' "$proxy_digest"
    } > "$candidate_record"
    chmod 600 "$candidate_record"

    if [[ -f "$record" ]]; then
      if ! cmp -s "$candidate_record" "$record"; then
        rm -f -- "$candidate_record"
        die "image digest drift detected for previously verified release SHA: $sha"
      fi
      rm -f -- "$candidate_record"
    else
      mv "$candidate_record" "$record"
    fi
  fi

  printf 'Verified Backend digest: %s\n' "$backend_digest"
  printf 'Verified Frontend digest: %s\n' "$frontend_digest"
  printf 'Verified MySQL digest: %s\n' "$mysql_digest"
  printf 'Verified Nginx digest: %s\n' "$proxy_digest"
}

validate_runtime_contract_compatibility() {
  local approved_contract_sha="$1"
  local candidate_sha="$2"
  local status

  [[ "$approved_contract_sha" != "$candidate_sha" ]] || return 0
  git cat-file -e "${approved_contract_sha}^{commit}" 2>/dev/null \
    || die "approved release contract commit is unavailable: $approved_contract_sha"
  git cat-file -e "${candidate_sha}^{commit}" 2>/dev/null \
    || die "candidate release contract commit is unavailable: $candidate_sha"

  if release_contract_changed "$approved_contract_sha" "$candidate_sha"; then
    die "production release contract differs from the approved contract SHA"
  fi
  return 0
}

release_contract_changed() {
  local approved_contract_sha="$1"
  local candidate_sha="$2"
  local status

  [[ "$approved_contract_sha" != "$candidate_sha" ]] || return 1
  git cat-file -e "${approved_contract_sha}^{commit}" 2>/dev/null \
    || die "approved release contract commit is unavailable: $approved_contract_sha"
  git cat-file -e "${candidate_sha}^{commit}" 2>/dev/null \
    || die "candidate release contract commit is unavailable: $candidate_sha"

  if git diff --quiet "$approved_contract_sha" "$candidate_sha" -- "${RELEASE_CONTRACT_PATHS[@]}"; then
    return 1
  else
    status=$?
  fi
  [[ "$status" -eq 1 ]] || die "unable to compare production release contracts"
  return 0
}

require_contract_boundary_approval() {
  local stored_contract_sha="$1"
  local current_release_sha="$2"
  local target_sha="$3"
  local approved_contract_from_sha="$4"
  local approved_control_sha="$5"
  local control_sha

  [[ -n "$approved_contract_from_sha" && -n "$approved_control_sha" ]] \
    || die "production release contract boundary requires approved_contract_from_sha and approved_control_sha"
  validate_sha "$approved_contract_from_sha"
  validate_sha "$approved_control_sha"
  [[ "$stored_contract_sha" == "$approved_contract_from_sha" ]] \
    || die "approved_contract_from_sha does not match stored contract-sha"
  [[ "$target_sha" != "$current_release_sha" ]] \
    || die "production release contract boundary requires a target different from current-sha"

  control_sha="$(current_clean_control_sha)"
  [[ "$control_sha" == "$approved_control_sha" ]] \
    || die "approved_control_sha does not match the current clean Control HEAD"
  validate_runtime_contract_compatibility "$control_sha" "$target_sha"

  PENDING_CONTRACT_SHA="$control_sha"
  printf 'Approved Production release contract boundary: %s -> %s\n' \
    "$stored_contract_sha" "$control_sha"
}

require_control_only_contract_adoption() {
  local stored_contract_sha="$1"
  local current_release_sha="$2"
  local target_sha="$3"
  local approved_contract_from_sha="$4"
  local approved_control_sha="$5"
  local control_sha

  [[ -n "$approved_contract_from_sha" && -n "$approved_control_sha" ]] \
    || die "control-only contract adoption requires approved_contract_from_sha and approved_control_sha"
  validate_sha "$approved_contract_from_sha"
  validate_sha "$approved_control_sha"
  [[ "$stored_contract_sha" == "$approved_contract_from_sha" ]] \
    || die "approved_contract_from_sha does not match stored contract-sha"
  [[ "$target_sha" == "$current_release_sha" ]] \
    || die "control-only contract adoption requires target SHA to match current-sha"

  control_sha="$(current_clean_control_sha)"
  [[ "$control_sha" == "$approved_control_sha" ]] \
    || die "approved_control_sha does not match the current clean Control HEAD"

  PENDING_CONTRACT_SHA="$control_sha"
  printf 'Approved Production control-only contract adoption: %s -> %s\n' \
    "$stored_contract_sha" "$control_sha"
}

require_migration_boundary_approval() {
  local target_sha="$1"
  local approved_migration_target_sha="$2"

  [[ -n "$approved_migration_target_sha" ]] \
    || die "database migration boundary requires approved_migration_target_sha"
  validate_sha "$approved_migration_target_sha"
  [[ "$approved_migration_target_sha" == "$target_sha" ]] \
    || die "approved_migration_target_sha does not match target SHA"
  printf 'Approved Production database migration boundary for target: %s\n' "$target_sha"
}

migration_bundle_changed() {
  local current_sha="$1"
  local target_sha="$2"
  local status

  [[ "$current_sha" != "$target_sha" ]] || return 1
  git -C "$CONTROL_WORKTREE_ROOT" cat-file -e "${current_sha}^{commit}" 2>/dev/null \
    || die "current release commit is unavailable for migration comparison: $current_sha"
  git -C "$CONTROL_WORKTREE_ROOT" cat-file -e "${target_sha}^{commit}" 2>/dev/null \
    || die "target release commit is unavailable for migration comparison: $target_sha"

  if git -C "$CONTROL_WORKTREE_ROOT" diff --quiet "$current_sha" "$target_sha" -- "$MIGRATION_BUNDLE_PATH"; then
    return 1
  else
    status=$?
  fi
  [[ "$status" -eq 1 ]] || die "unable to compare release migration bundles"
  return 0
}

require_no_migration_boundary_rollback() {
  local current_sha="$1"
  local target_sha="$2"

  if migration_bundle_changed "$current_sha" "$target_sha"; then
    die "rollback crosses a database migration boundary; pre-migration release activation is blocked and MySQL was preserved"
  fi
}

validate_current_release_for_contract_adoption() {
  local current_release_sha="$1"
  local service

  [[ -n "$current_release_sha" ]] || return 0
  ACTIVE_SHA="$current_release_sha"
  export ACTIVE_SHA
  printf 'Preflighting currently running release before control contract adoption: %s\n' "$current_release_sha"
  preflight_release "$current_release_sha"
  for service in mysql backend frontend proxy; do
    wait_healthy "$service" || die "running $service is not healthy during control contract adoption"
  done
  verify_running_release || die "running release identity does not match current-sha during control contract adoption"
  smoke_release || die "running release smoke failed during control contract adoption"
  if https_enabled; then
    verify_https_release || die "running HTTPS release verification failed during control contract adoption"
  fi
}

load_or_adopt_runtime_contract() {
  local requested_contract_sha="$1"
  local current_release_sha="$2"
  local state_path="$PAWCYCLE_STATE_DIR/contract-sha"
  local stored_contract_sha=""
  local control_sha

  control_sha="$(current_control_sha)"

  if [[ -e "$state_path" || -L "$state_path" ]]; then
    stored_contract_sha="$(read_state_sha contract-sha)"
    if [[ "$stored_contract_sha" == "$control_sha" ]]; then
      if [[ -n "$requested_contract_sha" && "$requested_contract_sha" != "$control_sha" ]]; then
        die "requested runtime contract SHA does not match current control HEAD"
      fi
      CONTRACT_SHA="$stored_contract_sha"
      return 0
    fi

    [[ -n "$requested_contract_sha" ]] \
      || die "production control SHA differs from contract state; --adopt-contract-sha with the current control HEAD is required"
    validate_sha "$requested_contract_sha"
    [[ "$requested_contract_sha" == "$control_sha" ]] \
      || die "requested runtime contract SHA does not match current control HEAD"
    validate_runtime_contract_compatibility "$stored_contract_sha" "$control_sha"
  else
    [[ -n "$requested_contract_sha" ]] \
      || die "production runtime contract state is missing; --adopt-contract-sha with the current control HEAD is required"
    validate_sha "$requested_contract_sha"
    [[ "$requested_contract_sha" == "$control_sha" ]] \
      || die "requested runtime contract SHA does not match current control HEAD"
  fi

  validate_current_release_for_contract_adoption "$current_release_sha"
  PENDING_CONTRACT_SHA="$control_sha"
  CONTRACT_SHA="$control_sha"
  printf 'Production control contract adoption validated: %s\n' "$CONTRACT_SHA"
}

load_runtime_contract() {
  local control_sha

  CONTRACT_SHA="$(read_state_sha contract-sha)"
  control_sha="$(current_control_sha)"
  [[ "$control_sha" == "$CONTRACT_SHA" ]] \
    || die "production control SHA differs from contract state; deploy with --adopt-contract-sha using the current control HEAD after explicit approval"
}

validate_rollback_contract_compatibility() {
  local target_sha="$1"
  local previous_sha=""
  local previous_contract_sha=""

  if [[ -e "$PAWCYCLE_STATE_DIR/previous-sha" || -L "$PAWCYCLE_STATE_DIR/previous-sha" ]]; then
    previous_sha="$(read_state_sha previous-sha)"
  fi
  if [[ -e "$PAWCYCLE_STATE_DIR/previous-contract-sha" || -L "$PAWCYCLE_STATE_DIR/previous-contract-sha" ]]; then
    previous_contract_sha="$(read_state_sha previous-contract-sha)"
  fi

  if [[ "$target_sha" == "$previous_sha" && "$previous_contract_sha" == "$CONTRACT_SHA" ]]; then
    return 0
  fi
  validate_runtime_contract_compatibility "$CONTRACT_SHA" "$target_sha"
}

wait_healthy() {
  local service="$1"
  local elapsed=0
  local container_id
  local status

  while (( elapsed < HEALTH_TIMEOUT_SECONDS )); do
    container_id="$(compose ps --quiet "$service")"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{ if .State.Health }}{{ .State.Health.Status }}{{ else }}{{ .State.Status }}{{ end }}' "$container_id")"
      case "$status" in
        healthy)
          printf '%s is healthy\n' "$service"
          return 0
          ;;
        unhealthy|exited|dead)
          printf '%s entered terminal status: %s\n' "$service" "$status" >&2
          return 1
          ;;
      esac
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done

  printf '%s did not become healthy within %ss\n' "$service" "$HEALTH_TIMEOUT_SECONDS" >&2
  return 1
}

smoke_release() {
  local proxy_id

  proxy_id="$(compose ps --quiet proxy)"
  [[ -n "$proxy_id" ]] || return 1
  if ! docker exec "$proxy_id" wget --quiet --output-document=/dev/null http://127.0.0.1:8081/products; then
    printf 'Frontend internal smoke failed: /products\n' >&2
    return 1
  fi
  if ! docker exec "$proxy_id" wget --quiet --output-document=/dev/null http://127.0.0.1:8081/api/products; then
    printf 'Backend internal smoke failed: /api/products\n' >&2
    return 1
  fi
  printf 'Internal release smoke checks passed\n'
}

validate_https_certificate() {
  local expected_domain="${1:-}"

  if [[ -n "$expected_domain" ]]; then
    validate_https_domain "$expected_domain"
  else
    load_https_domain
    expected_domain="$HTTPS_DOMAIN"
  fi
  docker run --rm --platform linux/amd64 \
    --entrypoint python \
    --env EXPECTED_DOMAIN="$expected_domain" \
    --env MIN_VALIDITY_SECONDS="$HTTPS_MIN_CERT_VALIDITY_SECONDS" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c \
    'import datetime, os, sys
from cryptography import x509

path="/etc/letsencrypt/live/pawcycle-production/fullchain.pem"
try:
    with open(path, "rb") as certificate_file:
        certificate=x509.load_pem_x509_certificate(certificate_file.read())
    san=certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
    dns_names=set(san.get_values_for_type(x509.DNSName))
    expected=os.environ["EXPECTED_DOMAIN"].lower()
    now=datetime.datetime.now(datetime.timezone.utc)
    minimum=now+datetime.timedelta(seconds=int(os.environ["MIN_VALIDITY_SECONDS"]))
    valid=(len(san) == 1 and dns_names == {expected}
           and certificate.not_valid_before_utc <= now
           and certificate.not_valid_after_utc >= minimum)
except (OSError, ValueError, x509.ExtensionNotFound):
    valid=False
sys.exit(0 if valid else 1)' \
    >/dev/null
  printf 'Certificate SAN and minimum validity checks passed\n'
}

verify_https_paths() {
  local path
  local code
  local redirect

  load_https_domain
  for path in /products /api/products; do
    curl --fail --silent --show-error --max-time 10 \
      --resolve "$HTTPS_DOMAIN:443:127.0.0.1" "https://$HTTPS_DOMAIN$path" >/dev/null || return 1
  done
  code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
    --header "Host: $HTTPS_DOMAIN" http://127.0.0.1/products)"
  redirect="$(curl --silent --output /dev/null --write-out '%{redirect_url}' --max-time 10 \
    --header "Host: $HTTPS_DOMAIN" http://127.0.0.1/products)"
  [[ "$code" == "301" && "$redirect" == "https://$HTTPS_DOMAIN/products" ]] || return 1
  smoke_release || return 1
  printf 'HTTPS application and approved-host redirect checks passed\n'
}

verify_https_release() {
  validate_https_certificate || return 1
  verify_https_paths || return 1
}

verify_running_release() {
  local service
  local expected_reference
  local container_id
  local configured_reference
  local revision
  local mysql_volume

  for service in mysql backend frontend proxy; do
    case "$service" in
      mysql) expected_reference="$MYSQL_IMAGE" ;;
      backend) expected_reference="${BACKEND_IMAGE}:${ACTIVE_SHA}" ;;
      frontend) expected_reference="${FRONTEND_IMAGE}:${ACTIVE_SHA}" ;;
      proxy) expected_reference="$PROXY_IMAGE" ;;
    esac

    container_id="$(compose ps --quiet "$service")"
    [[ -n "$container_id" ]] || return 1
    configured_reference="$(docker inspect --format '{{ .Config.Image }}' "$container_id")"
    [[ "$configured_reference" == "$expected_reference" ]] || {
      printf '%s is not running the requested immutable image\n' "$service" >&2
      return 1
    }
    if [[ "$service" == "backend" || "$service" == "frontend" ]]; then
      revision="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$container_id")"
      [[ "$revision" == "$ACTIVE_SHA" ]] || {
        printf '%s revision label does not match the requested release\n' "$service" >&2
        return 1
      }
    elif [[ "$service" == "mysql" ]]; then
      mysql_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$container_id")"
      [[ "$mysql_volume" == "$ACTIVE_MYSQL_VOLUME" ]] || {
        printf 'mysql is not using the active MySQL volume state\n' >&2
        return 1
      }
    fi
  done
}

activate_release() {
  local sha="$1"
  local service

  ACTIVE_SHA="$sha"
  export ACTIVE_SHA
  compose up --detach --pull never --remove-orphans mysql backend frontend || return 1
  for service in mysql backend frontend; do
    wait_healthy "$service" || return 1
  done
  compose up --detach --pull never --no-deps --force-recreate proxy || return 1
  wait_healthy proxy || return 1
  verify_running_release || return 1
  smoke_release || return 1
  if https_enabled; then
    verify_https_release || return 1
  fi
}

activate_backend_runtime() {
  local sha="$1"

  ACTIVE_SHA="$sha"
  export ACTIVE_SHA
  compose up --detach --pull never --no-deps --force-recreate backend || return 1
  wait_healthy backend || return 1
  wait_healthy mysql || return 1
  wait_healthy frontend || return 1
  compose up --detach --pull never --no-deps --force-recreate proxy || return 1
  wait_healthy proxy || return 1
  verify_running_release || return 1
  smoke_release || return 1
  if https_enabled; then
    verify_https_release || return 1
  fi
}

write_state() {
  local name="$1"
  local value="$2"
  local target="$PAWCYCLE_STATE_DIR/$name"

  printf '%s\n' "$value" > "${target}.tmp" || return 1
  if ! chmod 600 "${target}.tmp"; then
    rm -f -- "${target}.tmp"
    return 1
  fi
  if ! mv -f "${target}.tmp" "$target"; then
    rm -f -- "${target}.tmp"
    return 1
  fi
}

stop_application_services() {
  compose stop proxy frontend backend || true
}

stop_backend_service() {
  local running_backend_ids

  compose stop backend || die "Backend stop command failed; Scheduler state cannot be confirmed"
  running_backend_ids="$(compose ps --status running --quiet backend)" \
    || die "Backend stop verification failed; Scheduler state cannot be confirmed"
  [[ -z "$running_backend_ids" ]] \
    || die "Backend remains running after stop; Scheduler state cannot be confirmed"
}

prepare_release_context() {
  require_command curl
  require_command cmp
  require_command docker
  require_command flock
  require_command git
  require_command grep
  require_command rm
  require_command stat

  validate_image_repository "$BACKEND_IMAGE"
  validate_image_repository "$FRONTEND_IMAGE"
  validate_runtime_bundle "$PAWCYCLE_RUNTIME_DIR"
  prepare_state_directory
}

prepare_read_only_release_context() {
  require_command curl
  require_command docker
  require_command git
  require_command grep
  require_command stat

  validate_image_repository "$BACKEND_IMAGE"
  validate_image_repository "$FRONTEND_IMAGE"
  validate_runtime_bundle "$PAWCYCLE_RUNTIME_DIR"
  validate_absolute_directory "$PAWCYCLE_STATE_DIR" "state directory"
  [[ ! -L "$PAWCYCLE_STATE_DIR" && -d "$PAWCYCLE_STATE_DIR" ]] \
    || die "state directory must already exist as a non-symlink directory"
}

acquire_release_lock() {
  exec 9>"$PAWCYCLE_STATE_DIR/deploy.lock"
  chmod 600 "$PAWCYCLE_STATE_DIR/deploy.lock"
  flock --nonblock 9 || die "another production release or database restore command is running"
}

initialize_release_context() {
  validate_sha "$TARGET_SHA"
  prepare_release_context
  acquire_release_lock
  load_active_mysql_volume
}

initialize_read_only_release_context() {
  validate_sha "$TARGET_SHA"
  prepare_read_only_release_context
  load_active_mysql_volume
}
