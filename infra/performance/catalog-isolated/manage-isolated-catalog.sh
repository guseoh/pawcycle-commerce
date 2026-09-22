#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_NAME='pawcycle-performance-catalog'
SCOPE_LABEL='catalog-isolated'
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
VALIDATOR="$SCRIPT_DIR/validate-isolated-catalog.py"

usage() {
  cat >&2 <<'EOF'
Usage:
  sudo bash manage-isolated-catalog.sh ACTION \
    --config-file /absolute/path/DATASET.env \
    --password-file /absolute/path/db-password \
    --dataset-dir /absolute/path/DATASET \
    [--acknowledge VALUE]

Actions:
  preflight
  import-validate
  import-apply   --acknowledge APPLY:DATASET_ID
  up             --acknowledge START:DATASET_ID
  status
  down           --acknowledge DOWN:pawcycle-performance-catalog
EOF
  exit 64
}

die() {
  printf 'catalog_isolated_runtime=FAIL reason=%s\n' "$*" >&2
  exit 1
}

[[ "${EUID:-$(id -u)}" -eq 0 ]] || die 'root privileges are required'

action="${1:-}"
[[ -n "$action" ]] || usage
shift

config_file=''
password_file=''
dataset_dir=''
acknowledgement=''

while (($#)); do
  case "$1" in
    --config-file)
      config_file="${2:-}"
      shift 2
      ;;
    --password-file)
      password_file="${2:-}"
      shift 2
      ;;
    --dataset-dir)
      dataset_dir="${2:-}"
      shift 2
      ;;
    --acknowledge)
      acknowledgement="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      usage
      ;;
  esac
done

for value in "$config_file" "$password_file" "$dataset_dir"; do
  [[ -n "$value" && "$value" == /* ]] || die 'config/password/dataset paths must be absolute'
done

command -v python3 >/dev/null 2>&1 || die 'python3 is required'
command -v docker >/dev/null 2>&1 || die 'docker is required'
docker compose version >/dev/null 2>&1 || die 'docker compose is required'

python3 "$VALIDATOR" \
  --config-file "$config_file" \
  --password-file "$password_file" \
  --dataset-dir "$dataset_dir"

read_config_value() {
  local key="$1"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      print substr($0, length(key) + 2)
      found = 1
    }
    END { if (!found) exit 1 }
  ' "$config_file"
}

dataset_id="$(read_config_value PAWCYCLE_PERF_DATASET_ID)"
host_port="$(read_config_value PAWCYCLE_PERF_HOST_PORT)"
manifest_path="$dataset_dir/manifest.json"
password="$(cat "$password_file")"

export PAWCYCLE_PERF_DB_PASSWORD="$password"
export PAWCYCLE_PERF_MANIFEST_PATH="$manifest_path"
export PAWCYCLE_PERF_IMPORT_OPERATION='validate'
trap 'unset PAWCYCLE_PERF_DB_PASSWORD password' EXIT

compose() {
  docker compose \
    --project-name "$PROJECT_NAME" \
    --env-file "$config_file" \
    -f "$COMPOSE_FILE" \
    "$@"
}

compose config >/dev/null

assert_existing_project_identity() {
  local ids id scope dataset
  ids="$(docker ps -a \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --format '{{.ID}}')"
  [[ -n "$ids" ]] || return 0

  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    scope="$(docker inspect -f '{{ index .Config.Labels "com.pawcycle.performance.scope" }}' "$id")"
    dataset="$(docker inspect -f '{{ index .Config.Labels "com.pawcycle.performance.dataset" }}' "$id")"
    [[ "$scope" == "$SCOPE_LABEL" ]] || die "unexpected container scope for $id"
    [[ "$dataset" == "$dataset_id" ]] || die "existing performance container belongs to dataset $dataset"
  done <<<"$ids"
}

backend_running() {
  [[ -n "$(compose ps --status running -q backend 2>/dev/null || true)" ]]
}

run_import() {
  local operation="$1"
  PAWCYCLE_PERF_IMPORT_OPERATION="$operation" \
    compose --profile tools run --rm --no-deps catalog-import
}

wait_for_backend() {
  local cid state i
  cid="$(compose ps -q backend)"
  [[ -n "$cid" ]] || die 'isolated backend container was not created'

  for i in $(seq 1 60); do
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")"
    case "$state" in
      healthy)
        return 0
        ;;
      unhealthy|exited|dead)
        die "isolated backend entered state $state"
        ;;
    esac
    sleep 2
  done
  die 'isolated backend did not become healthy within 120 seconds'
}

case "$action" in
  preflight)
    assert_existing_project_identity
    printf 'catalog_isolated_runtime=PASS action=preflight dataset=%s\n' "$dataset_id"
    ;;

  import-validate)
    assert_existing_project_identity
    backend_running && die 'stop the isolated backend before catalog import'
    run_import validate
    printf 'catalog_isolated_runtime=PASS action=import-validate dataset=%s\n' "$dataset_id"
    ;;

  import-apply)
    [[ "$acknowledgement" == "APPLY:$dataset_id" ]] \
      || die "import apply requires --acknowledge APPLY:$dataset_id"
    assert_existing_project_identity
    backend_running && die 'stop the isolated backend before catalog import'
    run_import validate
    run_import apply
    printf 'catalog_isolated_runtime=PASS action=import-apply dataset=%s\n' "$dataset_id"
    ;;

  up)
    [[ "$acknowledgement" == "START:$dataset_id" ]] \
      || die "runtime start requires --acknowledge START:$dataset_id"
    assert_existing_project_identity
    compose up -d backend
    wait_for_backend
    command -v curl >/dev/null 2>&1 || die 'curl is required'
    curl --fail --silent --show-error \
      "http://127.0.0.1:$host_port/actuator/health/readiness" >/dev/null
    curl --fail --silent --show-error \
      "http://127.0.0.1:$host_port/api/products" >/dev/null
    printf 'catalog_isolated_runtime=PASS action=up dataset=%s endpoint=http://127.0.0.1:%s\n' \
      "$dataset_id" "$host_port"
    ;;

  status)
    assert_existing_project_identity
    compose ps
    ;;

  down)
    [[ "$acknowledgement" == "DOWN:$PROJECT_NAME" ]] \
      || die "runtime cleanup requires --acknowledge DOWN:$PROJECT_NAME"
    assert_existing_project_identity
    compose down --remove-orphans
    printf 'catalog_isolated_runtime=PASS action=down project=%s\n' "$PROJECT_NAME"
    ;;

  *)
    usage
    ;;
esac
