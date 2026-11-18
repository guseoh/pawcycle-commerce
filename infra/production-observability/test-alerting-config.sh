#!/usr/bin/env bash
# Validates the production alerting configuration (ARCH-016) with each tool's own checker:
# promtool rule syntax and unit tests, amtool config check and routing tests.
# Uses the image references pinned in compose.yaml. Needs Docker; touches no runtime and no secret.
set -Eeuo pipefail

here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose="$here/compose.yaml"

image() {
  local found
  found="$(grep -oE "image: $1:[^[:space:]]+" "$compose" | head -n 1 | sed 's/^image: //')"
  [[ "$found" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "image $1 is not pinned by digest in compose.yaml" >&2; exit 1; }
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

echo "== Prometheus rules: syntax"
run -v "$(mount_path "$here/prometheus"):/p:ro" --entrypoint promtool "$prometheus" \
  check rules /p/rules/pawcycle-production-alerts.yml

echo "== Prometheus rules: unit tests"
run -v "$(mount_path "$here/prometheus"):/p:ro" -w /p/tests --entrypoint promtool "$prometheus" \
  test rules pawcycle-production-alerts.test.yml

echo "== Prometheus rules: only critical alerts plus Watchdog, all scoped to production"
rules="$here/prometheus/rules/pawcycle-production-alerts.yml"
count() { grep -c -- "$1" "$rules" || true; }
fail() { echo "$1" >&2; exit 1; }
[[ "$(count '^      - alert: ')" -eq 5 ]] || fail "production rules must stay a five-alert set"
[[ "$(count 'severity: critical')" -eq 4 ]] || fail "production must have exactly four critical rules"
[[ "$(count 'severity: none')" -eq 1 ]] || fail "only Watchdog may use severity none"
[[ "$(count 'severity: warning')" -eq 0 ]] || fail "warning rules are out of production scope (ARCH-016)"
[[ "$(count 'scope: production')" -eq 5 ]] || fail "every production rule must carry scope=production"
[[ "$(count 'scope: local')" -eq 0 ]] || fail "local scope leaked into production rules"
# Every business expression is pinned to the production scrape job; only Watchdog is vector(1).
[[ "$(count '^        expr: ')" -eq 5 ]] || fail "rule expressions must stay single-line for this check"
[[ "$(grep '^        expr: ' "$rules" | grep -vc 'job="pawcycle-production-backend"' || true)" -eq 1 ]] || fail "a production rule expression is not pinned to job=pawcycle-production-backend"
grep -q '^        expr: vector(1)$' "$rules" || fail "Watchdog expression drifted"

echo "== Alertmanager: config"
run -v "$(mount_path "$here/alertmanager"):/etc/alertmanager:ro" --entrypoint amtool "$alertmanager" \
  check-config /etc/alertmanager/alertmanager.yml

echo "== Alertmanager: webhook only from a file"
if grep -Eq 'webhook_url:|https?://(discord|discordapp)\.com' "$here/alertmanager/alertmanager.yml"; then
  echo "Alertmanager config must read the Discord webhook only through webhook_url_file" >&2
  exit 1
fi
grep -q 'webhook_url_file: /run/pawcycle-secrets/discord-webhook' "$here/alertmanager/alertmanager.yml"

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
route pawcycle-production-discord alertname=PawCycleBackendScrapeUnavailable severity=critical scope=production
route pawcycle-production-discord alertname=PawCycleDbConnectionAcquireFailure severity=critical scope=production
route pawcycle-production-discord alertname=PawCycleStuckProcessingPayment severity=critical scope=production
route pawcycle-production-discord alertname=PawCycleUnknownPaymentAging severity=critical scope=production
route blackhole alertname=Watchdog severity=none scope=production
# An alert without a known severity must still reach the channel instead of being dropped.
route pawcycle-production-discord alertname=PawCycleUnexpected scope=production

echo "production alerting config OK"
