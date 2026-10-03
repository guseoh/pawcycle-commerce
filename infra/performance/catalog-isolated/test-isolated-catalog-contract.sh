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
lock_holder_pid=''
cleanup_test() {
  if [[ -n "$runner_pid" ]]; then
    kill -TERM "$runner_pid" 2>/dev/null || true
    wait "$runner_pid" 2>/dev/null || true
  fi
  if [[ -n "$lock_holder_pid" ]]; then
    kill -TERM "$lock_holder_pid" 2>/dev/null || true
    wait "$lock_holder_pid" 2>/dev/null || true
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
ssh_log="$tmp/ssh.log"
approved_image='ghcr.io/guseoh/pawcycle-backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
FAKE_BOOTSTRAP_STATE='healthy'
FAKE_BACKEND_STATE='healthy'
export FAKE_BOOTSTRAP_STATE FAKE_BACKEND_STATE

mkdir -p   "$source_root/infra/performance"   "$source_root/scripts"   "$source_root/backend/src/main/resources/catalog"   "$tmp/data"   "$fake_bin"

cp -a "$REPO_ROOT/infra/performance/catalog-isolated" "$source_root/infra/performance/"
cp -a "$REPO_ROOT/infra/performance/k6" "$source_root/infra/performance/"
mkdir -p "$source_root/infra/production"
cp "$REPO_ROOT/infra/production/diagnose-backend-state.sh" "$source_root/infra/production/"
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

provenance_checksum_before="$(sha256sum "$dataset_dir/provenance.json")"
if python3 "$provenance_tool" --source-root "$source_root" --dataset-dir "$dataset_dir" >"$tmp/provenance-overwrite.log" 2>&1; then
  printf 'provenance tool overwrote an existing immutable provenance\n' >&2
  exit 1
fi
grep -Fq 'provenance.json already exists; do not overwrite immutable provenance' "$tmp/provenance-overwrite.log"
[[ "$(sha256sum "$dataset_dir/provenance.json")" == "$provenance_checksum_before" ]]

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
assert compose["services"]["backend"]["environment"]["PAWCYCLE_CATALOG_PRODUCT_DISCOVERY_DIAGNOSTICS_ENABLED"] == "true"
bootstrap = compose["services"]["schema-bootstrap"]
bootstrap_environment = bootstrap["environment"]
assert "PAWCYCLE_CATALOG_PRODUCT_DISCOVERY_DIAGNOSTICS_ENABLED" not in bootstrap_environment
assert "PAWCYCLE_CATALOG_PRODUCT_DISCOVERY_DIAGNOSTICS_ENABLED" not in compose["services"]["catalog-import"]["environment"]
assert bootstrap_environment["SPRING_MAIN_WEB_APPLICATION_TYPE"] == "servlet"
assert "--spring.main.web-application-type=none" not in str(bootstrap.get("command"))
assert bootstrap_environment["SPRING_FLYWAY_ENABLED"] == "true"
assert bootstrap_environment["PAWCYCLE_CATALOG_MANIFEST_IMPORT_ENABLED"] == "false"
assert bootstrap_environment["PAWCYCLE_SCHEDULER_ENABLED"] == "false"
assert bootstrap_environment["PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED"] == "false"
assert not any("AUTH_SMOKE" in key for key in bootstrap_environment)
assert "pawcycle.maintenance.create-auth-smoke-member.enabled" not in str(bootstrap)
assert not bootstrap.get("ports")
assert bootstrap["healthcheck"]["test"] == [
    "CMD", "curl", "--fail", "--silent", "--show-error",
    "http://127.0.0.1:8080/actuator/health/readiness",
]
assert set(bootstrap["networks"]) == {"performance-db-egress"}
assert bootstrap["image"] == compose["services"]["backend"]["image"]
assert compose["services"]["catalog-import"]["environment"]["PAWCYCLE_CATALOG_MANIFEST_IMPORT_MODE"] == "validate"
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
    *".State.Health"*|*".State.Status"*)
      if [[ "$4" == "fake-schema-bootstrap" ]]; then
        printf '%s\n' "$FAKE_BOOTSTRAP_STATE"
      elif [[ "$4" == "fake-backend" ]]; then
        printf '%s\n' "$FAKE_BACKEND_STATE"
      else
        printf 'healthy\n'
      fi
      ;;
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
  *" ps -q schema-bootstrap "*)
    printf 'fake-schema-bootstrap\n'
    exit 0
    ;;
  *" up -d --no-deps --pull never schema-bootstrap "*)
    [[ "${FAKE_BOOTSTRAP_FAIL:-0}" != "1" ]]
    exit $?
    ;;
  *" rm --stop --force schema-bootstrap "*)
    [[ "${FAKE_BOOTSTRAP_CLEANUP_FAIL:-0}" != "1" ]]
    exit $?
    ;;
  *" run --rm --no-deps --pull never catalog-import "*)
    [[ "${FAKE_IMPORT_FAIL:-0}" != "1" ]]
    exit $?
    ;;
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
printf 'event|curl|%s\n' "$*" >>"${FAKE_DOCKER_LOG:?}"
if [[ "${FAKE_CURL_FAIL:-0}" == "1" ]]; then
  exit 22
fi
exit 0
EOF
chmod +x "$fake_bin/curl"

