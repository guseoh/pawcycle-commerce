#!/usr/bin/env bash
# Validates the local observability configuration with each tool's own checker (OBS-STACK-001):
# Prometheus rules (syntax + unit tests), Alertmanager config and routing, Loki config, Alloy syntax.
# Uses the image versions pinned in compose.observability.yaml. Needs Docker; touches no runtime.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose="$here/../compose.observability.yaml"

image() {
  local found
  found="$(grep -oE "image: $1:[^[:space:]]+" "$compose" | head -n 1 | sed 's/^image: //')"
  [[ -n "$found" ]] || { echo "image $1 is not pinned in compose.observability.yaml" >&2; exit 1; }
  printf '%s' "$found"
}

mount_path() {
  # Docker Desktop on Windows (Git Bash) needs a Windows path for bind mounts.
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

run() {
  MSYS_NO_PATHCONV=1 docker run --rm "$@"
}

prometheus="$(image prom/prometheus)"
alertmanager="$(image prom/alertmanager)"
loki="$(image grafana/loki)"
alloy="$(image grafana/alloy)"

echo "== Prometheus rules: syntax"
run -v "$(mount_path "$here/prometheus"):/p:ro" --entrypoint promtool "$prometheus" \
  check rules /p/rules/pawcycle-local-alerts.yml

echo "== Prometheus rules: unit tests"
run -v "$(mount_path "$here/prometheus"):/p:ro" -w /p/tests --entrypoint promtool "$prometheus" \
  test rules pawcycle-local-alerts.test.yml

echo "== Alertmanager: config"
run -v "$(mount_path "$here/alertmanager"):/etc/alertmanager:ro" --entrypoint amtool "$alertmanager" \
  check-config /etc/alertmanager/alertmanager.yml

echo "== Alertmanager: routing"
route() {
  local expected="$1"; shift
  local actual
  actual="$(run -v "$(mount_path "$here/alertmanager"):/etc/alertmanager:ro" --entrypoint amtool "$alertmanager" \
    config routes test --config.file=/etc/alertmanager/alertmanager.yml "$@" | tr -d '\r')"
  if [[ "$actual" != "$expected" ]]; then
    echo "route for [$*] is '$actual', expected '$expected'" >&2
    exit 1
  fi
  echo "ok: [$*] -> $actual"
}
route pawcycle-local-discord-critical alertname=PawCycleStuckProcessingPayment severity=critical scope=local
route pawcycle-local-discord-critical alertname=PawCycleDbConnectionAcquireFailure severity=critical scope=local
route pawcycle-local-discord-warning alertname=PawCycleSubscriptionOverdue severity=warning scope=local
route blackhole alertname=Watchdog severity=none scope=local

echo "== Loki: config"
run -v "$(mount_path "$here/loki"):/etc/loki:ro" "$loki" -config.file=/etc/loki/loki.yml -verify-config

echo "== Alloy: syntax"
run -v "$(mount_path "$here/alloy"):/etc/alloy:ro" "$alloy" fmt /etc/alloy/config.alloy >/dev/null

echo "observability config OK"
