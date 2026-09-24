#!/usr/bin/env bash
set -Eeuo pipefail

[[ "${EUID:-$(id -u)}" -eq 0 ]] || {
  printf 'test must run as root\n' >&2
  exit 1
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd -P)"

tmp="$(mktemp -d /tmp/pawcycle-isolated-catalog-test.XXXXXX)"
runner_pid=''
cleanup_test() {
  if [[ -n "$runner_pid" ]]; then
    kill -TERM "$runner_pid" 2>/dev/null || true
    wait "$runner_pid" 2>/dev/null || true
  fi
  rm -rf "$tmp"
}
trap cleanup_test EXIT

approved_sha='1111111111111111111111111111111111111111'
source_root="$tmp/$approved_sha"
isolated_dir="$source_root/infra/performance/catalog-isolated"
k6_dir="$source_root/infra/performance/k6"
manager="$isolated_dir/manage-isolated-catalog.sh"
compose_file="$isolated_dir/compose.yaml"
provenance_tool="$isolated_dir/prepare-isolated-catalog-provenance.py"
k6_runner="$k6_dir/run-isolated-capacity.sh"

dataset_id='catalog-core-control-v1'
dataset_dir="$tmp/data/$dataset_id"
config_file="$tmp/$dataset_id.env"
password_file="$tmp/db-password"
results_dir="$tmp/results"
fake_bin="$tmp/bin"
docker_log="$tmp/docker.log"
k6_log="$tmp/k6.log"
curl_log="$tmp/curl.log"
approved_image='ghcr.io/guseoh/pawcycle-backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

mkdir -p   "$source_root/infra/performance"   "$source_root/scripts"   "$source_root/backend/src/main/resources/catalog"   "$tmp/data"   "$fake_bin"

cp -a "$REPO_ROOT/infra/performance/catalog-isolated" "$source_root/infra/performance/"
cp -a "$REPO_ROOT/infra/performance/k6" "$source_root/infra/performance/"
cp "$REPO_ROOT/scripts/prepare-product-scale-data.py" "$source_root/scripts/"
cp "$REPO_ROOT/scripts/generate-product-data-v2.py" "$source_root/scripts/"
cp   "$REPO_ROOT/backend/src/main/resources/catalog/demo-catalog.json"   "$source_root/backend/src/main/resources/catalog/demo-catalog.json"
printf '%s\n' "$approved_sha" >"$source_root/.approved-sha"

find "$source_root" -type d -exec chmod 0555 {} +
find "$source_root" -type f -exec chmod 0444 {} +

mkdir -m 0700 "$dataset_dir"

python3 "$source_root/scripts/prepare-product-scale-data.py"   --target-products 32   --seed 20260826   --dataset-id "$dataset_id"   --output "$dataset_dir/manifest.json"   --report "$dataset_dir/report.json"

chmod 0444 "$dataset_dir/manifest.json" "$dataset_dir/report.json"

python3 "$provenance_tool"   --source-root "$source_root"   --dataset-dir "$dataset_dir" >/dev/null

[[ "$(stat -c '%a' "$dataset_dir/provenance.json")" == '444' ]]

python3 - \
  "$dataset_dir/provenance.json" \
  "$source_root/scripts/prepare-product-scale-data.py" \
  "$source_root/scripts/generate-product-data-v2.py" <<'PY'
import hashlib
import json
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


provenance = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert provenance["prepareWrapperSha256"] == sha256(Path(sys.argv[2]))
assert provenance["dataGeneratorSha256"] == sha256(Path(sys.argv[3]))
PY

