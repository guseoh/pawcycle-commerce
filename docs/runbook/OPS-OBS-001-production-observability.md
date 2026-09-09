# OPS-OBS-001 Production Observability

## 상태와 경계

작업 ID `OPS-OCI-005`, 등급 `고위험`, 실행 구분 `저장소 변경`의 지속 Runbook이다. 이 저장소 변경은 OCI·Production·Secret·Managed MySQL을 실행하지 않았으므로 Production Observability는 **Production Verified가 아니다**. Managed Observability, Alertmanager, centralized logging/Loki와 OpenTelemetry 전환은 범위 밖이다.

현재 Production은 OCI `app01`의 Backend/Frontend/Nginx와 별도 OCI Managed MySQL이다. 초기 Observability는 같은 `app01`에서 다음 세 lifecycle을 별도 Compose project로 운영한다.

- Application: 기존 `backend`, `frontend`, `proxy` release project
- Metrics proxy: `infra/production-metrics-proxy`의 `metrics-proxy` 하나
- Observability: `infra/production-observability`의 Prometheus와 Grafana

Metrics proxy와 Observability는 Application release를 recreate·wait·stop하지 않는다. Prometheus는 Application Docker `app` network에서 `metrics-proxy:9464`를 scrape하며 metrics-proxy host port는 없다. Prometheus와 Grafana UI만 `127.0.0.1`에 publish한다.

별도 Observability Compute는 **Deferred — Evidence Triggered**다. 실제 적용 후 CPU·memory·disk·application HTTP latency/error-rate overhead가 측정되거나 monitoring failure-domain 분리가 필요할 때 별도 결정으로 재검토한다.

## Repository 검증

저장소 root에서 다음을 실행한다. 이 명령은 OCI/Production에 연결하지 않는다.

```bash
bash infra/production-observability/validate-observability.sh
bash infra/production-metrics-proxy/test-metrics-proxy.sh
python infra/production/validate-production-contracts.py
bash infra/production/test-production-nginx.sh
bash infra/production/test-production-compose.sh
```

첫 두 명령은 Observability stack, same-host Docker network, endpoint-only metrics-proxy와 Backend container 교체 후 동적 DNS 재해석을 검증한다. Production Compose 검증은 active Compose가 `backend`/`frontend`/`proxy`만 소유하고 database service를 포함하지 않는지와 application release·HTTPS·volume lifecycle을 검증한다.

## 실제 운영 실행 전 조건

별도 고위험 실제 운영 승인이 있고, 승인된 merge SHA와 현재 `app01` 상태를 확인한 뒤에만 다음 절차를 준비한다.

- `pawcycle-production-app` network가 이미 존재하며 Application project가 소유한다. Observability project와 metrics-proxy project는 이 external network를 삭제하지 않는다.
- Application Compose의 service set은 `backend`, `frontend`, `proxy`이고 database service는 없다. OCI Managed MySQL lifecycle은 이 Runbook의 대상이 아니다.
- Backend는 host `:8080`을 publish하지 않고 Docker network에서만 `backend:8080`으로 접근한다.
- Metrics proxy는 host port를 publish하지 않고 `app` network에만 연결한다. `PAWCYCLE_METRICS_TARGET`은 `metrics-proxy:9464`로 runtime 주입한다.
- Prometheus/Grafana published UI는 각각 `127.0.0.1`만 사용한다. Grafana admin user/password file은 보호된 runtime 경로에서만 읽고 값은 명령·환경 출력·Git에 넣지 않는다.
- Prometheus/Grafana named volume은 기존 값을 유지하고 일반 `down`에서 삭제하지 않는다.

## Same-host 적용

아래 경로와 SHA는 승인된 `app01` runtime 계약을 확인한 뒤 사용한다. 이 절차는 Application control checkout의 HEAD와 working tree를 변경하지 않는다.

