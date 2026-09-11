#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_NAME="${PAWCYCLE_PRODUCTION_PROJECT:-pawcycle-production}"
APP_NETWORK="${PAWCYCLE_APP_NETWORK:-pawcycle-production-app}"
EDGE_NETWORK="${PAWCYCLE_EDGE_NETWORK:-pawcycle-production-edge}"
DATABASE_EGRESS_NETWORK="${PAWCYCLE_DATABASE_EGRESS_NETWORK:-pawcycle-production-database-egress}"
MODE=""
SNAPSHOT_PATH=""
TEMP_DIR=""

SERVICES=(backend frontend proxy)

usage() {
  printf 'usage: %s snapshot\n' "$0" >&2
  printf '       %s verify --snapshot <path>\n' "$0" >&2
  exit 64
}

valid_network_name() {
  [[ "$1" =~ ^[a-zA-Z0-9_.-]+$ ]]
}

validate_configuration() {
  valid_network_name "$PROJECT_NAME" \
    && valid_network_name "$APP_NETWORK" \
    && valid_network_name "$EDGE_NETWORK" \
    && valid_network_name "$DATABASE_EGRESS_NETWORK"
}

expected_networks() {
  local service="$1"
  case "$service" in
    backend) printf '%s\n' "$APP_NETWORK" "$DATABASE_EGRESS_NETWORK" | sort | paste -sd, - ;;
    frontend) printf '%s\n' "$APP_NETWORK" ;;
    proxy) printf '%s\n' "$APP_NETWORK" "$EDGE_NETWORK" | sort | paste -sd, - ;;
    *) return 1 ;;
  esac
}

container_ids_for() {
  local service="$1"
  docker ps --all --no-trunc --quiet \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter "label=com.docker.compose.service=$service" 2>/dev/null
}

all_project_container_ids() {
  docker ps --all --no-trunc --quiet \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" 2>/dev/null
}

inspect_identity() {
  local service="$1" container_id="$2" record inspected_id image_ref image_id compose_project compose_service networks expected network_ids

  record="$(docker inspect --format '{{.Id}}|{{.Config.Image}}|{{.Image}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' "$container_id")" || return 1
  IFS='|' read -r inspected_id image_ref image_id compose_project compose_service <<<"$record"
  [[ -n "$inspected_id" && "$inspected_id" == "$container_id" ]] || return 1
  [[ -n "$image_ref" && -n "$image_id" ]] || return 1
  [[ "$compose_project" == "$PROJECT_NAME" && "$compose_service" == "$service" ]] || return 1

  case "$service" in
    backend|frontend) [[ "$image_ref" =~ :[0-9a-f]{40}$ ]] || return 1 ;;
    proxy) [[ "$image_ref" =~ @sha256:[0-9a-f]{64}$ ]] || return 1 ;;
    *) return 1 ;;
  esac

  networks="$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' "$container_id" | sed '/^$/d' | sort | paste -sd, -)" || return 1
  expected="$(expected_networks "$service")"
  [[ -n "$networks" && "$networks" == "$expected" ]] || return 1

  network_ids="$(docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{println $name "=" $network.NetworkID}}{{end}}' "$container_id" | sed '/^$/d' | sort | paste -sd, -)" || return 1
  [[ -n "$network_ids" ]] || return 1

  printf 'service.%s.container_id=%s\n' "$service" "$container_id"
  printf 'service.%s.image_ref=%s\n' "$service" "$image_ref"
  printf 'service.%s.image_id=%s\n' "$service" "$image_id"
  printf 'service.%s.compose_project=%s\n' "$service" "$compose_project"
  printf 'service.%s.compose_service=%s\n' "$service" "$compose_service"
  printf 'service.%s.networks=%s\n' "$service" "$network_ids"
}