cat >"$fake_bin/sleep" <<'EOF'
#!/usr/bin/env bash
# Capacity startup polling must give the background Python collector time to start.
# Keep lifecycle-manager retries fast; only capacity tests use real polling delays.
if [[ -n "${FAKE_K6_LOG:-}" ]]; then
  exec /bin/sleep "$@"
fi
exit 0
EOF
chmod +x "$fake_bin/sleep"

cat >"$fake_bin/k6" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${FAKE_K6_HOLD:-0}" == "1" ]]; then
  trap 'printf "TERM\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' TERM
  trap 'printf "INT\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' INT
  trap 'printf "HUP\n" >>"${FAKE_K6_SIGNAL_LOG:?}"; exit 0' HUP
fi
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
  while :; do sleep 1; done
fi
exit 0
EOF
chmod +x "$fake_bin/k6"

cat >"$fake_bin/ssh" <<'PY'
#!/usr/bin/env python3
import shlex
import sys

command = sys.argv[-1]
if command.startswith("bash -s -- "):
    phase = shlex.split(command)[-1]
    sys.stdin.read()
    production = ["scope=production", "generated_at_epoch=1791000000",
                  "production_assessment=READY", "release_coordination=stable",
                  "docker_query=ok", "backend=healthy", "api_products_http=200",
                  "metrics_proxy_http=200"]
    observability = ["status=NORMAL", "production_assessment=READY", "prometheus_target=up"]
    print("__PAWCYCLE_GATE_PRODUCTION_BEGIN__")
    print("\n".join(production))
    print("__PAWCYCLE_GATE_PRODUCTION_END__")
    print("__PAWCYCLE_GATE_OBSERVABILITY_BEGIN__")
    print("\n".join(observability))
    print("__PAWCYCLE_GATE_OBSERVABILITY_END__")
    print(f"__PAWCYCLE_GATE_STATUS__ phase={phase} source_exit=0 production_exit=0 observability_exit=0 production_read_exit=0 observability_read_exit=0")
    sys.exit(0)
sys.exit(1)
PY
cp "$fake_bin/ssh" "$tmp/gate-valid-ssh"
cat >"$fake_bin/oci" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import os
import sys

if (os.environ.get("FAKE_OCI_FAIL") == "1" or
    (os.environ.get("FAKE_OCI_DEFAULT_CONTEXT") != "1" and
     (not os.environ.get("OCI_CLI_PROFILE") or not os.environ.get("OCI_CLI_REGION")))):
    print("private-marker ocid1.compartment.fixture ocid1.mysqldbsystem.fixture", file=sys.stderr)
    sys.exit(1)
if os.environ.get("FAKE_OCI_LOG"):
    with open(os.environ["FAKE_OCI_LOG"], "a", encoding="utf-8") as stream:
        stream.write("query\n")
    with open(os.environ["FAKE_OCI_LOG"], encoding="utf-8") as stream:
        if os.environ.get("FAKE_OCI_FAIL_ASSEMBLY") == "1" and len(stream.readlines()) > 6:
            sys.exit(1)
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
  PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$docker_log" FAKE_CURL_LOG="$curl_log" \
    FAKE_APPROVED_IMAGE="$approved_image" FAKE_IMAGE_MISSING="${FAKE_IMAGE_MISSING:-0}" \
    FAKE_CURL_FAIL="${FAKE_CURL_FAIL:-0}" FAKE_BOOTSTRAP_FAIL="${FAKE_BOOTSTRAP_FAIL:-0}" \
    FAKE_BOOTSTRAP_CLEANUP_FAIL="${FAKE_BOOTSTRAP_CLEANUP_FAIL:-0}" \
    FAKE_IMPORT_FAIL="${FAKE_IMPORT_FAIL:-0}" FAKE_EXISTING_CONTAINER="${FAKE_EXISTING_CONTAINER:-0}" \
    bash "$manager" "$@" --source-root "$source_root" --config-file "$config_file" \
      --password-file "$password_file" --dataset-dir "$dataset_dir"
}

run_down_manager() {
  PATH="$fake_bin:$PATH"   FAKE_DOCKER_LOG="$docker_log"   FAKE_CURL_LOG="$curl_log"   FAKE_APPROVED_IMAGE="$approved_image"   FAKE_IMAGE_MISSING="${FAKE_IMAGE_MISSING:-0}"   FAKE_EXISTING_CONTAINER="${FAKE_EXISTING_CONTAINER:-0}"   FAKE_SCOPE_LABEL="${FAKE_SCOPE_LABEL:-catalog-isolated}"   FAKE_DATASET_LABEL="${FAKE_DATASET_LABEL:-catalog-core-control-v1}"   bash "$manager" down "$@"     --config-file "$config_file"
}

command -v flock >/dev/null 2>&1 || {
  printf 'flock is required by the lifecycle contract test\n' >&2
  exit 1
}
lock_file='/run/lock/pawcycle-performance-catalog.lock'
lock_ready="$tmp/lifecycle-lock-held"
(
  exec 9>>"$lock_file"
  flock -n 9
  : >"$lock_ready"
  exec sleep 60
) &
lock_holder_pid=$!
for _ in {1..50}; do
  [[ -f "$lock_ready" ]] && break
  sleep 0.1
