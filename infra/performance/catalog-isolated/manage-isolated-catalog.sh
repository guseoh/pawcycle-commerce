#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_NAME='pawcycle-performance-catalog'
SCOPE_LABEL='catalog-isolated'
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_SOURCE_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd -P)"
COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
VALIDATOR="$SCRIPT_DIR/validate-isolated-catalog.py"

usage() {
  cat >&2 <<'EOF'
Usage:
  sudo bash manage-isolated-catalog.sh ACTION \
    --source-root /absolute/path/to/<APPROVED_SHA> \
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

cleanup_secret() {
  unset PAWCYCLE_PERF_DB_PASSWORD password
}

[[ "${EUID:-$(id -u)}" -eq 0 ]] || die 'root privileges are required'

action="${1:-}"
[[ -n "$action" ]] || usage
shift

source_root=''
config_file=''
password_file=''
dataset_dir=''
acknowledgement=''

while (($#)); do
  case "$1" in
    --source-root)
      source_root="${2:-}"
      shift 2
      ;;
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

read_config_value() {
  local key="$1"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      value = substr($0, length(key) + 2)
      count++
    }
    END {
      if (count != 1) exit 1
      print value
    }
  ' "$config_file"
}

command -v docker >/dev/null 2>&1 || die 'docker is required'
docker compose version >/dev/null 2>&1 || die 'docker compose is required'

if [[ "$action" == 'down' ]]; then
  [[ -n "$config_file" && "$config_file" == /* ]] || die 'down requires an absolute config path'
  [[ -f "$config_file" && ! -L "$config_file" ]] || die 'down config file must be a regular non-symlink file'
  [[ "$(stat -c '%u' "$config_file")" == '0' ]] || die 'down config file must be root-owned'
  [[ "$(stat -c '%a' "$config_file")" == '600' ]] || die 'down config file mode must be 0600'
else
  for value in "$source_root" "$config_file" "$password_file" "$dataset_dir"; do
    [[ -n "$value" && "$value" == /* ]]     || die 'source/config/password/dataset paths must be absolute'
  done

  source_root="$(cd -- "$source_root" && pwd -P)"
  [[ "$SCRIPT_SOURCE_ROOT" == "$source_root" ]]   || die 'manage-isolated-catalog.sh must run from the approved source root'

  command -v python3 >/dev/null 2>&1 || die 'python3 is required'
fi

dataset_id="$(read_config_value PAWCYCLE_PERF_DATASET_ID)" || die 'unable to read exactly one dataset identity from config'

case "$dataset_id" in
  catalog-core-control-v1)
    cleanup_schema='pawcycle_perf_core_control'
    ;;
  catalog-core-10k-v1)
    cleanup_schema='pawcycle_perf_core_10k'
    ;;
  *)
    die "unsupported performance dataset: $dataset_id"
    ;;
esac

if [[ "$action" != 'down' ]]; then
  python3 "$VALIDATOR"   --source-root "$source_root"   --config-file "$config_file"   --password-file "$password_file"   --dataset-dir "$dataset_dir"

  host_port="$(read_config_value PAWCYCLE_PERF_HOST_PORT)"
  backend_image="$(read_config_value PAWCYCLE_PERF_BACKEND_IMAGE)"
  manifest_path="$dataset_dir/manifest.json"
  password="$(cat "$password_file")"

  export PAWCYCLE_PERF_DB_PASSWORD="$password"
  export PAWCYCLE_PERF_MANIFEST_PATH="$manifest_path"
  export PAWCYCLE_PERF_IMPORT_OPERATION='validate'
  trap cleanup_secret EXIT
fi

compose() {
  docker compose     --project-name "$PROJECT_NAME"     --env-file "$config_file"     -f "$COMPOSE_FILE"     "$@"
}

assert_existing_project_identity() {
  local ids id scope dataset
  ids="$(docker ps -a     --filter "label=com.docker.compose.project=$PROJECT_NAME"     --format '{{.ID}}')"
  [[ -n "$ids" ]] || return 0

  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    scope="$(docker inspect -f '{{ index .Config.Labels "com.pawcycle.performance.scope" }}' "$id")"
    dataset="$(docker inspect -f '{{ index .Config.Labels "com.pawcycle.performance.dataset" }}' "$id")"
    [[ "$scope" == "$SCOPE_LABEL" ]] || die "unexpected container scope for $id"
    [[ "$dataset" == "$dataset_id" ]] || die "existing performance container belongs to dataset $dataset"
  done <<<"$ids"
}

if [[ "$action" == 'down' ]]; then
  [[ "$acknowledgement" == "DOWN:$PROJECT_NAME" ]]       || die "runtime cleanup requires --acknowledge DOWN:$PROJECT_NAME"
  assert_existing_project_identity

  # Cleanup must remain available when runtime inputs are damaged. These are
  # interpolation-only sentinels; no image is inspected and no DB secret is read.
  env \
    PAWCYCLE_PERF_BACKEND_IMAGE='cleanup.invalid/unused@sha256:0000000000000000000000000000000000000000000000000000000000000000' \
    PAWCYCLE_PERF_DB_PASSWORD='cleanup-only-unused' \
    PAWCYCLE_PERF_DB_URL="jdbc:mysql://cleanup.invalid:3306/$cleanup_schema?sslMode=REQUIRED" \
    PAWCYCLE_PERF_DB_USERNAME='pawcycle_perf_catalog' \
    PAWCYCLE_PERF_HOST_PORT='65535' \
    PAWCYCLE_PERF_IMPORT_OPERATION='validate' \
    PAWCYCLE_PERF_MANIFEST_PATH='/dev/null' \
    PAWCYCLE_PERF_SCHEMA="$cleanup_schema" \
    docker compose --project-name "$PROJECT_NAME" --env-file "$config_file" -f "$COMPOSE_FILE" down --remove-orphans
  printf 'catalog_isolated_runtime=PASS action=down project=%s\n' "$PROJECT_NAME"
  exit 0
fi

compose config >/dev/null

assert_local_backend_image() {
  local expected_digest os_arch
  expected_digest="${backend_image##*@}"

  docker image inspect "$backend_image" >/dev/null 2>&1     || die 'approved Backend image is not present locally; verify and pull it explicitly before retrying'

  os_arch="$(docker image inspect "$backend_image" --format '{{.Os}}/{{.Architecture}}')"
  [[ "$os_arch" == 'linux/amd64' ]]     || die "approved Backend image platform must be linux/amd64 (actual=$os_arch)"

  docker image inspect "$backend_image"     --format '{{range .RepoDigests}}{{println .}}{{end}}' |     grep -Fq "@$expected_digest"     || die 'local Backend image RepoDigest does not match the approved digest'
}

backend_running() {
  [[ -n "$(compose ps --status running -q backend 2>/dev/null || true)" ]]
}

run_import() {
  local operation="$1"
  PAWCYCLE_PERF_IMPORT_OPERATION="$operation"     compose --profile tools run --rm --no-deps --pull never catalog-import
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
    assert_local_backend_image
    assert_existing_project_identity
    printf 'catalog_isolated_runtime=PASS action=preflight dataset=%s\n' "$dataset_id"
    ;;

  import-validate)
    assert_local_backend_image
    assert_existing_project_identity
    backend_running && die 'stop the isolated backend before catalog import'
    run_import validate
    printf 'catalog_isolated_runtime=PASS action=import-validate dataset=%s\n' "$dataset_id"
    ;;

  import-apply)
    [[ "$acknowledgement" == "APPLY:$dataset_id" ]]       || die "import apply requires --acknowledge APPLY:$dataset_id"
    assert_local_backend_image
    assert_existing_project_identity
    backend_running && die 'stop the isolated backend before catalog import'
    run_import validate
    run_import apply
    printf 'catalog_isolated_runtime=PASS action=import-apply dataset=%s\n' "$dataset_id"
    ;;

  up)
    [[ "$acknowledgement" == "START:$dataset_id" ]]       || die "runtime start requires --acknowledge START:$dataset_id"
    command -v curl >/dev/null 2>&1 || die 'curl is required'
    assert_local_backend_image
    assert_existing_project_identity
    backend_running && die 'isolated backend is already running'

    startup_complete=0
    startup_exit() {
      local rc=$?
      trap - EXIT
      if [[ "$startup_complete" -eq 0 ]]; then
        compose down --remove-orphans >/dev/null 2>&1 || true
      fi
      cleanup_secret
      exit "$rc"
    }
    trap startup_exit EXIT

    compose up -d --pull never backend
    wait_for_backend
    curl --fail --silent --show-error       "http://127.0.0.1:$host_port/actuator/health/readiness" >/dev/null
    curl --fail --silent --show-error       "http://127.0.0.1:$host_port/api/products" >/dev/null

    startup_complete=1
    trap cleanup_secret EXIT
    printf 'catalog_isolated_runtime=PASS action=up dataset=%s endpoint=http://127.0.0.1:%s\n'       "$dataset_id" "$host_port"
    ;;

  status)
    assert_existing_project_identity
    compose ps
    ;;

  *)
    usage
    ;;
esac
