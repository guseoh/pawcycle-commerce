#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="infra/production/import-demo-catalog.sh"
AUTH_SCRIPT="infra/production/create-production-auth-smoke-member.sh"
bash -n "$SCRIPT"
bash -n "$AUTH_SCRIPT"
help_output="$(bash "$SCRIPT" --help)"
grep -Fq -- '--operation <validate|apply>' <<<"$help_output"
grep -Fq -- '--target <demo|customer>' <<<"$help_output"
grep -Fq -- '--identity-mode <release-state|running-container>' <<<"$help_output"
grep -Fq -- 'The default target is demo.' <<<"$help_output"
grep -Fq -- 'The default identity mode is release-state.' <<<"$help_output"
grep -Fq -- 'running-container is an explicit OCI-compatible identity path and never falls back automatically.' <<<"$help_output"
grep -Fq -- '--confirm-apply' <<<"$help_output"
grep -Fq -- '[[ "$TARGET" == "demo" || "$TARGET" == "customer" ]]' "$SCRIPT"
grep -Fq -- '[[ "$IDENTITY_MODE" == "release-state" || "$IDENTITY_MODE" == "running-container" ]]' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.enabled=true' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.target="$TARGET"' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.mode=' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.confirm-apply=true' "$SCRIPT"
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT\ status=PASS' "$SCRIPT"
grep -Fq -- 'IMPORT_ARGUMENTS+=(--pawcycle.catalog.manifest-import.manifest=classpath:catalog/demo-catalog.json)' "$SCRIPT"
! grep -Fq -- 'timeout --signal=' "$SCRIPT"

# release-state remains the default and keeps the existing registry/state/revision checks.
grep -Fq -- 'IDENTITY_MODE="release-state"' "$SCRIPT"
grep -Fq -- 'CURRENT_SHA_FILE="$STATE_DIR/current-sha"' "$SCRIPT"
grep -Fq -- 'IMAGE_STATE_FILE="$STATE_DIR/$RELEASE_SHA.images"' "$SCRIPT"
grep -Fq -- 'RUNTIME_CURRENT="$(readlink -f -- "$RUNTIME_DIR/current"' "$SCRIPT"
grep -Fq -- 'BACKEND_DIGEST_LINE=' "$SCRIPT"
grep -Fq -- '--format '\''{{ index .Config.Labels "org.opencontainers.image.revision" }}'\''' "$SCRIPT"
grep -Fq -- 'RUN_IMAGE="$BACKEND_DIGEST"' "$SCRIPT"

# running-container uses executable fake Docker/Git fixtures so ordering and fail-closed behavior are tested.
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$FIXTURE_ROOT"' EXIT
FAKE_BIN="$FIXTURE_ROOT/bin"
RUNTIME_FIXTURE="$FIXTURE_ROOT/runtime"
STATE_FIXTURE="$FIXTURE_ROOT/state"
DOCKER_LOG="$FIXTURE_ROOT/docker.log"
GIT_LOG="$FIXTURE_ROOT/git.log"
mkdir -p "$FAKE_BIN" "$RUNTIME_FIXTURE" "$STATE_FIXTURE"
chmod 700 "$RUNTIME_FIXTURE" "$STATE_FIXTURE"

write_unquoted_backend_env() {
  cat > "$RUNTIME_FIXTURE/backend.env" <<'ENVEOF'
PAWCYCLE_DATASOURCE_HOST=db.internal
PAWCYCLE_DATASOURCE_PORT=3306
PAWCYCLE_DATASOURCE_SSL_MODE=REQUIRED
SPRING_DATASOURCE_URL=jdbc:mysql://db.internal:3306/pawcycle
SPRING_DATASOURCE_USERNAME=pawcycle_app
SPRING_DATASOURCE_PASSWORD=fixture-password
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=20
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=60000
ENVEOF
  chmod 600 "$RUNTIME_FIXTURE/backend.env"
}

write_quoted_backend_env() {
  cat > "$RUNTIME_FIXTURE/backend.env" <<'ENVEOF'
PAWCYCLE_DATASOURCE_HOST='db.internal'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'
SPRING_DATASOURCE_URL='jdbc:mysql://db.internal:3306/pawcycle'
SPRING_DATASOURCE_USERNAME='pawcycle_app'
SPRING_DATASOURCE_PASSWORD='fixture-password'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='20'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='60000'
ENVEOF
  chmod 600 "$RUNTIME_FIXTURE/backend.env"
}

write_unquoted_backend_env

cat > "$FAKE_BIN/git" <<'GITEOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%q ' "$@" >> "$FAKE_GIT_LOG"
printf '\n' >> "$FAKE_GIT_LOG"
if [[ "${FAKE_MISMATCH:-}" == "sha" && "$*" == *" cat-file -e "* ]]; then
  exit 1
fi
case " $* " in
  *" cat-file -e "*|*" merge-base --is-ancestor "*) exit 0 ;;
  *) exit 2 ;;
esac
GITEOF
chmod +x "$FAKE_BIN/git"