done
[[ -f "$lock_ready" ]] || {
  printf 'unable to hold lifecycle lock for concurrency regression\n' >&2
  exit 1
}
for locked_action in schema-bootstrap import-validate import-apply rehearse up; do
  : >"$docker_log"
  case "$locked_action" in
    schema-bootstrap) action_args=(--acknowledge "BOOTSTRAP:$dataset_id") ;;
    import-apply) action_args=(--acknowledge "APPLY:$dataset_id") ;;
    rehearse) action_args=(--acknowledge "REHEARSE:$dataset_id") ;;
    up) action_args=(--acknowledge "START:$dataset_id") ;;
    *) action_args=() ;;
  esac
  if run_manager "$locked_action" "${action_args[@]}" >"$tmp/lock-output" 2>"$tmp/lock-error"; then
    printf 'lifecycle action ran while another process held the lock: %s\n' "$locked_action" >&2
    exit 1
  fi
  grep -q 'another isolated Catalog lifecycle action is running' "$tmp/lock-error"
  [[ ! -s "$docker_log" ]] || {
    printf 'lifecycle action reached Docker while lock was held: %s\n' "$locked_action" >&2
    exit 1
  }
done
: >"$docker_log"
if run_down_manager --acknowledge 'DOWN:pawcycle-performance-catalog' >"$tmp/lock-output" 2>"$tmp/lock-error"; then
  printf 'down ran while another process held the lock\n' >&2
  exit 1
fi
grep -q 'another isolated Catalog lifecycle action is running' "$tmp/lock-error"
[[ ! -s "$docker_log" ]]
: >"$docker_log"
run_manager preflight >/dev/null
grep -q 'image inspect' "$docker_log"
: >"$docker_log"
run_manager status >/dev/null
grep -q 'compose' "$docker_log"
kill -TERM "$lock_holder_pid" 2>/dev/null || true
wait "$lock_holder_pid" 2>/dev/null || true
lock_holder_pid=''

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
if run_manager schema-bootstrap >/dev/null 2>&1; then
  printf 'schema bootstrap unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'up -d --no-deps --pull never schema-bootstrap' "$docker_log"; then
  printf 'schema bootstrap ran before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
run_manager schema-bootstrap --acknowledge "BOOTSTRAP:$dataset_id" >/dev/null
python3 - "$docker_log" <<'PY'
import sys
lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
steps = [
    "up -d --no-deps --pull never schema-bootstrap",
    "ps -q schema-bootstrap",
    "State.Health",
    "rm --stop --force schema-bootstrap",
    "down --remove-orphans",
]
positions = [next(i for i, line in enumerate(lines) if step in line) for step in steps]
assert positions == sorted(positions), positions
PY

: >"$docker_log"
if FAKE_BOOTSTRAP_FAIL=1 run_manager schema-bootstrap --acknowledge "BOOTSTRAP:$dataset_id" >/dev/null 2>&1; then
  printf 'schema bootstrap unexpectedly succeeded when detached start failed\n' >&2
  exit 1
fi
grep -q 'down --remove-orphans' "$docker_log"

for state in unhealthy exited starting; do
  : >"$docker_log"
  if FAKE_BOOTSTRAP_STATE="$state" run_manager schema-bootstrap --acknowledge "BOOTSTRAP:$dataset_id" >/dev/null 2>&1; then
    printf 'schema bootstrap unexpectedly succeeded with health state %s\n' "$state" >&2
    exit 1
  fi
  grep -q 'down --remove-orphans' "$docker_log"
done

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
grep -q 'operation=apply|.*run --rm --no-deps --pull never catalog-import --pawcycle.catalog.manifest-import.confirm-apply=true' "$docker_log"
if grep 'operation=validate|' "$docker_log" | grep -q -- '--pawcycle.catalog.manifest-import.confirm-apply'; then
  printf 'validate received the apply confirmation\n' >&2
  exit 1
fi

: >"$docker_log"
if run_manager rehearse >/dev/null 2>&1; then
  printf 'rehearsal unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'up -d --no-deps --pull never schema-bootstrap' "$docker_log"; then
  printf 'rehearsal started before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
if FAKE_EXISTING_CONTAINER=1 run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null 2>&1; then
  printf 'rehearsal accepted an existing performance container\n' >&2
  exit 1
fi
if grep -q 'down --remove-orphans' "$docker_log"; then
  printf 'rehearsal cleaned an existing performance container\n' >&2
  exit 1
fi

for attempt in 1 2; do
  : >"$docker_log"
  : >"$curl_log"
  run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null
  python3 - "$docker_log" <<'PY'
import sys
lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
steps = [
    ("up -d --no-deps --pull never schema-bootstrap",),
    ("ps -q schema-bootstrap",),
    ("State.Health",),
    ("rm --stop --force schema-bootstrap",),
    ("operation=validate|", "run --rm --no-deps --pull never catalog-import"),
    ("operation=apply|", "run --rm --no-deps --pull never catalog-import"),
    ("up -d --pull never backend",),
    ("event|curl|", "/actuator/health/readiness"),
    ("event|curl|", "/api/products"),
    ("down --remove-orphans",),
]
positions = [next(i for i, line in enumerate(lines) if all(part in line for part in step)) for step in steps]
assert positions == sorted(positions), positions
assert "--pawcycle.catalog.manifest-import.confirm-apply=true" in lines[positions[5]]
assert "--pawcycle.catalog.manifest-import.confirm-apply" not in lines[positions[4]]
PY
  grep -q '/actuator/health/readiness' "$curl_log"
  grep -q '/api/products' "$curl_log"
  grep -q -- '--max-time 10 .*actuator/health/readiness' "$curl_log"
  grep -q -- '--max-time 10 .*api/products' "$curl_log"
  if grep -q -- '--volumes' "$docker_log"; then
    printf 'rehearsal cleanup must not remove volumes\n' >&2
    exit 1
  fi
