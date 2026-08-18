#!/usr/bin/env bash
# Read-only gate: no AWS, Compose, or database mutation.
set -Eeuo pipefail
case "$-" in *x*) set +x ;; esac
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/release-common.sh"
die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }
sha(){ [[ "$1" =~ ^[0-9a-f]{64}$ ]] || die 'invalid SHA-256 value'; }
git_sha(){ [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die 'invalid Git SHA'; }
regular600(){ [[ -f "$1" && ! -L "$1" && "$(stat -c '%a' "$1")" == 600 ]] || die "must be a regular mode-600 file: $1"; }
root_dir(){ [[ "$1" == /* && -d "$1" && ! -L "$1" ]] || die "$2 must be an existing absolute non-symlink directory"; }
record_value(){ local f=$1 k=$2 n v; regular600 "$f"; n="$(grep -Ec "^${k}=[^=[:cntrl:]]+$" "$f" || true)"; [[ "$n" == 1 ]] || die "record key must appear exactly once: $k"; v="$(sed -n "s/^${k}=//p" "$f")"; printf '%s' "$v"; }
exact_record(){ local f=$1 line k; shift; local -A allowed=(); for k in "$@"; do allowed[$k]=1; done; regular600 "$f"; while IFS= read -r line || [[ -n "$line" ]]; do [[ "$line" =~ ^([A-Z][A-Za-z0-9_]*)=([^=[:cntrl:]]+)$ ]] || die 'record line is malformed'; k="${BASH_REMATCH[1]}"; [[ -n "${allowed[$k]:-}" ]] || die "record contains an unknown or secret-shaped key: $k"; done < "$f"; for k in "$@"; do record_value "$f" "$k" >/dev/null; done; }
nonnegative(){ [[ "$1" =~ ^[0-9]+$ ]] || die 'record count must be a nonnegative integer'; }

CAP_DATABASE= CAP_HOST= CAP_PORT= CAP_MODE= CAP_URL=
capture_runtime(){
  local root=$1 role=$2
  root_dir "$root" 'runtime root'
  validate_runtime_bundle "$root"
  read_runtime_setting "$root/current/mysql.env" MYSQL_DATABASE CAP_DATABASE
  read_runtime_setting "$root/current/backend.env" PAWCYCLE_DATASOURCE_HOST CAP_HOST
  read_runtime_setting "$root/current/backend.env" PAWCYCLE_DATASOURCE_PORT CAP_PORT
  read_runtime_setting "$root/current/backend.env" PAWCYCLE_DATASOURCE_SSL_MODE CAP_MODE
  read_runtime_setting "$root/current/backend.env" SPRING_DATASOURCE_URL CAP_URL
  if [[ "$role" == rds ]]; then [[ "$CAP_MODE" == REQUIRED && "$CAP_HOST" != mysql && "$CAP_URL" != *allowPublicKeyRetrieval* ]] || die 'RDS runtime must be TLS REQUIRED without allowPublicKeyRetrieval'; else [[ "$CAP_HOST" == mysql && "$CAP_MODE" == DISABLED ]] || die 'Docker rollback runtime must be mysql/DISABLED'; fi
}
validate_state(){
  local v="$STATE/db-restore-verified" c="$STATE/db-restore-candidate" k
  exact_record "$v" FORMAT_VERSION RECORD_KIND BACKUP_ID_SHA256 MANIFEST_SHA256 MYSQL_IMAGE
  [[ "$(record_value "$v" FORMAT_VERSION)" == 1 && "$(record_value "$v" RECORD_KIND)" == verified && "$(record_value "$v" MYSQL_IMAGE)" == "$MYSQL_IMAGE" ]] || die 'OPS-013 verified restore record is invalid'
  exact_record "$c" FORMAT_VERSION RECORD_KIND BACKUP_ID_SHA256 MANIFEST_SHA256 MYSQL_IMAGE SOURCE_VOLUME CANDIDATE_VOLUME SCHEMA_SHA256 FLYWAY_SHA256 FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions
  [[ "$(record_value "$c" FORMAT_VERSION)" == 1 && "$(record_value "$c" RECORD_KIND)" == candidate && "$(record_value "$c" MYSQL_IMAGE)" == "$MYSQL_IMAGE" ]] || die 'OPS-025 candidate record is invalid'
  validate_mysql_volume "$(record_value "$c" CANDIDATE_VOLUME)"
  [[ "$(record_value "$c" SOURCE_VOLUME)" == pawcycle-production-mysql-data && "$(record_value "$c" CANDIDATE_VOLUME)" != pawcycle-production-mysql-data ]] || die 'candidate source volume does not preserve the active default source volume'
  for k in BACKUP_ID_SHA256 MANIFEST_SHA256; do sha "$(record_value "$v" "$k")"; [[ "$(record_value "$v" "$k")" == "$(record_value "$c" "$k")" ]] || die "OPS-013 and OPS-025 backup identity differs: $k"; done
  for k in SCHEMA_SHA256 FLYWAY_SHA256; do sha "$(record_value "$c" "$k")"; done
  for k in FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions; do nonnegative "$(record_value "$c" "$k")"; done
}
validate_evidence_target(){ local evidence=$1 host=$2 port=$3 database_hash=$4; [[ "$(record_value "$evidence" TARGET_HOST)" == "$host" && "$(record_value "$evidence" TARGET_PORT)" == "$port" && "$(record_value "$evidence" TARGET_DATABASE_SHA256)" == "$database_hash" ]] || die 'RDS evidence target identity does not match the validated runtime'; }
validate_evidence(){
  local e=$1 c="$STATE/db-restore-candidate" k
  [[ "$e" == /* && -f "$e" && ! -L "$e" ]] || die 'evidence must be an absolute regular non-symlink file'
  exact_record "$e" FORMAT_VERSION RECORD_KIND EVIDENCE_PHASE FINAL_CONSISTENCY_VERIFIED BACKUP_ID_SHA256 MANIFEST_SHA256 TARGET_HOST TARGET_PORT TARGET_DATABASE_SHA256 SCHEMA_SHA256 FLYWAY_SHA256 FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions APPLICATION_SHA CONTROL_SHA CONTRACT_SHA CONNECTIVITY_VERIFIED IMPORT_VERIFIED BACKEND_HEALTH_VERIFIED API_SMOKE_VERIFIED SOURCE_TARGET_DISTINCT PRODUCTION_CUTOVER
  [[ "$(record_value "$e" FORMAT_VERSION)" == 1 && "$(record_value "$e" RECORD_KIND)" == rds-target-verified && "$(record_value "$e" TARGET_PORT)" == 3306 && "$(record_value "$e" TARGET_HOST)" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+ap-northeast-2\.rds\.amazonaws\.com$ ]] || die 'RDS target evidence identity is invalid'
  for k in BACKUP_ID_SHA256 MANIFEST_SHA256 TARGET_DATABASE_SHA256 SCHEMA_SHA256 FLYWAY_SHA256; do sha "$(record_value "$e" "$k")"; done
  for k in APPLICATION_SHA CONTROL_SHA CONTRACT_SHA; do git_sha "$(record_value "$e" "$k")"; done
  for k in FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions; do nonnegative "$(record_value "$e" "$k")"; done
  for k in CONNECTIVITY_VERIFIED IMPORT_VERIFIED BACKEND_HEALTH_VERIFIED API_SMOKE_VERIFIED SOURCE_TARGET_DISTINCT; do [[ "$(record_value "$e" "$k")" == true ]] || die "RDS target evidence is not verified: $k"; done
  [[ "$(record_value "$e" PRODUCTION_CUTOVER)" == false ]] || die 'rehearsal evidence must not claim Production cutover'
  for k in BACKUP_ID_SHA256 MANIFEST_SHA256 SCHEMA_SHA256 FLYWAY_SHA256 FLYWAY_COUNT TABLE_members TABLE_products TABLE_skus TABLE_subscriptions; do [[ "$(record_value "$e" "$k")" == "$(record_value "$c" "$k")" ]] || die "RDS evidence does not match OPS-025 candidate: $k"; done
}
validate_evidence_phase(){ local evidence=$1 phase=$2 consistency=$3; [[ "$(record_value "$evidence" EVIDENCE_PHASE)" == "$phase" && "$(record_value "$evidence" FINAL_CONSISTENCY_VERIFIED)" == "$consistency" ]] || die "evidence phase or final consistency verification is invalid for $phase"; }
validate_source(){ local active="$STATE/active-mysql-volume" id health image mount; regular600 "$active"; [[ "$(<"$active")" == pawcycle-production-mysql-data ]] || die 'active Docker MySQL source volume is not preserved'; docker volume inspect pawcycle-production-mysql-data >/dev/null || die 'source Docker MySQL volume is missing'; id="$(docker ps --quiet --filter label=com.docker.compose.project=pawcycle-production --filter label=com.docker.compose.service=mysql)"; [[ "$(wc -w <<<"$id" | tr -d ' ')" == 1 ]] || die 'source Docker MySQL container is not uniquely running'; health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")"; image="$(docker inspect --format '{{.Config.Image}}' "$id")"; mount="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$id")"; [[ "$health" == healthy && "$image" == "$MYSQL_IMAGE" && "$mount" == pawcycle-production-mysql-data ]] || die 'source Docker MySQL health, image, or mount is invalid'; }
validate_release(){ local current="$STATE/current-sha" contract="$STATE/contract-sha" changes head; regular600 "$current"; regular600 "$contract"; [[ "$(<"$current")" == "$APP" && "$(<"$contract")" == "$CONTROL" ]] || die 'current Application SHA or contract SHA is stale'; changes="$(git -C "$CONTROL_WORKTREE_ROOT" status --porcelain --untracked-files=all)" || die 'unable to inspect Control worktree'; [[ -z "$changes" ]] || die 'Control worktree is not clean'; head="$(git -C "$CONTROL_WORKTREE_ROOT" rev-parse --verify HEAD)" || die 'unable to resolve Control SHA'; [[ "$head" == "$CONTROL" ]] || die 'Control SHA does not match the contract state'; }
lock_shared(){ regular600 "$STATE/deploy.lock"; exec 9<"$STATE/deploy.lock"; flock -sn 9 || die 'shared deploy lock is unavailable'; }
usage(){ printf '%s\n' 'Usage: rds-transition-gate.sh rehearsal|cutover|rollback --state-dir <absolute> --application-sha <sha> --control-sha <sha> [--evidence <absolute>] [--rds-runtime-dir <absolute>] --rollback-runtime-dir <absolute>'; }
[[ $# -ge 1 ]] || { usage >&2; exit 1; }; ACTION=$1; shift; STATE= APP= CONTROL= EVIDENCE= RDS= ROLLBACK=; declare -A seen=()
while (($#)); do case "$1" in --state-dir|--application-sha|--control-sha|--evidence|--rds-runtime-dir|--rollback-runtime-dir) [[ -z "${seen[$1]:-}" && $# -gt 1 && -n "$2" && "$2" != --* ]] || die "duplicate or missing value for $1"; seen[$1]=1; case "$1" in --state-dir) STATE=$2;; --application-sha) APP=$2;; --control-sha) CONTROL=$2;; --evidence) EVIDENCE=$2;; --rds-runtime-dir) RDS=$2;; --rollback-runtime-dir) ROLLBACK=$2;; esac; shift 2;; *) usage >&2; die "unknown argument: $1";; esac; done
root_dir "$STATE" 'state directory'; git_sha "$APP"; git_sha "$CONTROL"; lock_shared; validate_release; validate_state
case "$ACTION" in
 rehearsal) [[ -n "$EVIDENCE" && -n "$RDS" && -n "$ROLLBACK" ]] || die 'rehearsal requires evidence, RDS runtime, and rollback runtime'; validate_source; capture_runtime "$RDS" rds; rds_host="$CAP_HOST"; rds_port="$CAP_PORT"; rds_database_hash="$(printf '%s' "$CAP_DATABASE" | sha256sum | awk '{print $1}')"; capture_runtime "$ROLLBACK" docker; validate_evidence "$EVIDENCE"; validate_evidence_phase "$EVIDENCE" REHEARSAL false; validate_evidence_target "$EVIDENCE" "$rds_host" "$rds_port" "$rds_database_hash"; [[ "$(record_value "$EVIDENCE" APPLICATION_SHA)" == "$APP" && "$(record_value "$EVIDENCE" CONTROL_SHA)" == "$CONTROL" && "$(record_value "$EVIDENCE" CONTRACT_SHA)" == "$CONTROL" ]] || die 'evidence release identity is stale'; printf '%s\n' 'RDS rehearsal readiness gate passed (no Production cutover claimed)';;
 cutover) [[ -n "$EVIDENCE" && -n "$RDS" && -n "$ROLLBACK" ]] || die 'cutover requires evidence, RDS runtime, and rollback runtime'; validate_source; capture_runtime "$RDS" rds; rds_host="$CAP_HOST"; rds_port="$CAP_PORT"; rds_database_hash="$(printf '%s' "$CAP_DATABASE" | sha256sum | awk '{print $1}')"; capture_runtime "$ROLLBACK" docker; validate_evidence "$EVIDENCE"; validate_evidence_phase "$EVIDENCE" CUTOVER true; validate_evidence_target "$EVIDENCE" "$rds_host" "$rds_port" "$rds_database_hash"; [[ "$(record_value "$EVIDENCE" APPLICATION_SHA)" == "$APP" && "$(record_value "$EVIDENCE" CONTROL_SHA)" == "$CONTROL" && "$(record_value "$EVIDENCE" CONTRACT_SHA)" == "$CONTROL" ]] || die 'evidence release identity is stale'; printf '%s\n' 'RDS cutover readiness gate passed; post-activation Backend health, API smoke, and HTTPS gates remain pending';;
 rollback) [[ -n "$ROLLBACK" ]] || die 'rollback requires staged Docker runtime'; validate_source; capture_runtime "$ROLLBACK" docker; printf '%s\n' 'Docker rollback readiness gate passed; no activation was executed';;
 *) usage >&2; die 'unknown readiness action';;
esac