```bash
APP_CONTROL=/opt/pawcycle/control
METRICS_CONTROL=/opt/pawcycle/metrics-proxy-control
OBS_CONTROL=/opt/pawcycle/observability-control
APPROVED_SHA='<approved-merge-sha>'
GRAFANA_USER_FILE='<approved-runtime-user-file>'
GRAFANA_PASSWORD_FILE='<approved-runtime-password-file>'

APP_CONTAINER_IDS_BEFORE="$(sudo docker inspect --format '{{.Name}}={{.Id}}' \
  pawcycle-production-backend-1 \
  pawcycle-production-frontend-1 \
  pawcycle-production-proxy-1 | sort)"
CURRENT_SHA_BEFORE="$(sudo cat /opt/pawcycle/state/current-sha)"
PREVIOUS_SHA_BEFORE="$(sudo cat /opt/pawcycle/state/previous-sha 2>/dev/null || true)"

sudo docker network inspect pawcycle-production-app >/dev/null
sudo git -C "$APP_CONTROL" cat-file -e "${APPROVED_SHA}^{commit}"
sudo test -d "$METRICS_CONTROL" && sudo test -d "$OBS_CONTROL"
test "$(sudo git -C "$METRICS_CONTROL" rev-parse HEAD)" = "$APPROVED_SHA"
test "$(sudo git -C "$OBS_CONTROL" rev-parse HEAD)" = "$APPROVED_SHA"
```

먼저 metrics-proxy만 별도 project로 시작한다. host-port override를 설정하거나 host port를 추가하지 않는다.

```bash
cd "$METRICS_CONTROL/infra/production-metrics-proxy"
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose config --quiet
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose up --detach --wait --wait-timeout 60 --pull never
```

그 다음 Observability project를 시작한다. Grafana credential file은 runtime file 경계를 유지한다.

```bash
cd "$OBS_CONTROL/infra/production-observability"
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose config --quiet
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose up --detach --wait --wait-timeout 60 --pull never
```

## 적용 후 확인

다음 확인은 non-secret 상태와 HTTP status만 출력한다.

```bash
PROMETHEUS_URL=http://127.0.0.1:9090
GRAFANA_URL=http://127.0.0.1:3000
METRICS_PROXY_ID="$(sudo docker ps --quiet \
  --filter label=com.docker.compose.project=pawcycle-production-metrics-proxy \
  --filter label=com.docker.compose.service=metrics-proxy)"

test "$(sudo docker port "$METRICS_PROXY_ID" 9464/tcp)" = ""
sudo docker exec "$METRICS_PROXY_ID" wget --quiet --output-document=/dev/null \
  http://127.0.0.1:9464/actuator/prometheus
test "$(sudo docker exec "$METRICS_PROXY_ID" sh -ec \
  'wget -S -O /dev/null http://127.0.0.1:9464/api/products 2>&1 | awk '\''$1 == "HTTP/1.1" { print $2 }'\'' | tail -n 1')" = 404

curl --fail --silent --show-error "$PROMETHEUS_URL/-/ready" >/dev/null
curl --fail --silent --show-error "$GRAFANA_URL/api/health" >/dev/null
curl --fail --silent --show-error "$PROMETHEUS_URL/api/v1/targets" | \
  python3 -c 'import json, sys; targets=json.load(sys.stdin)["data"]["activeTargets"]; assert sum(t.get("labels", {}).get("job") == "pawcycle-production-backend" and t.get("health") == "up" for t in targets) == 1'

APP_CONTAINER_IDS_AFTER="$(sudo docker inspect --format '{{.Name}}={{.Id}}' \
  pawcycle-production-backend-1 \
  pawcycle-production-frontend-1 \
  pawcycle-production-proxy-1 | sort)"
test "$APP_CONTAINER_IDS_AFTER" = "$APP_CONTAINER_IDS_BEFORE"
test "$(sudo cat /opt/pawcycle/state/current-sha)" = "$CURRENT_SHA_BEFORE"
test "$(sudo cat /opt/pawcycle/state/previous-sha 2>/dev/null || true)" = "$PREVIOUS_SHA_BEFORE"
```