capture_snapshot() {
  local project_ids service ids container_id project_id known
  local -a project_id_list service_id_list

  validate_configuration || return 1
  if ! project_ids="$(all_project_container_ids)"; then
    return 1
  fi
  mapfile -t project_id_list < <(printf '%s\n' "$project_ids" | sed '/^$/d')
  ((${#project_id_list[@]} == ${#SERVICES[@]})) || return 1
  [[ "$(printf '%s\n' "${project_id_list[@]}" | sort -u | wc -l)" == "${#SERVICES[@]}" ]] || return 1

  printf 'format=oci-application-runtime-identity-v1\n'
  printf 'project=%s\n' "$PROJECT_NAME"
  printf 'service_set=%s\n' "$(IFS=,; printf '%s' "${SERVICES[*]}")"
  printf 'app_network=%s\n' "$APP_NETWORK"
  printf 'edge_network=%s\n' "$EDGE_NETWORK"
  printf 'database_egress_network=%s\n' "$DATABASE_EGRESS_NETWORK"

  for service in "${SERVICES[@]}"; do
    if ! ids="$(container_ids_for "$service")"; then
      return 1
    fi
    mapfile -t service_id_list < <(printf '%s\n' "$ids" | sed '/^$/d')
    ((${#service_id_list[@]} == 1)) || return 1
    container_id="${service_id_list[0]}"
    known=false
    for project_id in "${project_id_list[@]}"; do
      if [[ "$project_id" == "$container_id" ]]; then
        known=true
        break
      fi
    done
    [[ "$known" == true ]] || return 1
    local identity
    if ! identity="$(inspect_identity "$service" "$container_id")"; then
      return 1
    fi
    printf '%s\n' "$identity"
  done
}

declare -A SNAPSHOT=()

load_snapshot() {
  local key value
  [[ -f "$SNAPSHOT_PATH" && ! -L "$SNAPSHOT_PATH" ]] || return 1
  while IFS='=' read -r key value; do
    [[ -n "$key" && -n "$value" ]] || return 1
    [[ ! -v "SNAPSHOT[$key]" ]] || return 1
    case "$key" in
      format|project|service_set|app_network|edge_network|database_egress_network) ;;
      service.backend.container_id|service.backend.image_ref|service.backend.image_id|service.backend.compose_project|service.backend.compose_service|service.backend.networks) ;;
      service.frontend.container_id|service.frontend.image_ref|service.frontend.image_id|service.frontend.compose_project|service.frontend.compose_service|service.frontend.networks) ;;
      service.proxy.container_id|service.proxy.image_ref|service.proxy.image_id|service.proxy.compose_project|service.proxy.compose_service|service.proxy.networks) ;;
      *) return 1 ;;
    esac
    SNAPSHOT["$key"]="$value"
  done <"$SNAPSHOT_PATH"

  [[ "${SNAPSHOT[format]:-}" == oci-application-runtime-identity-v1 ]] || return 1
  [[ "${SNAPSHOT[project]:-}" == "$PROJECT_NAME" ]] || return 1
  [[ "${SNAPSHOT[service_set]:-}" == backend,frontend,proxy ]] || return 1
  [[ "${SNAPSHOT[app_network]:-}" == "$APP_NETWORK" ]] || return 1
  [[ "${SNAPSHOT[edge_network]:-}" == "$EDGE_NETWORK" ]] || return 1
  [[ "${SNAPSHOT[database_egress_network]:-}" == "$DATABASE_EGRESS_NETWORK" ]] || return 1

  local service field
  for service in "${SERVICES[@]}"; do
    for field in container_id image_ref image_id compose_project compose_service networks; do
      [[ -n "${SNAPSHOT[service.$service.$field]:-}" ]] || return 1
    done
  done
}

verify_snapshot() {
  local current_snapshot status
  TEMP_DIR="$(mktemp -d)"
  current_snapshot="$TEMP_DIR/current"
  umask 077
  if ! capture_snapshot >"$current_snapshot"; then
    rm -rf -- "$TEMP_DIR"
    return 1
  fi
  chmod 600 "$current_snapshot"
  if cmp --silent "$SNAPSHOT_PATH" "$current_snapshot"; then
    status=0
  else
    status=1
  fi
  rm -rf -- "$TEMP_DIR"
  return "$status"
}

[[ $# -ge 1 ]] || usage
MODE="$1"
shift
case "$MODE" in
  snapshot)
    (($# == 0)) || usage
    capture_snapshot
    ;;
  verify)
    [[ $# -eq 2 && "$1" == --snapshot ]] || usage
    SNAPSHOT_PATH="$2"
    load_snapshot || exit 1
    verify_snapshot
    ;;
  *) usage ;;
esac
