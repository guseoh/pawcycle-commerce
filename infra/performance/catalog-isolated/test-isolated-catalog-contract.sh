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
grep -q '/actuator/health/readiness' "$curl_log"
grep -q '/api/products' "$curl_log"
grep -q -- '--max-time 10 .*actuator/health/readiness' "$curl_log"
grep -q -- '--max-time 10 .*api/products' "$curl_log"

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
    FAKE_K6_HOLD="${FAKE_K6_HOLD:-0}" \
    FAKE_K6_PID_FILE="${FAKE_K6_PID_FILE:-}" \
    FAKE_K6_SIGNAL_LOG="${FAKE_K6_SIGNAL_LOG:-}" \
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
}

export PAWCYCLE_PERF_OCI_COMPARTMENT_ID='ocid1.compartment.fixture'
export PAWCYCLE_PERF_OCI_DB_SYSTEM_ID='ocid1.mysqldbsystem.fixture'

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
import os
import sys
import time

phases = {
    phase: {"count": 1.0, "sumSeconds": 0.25, "maxSeconds": 0.125}
    for phase in ("count-query", "list-query", "row-mapping", "repository-total")
}
with open(os.environ["FAKE_SSH_LOG"], "a", encoding="utf-8") as stream:
    stream.write(json.dumps({"executable": sys.argv[0], "argv": sys.argv[1:],
                             "msysArgConvExcl": os.environ.get("MSYS2_ARG_CONV_EXCL")}) + "\n")
start = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=10)
for second in range(0, 121, 5):
    print(json.dumps({
        "timestampUtc": (start + dt.timedelta(seconds=second)).isoformat().replace("+00:00", "Z"),
        "container": {"restartCount": 0, "oomKilled": False, "health": "healthy"},
        "productDiscoveryPhases": phases,
    }), flush=True)
time.sleep(2)
PY
chmod +x "$fake_bin/ssh"

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
assert len(calls) == 6
for call in calls:
    assert call["executable"].endswith("/bin/ssh")
    assert call["msysArgConvExcl"] == "*"
    assert call["argv"] == [
        "-o", "BatchMode=yes", "app01",
        f"sudo -n python3 '/opt/pawcycle-performance/source/{sys.argv[2]}/infra/performance/catalog-isolated/collect-stage-evidence.py' sample --port '18081' --duration-seconds 165",
    ]
PY
grep -q "^PAWCYCLE_PERF_APP01_ROOT='/opt/pawcycle-performance'$" "$source_root/infra/performance/catalog-isolated/app01-paths.sh"
grep -q "/opt/pawcycle-performance/source/$approved_sha/infra/performance/catalog-isolated/collect-stage-evidence.py" "$ssh_log"
[[ "$(wc -l <"$k6_log")" -eq 6 ]]
[[ -f "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ -f "$results_dir/$dataset_id-250rps-evidence.json" ]]
grep -q 'TARGET_RPS=25' "$k6_log"
grep -q 'TARGET_RPS=250' "$k6_log"
grep -q 'ISOLATED_DATASET_ID=catalog-core-control-v1' "$k6_log"

clear_capacity_results
: >"$k6_log"
: >"$ssh_log"
run_capacity --evidence-ssh-target app01 --isolated-host-port 18081 --target-rps 25 >/dev/null
[[ "$(wc -l <"$k6_log")" -eq 1 ]]
[[ "$(wc -l <"$ssh_log")" -eq 1 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
[[ -f "$results_dir/$dataset_id-25rps.json" ]]
[[ -f "$results_dir/$dataset_id-25rps-host.jsonl" ]]
[[ -f "$results_dir/$dataset_id-25rps-evidence.json" ]]
[[ "$(find "$results_dir" -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 3 ]]

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
if grep -Fq "$identity_file" "$tmp/explicit-output" "$tmp/explicit-error"; then
  printf 'SSH identity path appeared in runner output\n' >&2
  exit 1
fi
python3 - "$ssh_log" "$explicit_ssh" "$identity_file" "$approved_sha" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    calls = [json.loads(line) for line in stream]
assert len(calls) == 1
call = calls[0]
assert call["executable"] == sys.argv[2]
assert call["msysArgConvExcl"] == "*"
assert call["argv"] == [
    "-o", "BatchMode=yes", "-i", sys.argv[3], "fixture-user@fixture-host",
    f"sudo -n python3 '/opt/pawcycle-performance/source/{sys.argv[4]}/infra/performance/catalog-isolated/collect-stage-evidence.py' sample --port '18081' --duration-seconds 165",
]
PY
clear_capacity_results
assert_capacity_cli_rejected 'explicit SSH executable absent' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable "$tmp/no-such-ssh"
chmod 0644 "$explicit_ssh"
assert_capacity_cli_rejected 'explicit SSH executable not executable' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable "$explicit_ssh"
assert_capacity_cli_rejected 'SSH command string is not an executable' 'ssh is required for evidence collection' \
  --evidence-ssh-target app01 --isolated-host-port 18081 --evidence-ssh-executable 'ssh -v'

cat >"$fake_bin/ssh" <<'PY'
#!/usr/bin/env python3
import datetime as dt
import json
import os
import time

phases = {
    phase: {"count": 1.0, "sumSeconds": 0.25, "maxSeconds": 0.125}
    for phase in ("count-query", "list-query", "row-mapping", "repository-total")
}
print(json.dumps({"timestampUtc": "2026-09-24T00:00:00Z",
                  "container": {"restartCount": 0, "oomKilled": False, "health": "healthy"},
                  "productDiscoveryPhases": phases}), flush=True)
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
printf '{"timestampUtc":"2026-09-24T00:00:00Z","container":{"restartCount":0,"oomKilled":false,"health":"healthy"},"productDiscoveryPhases":{"count-query":{"count":1.0,"sumSeconds":0.25,"maxSeconds":0.125},"list-query":{"count":1.0,"sumSeconds":0.25,"maxSeconds":0.125},"row-mapping":{"count":1.0,"sumSeconds":0.25,"maxSeconds":0.125},"repository-total":{"count":1.0,"sumSeconds":0.25,"maxSeconds":0.125}}}\n'
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
