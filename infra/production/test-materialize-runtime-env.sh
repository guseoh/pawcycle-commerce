#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
SOURCE_FILE="$TEST_ROOT/source.env"
OUTPUT_DIR="$TEST_ROOT/runtime"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

write_source() {
  local host="$1"
  local port="${2:-3306}"
  local ssl="${3:-REQUIRED}"
  local extra="${4:-}"
  {
    printf "PAWCYCLE_DATASOURCE_HOST='%s'\n" "$host"
    printf "PAWCYCLE_DATASOURCE_PORT='%s'\n" "$port"
    printf "PAWCYCLE_DATASOURCE_DATABASE='pawcycle'\n"
    printf "PAWCYCLE_DATASOURCE_SSL_MODE='%s'\n" "$ssl"
    printf "SPRING_DATASOURCE_USERNAME='pawcycle_app'\n"
    printf "SPRING_DATASOURCE_PASSWORD='application-password'\n"
    printf "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'\n"
    printf "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='25'\n"
    printf "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='60000'\n"
    printf '%b' "$extra"
  } > "$SOURCE_FILE"
  chmod 600 "$SOURCE_FILE"
}

run_materialize() {
  "$SCRIPT_DIR/materialize-runtime-env.sh" --source-file "$SOURCE_FILE" --output-dir "$OUTPUT_DIR" >/dev/null
}

write_source db.example.com
run_materialize
[[ -L "$OUTPUT_DIR/current" ]]
BUNDLE="$(readlink -f "$OUTPUT_DIR/current")"
[[ "$(stat -c '%a' "$OUTPUT_DIR")" == 700 ]]
[[ "$(stat -c '%a' "$BUNDLE")" == 700 ]]
[[ "$(stat -c '%a' "$BUNDLE/backend.env")" == 600 ]]
[[ "$(stat -c '%a' "$BUNDLE/.complete")" == 600 ]]
[[ "$(stat -c '%a' "$OUTPUT_DIR/.materialize.lock")" == 600 ]]
[[ "$(find "$BUNDLE" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort | tr '\n' ' ')" == '.complete backend.env ' ]]
grep -Fxq "PAWCYCLE_DATASOURCE_HOST='db.example.com'" "$BUNDLE/backend.env"
grep -Fxq "PAWCYCLE_DATASOURCE_PORT='3306'" "$BUNDLE/backend.env"
grep -Fxq "PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'" "$BUNDLE/backend.env"
grep -Fxq "SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'" "$BUNDLE/backend.env"
! grep -Eq 'MYSQL_ROOT_PASSWORD|mysql\.env|allowPublicKeyRetrieval|sslMode=DISABLED' "$BUNDLE/backend.env"

PREVIOUS="$(readlink "$OUTPUT_DIR/current")"
write_source 10.20.30.40
run_materialize
[[ "$(readlink "$OUTPUT_DIR/current")" != "$PREVIOUS" ]]
grep -Fxq "PAWCYCLE_DATASOURCE_HOST='10.20.30.40'" "$(readlink -f "$OUTPUT_DIR/current")/backend.env"

for invalid in mysql localhost 127.0.0.1 8.8.8.8; do
  write_source "$invalid"
  if run_materialize; then
    printf 'invalid host was accepted: %s\n' "$invalid" >&2
    exit 1
  fi
done
write_source 10.20.30.40 3307
if run_materialize; then printf 'invalid port was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40 3306 DISABLED
if run_materialize; then printf 'DISABLED SSL mode was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40 3306 REQUIRED "UNKNOWN='value'\n"
if run_materialize; then printf 'unknown source key was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40
sed -i "/PAWCYCLE_DATASOURCE_PORT/a PAWCYCLE_DATASOURCE_PORT='3306'" "$SOURCE_FILE"
if run_materialize; then printf 'duplicate source key was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40
sed -i "s/SPRING_DATASOURCE_PASSWORD='application-password'/SPRING_DATASOURCE_PASSWORD='broken'quote'/" "$SOURCE_FILE"
if run_materialize; then printf 'malformed quoted value was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40
sed -i "/PAWCYCLE_DATASOURCE_DATABASE/d" "$SOURCE_FILE"
if run_materialize; then printf 'missing source key was accepted\n' >&2; exit 1; fi
write_source 10.20.30.40
ln -s "$SOURCE_FILE" "$TEST_ROOT/source-link"
SOURCE_FILE="$TEST_ROOT/source-link"
if run_materialize; then printf 'symlink source was accepted\n' >&2; exit 1; fi
SOURCE_FILE="$TEST_ROOT/source.env"
write_source 10.20.30.40
chmod 640 "$SOURCE_FILE"
if run_materialize; then printf 'non-600 source was accepted\n' >&2; exit 1; fi
chmod 600 "$SOURCE_FILE"

(
  exec 9>"$OUTPUT_DIR/.materialize.lock"
  flock -n 9
  sleep 2
) &
LOCK_HOLDER=$!
sleep 0.1
if run_materialize; then printf 'concurrent materialization was accepted\n' >&2; exit 1; fi
wait "$LOCK_HOLDER"

printf 'Runtime materializer positive, atomic, validation, and concurrency tests passed\n'
