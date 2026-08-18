#!/usr/bin/env bash

set -Eeuo pipefail

case "$-" in
  *x*) set +x ;;
esac

usage() {
  cat <<'EOF'
Usage: materialize-ssm-env.sh --ssm-prefix /path/prefix --output-dir /opt/pawcycle/runtime [--region ap-northeast-2] [--datasource-host <host> --datasource-port 3306 --datasource-ssl-mode DISABLED|REQUIRED]
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

SSM_PREFIX=""
OUTPUT_DIR=""
AWS_REGION="ap-northeast-2"
DATASOURCE_HOST="mysql"
DATASOURCE_PORT="3306"
DATASOURCE_SSL_MODE="DISABLED"
declare -A SEEN_ARGS=()

require_option_value() {
  [[ $# -ge 2 && -n "$2" && "$2" != --* ]] || die "missing value for $1"
}

while (( $# > 0 )); do
  case "$1" in
    --ssm-prefix|--output-dir|--region|--datasource-host|--datasource-port|--datasource-ssl-mode)
      [[ -z "${SEEN_ARGS[$1]:-}" ]] || die "duplicate argument: $1"
      SEEN_ARGS[$1]=1
      require_option_value "$1" "${2:-}"
      case "$1" in
        --ssm-prefix) SSM_PREFIX="$2" ;;
        --output-dir) OUTPUT_DIR="$2" ;;
        --region) AWS_REGION="$2" ;;
        --datasource-host) DATASOURCE_HOST="$2" ;;
        --datasource-port) DATASOURCE_PORT="$2" ;;
        --datasource-ssl-mode) DATASOURCE_SSL_MODE="$2" ;;
      esac
      shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

