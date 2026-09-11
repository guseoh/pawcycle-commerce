#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
IDENTITY="$SCRIPT_DIR/verify-observability-application-identity.sh"
RUNBOOK="$SCRIPT_DIR/../../docs/runbook/OPS-OBS-001-production-observability.md"
TEST_ROOT="$(mktemp -d)"
SNAPSHOT="$TEST_ROOT/runtime-identity"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

mkdir -p "$TEST_ROOT/bin"
cat >"$TEST_ROOT/bin/docker" <<'EOF'
#!/usr/bin/env bash

set -Eeuo pipefail

variant="${FAKE_RUNTIME_VARIANT:-stable}"
arguments="$*"

service_for_id() {
  case "$1" in
    backend-id|backend-new-id) printf 'backend' ;;
    frontend-id) printf 'frontend' ;;
    proxy-id) printf 'proxy' ;;
    *) return 1 ;;
  esac
}

if [[ "${1:-}" == ps ]]; then
  [[ "$variant" != query-failure ]] || exit 1
  if [[ "$arguments" == *'com.docker.compose.service='* ]]; then
    case "$arguments" in
      *'com.docker.compose.service=backend'*)
        [[ "$variant" != missing-service ]] && printf '%s\n' "$([[ "$variant" == container-change ]] && printf backend-new-id || printf backend-id)"
        ;;
      *'com.docker.compose.service=frontend'*)
        [[ "$variant" != missing-service ]] && printf 'frontend-id\n'
        ;;
      *'com.docker.compose.service=proxy'*)
        [[ "$variant" != missing-service ]] && printf 'proxy-id\n'
        ;;
      *) exit 1 ;;
    esac
  else
    printf 'backend-id\nfrontend-id\nproxy-id\n'
    [[ "$variant" == extra-service ]] && printf 'unexpected-id\n'
  fi
  exit 0
fi

[[ "${1:-}" == inspect && "${2:-}" == --format ]] || exit 1
format="$3"
container_id="$4"
service="$(service_for_id "$container_id")"

if [[ "$format" == *'{{.Id}}|'* ]]; then
  image_suffix='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  image_id='sha256:backend-image-id'
  compose_project='pawcycle-production'
  compose_service="$service"
  if [[ "$service" == backend && "$variant" == image-change ]]; then
    image_suffix='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    image_id='sha256:changed-backend-image-id'
  fi
  if [[ "$variant" == label-change && "$service" == backend ]]; then
    compose_service='frontend'
  fi
  if [[ "$variant" == project-change && "$service" == backend ]]; then
    compose_project='other-project'
  fi
  case "$service" in
    backend) printf '%s|ghcr.io/example/pawcycle-commerce-backend:%s|%s|%s|%s\n' "$container_id" "$image_suffix" "$image_id" "$compose_project" "$compose_service" ;;
    frontend) printf '%s|ghcr.io/example/pawcycle-commerce-frontend:%s|sha256:frontend-image-id|%s|%s\n' "$container_id" "$image_suffix" "$compose_project" "$compose_service" ;;
    proxy) printf '%s|nginx:1.30.3-alpine3.23@sha256:%064d|sha256:proxy-image-id|%s|%s\n' "$container_id" 1 "$compose_project" "$compose_service" ;;
  esac
  exit 0
fi

if [[ "$format" == *'NetworkID'* ]]; then
  network_id_suffix='stable'
  [[ "$variant" != network-change ]] || network_id_suffix='changed'
  case "$service" in
    backend)
      printf 'pawcycle-production-app = app-network-%s\n' "$network_id_suffix"
      printf 'pawcycle-production-database-egress = database-network-stable\n'
      ;;
    frontend) printf 'pawcycle-production-app = app-network-%s\n' "$network_id_suffix" ;;
    proxy)
      printf 'pawcycle-production-app = app-network-%s\n' "$network_id_suffix"
      printf 'pawcycle-production-edge = edge-network-stable\n'
      ;;
  esac
  exit 0
fi

if [[ "$format" == *'NetworkSettings.Networks'* ]]; then
  case "$service" in
    backend)
      printf 'pawcycle-production-app\n'
      [[ "$variant" != missing-network ]] && printf 'pawcycle-production-database-egress\n'
      ;;
    frontend) printf 'pawcycle-production-app\n' ;;
    proxy)
      printf 'pawcycle-production-app\n'
      printf 'pawcycle-production-edge\n'
      ;;
  esac
  exit 0
fi

exit 1
EOF
chmod +x "$TEST_ROOT/bin/docker"

PATH="$TEST_ROOT/bin:$PATH" FAKE_RUNTIME_VARIANT=stable bash "$IDENTITY" snapshot >"$SNAPSHOT"
chmod 600 "$SNAPSHOT"
PATH="$TEST_ROOT/bin:$PATH" FAKE_RUNTIME_VARIANT=stable bash "$IDENTITY" verify --snapshot "$SNAPSHOT"

for variant in image-change network-change label-change project-change missing-network container-change; do
  if PATH="$TEST_ROOT/bin:$PATH" FAKE_RUNTIME_VARIANT="$variant" bash "$IDENTITY" verify --snapshot "$SNAPSHOT"; then
    printf 'runtime identity mutation unexpectedly passed: %s\n' "$variant" >&2
    exit 1
  fi
done

if PATH="$TEST_ROOT/bin:$PATH" FAKE_RUNTIME_VARIANT=extra-service bash "$IDENTITY" snapshot >"$TEST_ROOT/extra"; then
  printf 'unexpected Compose service unexpectedly passed\n' >&2
  exit 1
