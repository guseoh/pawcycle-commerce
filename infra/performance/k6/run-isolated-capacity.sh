#!/usr/bin/env bash
set -Eeuo pipefail

k6_pid=''
collector_pid=''
gate_transport_file=''
k6_stdout=''
k6_stderr=''
k6_context=''
k6_summary=''
k6_host_samples=''
k6_target_rps=''
k6_started_at_utc=''
k6_finished_at_utc=''
k6_exit=''

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

write_k6_context() {
  [[ -n "$k6_context" ]] || return 0
  local summary_present=false host_samples_present=false
  [[ -s "$k6_summary" ]] && summary_present=true
  [[ -s "$k6_host_samples" ]] && host_samples_present=true
  {
    printf 'target_rps=%s\n' "$k6_target_rps"
    printf 'k6_started_at_utc=%s\n' "$k6_started_at_utc"
    printf 'k6_finished_at_utc=%s\n' "$k6_finished_at_utc"
    printf 'k6_exit=%s\n' "$k6_exit"
    printf 'summary_present=%s\n' "$summary_present"
    printf 'host_samples_present=%s\n' "$host_samples_present"
  } >"$k6_context"
}

cleanup_children() {
  local exit_status=$?
  trap - EXIT INT TERM HUP
  if [[ -n "$gate_transport_file" ]]; then
    rm -f -- "$gate_transport_file"
    gate_transport_file=''
  fi
  if [[ -n "$k6_pid" ]]; then
    if job_is_running "$k6_pid"; then
      kill -TERM "$k6_pid" 2>/dev/null || true
    fi
    if wait "$k6_pid"; then
      k6_exit=0
    else
      k6_exit=$?
    fi
    k6_pid=''
    k6_finished_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    write_k6_context
  fi
  if [[ -n "$collector_pid" ]]; then
    stop_and_reap "$collector_pid"
    collector_pid=''
  fi
  if [[ -n "$k6_context" ]]; then
    if [[ "$k6_exit" == 'running' ]]; then
      k6_exit='runner_interrupted'
    fi
    if [[ -n "$k6_started_at_utc" && -z "$k6_finished_at_utc" ]]; then
      k6_finished_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    fi
    write_k6_context
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
    [--evidence-ssh-executable SSH_EXECUTABLE] \
    [--evidence-ssh-identity-file IDENTITY_FILE] \
    [--python-executable PYTHON_EXECUTABLE] \
    [--target-rps 25|50|100|150|200|250] \
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
evidence_ssh_executable='ssh'
evidence_ssh_identity_file=''
python_executable='python3'
isolated_host_port=''
target_rates=(25 50 100 150 200 250)

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
    --evidence-ssh-executable)
      (($# >= 2)) && [[ -n "$2" ]] || usage
      evidence_ssh_executable="$2"
      shift 2
      ;;
    --evidence-ssh-identity-file)
      (($# >= 2)) && [[ -n "$2" ]] || usage
      evidence_ssh_identity_file="$2"
      shift 2
      ;;
    --isolated-host-port)
      isolated_host_port="${2:-}"
      shift 2
      ;;
    --python-executable)
      (($# >= 2)) && [[ -n "$2" ]] || usage
      python_executable="$2"
      shift 2
      ;;
    --target-rps)
      (($# >= 2)) || usage
      case "$2" in
        25|50|100|150|200|250) target_rates=("$2") ;;
        *) usage ;;
      esac
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
evidence_ssh_executable="$(command -v -- "$evidence_ssh_executable")" && \
  [[ -f "$evidence_ssh_executable" && -x "$evidence_ssh_executable" ]] || { printf 'ssh is required for evidence collection\n' >&2; exit 1; }
python_executable="$(command -v -- "$python_executable")" && \
  [[ -f "$python_executable" && -x "$python_executable" ]] || {
  printf 'Python 3 executable is required for evidence collection\n' >&2; exit 1;
}
# Execution aliases can exist on PATH without providing an interpreter.
python_major="$("$python_executable" -c 'import sys; print(sys.version_info.major)' 2>/dev/null)" && \
  [[ "${python_major%$'\r'}" == '3' ]] || {
  printf 'Python 3 interpreter preflight failed; select a usable --python-executable\n' >&2; exit 1;
}
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

# The desktop archive can have a different local path from the app01 archive.
source "$source_root/infra/performance/catalog-isolated/app01-paths.sh"
[[ "$PAWCYCLE_PERF_APP01_ROOT" == /* ]] || {
  printf 'app01 performance root must be absolute\n' >&2
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
"$python_executable" "$source_root/infra/performance/catalog-isolated/collect-stage-evidence.py" preflight
mkdir -p "$results_dir"

evidence_ssh_args=(-o BatchMode=yes)
if [[ -n "$evidence_ssh_identity_file" ]]; then
  evidence_ssh_args+=(-i "$evidence_ssh_identity_file")
fi

diagnostic_script="$source_root/infra/production/diagnose-backend-state.sh"
[[ -f "$diagnostic_script" && ! -L "$diagnostic_script" ]] || {
  printf 'approved Production diagnostic is missing or invalid\n' >&2
  exit 1
}
diagnostic_sha256="$(sha256sum "$diagnostic_script")"
diagnostic_sha256="${diagnostic_sha256%% *}"
[[ "$diagnostic_sha256" =~ ^[0-9a-f]{64}$ ]] || {
  printf 'approved Production diagnostic identity could not be verified\n' >&2
  exit 1
}

gate_has_exact_line() {
  local file="$1" expected="$2" count
  count="$(awk -v expected="$expected" '$0 == expected { count++ } END { print count + 0 }' "$file")"
  [[ "$count" == 1 ]]
}

extract_gate_section() {
  local input="$1" begin_marker="$2" end_marker="$3" output="$4"
  awk -v begin_marker="$begin_marker" -v end_marker="$end_marker" '
    $0 == begin_marker {
      if (inside || began) invalid = 1
      began = 1
      inside = 1
      next
    }
    $0 == end_marker {
      if (!inside || ended) invalid = 1
      inside = 0
      ended = 1
      next
    }
    inside { print }
    END {
      if (invalid || !began || !ended || inside) exit 1
    }
  ' "$input" >"$output"
}

run_safety_gate() {
  local target_rps="$1" phase="$2"
  local artifact_prefix="$results_dir/$dataset_id-${target_rps}rps-$phase"
  local production_artifact="$artifact_prefix-production-gate.txt"
  local observability_artifact="$artifact_prefix-observability-gate.txt"
  local context_artifact="$artifact_prefix-gate-context.txt"
  local started_at_utc finished_at_utc='pending'
  local source_exit='not-captured' production_exit='not-captured' observability_exit='not-captured'
  local production_read_exit='not-captured' observability_read_exit='not-captured'
  local transport_exit='not-run' status_line remote_phase
  local production_framing='FAIL' observability_framing='FAIL' gate_ok=true remote_command
  local remote_source_root="$PAWCYCLE_PERF_APP01_ROOT/source/$approved_sha"
  local remote_marker="$remote_source_root/.approved-sha"
  local remote_diagnostic="$remote_source_root/infra/production/diagnose-backend-state.sh"

  started_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  : >"$production_artifact"
  : >"$observability_artifact"
  {
    printf 'gate_phase=%s\n' "$phase"
    printf 'gate_started_at_utc=%s\n' "$started_at_utc"
    printf 'gate_finished_at_utc=pending\n'
    printf 'source_identity_exit=not-captured\n'
    printf 'production_diagnostic_exit=not-captured\n'
    printf 'observability_diagnostic_exit=not-captured\n'
    printf 'production_artifact_read_exit=not-captured\n'
    printf 'observability_artifact_read_exit=not-captured\n'
    printf 'ssh_transport_exit=not-run\n'
    printf 'artifact_framing=FAIL\n'
  } >"$context_artifact"

  printf -v remote_command 'bash -s -- %q %q %q %q %q' \
    "$remote_diagnostic" "$remote_marker" "$approved_sha" "$diagnostic_sha256" "$phase"
  gate_transport_file="$(mktemp)"
  if MSYS2_ARG_CONV_EXCL='*' "$evidence_ssh_executable" "${evidence_ssh_args[@]}" \
    "$evidence_ssh_target" "$remote_command" >"$gate_transport_file" <<'REMOTE_GATE_SCRIPT'
set -u

diagnostic="$1"
marker_file="$2"
expected_source_sha="$3"
expected_diagnostic_sha="$4"
phase="$5"
source_exit=1
production_exit=125
observability_exit=125
production_read_exit=0
observability_read_exit=0
temporary_directory=''
production_result=''
observability_result=''

cleanup_gate_files() {
  if [[ -n "$temporary_directory" ]]; then
    rm -f -- "$production_result" "$observability_result"
    rmdir -- "$temporary_directory" 2>/dev/null || true
  fi
}
trap cleanup_gate_files EXIT

marker_value=''
actual_diagnostic_sha=''
if [[ "$expected_source_sha" =~ ^[0-9a-f]{40}$ &&
      "$expected_diagnostic_sha" =~ ^[0-9a-f]{64}$ &&
      -f "$marker_file" && ! -L "$marker_file" ]] &&
   marker_value="$(cat -- "$marker_file" 2>/dev/null)" &&
   [[ "$marker_value" == "$expected_source_sha" && -f "$diagnostic" && ! -L "$diagnostic" ]] &&
   actual_diagnostic_sha="$(sha256sum "$diagnostic" 2>/dev/null)"; then
  actual_diagnostic_sha="${actual_diagnostic_sha%% *}"
  if [[ "$actual_diagnostic_sha" == "$expected_diagnostic_sha" ]]; then
    source_exit=0
  fi
fi

if [[ "$source_exit" == 0 ]]; then
  if temporary_directory="$(mktemp -d /tmp/pawcycle-capacity-gate.XXXXXX 2>/dev/null)"; then
    production_result="$temporary_directory/production-result"
    observability_result="$temporary_directory/observability-result"
    if sudo -n bash "$diagnostic" --scope production >"$production_result" 2>/dev/null; then
      production_exit=0
    else
      production_exit=$?
    fi
    if bash "$diagnostic" --scope observability --prometheus-url http://127.0.0.1:9090 \
      --production-result "$production_result" >"$observability_result" 2>/dev/null; then
      observability_exit=0
    else
      observability_exit=$?
    fi
  else
    source_exit=1
  fi
fi

printf '%s\n' '__PAWCYCLE_GATE_PRODUCTION_BEGIN__'
if [[ -n "$production_result" && -f "$production_result" ]]; then
  cat -- "$production_result" || production_read_exit=$?
else
  production_read_exit=125
fi
printf '%s\n' '__PAWCYCLE_GATE_PRODUCTION_END__'
printf '%s\n' '__PAWCYCLE_GATE_OBSERVABILITY_BEGIN__'
if [[ -n "$observability_result" && -f "$observability_result" ]]; then
  cat -- "$observability_result" || observability_read_exit=$?
else
  observability_read_exit=125
fi
printf '%s\n' '__PAWCYCLE_GATE_OBSERVABILITY_END__'
printf '__PAWCYCLE_GATE_STATUS__ phase=%s source_exit=%s production_exit=%s observability_exit=%s production_read_exit=%s observability_read_exit=%s\n' \
  "$phase" "$source_exit" "$production_exit" "$observability_exit" \
  "$production_read_exit" "$observability_read_exit"
exit 0
REMOTE_GATE_SCRIPT
  then
    transport_exit=0
  else
    transport_exit=$?
  fi

  status_line="$(grep -E '^__PAWCYCLE_GATE_STATUS__ ' "$gate_transport_file" | tail -n 1 || true)"
  if [[ "$status_line" =~ ^__PAWCYCLE_GATE_STATUS__\ phase=(pre|post)\ source_exit=([0-9]+)\ production_exit=([0-9]+)\ observability_exit=([0-9]+)\ production_read_exit=([0-9]+)\ observability_read_exit=([0-9]+)$ ]]; then
    remote_phase="${BASH_REMATCH[1]}"
    if [[ "$remote_phase" == "$phase" ]]; then
      source_exit="${BASH_REMATCH[2]}"
      production_exit="${BASH_REMATCH[3]}"
      observability_exit="${BASH_REMATCH[4]}"
      production_read_exit="${BASH_REMATCH[5]}"
      observability_read_exit="${BASH_REMATCH[6]}"
    fi
  fi

  if extract_gate_section "$gate_transport_file" '__PAWCYCLE_GATE_PRODUCTION_BEGIN__' \
    '__PAWCYCLE_GATE_PRODUCTION_END__' "$production_artifact"; then
    production_framing='PASS'
  fi
  if extract_gate_section "$gate_transport_file" '__PAWCYCLE_GATE_OBSERVABILITY_BEGIN__' \
    '__PAWCYCLE_GATE_OBSERVABILITY_END__' "$observability_artifact"; then
    observability_framing='PASS'
  fi
  finished_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

  if [[ "$transport_exit" != 0 || "$source_exit" != 0 || "$production_exit" != 0 ||
        "$observability_exit" != 0 || "$production_read_exit" != 0 ||
        "$observability_read_exit" != 0 || "$production_framing" != PASS ||
        "$observability_framing" != PASS ]] ||
     ! gate_has_exact_line "$production_artifact" 'scope=production' ||
     ! gate_has_exact_line "$production_artifact" 'production_assessment=READY' ||
     ! gate_has_exact_line "$production_artifact" 'release_coordination=stable' ||
     ! gate_has_exact_line "$observability_artifact" 'status=NORMAL' ||
     ! gate_has_exact_line "$observability_artifact" 'production_assessment=READY' ||
     ! gate_has_exact_line "$observability_artifact" 'prometheus_target=up'; then
    gate_ok=false
  fi

  {
    printf 'gate_phase=%s\n' "$phase"
    printf 'gate_started_at_utc=%s\n' "$started_at_utc"
    printf 'gate_finished_at_utc=%s\n' "$finished_at_utc"
    printf 'source_identity_exit=%s\n' "$source_exit"
    printf 'production_diagnostic_exit=%s\n' "$production_exit"
    printf 'observability_diagnostic_exit=%s\n' "$observability_exit"
    printf 'production_artifact_read_exit=%s\n' "$production_read_exit"
    printf 'observability_artifact_read_exit=%s\n' "$observability_read_exit"
    printf 'ssh_transport_exit=%s\n' "$transport_exit"
    printf 'artifact_framing=production:%s,observability:%s\n' "$production_framing" "$observability_framing"
  } >"$context_artifact"

  rm -f -- "$gate_transport_file"
  gate_transport_file=''
  if [[ "$gate_ok" == true ]]; then
    return 0
  fi
  printf '%s safety gate failed; preserve Production and Observability outputs and stop\n' "$phase" >&2
  return 1
}

for target_rps in "${target_rates[@]}"; do
  if ! run_safety_gate "$target_rps" pre; then
    exit 1
  fi

  host_samples="$results_dir/$dataset_id-${target_rps}rps-host.jsonl"
  k6_target_rps="$target_rps"
  k6_summary="$results_dir/$dataset_id-${target_rps}rps.json"
  k6_stdout="$results_dir/$dataset_id-${target_rps}rps-k6.stdout.log"
  k6_stderr="$results_dir/$dataset_id-${target_rps}rps-k6.stderr.log"
  k6_context="$results_dir/$dataset_id-${target_rps}rps-k6-context.txt"
  k6_host_samples="$host_samples"
  k6_started_at_utc=''
  k6_finished_at_utc=''
  k6_exit='not_started'
  : >"$k6_stdout"
  : >"$k6_stderr"
  write_k6_context

  remote_collector="$PAWCYCLE_PERF_APP01_ROOT/source/$approved_sha/infra/performance/catalog-isolated/collect-stage-evidence.py"
  # One remote-command argument; native Windows SSH must receive Linux paths unchanged.
  remote_command="sudo -n python3 '$remote_collector' sample --port '$isolated_host_port' --duration-seconds 165"
  MSYS2_ARG_CONV_EXCL='*' "$evidence_ssh_executable" "${evidence_ssh_args[@]}" \
    "$evidence_ssh_target" "$remote_command" >"$host_samples" &
  collector_pid=$!
  for _ in {1..10}; do
    [[ -s "$host_samples" ]] && break
    job_is_running "$collector_pid" || break
    sleep 1
  done
  if ! job_is_running "$collector_pid" || [[ ! -s "$host_samples" ]]; then
    if job_is_running "$collector_pid"; then
      stop_and_reap "$collector_pid"
    fi
    collector_pid=''
    k6_finished_at_utc=''
    k6_exit='not_started'
    write_k6_context
    run_safety_gate "$target_rps" post || true
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
  k6_started_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  k6_exit='running'
  write_k6_context
  k6 "${k6_args[@]}" >"$k6_stdout" 2>"$k6_stderr" &
  k6_pid=$!
  while job_is_running "$k6_pid"; do
    if ! job_is_running "$collector_pid"; then
      if job_is_running "$k6_pid"; then
        kill -TERM "$k6_pid" 2>/dev/null || true
      fi
      if wait "$k6_pid"; then
        k6_exit=0
      else
        k6_exit=$?
      fi
      k6_pid=''
      k6_finished_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
      write_k6_context
      wait "$collector_pid" || true
      collector_pid=''
      run_safety_gate "$target_rps" post || true
      printf 'Host evidence collector stopped during load\n' >&2
      exit 1
    fi
    sleep 2
  done
  if wait "$k6_pid"; then
    k6_exit=0
  else
    k6_exit=$?
    k6_ok=false
  fi
  k6_pid=''
  k6_finished_at_utc="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  write_k6_context
  if ! wait "$collector_pid"; then
    collector_pid=''
    run_safety_gate "$target_rps" post || true
    printf 'Host evidence collector failed; stop before next stage\n' >&2
    exit 1
  fi
  collector_pid=''
  post_gate_ok=true
  run_safety_gate "$target_rps" post || post_gate_ok=false
  if "$python_executable" "$source_root/infra/performance/catalog-isolated/collect-stage-evidence.py" assemble \
    --summary "$results_dir/$dataset_id-${target_rps}rps.json" \
    --host-samples "$host_samples" \
    --output "$results_dir/$dataset_id-${target_rps}rps-evidence.json"; then
    :
  else
    assembly_status=$?
    printf 'automatic_evidence_assembly=FAIL k6_ok=%s; preserve summary and host samples; do not rerun load or advance RPS\n' "$k6_ok" >&2
    exit "$assembly_status"
  fi
  if [[ "$k6_ok" != true ]]; then
    printf 'k6 stage failed; stop before next RPS\n' >&2
    exit 1
  fi
  if [[ "$post_gate_ok" != true ]]; then
    exit 1
  fi
done