[[ "$SSM_PREFIX" == /* && "$SSM_PREFIX" != "/" ]] || die "SSM prefix must be an absolute non-root parameter path"
SSM_PREFIX="${SSM_PREFIX%/}"
[[ "$OUTPUT_DIR" == /* && "$OUTPUT_DIR" != "/" ]] || die "output directory must be an absolute directory other than /"
[[ "$AWS_REGION" == "ap-northeast-2" ]] || die "approved region is ap-northeast-2"
[[ "$DATASOURCE_HOST" != *$'\n'* && "$DATASOURCE_HOST" != *$'\r'* && "$DATASOURCE_HOST" != *"'"* && "$DATASOURCE_HOST" != *[[:cntrl:]]* ]] || die "datasource host has an unsafe shape"
[[ "$DATASOURCE_PORT" =~ ^[0-9]+$ ]] || die "datasource port must be numeric"
[[ "$DATASOURCE_SSL_MODE" == "DISABLED" || "$DATASOURCE_SSL_MODE" == "REQUIRED" ]] || die "datasource ssl mode must be DISABLED or REQUIRED"
if [[ "$DATASOURCE_HOST" == "mysql" && "$DATASOURCE_PORT" == "3306" && "$DATASOURCE_SSL_MODE" == "DISABLED" ]]; then
  :
elif [[ "$DATASOURCE_HOST" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+ap-northeast-2\.rds\.amazonaws\.com$ && ${#DATASOURCE_HOST} -le 253 && "$DATASOURCE_PORT" == "3306" && "$DATASOURCE_SSL_MODE" == "REQUIRED" ]]; then
  :
else
  die "datasource combination must be Docker mysql:3306/DISABLED or a Seoul RDS endpoint:3306/REQUIRED"
fi
command -v aws >/dev/null 2>&1 || die "AWS CLI is required"
command -v flock >/dev/null 2>&1 || die "flock is required"
command -v realpath >/dev/null 2>&1 || die "realpath is required"

umask 077
install -d -m 700 "$OUTPUT_DIR"
if [[ -e "$OUTPUT_DIR/.materialize.lock" || -L "$OUTPUT_DIR/.materialize.lock" ]]; then
  [[ -f "$OUTPUT_DIR/.materialize.lock" && ! -L "$OUTPUT_DIR/.materialize.lock" && "$(stat -c '%a' "$OUTPUT_DIR/.materialize.lock")" == "600" ]] \
    || die "materialize lock must be a regular mode-600 file"
else
  install -m 600 /dev/null "$OUTPUT_DIR/.materialize.lock"
fi
exec 9<>"$OUTPUT_DIR/.materialize.lock"
flock --nonblock 9 || die "another runtime materialization is running"
[[ ! -e "$OUTPUT_DIR/current" || -L "$OUTPUT_DIR/current" ]] \
  || die "output current path exists and is not a managed symlink"

PREVIOUS_BUNDLE=""
if [[ -L "$OUTPUT_DIR/current" ]]; then
  previous_name="$(readlink "$OUTPUT_DIR/current")"
  [[ "$previous_name" =~ ^\.bundle\.[A-Za-z0-9]+$ ]] \
    || die "current runtime symlink target is not a managed bundle"
  previous_path="$OUTPUT_DIR/$previous_name"
  [[ -d "$previous_path" && ! -L "$previous_path" ]] \
    || die "current runtime bundle target is missing or unsafe"
  output_resolved="$(realpath -e "$OUTPUT_DIR")"
  PREVIOUS_BUNDLE="$(realpath -e "$previous_path")"
  [[ "$PREVIOUS_BUNDLE" == "$output_resolved"/.bundle.* ]] \
    || die "previous runtime bundle resolves outside the managed directory"
fi

BUNDLE_DIR="$(mktemp -d "$OUTPUT_DIR/.bundle.XXXXXX")"
NEXT_LINK="$OUTPUT_DIR/.current.next"
trap 'rm -f -- "$NEXT_LINK"; if [[ -n "${BUNDLE_DIR:-}" && -d "$BUNDLE_DIR" && ! -e "$BUNDLE_DIR/.complete" ]]; then rm -rf -- "$BUNDLE_DIR"; fi' EXIT
chmod 700 "$BUNDLE_DIR"

get_parameter() {
  local leaf="$1"
  local value

  if ! value="$(aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$SSM_PREFIX/$leaf" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text 2>/dev/null)"; then
    die "required SSM parameter is missing or unreadable: $SSM_PREFIX/$leaf"
  fi
  [[ -n "$value" && "$value" != "None" ]] || die "required SSM parameter is empty: $SSM_PREFIX/$leaf"
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || die "SSM parameter must be a single-line value: $SSM_PREFIX/$leaf"
  printf '%s' "$value"
}

write_env() {
  local file="$1"
  local key="$2"
  local value="$3"
  local escaped="${value//\'/\\\'}"

  printf "%s='%s'\n" "$key" "$escaped" >> "$file"
}

MYSQL_DATABASE="$(get_parameter MYSQL_DATABASE)"
MYSQL_USER="$(get_parameter MYSQL_USER)"
MYSQL_PASSWORD="$(get_parameter MYSQL_PASSWORD)"
MYSQL_ROOT_PASSWORD="$(get_parameter MYSQL_ROOT_PASSWORD)"
SUBSCRIPTION_AUTOMATION_ENABLED="$(get_parameter PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED)"
SUBSCRIPTION_AUTOMATION_BATCH_SIZE="$(get_parameter PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE)"
SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS="$(get_parameter PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS)"

[[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]{1,64}$ ]] || die "MYSQL_DATABASE has an unsafe identifier shape"
[[ "$MYSQL_USER" =~ ^[A-Za-z0-9_]{1,32}$ ]] || die "MYSQL_USER has an unsafe identifier shape"

[[ "$SUBSCRIPTION_AUTOMATION_ENABLED" == "true" \
  || "$SUBSCRIPTION_AUTOMATION_ENABLED" == "false" ]] \
  || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED must be exactly true or false"
[[ "$SUBSCRIPTION_AUTOMATION_BATCH_SIZE" =~ ^[1-9][0-9]*$ ]] \
  || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE must be a positive integer"
[[ "$SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS" =~ ^[1-9][0-9]*$ ]] \
  || die "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS must be a positive integer"

MYSQL_ENV="$BUNDLE_DIR/mysql.env"
BACKEND_ENV="$BUNDLE_DIR/backend.env"

write_env "$MYSQL_ENV" MYSQL_DATABASE "$MYSQL_DATABASE"
write_env "$MYSQL_ENV" MYSQL_USER "$MYSQL_USER"
write_env "$MYSQL_ENV" MYSQL_PASSWORD "$MYSQL_PASSWORD"
write_env "$MYSQL_ENV" MYSQL_ROOT_PASSWORD "$MYSQL_ROOT_PASSWORD"

if [[ "$DATASOURCE_SSL_MODE" == "DISABLED" ]]; then
  JDBC_URL="jdbc:mysql://${DATASOURCE_HOST}:${DATASOURCE_PORT}/${MYSQL_DATABASE}?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
else
  JDBC_URL="jdbc:mysql://${DATASOURCE_HOST}:${DATASOURCE_PORT}/${MYSQL_DATABASE}?sslMode=REQUIRED&serverTimezone=UTC"
fi
write_env "$BACKEND_ENV" PAWCYCLE_DATASOURCE_HOST "$DATASOURCE_HOST"
write_env "$BACKEND_ENV" PAWCYCLE_DATASOURCE_PORT "$DATASOURCE_PORT"
write_env "$BACKEND_ENV" PAWCYCLE_DATASOURCE_SSL_MODE "$DATASOURCE_SSL_MODE"
write_env "$BACKEND_ENV" SPRING_DATASOURCE_URL "$JDBC_URL"
write_env "$BACKEND_ENV" SPRING_DATASOURCE_USERNAME "$MYSQL_USER"
write_env "$BACKEND_ENV" SPRING_DATASOURCE_PASSWORD "$MYSQL_PASSWORD"
write_env "$BACKEND_ENV" PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED "$SUBSCRIPTION_AUTOMATION_ENABLED"
write_env "$BACKEND_ENV" PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE "$SUBSCRIPTION_AUTOMATION_BATCH_SIZE"
write_env "$BACKEND_ENV" PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS "$SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"

chmod 600 "$MYSQL_ENV" "$BACKEND_ENV"
printf 'OPS-010 runtime bundle complete\n' > "$BUNDLE_DIR/.complete"
chmod 600 "$BUNDLE_DIR/.complete"

ln -s "$(basename -- "$BUNDLE_DIR")" "$NEXT_LINK"
mv -Tf "$NEXT_LINK" "$OUTPUT_DIR/current"
BUNDLE_DIR=""

if [[ -n "$PREVIOUS_BUNDLE" ]]; then
  rm -rf -- "$PREVIOUS_BUNDLE" \
    || die "new runtime bundle is active, but the previous bundle cleanup failed"
fi

trap - EXIT
flock --unlock 9
exec 9>&-
printf 'Materialized required runtime files under %s/current\n' "$OUTPUT_DIR"
