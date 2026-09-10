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

if grep -Eq '(/opt/pawcycle/control|current-sha|previous-sha)' "$RUNBOOK"; then
  printf 'observability OCI runbook still requires retired release-state paths\n' >&2
  exit 1
fi
grep -Fq '/opt/pawcycle/source/repo' "$RUNBOOK"
if grep -Eq 'Config\.Env|\.Config\.Env|docker compose.*(environment|env)' "$IDENTITY"; then
  printf 'runtime identity contract must not inspect or print container environment\n' >&2
  exit 1
fi

printf 'OCI application runtime identity snapshot, mutation, boundary, and no-secret-dump contracts passed\n'