Prometheus target `up`은 Docker-internal `metrics-proxy:9464/actuator/prometheus` 경로가 동작한다는 뜻이다. Metrics proxy의 `/actuator/prometheus`만 2xx이고 `/api/products`를 포함한 일반 path는 404여야 한다. Backend `:8080`, Prometheus UI, Grafana UI가 public interface에 bind되지 않았는지 Compose model과 host listening socket으로 확인한다. 세 개의 provisioning dashboard는 다음 의미를 유지한다.

1. `Production Overview`: up, HTTP request rate, 5xx rate, p95 latency
2. `Runtime`: process CPU, JVM heap, GC pause, threads, Hikari active/idle/pending/max
3. `PawCycle Operations`: reconciliation, subscription automation, idempotency, commerce pending

## Same-host overhead calibration

현재 repository에는 지속적인 host metric collector가 없다. 따라서 초기 적용 후에는 Application release SHA, workload, scrape interval과 측정 조건을 고정하고, Observability **OFF**와 **ON** 각각에서 짧은 동일 window의 read-only 정상 트래픽을 사용한다. stress/load/capacity test를 수행하지 않는다.

각 window에서 UTC 시작·종료 시각과 다음 aggregate evidence만 보존한다.

```bash
nproc
vmstat 1 60
free -b
df -P /
sudo docker stats --no-stream
sudo docker system df --verbose
for i in $(seq 1 30); do
  curl --silent --show-error --output /dev/null \
    --write-out '%{http_code} %{time_total}\n' \
    https://<approved-production-domain>/api/products
done
```

- CPU: host `vmstat`의 user+system 사용률과 application/Observability container CPU를 OFF/ON 비교한다.
- Memory: host `MemAvailable`과 container memory를 비교하고 OOM/restart 여부를 확인한다.
- Disk: root filesystem available bytes와 Prometheus/Grafana named volume 사용량 증가를 비교한다.
- HTTP: fixed sample의 status error ratio와 `time_total` p95를 비교한다. response body, cookie, credential과 운영 원시 데이터는 보존하지 않는다.

이 결과는 성능 tuning이나 새 threshold 승인이 아니다. 측정된 CPU/memory/disk/latency overhead 또는 failure-isolation 필요가 확인될 때만 별도 Compute decision을 연다.

Host metric gap을 continuous series로 해결하려면 `/proc`·`/sys` mount, 권한, collector image와 lifecycle, 추가 resource를 정해야 한다. 이 변경은 새로운 보안·운영 결정을 요구하므로 이번 범위에서는 추가하지 않으며, 필요 시 **Decision Required — Evidence Triggered**로 승격한다.

## Stop / rollback

다음 중 하나라도 성립하면 적용을 중단하고 Application release, HTTPS, certificate, migration 또는 Managed MySQL을 조작하지 않는다.

- Application container ID 또는 `current-sha`/`previous-sha`가 preflight와 다르다.
- Application Compose가 `backend`/`frontend`/`proxy` 외 service를 소유하거나 Backend `:8080` host publish가 보인다.
- `pawcycle-production-app` network가 없거나 external network 계약이 다르다.
- metrics-proxy가 host port를 publish하거나 endpoint-only/동적 DNS 확인에 실패한다.
- Prometheus/Grafana UI가 loopback 외 address에 bind되거나 runtime credential file이 보호된 경계를 벗어난다.

실패 시 Observability를 먼저 내리고 metrics-proxy를 내린다. named volume과 Application external network는 삭제하지 않는다.

```bash
cd "$OBS_CONTROL/infra/production-observability"
sudo env \
  PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  PAWCYCLE_METRICS_TARGET=metrics-proxy:9464 \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$GRAFANA_USER_FILE" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$GRAFANA_PASSWORD_FILE" \
  docker compose down --remove-orphans

cd "$METRICS_CONTROL/infra/production-metrics-proxy"
sudo env PAWCYCLE_APP_NETWORK=pawcycle-production-app \
  docker compose down --remove-orphans
```

Rollback은 Observability project와 metrics-proxy project에만 적용한다. `--volumes`를 사용하지 않으며 Application container, release SHA, migration state, Managed MySQL, HTTPS runtime과 certificate state를 변경하지 않는다. 저장소 준비 변경의 복구는 일반 revert PR로 수행하고 reset·rebase·force push는 사용하지 않는다.
