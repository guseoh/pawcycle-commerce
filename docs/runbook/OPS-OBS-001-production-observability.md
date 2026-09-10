# OPS-OBS-001 Production Observability

## 상태와 경계

작업 ID `OPS-OBS-001D`의 지속 Runbook이며, `OPS-OCI-005A`가 OCI app01 실행 계약을 보정한다. 이번 저장소 변경의 risk는 `normal`; 실제 Production 적용은 별도 고위험 승인 대상이다. 이 문서는 AWS·Production·Secret·DB를 실행하지 않았고, Production Observability는 아직 **Production Verified가 아니다**. Managed Observability, Alertmanager, centralized logging은 범위 밖이다.

## Repository 검증

저장소 root에서 다음을 실행한다.

```bash
bash infra/production-observability/validate-observability.sh
bash infra/production-metrics-proxy/test-metrics-proxy.sh
bash infra/production/test-observability-application-identity.sh
python infra/production/validate-production-contracts.py
bash infra/production/test-production-nginx.sh
bash infra/production/test-production-compose.sh
```

처음 두 명령은 Observability stack과 disposable external-network metrics-proxy lifecycle을 검증한다. 세 번째 명령은 OCI Application runtime identity snapshot/verify와 mutation fail-closed 경계를 검증한다. metrics-proxy 검증은 hardening 계약과 Backend container 교체 후 동적 DNS 재해석도 포함한다. Repository Validation은 같은 script와 기존 Production contract lanes를 실행한다.

## 실제 운영 실행 전 조건

별도 고위험 실제 운영 실행 승인 뒤에만 다음을 준비한다.

- OCI `app01`에서는 이 repository의 `infra/production-observability/compose.yaml`만 사용한다.
- Production Security Group의 metrics port ingress source는 **Observability Security Group only**다. public CIDR(`0.0.0.0/0`, `::/0`)은 금지한다.
- `PAWCYCLE_METRICS_TARGET`에는 Production metrics-proxy의 private target과 port만 runtime에서 주입한다. 실제 IP, hostname, account, instance ID는 repository에 기록하지 않는다.
- Grafana admin user/password는 각각 root-only runtime file로 주입한다. plaintext 값과 file 내용은 명령 이력·Compose env·Git에 넣지 않는다.
- Prometheus/Grafana의 published UI는 `127.0.0.1`만 사용한다. 운영자는 승인된 host-local forwarding을 통해서만 접근한다.

## Metrics-proxy 실제 적용 경계

실제 운영 승인 후에도 OCI Application source/control checkout `/opt/pawcycle/source/repo`의 HEAD와 working tree를 변경하지 않는다. Observability는 Application container, Managed MySQL, HTTPS/certificate state를 recreate하거나 변경하지 않는다. 승인된 metrics-proxy artifact는 이 checkout에서 만든 detached sibling worktree `/opt/pawcycle/metrics-proxy-control`의 `infra/production-metrics-proxy` project로만 적용한다. Observability apply 전후에는 application runtime identity snapshot을 비교해 container, image, Compose label과 network boundary가 바뀌면 fail-closed 한다.

아래 명령은 **별도 고위험 실제 운영 실행 승인 후에만** OCI `app01`에서 사용한다. `APPROVED_SHA`에는 검토·병합이 끝난 승인 commit SHA만 넣는다.