done

for failure in bootstrap import curl; do
  : >"$docker_log"
  case "$failure" in
    bootstrap) FAKE_BOOTSTRAP_FAIL=1 run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null 2>&1 && exit 1 ;;
    import) FAKE_IMPORT_FAIL=1 run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null 2>&1 && exit 1 ;;
    curl) FAKE_CURL_FAIL=1 run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null 2>&1 && exit 1 ;;
  esac
  grep -q 'down --remove-orphans' "$docker_log"
done

: >"$docker_log"
if FAKE_BOOTSTRAP_CLEANUP_FAIL=1 run_manager rehearse --acknowledge "REHEARSE:$dataset_id" >/dev/null 2>&1; then
  printf 'rehearsal unexpectedly continued after schema-bootstrap cleanup failed\n' >&2
  exit 1
fi
python3 - "$docker_log" <<'PY'
import sys
lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
cleanup = next(i for i, line in enumerate(lines) if "rm --stop --force schema-bootstrap" in line)
down = next(i for i, line in enumerate(lines) if "down --remove-orphans" in line)
assert cleanup < down, (cleanup, down)
assert not any("catalog-import" in line for line in lines)
assert not any("up -d --pull never backend" in line for line in lines)
assert not any("event|curl|" in line for line in lines)
PY

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
if grep -Eq 'catalog-import|schema-bootstrap|operation=apply\|' "$docker_log"; then
  printf 'EXACT runtime start unexpectedly performed import/bootstrap\n' >&2
  exit 1
fi
grep -q '/actuator/health/readiness' "$curl_log"
grep -q '/api/products' "$curl_log"
grep -q -- '--max-time 10 .*actuator/health/readiness' "$curl_log"
grep -q -- '--max-time 10 .*api/products' "$curl_log"

# Historical origin fixtures retain identical artifact/generator digests.
python3 - "$dataset_dir/provenance.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
provenance = json.loads(path.read_text(encoding="utf-8"))
provenance["approvedSourceSha"] = "2" * 40
path.chmod(0o644)
path.write_text(json.dumps(provenance, sort_keys=True, indent=2) + "\n", encoding="utf-8")
path.chmod(0o444)
PY
dataset_checksums_before="$(sha256sum "$dataset_dir/manifest.json" "$dataset_dir/report.json" "$dataset_dir/provenance.json")"
for import_failure in 0 1; do
  : >"$docker_log"
  : >"$curl_log"
  if [[ "$import_failure" == 0 ]]; then
    run_manager up --acknowledge "START:$dataset_id" >"$tmp/equivalent-up.log"
  elif FAKE_IMPORT_FAIL=1 run_manager up --acknowledge "START:$dataset_id" >"$tmp/equivalent-up.log" 2>&1; then
    printf 'DIGEST_EQUIVALENT runtime start accepted failed import validation\n' >&2
    exit 1
  fi
  grep -q 'catalog_isolation_preflight=PASS' "$tmp/equivalent-up.log"
  grep -q '"dataset_source_compatibility": "DIGEST_EQUIVALENT"' "$tmp/equivalent-up.log"
  if grep -q 'fixture-password' "$tmp/equivalent-up.log"; then
    printf 'runtime start exposed the DB password\n' >&2
    exit 1
  fi
  python3 - "$docker_log" "$import_failure" "$approved_image" "$compose_file" <<'PY'
import sys

lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
imports = [i for i, line in enumerate(lines) if "run --rm --no-deps --pull never catalog-import" in line]
assert len(imports) == 1, imports
assert "operation=validate|" in lines[imports[0]]
assert "--pawcycle.catalog.manifest-import.confirm-apply" not in lines[imports[0]]
assert sys.argv[3] in next(line for line in lines if "image inspect" in line)
assert "-f " + sys.argv[4] in lines[imports[0]]
assert not any("operation=apply|" in line or "schema-bootstrap" in line for line in lines)
starts = [i for i, line in enumerate(lines) if "up -d --pull never backend" in line]
if sys.argv[2] == "0":
    assert len(starts) == 1 and imports[0] < starts[0], (imports, starts)
    assert any("event|curl|" in line and "/api/products" in line for line in lines)
else:
    assert not starts, starts
    assert not any("down --remove-orphans" in line or "event|curl|" in line for line in lines)
PY
  [[ "$(sha256sum "$dataset_dir/manifest.json" "$dataset_dir/report.json" "$dataset_dir/provenance.json")" == "$dataset_checksums_before" ]]
