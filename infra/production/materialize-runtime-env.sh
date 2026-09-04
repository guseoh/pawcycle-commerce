#!/usr/bin/env bash

set -Eeuo pipefail
set +x

OUTPUT_DIR=""
SOURCE_FILE=""
TEMP_BUNDLE=""
LOCK_FD=""

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  printf 'Usage: %s --source-file <absolute-path> --output-dir <absolute-path>\n' "${0##*/}" >&2
}

validate_absolute_path() {
  [[ "$1" == /* && "$1" != "/" ]] || die "$2 must be an absolute path other than /"
}

validate_source_file() {
  [[ -f "$SOURCE_FILE" && ! -L "$SOURCE_FILE" ]] || die "source file must be a regular non-symlink file"
  [[ "$(stat -c '%a' "$SOURCE_FILE")" == "600" ]] || die "source file mode must be 600"
  [[ "$(stat -c '%u' "$SOURCE_FILE")" == "$(id -u)" ]] || die "source file owner must match the executing UID"
}

validate_key_set() {
  local line key
  local canonical_re="^([A-Z][A-Z0-9_]*)='.*'$"
  local -A allowed=()
  local -A count=()
  for key in "$@"; do
    allowed["$key"]=1
    count["$key"]=0
  done
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ $canonical_re ]] || die "source file contains a malformed canonical setting"
    key="${BASH_REMATCH[1]}"
    [[ -n "${allowed[$key]:-}" ]] || die "source file contains an unknown setting"
    count["$key"]=$((count["$key"] + 1))
  done < "$SOURCE_FILE"
  for key in "$@"; do
    [[ "${count[$key]}" == 1 ]] || die "source file setting is missing or duplicated"
  done
}

read_setting() {
  local key="$1"
  local line value encoded
  local quote="'"
  local prefix="${key}=${quote}"
  while IFS= read -r line; do
    if [[ "$line" == "$key="* ]]; then
      value="${line#"$prefix"}"
      value="${value%"$quote"}"
      encoded="$value"
      while [[ "$encoded" == *"$quote"* ]]; do
        [[ "$encoded" == *"\\$quote"* ]] || die "source file contains an unescaped quote"
        encoded="${encoded//\\$quote/}"
      done
      value="${value//\\$quote/$quote}"
      [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || die "source file contains a control character"
      printf '%s' "$value"
      return 0
    fi
  done < "$SOURCE_FILE"
  die "source file setting is missing"
}

validate_datasource_host() {
  local host="$1"
  local octet
  local -a octets

  if [[ "$host" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
    IFS=. read -r -a octets <<< "$host"
    for octet in "${octets[@]}"; do
      (( octet <= 255 )) || die "datasource host is not a private IPv4 address"
    done
    if (( octets[0] == 10 )); then return 0; fi
    if (( octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31 )); then return 0; fi
    if (( octets[0] == 192 && octets[1] == 168 )); then return 0; fi
    die "datasource host is not a private IPv4 address"
  fi

  [[ ${#host} -le 253 && "$host" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] \
    || die "datasource host is not an approved DNS name"
}

escape_single_quotes() {
  local value="$1"
  local escaped=""
  local character

  while [[ -n "$value" ]]; do
    character="${value:0:1}"
    value="${value:1}"
    if [[ "$character" == "'" ]]; then
      escaped+="\\'"
    else
      escaped+="$character"
    fi
  done
  printf '%s' "$escaped"
}

write_setting() {
  printf "%s='%s'\n" "$1" "$(escape_single_quotes "$2")" >> "$TEMP_BUNDLE/backend.env"
}

cleanup() {
  if [[ -n "$TEMP_BUNDLE" && -d "$TEMP_BUNDLE" ]]; then
    rm -rf -- "$TEMP_BUNDLE"
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

while (($#)); do
  case "$1" in
    --source-file)
      [[ $# -gt 1 && -n "$2" ]] || { usage; die "--source-file requires a value"; }
      [[ -z "$SOURCE_FILE" ]] || die "--source-file cannot be repeated"
      SOURCE_FILE="$2"
      shift 2
      ;;
    --output-dir)
      [[ $# -gt 1 && -n "$2" ]] || { usage; die "--output-dir requires a value"; }
      [[ -z "$OUTPUT_DIR" ]] || die "--output-dir cannot be repeated"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      usage
      die "unknown argument"
      ;;
  esac
done

[[ -n "$SOURCE_FILE" && -n "$OUTPUT_DIR" ]] || { usage; die "both source and output paths are required"; }
validate_absolute_path "$SOURCE_FILE" "source file"
validate_absolute_path "$OUTPUT_DIR" "output directory"
validate_source_file

validate_key_set \
  PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_DATABASE \
  PAWCYCLE_DATASOURCE_SSL_MODE SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS

PAWCYCLE_DATASOURCE_HOST="$(read_setting PAWCYCLE_DATASOURCE_HOST)"
PAWCYCLE_DATASOURCE_PORT="$(read_setting PAWCYCLE_DATASOURCE_PORT)"
PAWCYCLE_DATASOURCE_DATABASE="$(read_setting PAWCYCLE_DATASOURCE_DATABASE)"
PAWCYCLE_DATASOURCE_SSL_MODE="$(read_setting PAWCYCLE_DATASOURCE_SSL_MODE)"
SPRING_DATASOURCE_USERNAME="$(read_setting SPRING_DATASOURCE_USERNAME)"
SPRING_DATASOURCE_PASSWORD="$(read_setting SPRING_DATASOURCE_PASSWORD)"
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED="$(read_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED)"
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE="$(read_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE)"
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS="$(read_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS)"

validate_datasource_host "$PAWCYCLE_DATASOURCE_HOST"
[[ "$PAWCYCLE_DATASOURCE_PORT" == 3306 ]] || die "datasource port must be exactly 3306"
[[ "$PAWCYCLE_DATASOURCE_SSL_MODE" == REQUIRED ]] || die "datasource SSL mode must be exactly REQUIRED"
[[ "$PAWCYCLE_DATASOURCE_DATABASE" =~ ^[A-Za-z0-9_]{1,64}$ ]] || die "datasource database identifier is invalid"
[[ -n "$SPRING_DATASOURCE_USERNAME" && "$SPRING_DATASOURCE_USERNAME" != *$'\n'* && "$SPRING_DATASOURCE_USERNAME" != *$'\r'* ]] || die "datasource username is invalid"
[[ -n "$SPRING_DATASOURCE_PASSWORD" && "$SPRING_DATASOURCE_PASSWORD" != *$'\n'* && "$SPRING_DATASOURCE_PASSWORD" != *$'\r'* ]] || die "datasource password is invalid"
[[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" == true || "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" == false ]] || die "subscription automation enabled value is invalid"
[[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE" =~ ^[1-9][0-9]*$ ]] || die "subscription automation batch size is invalid"
[[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS" =~ ^[1-9][0-9]*$ ]] || die "subscription automation delay is invalid"

SPRING_DATASOURCE_URL="jdbc:mysql://${PAWCYCLE_DATASOURCE_HOST}:3306/${PAWCYCLE_DATASOURCE_DATABASE}?sslMode=REQUIRED&serverTimezone=UTC"
if [[ -e "$OUTPUT_DIR" && ( -L "$OUTPUT_DIR" || ! -d "$OUTPUT_DIR" ) ]]; then
  die "output directory must be a non-symlink directory"
fi
install -d -m 700 "$OUTPUT_DIR"
[[ ! -L "$OUTPUT_DIR" && "$(stat -c '%a' "$OUTPUT_DIR")" == 700 ]] || die "output directory mode must be 700"
LOCK_PATH="$OUTPUT_DIR/.materialize.lock"
if [[ -e "$LOCK_PATH" && ( -L "$LOCK_PATH" || ! -f "$LOCK_PATH" ) ]]; then die "materialize lock must be a regular non-symlink file"; fi
touch "$LOCK_PATH"
chmod 600 "$LOCK_PATH"
exec {LOCK_FD}<"$LOCK_PATH"
flock --nonblock "$LOCK_FD" || die "runtime materialization is in progress"

TEMP_BUNDLE="$(mktemp -d "$OUTPUT_DIR/.bundle.XXXXXX")"
chmod 700 "$TEMP_BUNDLE"
touch "$TEMP_BUNDLE/backend.env"
chmod 600 "$TEMP_BUNDLE/backend.env"
write_setting PAWCYCLE_DATASOURCE_HOST "$PAWCYCLE_DATASOURCE_HOST"
write_setting PAWCYCLE_DATASOURCE_PORT "$PAWCYCLE_DATASOURCE_PORT"
write_setting PAWCYCLE_DATASOURCE_DATABASE "$PAWCYCLE_DATASOURCE_DATABASE"
write_setting PAWCYCLE_DATASOURCE_SSL_MODE "$PAWCYCLE_DATASOURCE_SSL_MODE"
write_setting SPRING_DATASOURCE_URL "$SPRING_DATASOURCE_URL"
write_setting SPRING_DATASOURCE_USERNAME "$SPRING_DATASOURCE_USERNAME"
write_setting SPRING_DATASOURCE_PASSWORD "$SPRING_DATASOURCE_PASSWORD"
write_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED"
write_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE"
write_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"
printf 'RUNTIME_ENV_FORMAT=1\n' > "$TEMP_BUNDLE/.complete"
chmod 600 "$TEMP_BUNDLE/.complete"

ln -s "$(basename "$TEMP_BUNDLE")" "$OUTPUT_DIR/.current.$$.tmp"
mv -Tf "$OUTPUT_DIR/.current.$$.tmp" "$OUTPUT_DIR/current"
TEMP_BUNDLE=""
printf 'Runtime environment materialized successfully\n'