fi

if grep -Eq '(/opt/pawcycle/control|current[-_]sha|previous[-_]sha)' "$RUNBOOK"; then
  printf 'observability OCI runbook still requires retired release-state paths\n' >&2
  exit 1
fi
grep -Fq '/opt/pawcycle/source/repo' "$RUNBOOK"
for session_contract in \
  '다음 두 적용 블록은 각각 짧고 독립적인 실행 단위다' \
  '앞선 SSH shell의 local variable' \
  '--project-directory' \
  '--file' \
  '--pull never'; do
  if ! grep -Fq -- "$session_contract" "$RUNBOOK"; then
    printf 'observability OCI runbook missing shell session contract: %s\n' "$session_contract" >&2
    exit 1
  fi
done
for post_start_contract in \
  '{{.State.Status}}' \
  '{{.State.Restarting}}' \
  '{{.RestartCount}}' \
  'sleep 5'; do
  if ! grep -Fq -- "$post_start_contract" "$RUNBOOK"; then
    printf 'observability OCI runbook missing post-start stability contract: %s\n' "$post_start_contract" >&2
    exit 1
  fi
done
if grep -Eq 'Config\.Env|\.Config\.Env|docker compose.*(environment|env)' "$IDENTITY"; then
  printf 'runtime identity contract must not inspect or print container environment\n' >&2
  exit 1
fi

RUNBOOK_BLOCK_DIR="$TEST_ROOT/runbook-blocks"
mkdir -p "$RUNBOOK_BLOCK_DIR"
block_number=0
in_bash_block=false
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" == '```bash' ]]; then
    block_number=$((block_number + 1))
    block_file="$RUNBOOK_BLOCK_DIR/$block_number"
    : >"$block_file"
    in_bash_block=true
  elif [[ "$line" == '```' && "$in_bash_block" == true ]]; then
    in_bash_block=false
  elif [[ "$in_bash_block" == true ]]; then
    printf '%s\n' "$line" >>"$block_file"
  fi
done <"$RUNBOOK"

fail_runbook_contract() {
  printf 'runbook fail-closed contract violated: %s\n' "$1" >&2
  exit 1
}

assert_block_contains() {
  local block_file="$1" expected="$2" description="$3"
  grep -Fq -- "$expected" "$block_file" || fail_runbook_contract "$description"
}

checksum_block=''
snapshot_block=''
compose_up_blocks=0
for block_file in "$RUNBOOK_BLOCK_DIR"/*; do
  [[ -f "$block_file" ]] || continue
  if grep -Fq 'EXPECTED_IDENTITY_SHA256' "$block_file"; then
    checksum_block="$block_file"
  fi
  if grep -Fq 'snapshot > "$RUNTIME_IDENTITY"' "$block_file"; then
    snapshot_block="$block_file"
  fi
  if grep -Fq 'up --detach' "$block_file"; then
    compose_up_blocks=$((compose_up_blocks + 1))
    before_up="$RUNBOOK_BLOCK_DIR/before-up-$compose_up_blocks"
    sed '/up --detach/,$d' "$block_file" >"$before_up"
    assert_block_contains "$before_up" 'set -Eeuo pipefail' 'compose up block lacks fail-closed shell boundary'
    assert_block_contains "$before_up" 'snapshot > "$RUNTIME_IDENTITY"' 'compose up block lacks runtime identity snapshot'
    assert_block_contains "$before_up" 'IDENTITY_SCRIPT="$SOURCE_ROOT/infra/production/verify-observability-application-identity.sh"' 'compose up block lacks approved identity verifier path'
  fi
done

[[ -n "$checksum_block" ]] || fail_runbook_contract 'checksum block not found'
assert_block_contains "$checksum_block" 'set -Eeuo pipefail' 'checksum block lacks fail-closed shell boundary'
assert_block_contains "$checksum_block" 'if ! EXPECTED_IDENTITY_SHA256=' 'identity verifier checksum source failure is not explicit'
assert_block_contains "$checksum_block" 'if ! test "$(sha256sum "$IDENTITY_SCRIPT"' 'identity checksum failure is not explicit'
assert_block_contains "$checksum_block" 'exit 1' 'checksum failure does not terminate the bootstrap'

[[ -n "$snapshot_block" ]] || fail_runbook_contract 'snapshot block not found'
assert_block_contains "$snapshot_block" 'set -Eeuo pipefail' 'snapshot block lacks fail-closed shell boundary'
assert_block_contains "$snapshot_block" 'if ! sudo bash "$IDENTITY_SCRIPT" snapshot > "$RUNTIME_IDENTITY"; then' 'snapshot failure is not explicit'
assert_block_contains "$snapshot_block" 'rm -f -- "$RUNTIME_IDENTITY"' 'snapshot failure does not remove the invalid snapshot'
assert_block_contains "$snapshot_block" 'verify --snapshot "$RUNTIME_IDENTITY"' 'snapshot block does not validate the snapshot before mutation'
assert_block_contains "$snapshot_block" 'exit 1' 'snapshot failure does not terminate the pre-apply block'

[[ "$compose_up_blocks" == 2 ]] || fail_runbook_contract "expected two guarded compose up blocks, found $compose_up_blocks"

printf 'OCI application runtime identity snapshot, mutation, boundary, and no-secret-dump contracts passed\n'
