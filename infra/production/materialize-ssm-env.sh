#!/usr/bin/env bash

set -Eeuo pipefail

case "$-" in
  *x*) set +x ;;
esac

usage() {
  cat <<'EOF'
Usage: materialize-ssm-env.sh --ssm-prefix /path/prefix --output-dir /opt/pawcycle/runtime [--region ap-northeast-2]
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

SSM_PREFIX=""
OUTPUT_DIR=""
AWS_REGION="ap-northeast-2"

while (( $# > 0 )); do
  case "$1" in
    --ssm-prefix) SSM_PREFIX="${2:-}"; shift 2 ;;
    --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

[[ "$SSM_PREFIX" == /* && "$SSM_PREFIX" != "/" ]] || die "SSM prefix must be an absolute non-root parameter path"
SSM_PREFIX="${SSM_PREFIX%/}"
[[ "$OUTPUT_DIR" == /* && "$OUTPUT_DIR" != "/" ]] || die "output directory must be an absolute directory other than /"
[[ "$AWS_REGION" == "ap-northeast-2" ]] || die "approved region is ap-northeast-2"
command -v aws >/dev/null 2>&1 || die "AWS CLI is required"
command -v flock >/dev/null 2>&1 || die "flock is required"
command -v realpath >/dev/null 2>&1 || die "realpath is required"

umask 077
install -d -m 700 "$OUTPUT_DIR"
exec 9>"$OUTPUT_DIR/.materialize.lock"
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

MYSQL_ENV="$BUNDLE_DIR/mysql.env"
BACKEND_ENV="$BUNDLE_DIR/backend.env"

write_env "$MYSQL_ENV" MYSQL_DATABASE "$MYSQL_DATABASE"
write_env "$MYSQL_ENV" MYSQL_USER "$MYSQL_USER"
write_env "$MYSQL_ENV" MYSQL_PASSWORD "$MYSQL_PASSWORD"
write_env "$MYSQL_ENV" MYSQL_ROOT_PASSWORD "$MYSQL_ROOT_PASSWORD"

write_env "$BACKEND_ENV" SPRING_DATASOURCE_URL "jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
write_env "$BACKEND_ENV" SPRING_DATASOURCE_USERNAME "$MYSQL_USER"
write_env "$BACKEND_ENV" SPRING_DATASOURCE_PASSWORD "$MYSQL_PASSWORD"

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