cat > "$FAKE_BIN/docker" <<'DOCKEREOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%q ' "$@" >> "$FAKE_DOCKER_LOG"
printf '\n' >> "$FAKE_DOCKER_LOG"

command_name="${1:-}"
shift || true
case "$command_name" in
  ps)
    printf '%s\n' 'backend-short-id'
    ;;
  inspect)
    [[ "${1:-}" == "--format" ]] || exit 2
    format="${2:-}"
    target="${3:-}"
    case "$format" in
      '{{.State.Status}}') printf '%s\n' 'running' ;;
      '{{.State.Health.Status}}') printf '%s\n' 'healthy' ;;
      '{{.Config.Image}}') printf 'pawcycle-backend:%s\n' "$FAKE_RELEASE_SHA" ;;
      '{{range $network_name, $network_attachment := .NetworkSettings.Networks}}{{println $network_name}}{{end}}')
        printf '%s\n' 'pawcycle-production-app' 'pawcycle-production-database-egress'
        ;;
      '{{.Id}}') printf '%s\n' 'backend-full-id' ;;
      '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}')
        if [[ "${FAKE_MISMATCH:-}" == "compose" ]]; then
          printf '%s\n' '/wrong/working-dir'
        else
          printf '%s\n' "$FAKE_SCRIPT_DIR"
        fi
        ;;
      '{{ index .Config.Labels "com.docker.compose.project.config_files" }}')
        printf '%s/compose.yaml\n' "$FAKE_SCRIPT_DIR"
        ;;
      '{{.Image}}') printf '%s\n' 'sha256:validated-running-image' ;;
      *) printf 'unexpected docker inspect format: %s target=%s\n' "$format" "$target" >&2; exit 3 ;;
    esac
    ;;
  network)
    [[ "${1:-}" == "inspect" && "${2:-}" == "--format" ]] || exit 2
    format="${3:-}"
    network_name="${4:-}"
    case "$format" in
      '{{.Internal}}')
        case "$network_name" in
          pawcycle-production-app) printf '%s\n' 'true' ;;
          pawcycle-production-database-egress) printf '%s\n' 'false' ;;
          *) exit 3 ;;
        esac
        ;;
      '{{range $container_id, $container := .Containers}}{{println $container_id}}{{end}}')
        printf '%s\n' 'backend-full-id'
        if [[ "${FAKE_MISMATCH:-}" == "network" ]]; then
          printf '%s\n' 'unexpected-member-id'
        fi
        ;;
      *) exit 3 ;;
    esac
    ;;
  container)
    [[ "${1:-}" == "inspect" ]] || exit 2
    exit 1
    ;;
  image)
    [[ "${1:-}" == "inspect" && "${2:-}" == "--format" && "${3:-}" == '{{.Id}}' ]] || exit 2
    if [[ "${FAKE_MISMATCH:-}" == "image" ]]; then
      printf '%s\n' 'sha256:different-local-image'
    else
      printf '%s\n' 'sha256:validated-running-image'
    fi
    ;;
  run)
    run_args="$*"
    env_file=""
    while (( $# > 0 )); do
      case "$1" in
        --env-file)
          env_file="${2:-}"
          shift 2
          ;;
        *) shift ;;
      esac
    done
    [[ -n "$env_file" && -r "$env_file" ]] || exit 4
    env_content="$(cat "$env_file")"
    grep -Fxq 'PAWCYCLE_DATASOURCE_HOST=db.internal' <<<"$env_content" || exit 5
    grep -Fxq 'PAWCYCLE_DATASOURCE_PORT=3306' <<<"$env_content" || exit 5
    grep -Fxq 'PAWCYCLE_DATASOURCE_SSL_MODE=REQUIRED' <<<"$env_content" || exit 5
    grep -Fxq 'SPRING_DATASOURCE_URL=jdbc:mysql://db.internal:3306/pawcycle' <<<"$env_content" || exit 5
    grep -Fxq 'SPRING_DATASOURCE_USERNAME=pawcycle_app' <<<"$env_content" || exit 5
    grep -Fxq 'SPRING_DATASOURCE_PASSWORD=fixture-password' <<<"$env_content" || exit 5
    grep -Fxq 'PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false' <<<"$env_content" || exit 5
    if [[ " $run_args " != *" sha256:validated-running-image "* ]]; then
      printf '%s\n' 'docker run did not receive the validated image ID' >&2
      exit 6
    fi
    if [[ " $run_args " == *"--pawcycle.catalog.manifest-import.mode=apply"* ]]; then
      [[ " $run_args " == *"--pawcycle.catalog.manifest-import.confirm-apply=true"* ]] || exit 7
      printf '%s\n' 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS baseline={CATALOG_IMPORT_RESULT operation=APPLY status=PASS} supplement={status=PASS}'
    else
      printf '%s\n' 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS baseline={CATALOG_IMPORT_RESULT operation=VALIDATE status=PASS} supplement={status=PASS}'
    fi
    ;;
  *)
    printf 'unexpected docker command: %s\n' "$command_name" >&2
    exit 2
    ;;