done

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
    FAKE_SSH_LOG="$ssh_log" \
    FAKE_GATE_SCRIPT_LOG="$tmp/gate-script.log" \
    FAKE_GATE_MODE="${FAKE_GATE_MODE:-normal}" \
    FAKE_SSH_COLLECTOR_MODE="${FAKE_SSH_COLLECTOR_MODE:-normal}" \
    FAKE_K6_HOLD="${FAKE_K6_HOLD:-0}" \
    FAKE_K6_PID_FILE="${FAKE_K6_PID_FILE:-}" \
    FAKE_K6_SIGNAL_LOG="${FAKE_K6_SIGNAL_LOG:-}" \
    FAKE_SSH_PID_FILE="${FAKE_SSH_PID_FILE:-}" \
    FAKE_SSH_SIGNAL_LOG="${FAKE_SSH_SIGNAL_LOG:-}" \
    "$BASH" "$k6_runner" \
    --source-root "$source_root" \
    --target-url 'http://127.0.0.1:18080' \
    --dataset-id "$dataset_id" \
    --results-dir "$results_dir" \
    --acknowledge-isolated-load YES \
    "$@"
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
  [[ "$output" != *'ocid1.'* && "$output" != *'private-marker'* ]]
}

export PAWCYCLE_PERF_OCI_COMPARTMENT_ID='ocid1.compartment.fixture'
export PAWCYCLE_PERF_OCI_DB_SYSTEM_ID='ocid1.mysqldbsystem.fixture'
export OCI_CLI_PROFILE='fixture'
export OCI_CLI_REGION='fixture-region'

assert_capacity_rejected 'unapproved source root' 'k6 runner must run from the approved source root' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --source-root "$tmp"
assert_capacity_rejected 'unsupported dataset' 'Usage:' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --dataset-id unsupported
assert_capacity_rejected 'missing load acknowledgement' 'Usage:' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --acknowledge-isolated-load NO

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
RUNNER_PATH="$missing_python_path" assert_capacity_rejected 'python3 absent' 'Python 3 executable is required for evidence collection' --evidence-ssh-target app01 --isolated-host-port 18081
RUNNER_PATH="$missing_oci_path" assert_capacity_rejected 'OCI CLI absent' 'OCI CLI is required for evidence collection' --evidence-ssh-target app01 --isolated-host-port 18081

alias_path="$tmp/windows-alias"
mkdir -p "$alias_path"
printf '#!/bin/bash\nprintf "Python\\n"\nexit 49\n' >"$alias_path/python3"
chmod +x "$alias_path/python3"
RUNNER_PATH="$alias_path:$fake_bin:$PATH" assert_capacity_rejected 'Windows execution alias' 'Python 3 interpreter preflight failed' --evidence-ssh-target app01 --isolated-host-port 18081
printf '#!/bin/bash\nprintf "2\\n"\n' >"$alias_path/python3"
RUNNER_PATH="$alias_path:$fake_bin:$PATH" assert_capacity_rejected 'Python 2 interpreter' 'Python 3 interpreter preflight failed' --evidence-ssh-target app01 --isolated-host-port 18081
assert_capacity_rejected 'explicit Python absent' 'Python 3 executable is required' --python-executable "$tmp/no-python" --evidence-ssh-target app01 --isolated-host-port 18081

unset OCI_CLI_PROFILE
assert_capacity_rejected 'missing OCI profile context' 'OCI Monitoring query failed for CPUUtilization' --evidence-ssh-target app01 --isolated-host-port 18081
export OCI_CLI_PROFILE='fixture'
unset OCI_CLI_REGION
assert_capacity_rejected 'missing OCI region context' 'OCI Monitoring query failed for CPUUtilization' --evidence-ssh-target app01 --isolated-host-port 18081
export OCI_CLI_REGION='fixture-region'
FAKE_OCI_FAIL=1 assert_capacity_rejected 'invalid OCI authentication context' 'OCI Monitoring query failed for CPUUtilization' --evidence-ssh-target app01 --isolated-host-port 18081
[[ ! -e "$results_dir" ]]

mkdir -p "$results_dir"
printf 'stale\n' >"$results_dir/stale.json"
if run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null 2>&1; then
  printf 'isolated k6 runner accepted a non-empty results directory\n' >&2
  exit 1
fi
[[ ! -s "$k6_log" ]]
rm -f "$results_dir/stale.json"

cp "$tmp/gate-valid-ssh" "$fake_bin/ssh"
: >"$k6_log"
assert_capacity_rejected 'collector start failure' 'Host evidence collector did not start' --evidence-ssh-target app01 --isolated-host-port 18081
clear_capacity_results

cat >"$fake_bin/ssh" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import os
import shlex
import signal
import sys
import time

phases = {
    phase: {"count": 1.0, "sumSeconds": 0.25, "maxSeconds": 0.125}
    for phase in ("count-query", "list-query", "row-mapping", "repository-total")
}
arguments = sys.argv[1:]
command = arguments[-1]
with open(os.environ["FAKE_SSH_LOG"], "a", encoding="utf-8") as stream:
    stream.write(json.dumps({"executable": sys.argv[0], "argv": arguments,
                             "msysArgConvExcl": os.environ.get("MSYS2_ARG_CONV_EXCL")}) + "\n")