```bash
umask 077
APP_CONTROL=/opt/pawcycle/source/repo
APP_COMPOSE_DIR="$APP_CONTROL/infra/production"
METRICS_CONTROL=/opt/pawcycle/metrics-proxy-control
APPROVED_SHA='<approved-merge-sha>'
RUNTIME_IDENTITY="$(mktemp /tmp/pawcycle-application-runtime-identity.XXXXXX)"
IDENTITY_SCRIPT="$(mktemp /tmp/pawcycle-verify-observability-application-identity.XXXXXX)"

test -d "$APP_COMPOSE_DIR"

sudo docker network inspect pawcycle-production-app >/dev/null
sudo docker network inspect pawcycle-production-edge >/dev/null
if sudo ss -lnt | awk '{print $4}' | grep -Eq '(^|:)9464$'; then
  echo 'TCP 9464 is already in use' >&2
  exit 1
fi

if sudo test -e "$METRICS_CONTROL"; then
  echo "$METRICS_CONTROL already exists; verify it explicitly instead of replacing it" >&2
  exit 1
fi

sudo git -C "$APP_CONTROL" fetch --prune origin main
sudo git -C "$APP_CONTROL" cat-file -e "${APPROVED_SHA}^{commit}"
sudo git -C "$APP_CONTROL" worktree add --detach "$METRICS_CONTROL" "$APPROVED_SHA"
test "$(sudo git -C "$METRICS_CONTROL" rev-parse HEAD)" = "$APPROVED_SHA"

EXPECTED_IDENTITY_SHA256="$(sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/verify-observability-application-identity.sh" |
  sha256sum | awk '{print $1}')"
sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/verify-observability-application-identity.sh" > "$IDENTITY_SCRIPT"
chmod 500 "$IDENTITY_SCRIPT"
test "$(sha256sum "$IDENTITY_SCRIPT" | awk '{print $1}')" = "$EXPECTED_IDENTITY_SHA256"

sudo bash "$IDENTITY_SCRIPT" snapshot > "$RUNTIME_IDENTITY"
chmod 600 "$RUNTIME_IDENTITY"

cd "$METRICS_CONTROL/infra/production-metrics-proxy"
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_EDGE_NETWORK=pawcycle-production-edge \
  PAWCYCLE_METRICS_PORT=9464 \
  docker compose config --quiet
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_EDGE_NETWORK=pawcycle-production-edge \
  PAWCYCLE_METRICS_PORT=9464 \
  docker compose pull metrics-proxy
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_EDGE_NETWORK=pawcycle-production-edge \
  PAWCYCLE_METRICS_PORT=9464 \
  docker compose up --detach --no-deps --wait --wait-timeout 60 --pull never metrics-proxy

sudo bash "$IDENTITY_SCRIPT" verify --snapshot "$RUNTIME_IDENTITY"

curl -fsS http://127.0.0.1:9464/actuator/prometheus >/dev/null
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:9464/api/products)" = 404
```

실패 시 Application release나 DB를 복구 대상으로 삼지 않는다. 같은 sibling project에서 metrics-proxy만 내린다.

```bash
cd /opt/pawcycle/metrics-proxy-control/infra/production-metrics-proxy
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_EDGE_NETWORK=pawcycle-production-edge \
  PAWCYCLE_METRICS_PORT=9464 \
  docker compose down --remove-orphans
```

`external: true` network는 standalone project가 소유하지 않으므로 rollback에서 삭제하지 않는다. sibling worktree 제거도 자동 rollback에 포함하지 않는다.

## 적용 후 scrape·dashboard 확인

Prometheus의 `/-/ready`가 성공하고 targets API에서 `pawcycle-production-backend`가 `up`인지 확인한다. Grafana에서 다음 세 provisioning dashboard만 존재하는지 확인한다.

1. `Production Overview`: up, HTTP request rate, 5xx rate, p95 latency
2. `Runtime`: CPU, JVM heap, GC pause, threads, Hikari active/idle/pending/max
3. `PawCycle Operations`: reconciliation, subscription automation, idempotency, commerce pending

metrics-proxy는 `/actuator/prometheus`만 200이며 API 및 다른 path는 404여야 한다. Backend `:8080` host port가 새로 공개되지 않았는지 확인한다. 이 확인은 dashboard 데이터가 정상이라는 뜻일 뿐 Production Verified 판정을 대체하지 않는다.

Backend 진단은 OCI app01의 localhost 경계를 넘지 않는다. 승인된 merge SHA의 진단 script blob을 Application source/control checkout `/opt/pawcycle/source/repo`에서 읽어 `/tmp`에 detached artifact로 materialize하고 SHA-256을 원본 blob과 대조한다. 이 준비와 진단은 Application Compose, Managed MySQL, HTTPS/certificate state를 변경하지 않는다.