esac
DOCKEREOF
chmod +x "$FAKE_BIN/docker"

RELEASE_SHA_FIXTURE='1111111111111111111111111111111111111111'
SCRIPT_DIR_ABS="$(cd "$(dirname "$SCRIPT")" && pwd -P)"

run_running_container_fixture() {
  local operation="$1"
  local mismatch="${2:-}"
  local -a apply_args=()
  if [[ "$operation" == "apply" ]]; then
    apply_args+=(--confirm-apply)
  fi
  : > "$DOCKER_LOG"
  : > "$GIT_LOG"
  sudo env \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_DOCKER_LOG="$DOCKER_LOG" \
    FAKE_GIT_LOG="$GIT_LOG" \
    FAKE_MISMATCH="$mismatch" \
    FAKE_RELEASE_SHA="$RELEASE_SHA_FIXTURE" \
    FAKE_SCRIPT_DIR="$SCRIPT_DIR_ABS" \
    bash "$SCRIPT" \
      --target customer \
      --operation "$operation" \
      --identity-mode running-container \
      --sha "$RELEASE_SHA_FIXTURE" \
      --backend-image pawcycle-backend \
      --runtime-dir "$RUNTIME_FIXTURE" \
      --state-dir "$STATE_FIXTURE" \
      "${apply_args[@]}"
}

# Current OCI runtime format is unquoted KEY=value. Validate and apply must both preserve values exactly.
for operation in validate apply; do
  output="$(run_running_container_fixture "$operation")"
  grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS' <<<"$output"
  grep -Fq -- 'cat-file -e' "$GIT_LOG"
  grep -Fq -- 'merge-base --is-ancestor' "$GIT_LOG"
  grep -Fq -- 'com.docker.compose.project.working_dir' "$DOCKER_LOG"
  grep -Fq -- 'com.docker.compose.project.config_files' "$DOCKER_LOG"
  grep -Fq -- 'image inspect --format' "$DOCKER_LOG"
  grep -Fq -- 'network inspect --format' "$DOCKER_LOG"
  grep -Fq -- 'run ' "$DOCKER_LOG"
  grep -Fq -- 'sha256:validated-running-image' "$DOCKER_LOG"
done

for mismatch in sha compose image network; do
  for operation in validate apply; do
    if run_running_container_fixture "$operation" "$mismatch" >/dev/null 2>&1; then
      printf 'FAIL: running-container %s mismatch did not fail closed in %s mode\n' "$mismatch" "$operation" >&2
      exit 1
    fi
  done
done

# Existing managed release bundles may use single-quoted values. They must decode to the same Docker env contract.
write_quoted_backend_env
output="$(run_running_container_fixture validate)"
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS' <<<"$output"

# Partial quoting is malformed and must fail before Docker run.
cat > "$RUNTIME_FIXTURE/backend.env" <<'ENVEOF'
PAWCYCLE_DATASOURCE_HOST='db.internal
PAWCYCLE_DATASOURCE_PORT=3306
PAWCYCLE_DATASOURCE_SSL_MODE=REQUIRED
SPRING_DATASOURCE_URL=jdbc:mysql://db.internal:3306/pawcycle
SPRING_DATASOURCE_USERNAME=pawcycle_app
SPRING_DATASOURCE_PASSWORD=fixture-password
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=20
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=60000
ENVEOF
chmod 600 "$RUNTIME_FIXTURE/backend.env"
if run_running_container_fixture validate >/dev/null 2>&1; then
  printf 'FAIL: malformed Backend runtime quoting did not fail closed\n' >&2
  exit 1
fi

# The running-container path must not silently synthesize legacy release state.
! grep -Fq -- 'printf '\''%s\n'\'' "$RELEASE_SHA" > "$STATE_DIR/current-sha"' "$SCRIPT"
! grep -Fq -- 'touch "$STATE_DIR/$RELEASE_SHA.images"' "$SCRIPT"
! grep -Fq -- 'ln -s' "$SCRIPT"

for wrapper in "$SCRIPT" "$AUTH_SCRIPT"; do
  ! grep -Eq -- 'DATA_NETWORK|pawcycle-production-data|com\.docker\.compose\.service=mysql|MYSQL_(CONTAINER|CONTAINERS)' "$wrapper"
  grep -Fq -- 'NetworkSettings.Networks' "$wrapper"
  grep -Fq -- "network inspect --format '{{.Internal}}'" "$wrapper"
  grep -Fq -- 'production Backend database network is ambiguous' "$wrapper"
  grep -Fq -- 'DATABASE_EGRESS_NETWORK' "$wrapper"
  grep -Fq -- '--network "$DATABASE_EGRESS_NETWORK"' "$wrapper"
  grep -Fq -- 'database egress network membership is invalid' "$wrapper"
done
grep -Fq -- 'database-egress' infra/production/compose.yaml
! grep -Eq -- '^  mysql:' infra/production/compose.yaml
grep -Fq -- 'postflight' backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogImportResult.java
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS' backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportResult.java
printf 'PASS: production catalog import target, runtime identity, and Backend env contract\n'