if command.startswith("bash -s -- "):
    phase = shlex.split(command)[-1]
    remote_script = sys.stdin.read()
    script_log = os.environ.get("FAKE_GATE_SCRIPT_LOG")
    if script_log:
        with open(script_log, "a", encoding="utf-8") as stream:
            stream.write(remote_script + "\n")
    mode = os.environ.get("FAKE_GATE_MODE", "normal")
    production = ["scope=production", f"generated_at_epoch={int(time.time())}",
                  "production_assessment=READY", "release_coordination=stable",
                  "docker_query=ok", "backend=healthy", "api_products_http=200",
                  "metrics_proxy_http=200"]
    observability = ["status=NORMAL", "production_assessment=READY", "prometheus_target=up"]
    production_exit = 0
    observability_exit = 0
    if mode == "production-failure" and phase == "pre":
        production[2] = "production_assessment=DEGRADED"
        observability = ["status=DEGRADED", "production_assessment=DEGRADED", "prometheus_target=up"]
        production_exit = 1
        observability_exit = 1
    elif mode == "snapshot-validation-failure" and phase == "post":
        production = [line for line in production if not line.startswith("backend=")]
        observability = ["status=UNKNOWN", "production_assessment=UNKNOWN", "prometheus_target=up"]
        observability_exit = 1
    print("__PAWCYCLE_GATE_PRODUCTION_BEGIN__")
    print("\n".join(production))
    print("__PAWCYCLE_GATE_PRODUCTION_END__")
    print("__PAWCYCLE_GATE_OBSERVABILITY_BEGIN__")
    print("\n".join(observability))
    print("__PAWCYCLE_GATE_OBSERVABILITY_END__")
    print(f"__PAWCYCLE_GATE_STATUS__ phase={phase} source_exit=0 production_exit={production_exit} observability_exit={observability_exit} production_read_exit=0 observability_read_exit=0")
    raise SystemExit(0)

collector_mode = os.environ.get("FAKE_SSH_COLLECTOR_MODE", "normal")
if collector_mode == "start-fail":
    raise SystemExit(1)

start = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=10)
sample_seconds = (0,) if collector_mode in {"load-fail", "hold"} else range(0, 121, 5)
def sample(second):
    print(json.dumps({
        "timestampUtc": (start + dt.timedelta(seconds=second)).isoformat().replace("+00:00", "Z"),
        "container": {"restartCount": 0, "oomKilled": False, "health": "healthy"},
        "productDiscoveryPhases": phases,
    }), flush=True)
for second in sample_seconds:
    sample(second)

if collector_mode == "load-fail":
    while not os.path.getsize(os.environ["FAKE_K6_LOG"]):
        time.sleep(0.05)
    time.sleep(3)
    raise SystemExit(1)
if collector_mode == "hold":
    signal_names = {signal.SIGTERM: "TERM", signal.SIGINT: "INT", signal.SIGHUP: "HUP"}
    def stop(signum, _frame):
        path = os.environ.get("FAKE_SSH_SIGNAL_LOG")
        if path:
            with open(path, "a", encoding="utf-8") as stream:
                stream.write(signal_names[signum] + "\n")
        raise SystemExit(0)
    for signum in signal_names:
        signal.signal(signum, stop)
    pid_file = os.environ.get("FAKE_SSH_PID_FILE")
    if pid_file:
        with open(pid_file, "w", encoding="utf-8") as stream:
            stream.write(str(os.getpid()) + "\n")
    while True:
        time.sleep(1)
time.sleep(2)
PY
chmod +x "$fake_bin/ssh"
cp "$fake_bin/ssh" "$tmp/full-capacity-ssh"

assert_capacity_cli_rejected() {
  : >"$ssh_log"
  assert_capacity_rejected "$@"
  [[ ! -s "$ssh_log" ]]
}

assert_capacity_cli_rejected 'unsupported target RPS' 'Usage:' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps 30
assert_capacity_cli_rejected 'malformed target RPS' 'Usage:' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps abc
assert_capacity_cli_rejected 'missing target RPS' 'Usage:' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps

run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null
python3 - "$ssh_log" "$approved_sha" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    calls = [json.loads(line) for line in stream]
assert len(calls) == 18
for stage_index in range(6):
    pre_gate, collector, post_gate = calls[stage_index * 3:stage_index * 3 + 3]
    for call in (pre_gate, collector, post_gate):
        assert call["executable"].endswith("/bin/ssh")
        assert call["msysArgConvExcl"] == "*"
        assert call["argv"][:3] == ["-o", "BatchMode=yes", "app01"]
    assert pre_gate["argv"][3].startswith("bash -s -- ") and pre_gate["argv"][3].endswith(" pre")
    assert post_gate["argv"][3].startswith("bash -s -- ") and post_gate["argv"][3].endswith(" post")
    assert collector["argv"] == [
        "-o", "BatchMode=yes", "app01",
        f"sudo -n python3 '/opt/pawcycle-performance/source/{sys.argv[2]}/infra/performance/catalog-isolated/collect-stage-evidence.py' sample --port '18081' --duration-seconds 165",
    ]