```bash
APP_CONTROL=/opt/pawcycle/source/repo
APPROVED_SHA='<approved-merge-sha>'
DIAG_SCRIPT=/tmp/pawcycle-diagnose-backend-state.sh

sudo git -C "$APP_CONTROL" fetch --prune origin main
sudo git -C "$APP_CONTROL" cat-file -e "${APPROVED_SHA}^{commit}"
EXPECTED_DIAG_SHA256="$(sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/diagnose-backend-state.sh" |
  sha256sum | awk '{print $1}')"
sudo git -C "$APP_CONTROL" show \
  "${APPROVED_SHA}:infra/production/diagnose-backend-state.sh" > "$DIAG_SCRIPT"
chmod 500 "$DIAG_SCRIPT"
test "$(sha256sum "$DIAG_SCRIPT" | awk '{print $1}')" = "$EXPECTED_DIAG_SHA256"
```

Production snapshot과 localhost Prometheus 결합은 같은 app01에서 연속 실행한다. `state-dir`는 HTTPS domain과 optional transition/lock 관찰에만 사용하며, observability가 없는 release marker를 만들거나 요구하지 않는다. `deploy.lock`은 획득하지 않고 모든 Docker 조회는 read-only다.

```bash
PRODUCTION_RESULT="$(mktemp /tmp/pawcycle-production-diagnostic.XXXXXX)"
chmod 600 "$PRODUCTION_RESULT"

sudo bash "$DIAG_SCRIPT" \
  --scope production \
  --https-origin 'https://<approved-production-domain>' \
  --state-dir /opt/pawcycle/state > "$PRODUCTION_RESULT"

if bash "$DIAG_SCRIPT" \
  --scope observability \
  --prometheus-url http://127.0.0.1:9090 \
  --production-result "$PRODUCTION_RESULT"; then
  :
else
  DIAGNOSTIC_RC=$?
  printf 'observability diagnostic failed closed with exit=%s\n' "$DIAGNOSTIC_RC" >&2
  exit "$DIAGNOSTIC_RC"
fi
```

최종 `NORMAL`만 exit `0`이다. `BACKEND_DOWN`, `OBSERVABILITY_DEGRADED`, `DEGRADED`, `UNKNOWN`은 non-zero다. Docker 조회 실패, 진행 중인 transition/lock, 진단 중 runtime 문제, malformed snapshot, 기본 120초를 넘긴 snapshot, Prometheus 응답·파싱·target cardinality 불일치는 `UNKNOWN`으로 fail-closed 한다. `/products` 응답은 판정에 사용하지 않는다.

검증이 끝난 임시 진단 script와 snapshot은 app01에서 제거할 수 있다. 이 정리는 Application source/control checkout, Application container, Managed MySQL, HTTPS/certificate state를 변경하지 않는다.

```bash
rm -f /tmp/pawcycle-diagnose-backend-state.sh \
  /tmp/pawcycle-verify-observability-application-identity.* \
  /tmp/pawcycle-application-runtime-identity.* \
  /tmp/pawcycle-production-diagnostic
```

## 실패·rollback 경계

- metrics target down: Prometheus targets에서 원인을 확인하고 metrics-proxy의 endpoint-only health를 확인한다. scraper만 재기동하며 Application/DB에는 개입하지 않는다.
- metrics-proxy failure: `/actuator/prometheus` 200과 다른 path 404를 확인한다. standalone proxy만 재기동하거나 위 rollback 명령으로 제거하고 release·migration·MySQL volume은 변경하지 않는다.
- Prometheus startup failure: runtime config directory writable 조건과 target injection을 확인하고 Prometheus만 재기동한다.
- Grafana startup failure: root가 UID 472 소유·mode 0400으로 준비한 admin runtime files의 readability를 확인하고 Grafana만 재기동한다. credential 값은 출력하지 않는다.

scrape 또는 metrics-proxy 실패 시 application release, DB, Flyway migration, MySQL volume, Backend/Frontend image identity와 Application Compose state를 바꾸지 않는다. Observability Compose는 named volume을 자동 삭제하지 않는다. 저장소 준비 자체의 복구는 이 변경을 되돌리는 일반 revert PR만 사용하며 reset·rebase·force push는 사용하지 않는다. 실제 운영 rollback은 Observability service와 standalone metrics-proxy runtime만 분리해 되돌리고, 기존 Production application/HTTPS/Managed MySQL 절차에는 개입하지 않는다.
