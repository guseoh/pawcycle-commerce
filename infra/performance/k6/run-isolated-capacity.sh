#!/usr/bin/env bash
set -Eeuo pipefail

k6_pid=''
collector_pid=''

job_is_running() {
  local target_pid="$1" job_pid
  while IFS= read -r job_pid; do
    [[ "$job_pid" == "$target_pid" ]] && return 0
  done < <(jobs -pr)
  return 1
}

stop_and_reap() {
  local child_pid="$1"
  [[ "$child_pid" =~ ^[0-9]+$ ]] || return 0
  if job_is_running "$child_pid"; then
    kill -TERM "$child_pid" 2>/dev/null || true
  fi
  wait "$child_pid" 2>/dev/null || true
}

cleanup_children() {
  local exit_status=$?
  trap - EXIT INT TERM HUP
  if [[ -n "$k6_pid" ]]; then
    stop_and_reap "$k6_pid"
    k6_pid=''
  fi
  if [[ -n "$collector_pid" ]]; then
    stop_and_reap "$collector_pid"
    collector_pid=''
  fi
  exit "$exit_status"
}

trap cleanup_children EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

usage() {
  cat >&2 <<'EOF'
Usage:
  bash run-isolated-capacity.sh \
    --source-root /absolute/path/to/<APPROVED_SHA> \
    --target-url http://127.0.0.1:PORT \
    --dataset-id catalog-core-control-v1|catalog-core-10k-v1 \
    --results-dir /absolute/path \
    --evidence-ssh-target SSH_ALIAS --isolated-host-port PORT \
    --acknowledge-isolated-load YES
EOF
  exit 64
}

source_root=''
target_url=''
dataset_id=''
results_dir=''
acknowledgement=''
evidence_ssh_target=''
isolated_host_port=''

while (($#)); do
  case "$1" in
    --source-root)
      source_root="${2:-}"
      shift 2
      ;;
    --target-url)
      target_url="${2:-}"
      shift 2
      ;;
    --dataset-id)
      dataset_id="${2:-}"
      shift 2
      ;;
    --results-dir)
      results_dir="${2:-}"
      shift 2
      ;;
    --evidence-ssh-target)
      evidence_ssh_target="${2:-}"
      shift 2
      ;;
    --isolated-host-port)
      isolated_host_port="${2:-}"
      shift 2
      ;;
    --acknowledge-isolated-load)
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

[[ "$source_root" == /* ]] || usage
[[ "$target_url" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]{1,5})?/?$ ]] || usage
case "$dataset_id" in
  catalog-core-control-v1|catalog-core-10k-v1) ;;
  *) usage ;;
esac
[[ "$results_dir" == /* ]] || usage
[[ "$acknowledgement" == 'YES' ]] || usage
[[ -n "$evidence_ssh_target" && -n "$isolated_host_port" ]] || usage
[[ "$evidence_ssh_target" =~ ^[a-zA-Z0-9][a-zA-Z0-9._@-]*$ ]] || usage
[[ "$isolated_host_port" =~ ^[0-9]{1,5}$ ]] || usage
((10#$isolated_host_port >= 1 && 10#$isolated_host_port <= 65535)) || usage
command -v ssh >/dev/null 2>&1 || { printf 'ssh is required for evidence collection\n' >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { printf 'python3 is required for evidence collection\n' >&2; exit 1; }
command -v oci >/dev/null 2>&1 || { printf 'OCI CLI is required for evidence collection\n' >&2; exit 1; }
[[ "${PAWCYCLE_PERF_OCI_COMPARTMENT_ID:-}" =~ ^ocid1\.compartment\.[a-zA-Z0-9._-]+$ ]] || {
  printf 'OCI Monitoring compartment identity is required in the environment\n' >&2
  exit 1
}
[[ "${PAWCYCLE_PERF_OCI_DB_SYSTEM_ID:-}" =~ ^ocid1\.mysqldbsystem\.[a-zA-Z0-9._-]+$ ]] || {
  printf 'OCI Monitoring DB System identity is required in the environment\n' >&2
  exit 1
}

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
script_source_root="$(cd -- "$script_dir/../../.." && pwd -P)"
source_root="$(cd -- "$source_root" && pwd -P)"

[[ "$script_source_root" == "$source_root" ]] || {
  printf 'k6 runner must run from the approved source root\n' >&2
  exit 1
}
[[ -f "$source_root/.approved-sha" && ! -L "$source_root/.approved-sha" ]] || {
  printf 'approved source marker is missing or invalid\n' >&2
  exit 1
}
approved_sha="$(tr -d '\r\n' <"$source_root/.approved-sha")"
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'approved source marker must contain a 40-character lowercase SHA\n' >&2
  exit 1
}
[[ "$(basename -- "$source_root")" == "$approved_sha" ]] || {
  printf 'approved source directory does not match marker SHA\n' >&2
  exit 1
}

command -v k6 >/dev/null 2>&1 || {
  printf 'k6 is required\n' >&2
  exit 1
}

if [[ -e "$results_dir" ]] && [[ -n "$(find "$results_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'results directory must be empty: %s\n' "$results_dir" >&2
  exit 1
fi
mkdir -p "$results_dir"

for target_rps in 25 50 100 150 200 250; do
  host_samples="$results_dir/$dataset_id-${target_rps}rps-host.jsonl"
  remote_collector="/opt/pawcycle/performance-source/$approved_sha/infra/performance/catalog-isolated/collect-stage-evidence.py"
  ssh -o BatchMode=yes "$evidence_ssh_target" \
    sudo -n python3 "$remote_collector" sample --port "$isolated_host_port" \
    --duration-seconds 165 >"$host_samples" &
  collector_pid=$!
  for _ in {1..10}; do
    [[ -s "$host_samples" ]] && break
    job_is_running "$collector_pid" || break
    sleep 1
  done
  if ! job_is_running "$collector_pid" || [[ ! -s "$host_samples" ]]; then
    printf 'Host evidence collector did not start\n' >&2
    exit 1
  fi

  k6_ok=true
  k6_args=(run
    -e "BASE_URL=$target_url"
    -e "ISOLATED_DATASET_ID=$dataset_id"
    -e "ISOLATED_LOAD_ACKNOWLEDGEMENT=$acknowledgement"
    -e "TARGET_RPS=$target_rps"
    -e "RESULTS_DIR=$results_dir"
    "$script_dir/isolated-capacity-api-products.js")
  k6 "${k6_args[@]}" &
  k6_pid=$!
  while job_is_running "$k6_pid"; do
    if ! job_is_running "$collector_pid"; then
      stop_and_reap "$k6_pid"
      k6_pid=''
      wait "$collector_pid" || true
      collector_pid=''
      printf 'Host evidence collector stopped during load\n' >&2
      exit 1
    fi
    sleep 2
  done
  wait "$k6_pid" || k6_ok=false
  k6_pid=''
  if ! wait "$collector_pid"; then
    collector_pid=''
    printf 'Host evidence collector failed; stop before next stage\n' >&2
    exit 1
  fi
  collector_pid=''
  python3 "$source_root/infra/performance/catalog-isolated/collect-stage-evidence.py" assemble \
    --summary "$results_dir/$dataset_id-${target_rps}rps.json" \
    --host-samples "$host_samples" \
    --output "$results_dir/$dataset_id-${target_rps}rps-evidence.json"
  if [[ "$k6_ok" != true ]]; then
    printf 'k6 stage failed; stop before next RPS\n' >&2
    exit 1
  fi
done