PY
grep -q "^PAWCYCLE_PERF_APP01_ROOT='/opt/pawcycle-performance'$" "$source_root/infra/performance/catalog-isolated/app01-paths.sh"
grep -q "/opt/pawcycle-performance/source/$approved_sha/infra/performance/catalog-isolated/collect-stage-evidence.py" "$ssh_log"
[[ "$(wc -l <"$k6_log")" -eq 6 ]]
[[ -f "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ -f "$results_dir/$dataset_id-250rps-evidence.json" ]]
grep -Fxq 'production_assessment=READY' "$results_dir/$dataset_id-25rps-pre-production-gate.txt"
grep -Fxq 'status=NORMAL' "$results_dir/$dataset_id-25rps-post-observability-gate.txt"
[[ "$(wc -l <"$results_dir/$dataset_id-25rps-pre-production-gate.txt")" -eq 8 ]]
[[ "$(wc -l <"$results_dir/$dataset_id-25rps-post-observability-gate.txt")" -eq 3 ]]
python3 - "$results_dir/$dataset_id-25rps-pre-production-gate.txt" \
  "$results_dir/$dataset_id-25rps-post-observability-gate.txt" <<'PY'
import re
import sys
from pathlib import Path

production_lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
assert [line.split("=", 1)[0] for line in production_lines] == [
    "scope", "generated_at_epoch", "production_assessment", "release_coordination",
    "docker_query", "backend", "api_products_http", "metrics_proxy_http",
]
assert re.fullmatch(r"[0-9]{10}", production_lines[1].split("=", 1)[1])
observability_lines = Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
assert observability_lines == [
    "status=NORMAL", "production_assessment=READY", "prometheus_target=up",
]
PY
grep -Fq 'sudo -n bash "$diagnostic" --scope production >"$production_result"' "$tmp/gate-script.log"
grep -Fq -- '--production-result "$production_result"' "$tmp/gate-script.log"
grep -Fq 'bash "$diagnostic" --scope observability' "$tmp/gate-script.log"
grep -q 'TARGET_RPS=25' "$k6_log"
grep -q 'TARGET_RPS=250' "$k6_log"
grep -q 'ISOLATED_DATASET_ID=catalog-core-control-v1' "$k6_log"

clear_capacity_results
: >"$k6_log"
: >"$ssh_log"
run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps 25 >/dev/null
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
[[ "$(wc -l <"$ssh_log")" -eq 3 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
[[ -f "$results_dir/$dataset_id-25rps.json" ]]
[[ -f "$results_dir/$dataset_id-25rps-host.jsonl" ]]
[[ -f "$results_dir/$dataset_id-25rps-evidence.json" ]]
for phase in pre post; do
  [[ -s "$results_dir/$dataset_id-25rps-$phase-production-gate.txt" ]]
  [[ -s "$results_dir/$dataset_id-25rps-$phase-observability-gate.txt" ]]
  [[ -s "$results_dir/$dataset_id-25rps-$phase-gate-context.txt" ]]
done
[[ "$(find "$results_dir" -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 9 ]]

# Production failure preserves its raw snapshot and the Observability result, and blocks load.
clear_capacity_results
: >"$k6_log"
if FAKE_GATE_MODE=production-failure run_capacity --evidence-ssh-target app01 \
  --isolated-host-port 18081 --target-rps 50 >"$tmp/production-gate-failure" 2>&1; then
  printf 'runner accepted a failed Production gate\n' >&2
  exit 1
fi
[[ ! -s "$k6_log" ]]
grep -Fxq 'production_assessment=DEGRADED' "$results_dir/$dataset_id-50rps-pre-production-gate.txt"
grep -Fxq 'status=DEGRADED' "$results_dir/$dataset_id-50rps-pre-observability-gate.txt"
grep -Fxq 'production_diagnostic_exit=1' "$results_dir/$dataset_id-50rps-pre-gate-context.txt"
grep -Fxq 'observability_diagnostic_exit=1' "$results_dir/$dataset_id-50rps-pre-gate-context.txt"
clear_capacity_results

# An invalid snapshot is retained with Observability UNKNOWN after a completed fake stage.
: >"$k6_log"
if FAKE_GATE_MODE=snapshot-validation-failure run_capacity --evidence-ssh-target app01 \
  --isolated-host-port 18081 >"$tmp/snapshot-validation-failure" 2>&1; then
  printf 'runner accepted an UNKNOWN post-load Observability gate\n' >&2
  exit 1
fi
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
[[ -s "$results_dir/$dataset_id-25rps-evidence.json" ]]
! grep -q '^backend=' "$results_dir/$dataset_id-25rps-post-production-gate.txt"
grep -Fxq 'status=UNKNOWN' "$results_dir/$dataset_id-25rps-post-observability-gate.txt"
grep -Fxq 'production_assessment=UNKNOWN' "$results_dir/$dataset_id-25rps-post-observability-gate.txt"
grep -Fxq 'observability_diagnostic_exit=1' "$results_dir/$dataset_id-25rps-post-gate-context.txt"
clear_capacity_results
selected_python="$tmp/python with spaces/interpreter"
mkdir -p "$(dirname "$selected_python")"
cat >"$selected_python" <<EOF
#!/bin/bash
printf '%s\\n' "\$1" >>"$tmp/python-calls"
if [[ "\$1" == '-c' ]]; then printf '3\\r\\n'; else exec "$python3_path" "\$@"; fi
EOF
chmod +x "$selected_python"
: >"$k6_log"
: >"$ssh_log"
run_capacity --python-executable "$selected_python" --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps 50 >/dev/null
[[ "$(wc -l <"$tmp/python-calls")" -eq 3 ]]
[[ -s "$results_dir/$dataset_id-50rps-evidence.json" ]]
clear_capacity_results

# Linux DEFAULT/config-file auth remains usable without profile/region env overrides.
unset OCI_CLI_PROFILE OCI_CLI_REGION
FAKE_OCI_DEFAULT_CONTEXT=1 run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps 50 >/dev/null
export OCI_CLI_PROFILE='fixture' OCI_CLI_REGION='fixture-region'
clear_capacity_results

# A post-load context failure preserves inputs, stops the series, and never retries load.
export FAKE_OCI_LOG="$tmp/oci-calls" FAKE_OCI_FAIL_ASSEMBLY=1
: >"$FAKE_OCI_LOG"
: >"$k6_log"
if run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 >"$tmp/assembly-failure" 2>&1; then
  printf 'runner accepted automatic assembly failure\n' >&2; exit 1
fi
grep -Fq 'automatic_evidence_assembly=FAIL k6_ok=true' "$tmp/assembly-failure"
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
[[ -s "$results_dir/$dataset_id-25rps.json" && -s "$results_dir/$dataset_id-25rps-host.jsonl" ]]
[[ ! -e "$results_dir/$dataset_id-25rps-evidence.json" ]]
! grep -Eq 'ocid1\.|private-marker' "$tmp/assembly-failure"
unset FAKE_OCI_LOG FAKE_OCI_FAIL_ASSEMBLY
clear_capacity_results
explicit_ssh="$tmp/explicit ssh/selected-ssh"
identity_file='C:/fixture keys/identity'
mkdir -p "$(dirname "$explicit_ssh")"
cp "$fake_bin/ssh" "$explicit_ssh"
# The PATH client must not be used when an executable is explicitly selected.
printf '#!/usr/bin/env bash\nexit 1\n' >"$fake_bin/ssh"
: >"$k6_log"
: >"$ssh_log"
run_capacity --evidence-ssh-target fixture-user@fixture-host --isolated-host-port 18081 \
  --evidence-ssh-executable "$explicit_ssh" --evidence-ssh-identity-file "$identity_file" \
  --target-rps 25 >"$tmp/explicit-output" 2>"$tmp/explicit-error"
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
[[ -s "$results_dir/$dataset_id-25rps.json" ]]
[[ -s "$results_dir/$dataset_id-25rps-host.jsonl" ]]
[[ -s "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ -s "$results_dir/$dataset_id-25rps-pre-production-gate.txt" ]]
[[ -s "$results_dir/$dataset_id-25rps-post-observability-gate.txt" ]]
if grep -Fq "$identity_file" "$tmp/explicit-output" "$tmp/explicit-error"; then
  printf 'SSH identity path appeared in runner output\n' >&2
  exit 1
fi
python3 - "$ssh_log" "$explicit_ssh" "$identity_file" "$approved_sha" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    calls = [json.loads(line) for line in stream]
assert len(calls) == 3
for call in calls:
    assert call["executable"] == sys.argv[2]
    assert call["msysArgConvExcl"] == "*"
    assert call["argv"][:5] == ["-o", "BatchMode=yes", "-i", sys.argv[3], "fixture-user@fixture-host"]
assert calls[0]["argv"][5].startswith("bash -s -- ") and calls[0]["argv"][5].endswith(" pre")
assert calls[1]["argv"][5] == (
    f"sudo -n python3 '/opt/pawcycle-performance/source/{sys.argv[4]}/infra/performance/catalog-isolated/collect-stage-evidence.py' sample --port '18081' --duration-seconds 165"
)
assert calls[2]["argv"][5].startswith("bash -s -- ") and calls[2]["argv"][5].endswith(" post")
PY
clear_capacity_results
assert_capacity_cli_rejected 'explicit SSH executable absent' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable "$tmp/no-such-ssh"
chmod 0644 "$explicit_ssh"
assert_capacity_cli_rejected 'explicit SSH executable not executable' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable "$explicit_ssh"
assert_capacity_cli_rejected 'SSH command string is not an executable' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable 'ssh -v'

cp "$tmp/full-capacity-ssh" "$fake_bin/ssh"
: >"$k6_log"
if FAKE_K6_HOLD=1 FAKE_SSH_COLLECTOR_MODE=load-fail run_capacity \
  --evidence-ssh-target app01 --isolated-host-port 18081 >/dev/null 2>&1; then
  printf 'isolated k6 runner succeeded after evidence collector failure\n' >&2
  exit 1
fi
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
[[ ! -e "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ ! -e "$results_dir/$dataset_id-50rps-host.jsonl" ]]
clear_capacity_results

cp "$tmp/full-capacity-ssh" "$fake_bin/ssh"
signal_dir="$tmp/signal-test"
mkdir -p "$signal_dir"
: >"$k6_log"
PATH="$fake_bin:$PATH" FAKE_K6_LOG="$k6_log" FAKE_K6_HOLD=1 \
  FAKE_SSH_LOG="$tmp/signal-ssh.log" FAKE_GATE_SCRIPT_LOG="$tmp/signal-gate-script.log" \
  FAKE_GATE_MODE=normal FAKE_SSH_COLLECTOR_MODE=hold \
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