external_scripts="$tmp/external-scripts"
mkdir -m 0555 "$external_scripts"
cp "$source_root/scripts/prepare-product-scale-data.py" "$external_scripts/"
cp "$source_root/scripts/generate-product-data-v2.py" "$external_scripts/"
chmod 0444 "$external_scripts"/*.py
mv "$source_root/scripts" "$source_root/scripts-real"
ln -s "$external_scripts" "$source_root/scripts"
rm "$dataset_dir/provenance.json"
if python3 "$provenance_tool" --source-root "$source_root" --dataset-dir "$dataset_dir" >/dev/null 2>&1; then
  printf 'provenance accepted an intermediate source directory symlink\n' >&2
  exit 1
fi
rm "$source_root/scripts"
mv "$source_root/scripts-real" "$source_root/scripts"

chmod 0775 "$source_root/scripts"
if python3 "$provenance_tool" --source-root "$source_root" --dataset-dir "$dataset_dir" >/dev/null 2>&1; then
  printf 'provenance accepted a group-writable intermediate source directory\n' >&2
  exit 1
fi
chmod 0555 "$source_root/scripts"

external_manifest="$tmp/external-demo-catalog.json"
cp "$source_root/backend/src/main/resources/catalog/demo-catalog.json" "$external_manifest"
chmod 0444 "$external_manifest"
base_manifest="$source_root/backend/src/main/resources/catalog/demo-catalog.json"
mv "$base_manifest" "$base_manifest-real"
ln -s "$external_manifest" "$base_manifest"
if python3 "$provenance_tool" --source-root "$source_root" --dataset-dir "$dataset_dir" >/dev/null 2>&1; then
  printf 'provenance accepted a direct source file symlink\n' >&2
  exit 1
fi
rm "$base_manifest"
mv "$base_manifest-real" "$base_manifest"
python3 "$provenance_tool" --source-root "$source_root" --dataset-dir "$dataset_dir" >/dev/null

cat >"$config_file" <<EOF
PAWCYCLE_PERF_DATASET_ID=catalog-core-control-v1
PAWCYCLE_PERF_SCHEMA=pawcycle_perf_core_control
PAWCYCLE_PERF_BACKEND_IMAGE=$approved_image
PAWCYCLE_PERF_DB_URL=jdbc:mysql://mysql.internal:3306/pawcycle_perf_core_control?sslMode=REQUIRED
PAWCYCLE_PERF_DB_USERNAME=pawcycle_perf_catalog
PAWCYCLE_PERF_HOST_PORT=18080
EOF
chmod 0600 "$config_file"
printf '%s\n' 'fixture-password' >"$password_file"
chmod 0400 "$password_file"

# Real Compose parsing is part of the contract test. It does not start containers.
PAWCYCLE_PERF_DB_PASSWORD='fixture-password' PAWCYCLE_PERF_MANIFEST_PATH="$dataset_dir/manifest.json" PAWCYCLE_PERF_IMPORT_OPERATION='validate' docker compose   --project-name pawcycle-performance-catalog   --profile tools   --env-file "$config_file"   -f "$compose_file"   config >"$tmp/resolved-compose.yaml"
PAWCYCLE_PERF_DB_PASSWORD='fixture-password' PAWCYCLE_PERF_MANIFEST_PATH="$dataset_dir/manifest.json" PAWCYCLE_PERF_IMPORT_OPERATION='validate' docker compose   --project-name pawcycle-performance-catalog   --profile tools   --env-file "$config_file"   -f "$compose_file"   config --format json >"$tmp/resolved-compose.json"

python3 - "$tmp/resolved-compose.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    compose = json.load(stream)
assert compose["services"]["backend"]["environment"]["SERVER_TOMCAT_MBEANREGISTRY_ENABLED"] == "true"
assert "SERVER_TOMCAT_MBEANREGISTRY_ENABLED" not in compose["services"]["catalog-import"]["environment"]
PY

grep -q 'name: pawcycle-performance-catalog' "$tmp/resolved-compose.yaml"
grep -q 'host_ip: 127.0.0.1' "$tmp/resolved-compose.yaml"
grep -q 'performance-db-egress' "$tmp/resolved-compose.yaml"
grep -q 'read_only: true' "$tmp/resolved-compose.yaml"
grep -q 'no-new-privileges:true' "$tmp/resolved-compose.yaml"
if grep -Eq 'pawcycle-production|app-ingress|0\.0\.0\.0' "$tmp/resolved-compose.yaml"; then
  printf 'resolved Compose unexpectedly references Production/public network state\n' >&2
  exit 1
fi

cat >"$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
log="${FAKE_DOCKER_LOG:?}"
approved_image="${FAKE_APPROVED_IMAGE:?}"
printf 'docker|operation=%s|%s\n' "${PAWCYCLE_PERF_IMPORT_OPERATION:-}" "$*" >>"$log"

if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi
if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  if [[ "${FAKE_IMAGE_MISSING:-0}" == "1" ]]; then
    exit 1
  fi
  joined=" $* "
  case "$joined" in
    *"{{.Os}}/{{.Architecture}}"*)
      printf 'linux/amd64\n'
      exit 0
      ;;
    *".RepoDigests"*)
      printf '%s\n' "$approved_image"
      exit 0
      ;;
    *)
      exit 0
      ;;
  esac
fi
if [[ "${1:-}" == "ps" ]]; then
  if [[ "${2:-}" == "-a" && "${FAKE_EXISTING_CONTAINER:-0}" == "1" ]]; then
    printf 'fake-existing\n'
  fi
  exit 0
fi
if [[ "${1:-}" == "inspect" ]]; then
  joined=" $* "
  case "$joined" in
    *"com.pawcycle.performance.scope"*)
      printf '%s\n' "${FAKE_SCOPE_LABEL:-catalog-isolated}"
      ;;
    *"com.pawcycle.performance.dataset"*)
      printf '%s\n' "${FAKE_DATASET_LABEL:-catalog-core-control-v1}"
      ;;
    *)
      printf 'healthy\n'
      ;;
  esac
  exit 0
fi

joined=" $* "
case "$joined" in
  *" config "*) exit 0 ;;
  *" ps --status running -q backend "*) exit 0 ;;
  *" ps -q backend "*)
    printf 'fake-backend\n'
    exit 0
    ;;
  *" run --rm --no-deps --pull never catalog-import "*) exit 0 ;;
  *" up -d --pull never backend "*) exit 0 ;;
  *" down --remove-orphans "*) exit 0 ;;
  *" ps "*) exit 0 ;;
esac
exit 0
EOF
chmod +x "$fake_bin/docker"

cat >"$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'curl|%s\n' "$*" >>"${FAKE_CURL_LOG:?}"
if [[ "${FAKE_CURL_FAIL:-0}" == "1" ]]; then
  exit 22
fi
exit 0
EOF
chmod +x "$fake_bin/curl"

cat >"$fake_bin/k6" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'k6|%s\n' "$*" >>"${FAKE_K6_LOG:?}"
[[ -z "${FAKE_K6_PID_FILE:-}" ]] || printf '%s\n' "$$" >"$FAKE_K6_PID_FILE"
results_dir=''
dataset_id=''
target_rps=''
while (($#)); do
  if [[ "$1" == '-e' ]]; then
    case "${2:-}" in
      RESULTS_DIR=*) results_dir="${2#RESULTS_DIR=}" ;;
      ISOLATED_DATASET_ID=*) dataset_id="${2#ISOLATED_DATASET_ID=}" ;;
      TARGET_RPS=*) target_rps="${2#TARGET_RPS=}" ;;
    esac
    shift 2
  else
    shift
  fi
done
if [[ -n "$results_dir" ]]; then
  python3 - "$results_dir/$dataset_id-${target_rps}rps.json" "$dataset_id" "$target_rps" <<'PY'
import datetime as dt
import json
import sys

start = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=10)
end = start + dt.timedelta(seconds=120.5)
with open(sys.argv[1], "w", encoding="utf-8") as stream:
    json.dump({"datasetId": sys.argv[2], "targetRps": int(sys.argv[3]),
               "actualRps": int(sys.argv[3]), "measurementStartUtc": start.isoformat(),
               "measurementEndUtc": end.isoformat(), "maxMs": 500,
               "droppedIterations": 0, "expectedStatusErrorRate": 0}, stream)
    stream.write("\n")
PY
fi
if [[ "${FAKE_K6_HOLD:-0}" == "1" ]]; then
  trap 'printf "TERM\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' TERM
  trap 'printf "INT\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' INT
  trap 'printf "HUP\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' HUP
  while :; do sleep 1; done
fi
exit 0
EOF
chmod +x "$fake_bin/k6"

cat >"$fake_bin/ssh" <<'EOF'
#!/usr/bin/env bash
exit 1
EOF
cat >"$fake_bin/oci" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import sys

start = dt.datetime.fromisoformat(sys.argv[sys.argv.index("--start-time") + 1].replace("Z", "+00:00"))
end = dt.datetime.fromisoformat(sys.argv[sys.argv.index("--end-time") + 1].replace("Z", "+00:00"))
bucket_end = start.replace(second=0, microsecond=0) + dt.timedelta(minutes=1)
points = []
while bucket_end < end:
    points.append({"timestamp": bucket_end.isoformat().replace("+00:00", "Z"), "value": 1})
    bucket_end += dt.timedelta(minutes=1)
print(json.dumps({"data": [{"aggregated-datapoints": points}]}))
PY
chmod +x "$fake_bin/ssh" "$fake_bin/oci"

run_manager() {
  PATH="$fake_bin:$PATH"   FAKE_DOCKER_LOG="$docker_log"   FAKE_CURL_LOG="$curl_log"   FAKE_APPROVED_IMAGE="$approved_image"   FAKE_IMAGE_MISSING="${FAKE_IMAGE_MISSING:-0}"   FAKE_CURL_FAIL="${FAKE_CURL_FAIL:-0}"   bash "$manager" "$@"     --source-root "$source_root"     --config-file "$config_file"     --password-file "$password_file"     --dataset-dir "$dataset_dir"
}

run_down_manager() {
  PATH="$fake_bin:$PATH"   FAKE_DOCKER_LOG="$docker_log"   FAKE_CURL_LOG="$curl_log"   FAKE_APPROVED_IMAGE="$approved_image"   FAKE_IMAGE_MISSING="${FAKE_IMAGE_MISSING:-0}"   FAKE_EXISTING_CONTAINER="${FAKE_EXISTING_CONTAINER:-0}"   FAKE_SCOPE_LABEL="${FAKE_SCOPE_LABEL:-catalog-isolated}"   FAKE_DATASET_LABEL="${FAKE_DATASET_LABEL:-catalog-core-control-v1}"   bash "$manager" down "$@"     --config-file "$config_file"
}

run_manager preflight >/dev/null
grep -q 'image inspect' "$docker_log"
grep -q '{{.Os}}/{{.Architecture}}' "$docker_log"
grep -q '.RepoDigests' "$docker_log"

: >"$docker_log"
if FAKE_IMAGE_MISSING=1 run_manager preflight >/dev/null 2>&1; then
  printf 'preflight unexpectedly succeeded without the approved local image\n' >&2
  exit 1
fi
grep -q 'image inspect' "$docker_log"

: >"$docker_log"
if run_manager import-apply >/dev/null 2>&1; then
  printf 'import-apply unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'run --rm --no-deps --pull never catalog-import' "$docker_log"; then
  printf 'import ran before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
run_manager import-apply --acknowledge "APPLY:$dataset_id" >/dev/null
grep -q 'operation=validate|.*run --rm --no-deps --pull never catalog-import' "$docker_log"
grep -q 'operation=apply|.*run --rm --no-deps --pull never catalog-import' "$docker_log"

: >"$docker_log"
if run_manager up >/dev/null 2>&1; then
  printf 'runtime start unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'up -d --pull never backend' "$docker_log"; then
  printf 'runtime started before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
: >"$curl_log"
if FAKE_CURL_FAIL=1 run_manager up   --acknowledge "START:$dataset_id" >/dev/null 2>&1; then
  printf 'runtime start unexpectedly succeeded when readiness/API curl failed\n' >&2
  exit 1
fi
grep -q 'up -d --pull never backend' "$docker_log"
grep -q 'down --remove-orphans' "$docker_log"

: >"$docker_log"
: >"$curl_log"
run_manager up --acknowledge "START:$dataset_id" >/dev/null
grep -q 'up -d --pull never backend' "$docker_log"
grep -q '/actuator/health/readiness' "$curl_log"
grep -q '/api/products' "$curl_log"

: >"$docker_log"
if run_down_manager >/dev/null 2>&1; then
  printf 'runtime cleanup unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'down --remove-orphans' "$docker_log"; then
  printf 'runtime cleanup ran before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
run_down_manager --acknowledge 'DOWN:pawcycle-performance-catalog' >/dev/null
grep -q 'down --remove-orphans' "$docker_log"
if grep -q -- '--volumes' "$docker_log"; then
  printf 'runtime cleanup must not remove volumes\n' >&2
  exit 1
fi

: >"$docker_log"
if FAKE_EXISTING_CONTAINER=1 FAKE_DATASET_LABEL='catalog-core-10k-v1' run_down_manager   --acknowledge 'DOWN:pawcycle-performance-catalog' >/dev/null 2>&1; then
  printf 'runtime cleanup accepted a dataset identity mismatch\n' >&2
  exit 1
fi
if grep -q 'down --remove-orphans' "$docker_log"; then
  printf 'runtime cleanup ran after a dataset identity mismatch\n' >&2
  exit 1
fi

: >"$docker_log"
if FAKE_EXISTING_CONTAINER=1 FAKE_SCOPE_LABEL='other-scope' run_down_manager   --acknowledge 'DOWN:pawcycle-performance-catalog' >/dev/null 2>&1; then
  printf 'runtime cleanup accepted a scope identity mismatch\n' >&2
  exit 1
fi
if grep -q 'down --remove-orphans' "$docker_log"; then
  printf 'runtime cleanup ran after a scope identity mismatch\n' >&2
  exit 1
fi

rm "$dataset_dir/manifest.json" "$dataset_dir/report.json" "$dataset_dir/provenance.json" "$password_file"
: >"$docker_log"
FAKE_IMAGE_MISSING=1 run_down_manager --acknowledge 'DOWN:pawcycle-performance-catalog' >/dev/null
grep -q 'down --remove-orphans' "$docker_log"
if grep -q 'image inspect' "$docker_log"; then
  printf 'runtime cleanup must not depend on the local Backend image\n' >&2
  exit 1
fi

: >"$k6_log"
if PATH="$fake_bin:$PATH" FAKE_K6_LOG="$k6_log" "$BASH" "$k6_runner" \
  --source-root "$source_root" --target-url 'https://example.com' \
  --dataset-id "$dataset_id" --results-dir "$results_dir" \
  --evidence-ssh-target app01 --isolated-host-port 18081 \
  --acknowledge-isolated-load YES >/dev/null 2>&1; then
  printf 'isolated k6 runner accepted a non-loopback target\n' >&2
  exit 1
fi
[[ ! -s "$k6_log" ]]

run_capacity() {
  PATH="${RUNNER_PATH:-$fake_bin:$PATH}" FAKE_K6_LOG="$k6_log" \
    FAKE_K6_HOLD="${FAKE_K6_HOLD:-0}" \
    FAKE_K6_PID_FILE="${FAKE_K6_PID_FILE:-}" \
    FAKE_K6_SIGNAL_LOG="${FAKE_K6_SIGNAL_LOG:-}" \
    "$BASH" "$k6_runner" \
    --source-root "$source_root" \
    --target-url 'http://127.0.0.1:18080' \
    --dataset-id "$dataset_id" \
    --results-dir "$results_dir" \
    "$@" \
    --acknowledge-isolated-load YES
}

clear_capacity_results() {
  find "$results_dir" -mindepth 1 -maxdepth 1 -type f -delete
}

assert_capacity_rejected() {
  local description="$1"
  local expected_message="$2"
  local output
  shift
  shift
  : >"$k6_log"
  if output="$(run_capacity "$@" 2>&1)"; then
    printf 'isolated k6 runner accepted invalid Evidence setup: %s\n' "$description" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected_message"* ]]; then
    printf 'isolated k6 runner failed with unexpected reason for %s: %s\n' "$description" "$output" >&2
    exit 1
  fi
  [[ ! -s "$k6_log" ]]
}

export PAWCYCLE_PERF_OCI_COMPARTMENT_ID='ocid1.compartment.fixture'
export PAWCYCLE_PERF_OCI_DB_SYSTEM_ID='ocid1.mysqldbsystem.fixture'

assert_capacity_rejected 'both Evidence arguments omitted' 'Usage:'
assert_capacity_rejected 'only SSH target supplied' 'Usage:' --evidence-ssh-target app01
assert_capacity_rejected 'only host port supplied' 'Usage:' --isolated-host-port 18081
assert_capacity_rejected 'invalid SSH target' 'Usage:' --evidence-ssh-target 'app01;touch' --isolated-host-port 18081
assert_capacity_rejected 'invalid port' 'Usage:' --evidence-ssh-target app01 --isolated-host-port 0
assert_capacity_rejected 'port out of range' 'Usage:' --evidence-ssh-target app01 --isolated-host-port 65536

unset PAWCYCLE_PERF_OCI_COMPARTMENT_ID
assert_capacity_rejected 'OCI Monitoring compartment identity absent' 'OCI Monitoring compartment identity is required' --evidence-ssh-target app01 --isolated-host-port 18081
export PAWCYCLE_PERF_OCI_COMPARTMENT_ID='ocid1.compartment.fixture'
unset PAWCYCLE_PERF_OCI_DB_SYSTEM_ID
assert_capacity_rejected 'OCI Monitoring DB System identity absent' 'OCI Monitoring DB System identity is required' --evidence-ssh-target app01 --isolated-host-port 18081
export PAWCYCLE_PERF_OCI_DB_SYSTEM_ID='ocid1.mysqldbsystem.fixture'

missing_ssh_path="$tmp/missing-ssh"
missing_python_path="$tmp/missing-python"
missing_oci_path="$tmp/missing-oci"
mkdir -p "$missing_ssh_path" "$missing_python_path" "$missing_oci_path"
python3_path="$(command -v python3)"
cp "$fake_bin/oci" "$missing_ssh_path/oci"
ln -s "$python3_path" "$missing_ssh_path/python3"
cp "$fake_bin/ssh" "$missing_python_path/ssh"
cp "$fake_bin/oci" "$missing_python_path/oci"
cp "$fake_bin/ssh" "$missing_oci_path/ssh"
ln -s "$python3_path" "$missing_oci_path/python3"
RUNNER_PATH="$missing_ssh_path" assert_capacity_rejected 'ssh CLI absent' 'ssh is required for evidence collection' --evidence-ssh-target app01 --isolated-host-port 18081
RUNNER_PATH="$missing_python_path" assert_capacity_rejected 'python3 absent' 'python3 is required for evidence collection' --evidence-ssh-target app01 --isolated-host-port 18081
RUNNER_PATH="$missing_oci_path" assert_capacity_rejected 'OCI CLI absent' 'OCI CLI is required for evidence collection' --evidence-ssh-target app01 --isolated-host-port 18081

mkdir -p "$results_dir"
printf 'stale\n' >"$results_dir/stale.json"
if run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null 2>&1; then
  printf 'isolated k6 runner accepted a non-empty results directory\n' >&2
  exit 1
fi
[[ ! -s "$k6_log" ]]
rm -f "$results_dir/stale.json"

cat >"$fake_bin/ssh" <<'EOF'
#!/usr/bin/env bash
exit 1
EOF
chmod +x "$fake_bin/ssh"
: >"$k6_log"
assert_capacity_rejected 'collector start failure' 'Host evidence collector did not start' --evidence-ssh-target app01 --isolated-host-port 18081
clear_capacity_results

cat >"$fake_bin/ssh" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import time

start = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=10)
for second in range(0, 121, 5):
    print(json.dumps({
        "timestampUtc": (start + dt.timedelta(seconds=second)).isoformat().replace("+00:00", "Z"),
        "container": {"restartCount": 0, "oomKilled": False, "health": "healthy"},
    }), flush=True)
time.sleep(2)
PY
chmod +x "$fake_bin/ssh"

run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null
[[ "$(wc -l <"$k6_log")" -eq 6 ]]
[[ -f "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ -f "$results_dir/$dataset_id-250rps-evidence.json" ]]
grep -q 'TARGET_RPS=25' "$k6_log"
grep -q 'TARGET_RPS=250' "$k6_log"
grep -q 'ISOLATED_DATASET_ID=catalog-core-control-v1' "$k6_log"

clear_capacity_results
cat >"$fake_bin/ssh" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import os
import time

print(json.dumps({"timestampUtc": "2026-09-24T00:00:00Z",
                  "container": {"restartCount": 0, "oomKilled": False, "health": "healthy"}}), flush=True)
while not os.path.getsize(os.environ["FAKE_K6_LOG"]):
    time.sleep(0.05)
time.sleep(3)
raise SystemExit(1)
PY
chmod +x "$fake_bin/ssh"
: >"$k6_log"
if FAKE_K6_HOLD=1 run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null 2>&1; then
  printf 'isolated k6 runner succeeded after evidence collector failure\n' >&2
  exit 1
fi
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
[[ ! -e "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ ! -e "$results_dir/$dataset_id-50rps-host.jsonl" ]]
clear_capacity_results

cat >"$fake_bin/ssh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
trap 'printf "TERM\n" >>"${FAKE_SSH_SIGNAL_LOG:?}"; exit 0' TERM
trap 'printf "INT\n" >>"${FAKE_SSH_SIGNAL_LOG:?}"; exit 0' INT
trap 'printf "HUP\n" >>"${FAKE_SSH_SIGNAL_LOG:?}"; exit 0' HUP
printf '%s\n' "$$" >"${FAKE_SSH_PID_FILE:?}"
printf '{"timestampUtc":"2026-09-24T00:00:00Z","container":{"restartCount":0,"oomKilled":false,"health":"healthy"}}\n'
while :; do sleep 1; done
EOF
chmod +x "$fake_bin/ssh"
signal_dir="$tmp/signal-test"
mkdir -p "$signal_dir"
: >"$k6_log"
PATH="$fake_bin:$PATH" FAKE_K6_LOG="$k6_log" FAKE_K6_HOLD=1 \
  FAKE_K6_PID_FILE="$signal_dir/k6.pid" FAKE_K6_SIGNAL_LOG="$signal_dir/k6.signals" \
  FAKE_SSH_PID_FILE="$signal_dir/ssh.pid" FAKE_SSH_SIGNAL_LOG="$signal_dir/ssh.signals" \
  "$BASH" "$k6_runner" --source-root "$source_root" \
  --target-url 'http://127.0.0.1:18080' --dataset-id "$dataset_id" \
  --results-dir "$results_dir" --evidence-ssh-target app01 --isolated-host-port 18081 \
  --acknowledge-isolated-load YES >/dev/null 2>&1 &
runner_pid=$!
for _ in {1..100}; do
  [[ -s "$signal_dir/k6.pid" && -s "$signal_dir/ssh.pid" ]] && break
  sleep 0.1
done
[[ -s "$signal_dir/k6.pid" && -s "$signal_dir/ssh.pid" ]]
kill -TERM "$runner_pid"
set +e
wait "$runner_pid"
runner_status=$?
set -e
runner_pid=''
[[ "$runner_status" -eq 143 ]]
grep -q '^TERM$' "$signal_dir/k6.signals"
grep -q '^TERM$' "$signal_dir/ssh.signals"
for pid_file in "$signal_dir/k6.pid" "$signal_dir/ssh.pid"; do
  child_pid="$(cat "$pid_file")"
  if kill -0 "$child_pid" 2>/dev/null; then
    printf 'isolated runner left child process %s alive after signal cleanup\n' "$child_pid" >&2
    exit 1
  fi
done

printf 'isolated_catalog_contract_test=PASS\n'
