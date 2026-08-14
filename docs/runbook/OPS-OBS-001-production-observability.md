# OPS-OBS-001 Production Observability

## 상태와 경계

작업 ID `OPS-OBS-001D`, 등급 `고위험`, 실행 구분 `저장소 변경`의 지속 Runbook이다. 이 문서는 AWS·Production·Secret·DB를 실행하지 않았고, Production Observability는 아직 **Production Verified가 아니다**. Managed Observability, Alertmanager, centralized logging은 범위 밖이다.

## Repository 검증

저장소 root에서 다음을 실행한다.

```bash
bash infra/production-observability/validate-observability.sh
bash infra/production-metrics-proxy/test-metrics-proxy.sh
python infra/production/validate-production-contracts.py
bash infra/production/test-production-nginx.sh
bash infra/production/test-production-compose.sh
```

첫 두 명령은 Observability stack과 disposable external-network metrics-proxy lifecycle을 검증한다. Repository Validation은 같은 script와 기존 Production contract lanes를 실행한다.

## 실제 운영 실행 전 조건

별도 고위험 실제 운영 실행 승인 뒤에만 다음을 준비한다.

- Observability EC2는 arm64 `t4g.small`이며 이 repository의 `infra/production-observability/compose.yaml`만 사용한다.
- Production Security Group의 metrics port ingress source는 **Observability Security Group only**다. public CIDR(`0.0.0.0/0`, `::/0`)은 금지한다.
- `PAWCYCLE_METRICS_TARGET`에는 Production metrics-proxy의 private target과 port만 runtime에서 주입한다. 실제 IP, hostname, account, instance ID는 repository에 기록하지 않는다.
- Grafana admin user/password는 각각 root-only runtime file로 주입한다. plaintext 값과 file 내용은 명령 이력·Compose env·Git에 넣지 않는다.
- Prometheus/Grafana의 published UI는 `127.0.0.1`만 사용한다. 운영자는 SSM port forwarding을 통해서만 접근한다.

## Metrics-proxy 실제 적용 경계

실제 운영 승인 후에도 기존 `/opt/pawcycle/control` HEAD를 변경하지 않는다. Application `current-sha`/`previous-sha`를 변경하지 않고 Backend/Frontend/MySQL/proxy를 recreate하지 않는다. 승인된 metrics-proxy artifact는 별도 sibling control/worktree(권장 `/opt/pawcycle/metrics-proxy-control`)에서 `infra/production-metrics-proxy` project로만 적용한다.

적용 전 기존 `pawcycle-production-edge`·`pawcycle-production-app` network 존재, TCP 9464 충돌 여부, Application container IDs, current-sha/previous-sha와 active MySQL volume을 비민감 값으로 기록한다. metrics-proxy project만 기동하고 `/actuator/prometheus` 200 및 다른 path 404를 확인한다. 적용 후 같은 Application container IDs와 state가 그대로인지 비교한다. rollback은 metrics-proxy project만 `down`하며 external network, Application, DB, Flyway, volume은 건드리지 않는다.

## 적용 후 scrape·dashboard 확인

Prometheus의 `/-/ready`가 성공하고 targets API에서 `pawcycle-production-backend`가 `up`인지 확인한다. Grafana에서 다음 세 provisioning dashboard만 존재하는지 확인한다.

1. `Production Overview`: up, HTTP request rate, 5xx rate, p95 latency
2. `Runtime`: CPU, JVM heap, GC pause, threads, Hikari active/idle/pending/max
3. `PawCycle Operations`: reconciliation, subscription automation, idempotency, commerce pending

metrics-proxy는 `/actuator/prometheus`만 200이며 API 및 다른 path는 404여야 한다. Backend `:8080` host port가 새로 공개되지 않았는지 확인한다. 이 확인은 dashboard 데이터가 정상이라는 뜻일 뿐 Production Verified 판정을 대체하지 않는다.

## 실패·rollback 경계

- metrics target down: Prometheus targets에서 원인을 확인하고 metrics-proxy의 endpoint-only health를 확인한다. scraper만 재기동하며 Application/DB에는 개입하지 않는다.
- metrics-proxy failure: `/actuator/prometheus` 200과 다른 path 404를 확인한다. proxy만 재기동하고 release·migration·MySQL volume은 변경하지 않는다.
- Prometheus startup failure: runtime config directory writable 조건과 target injection을 확인하고 Prometheus만 재기동한다.
- Grafana startup failure: root가 UID 472 소유·mode 0400으로 준비한 admin runtime files의 readability를 확인하고 Grafana만 재기동한다. credential 값은 출력하지 않는다.

scrape 또는 metrics-proxy 실패 시 application release, DB, Flyway migration, MySQL volume, Backend/Frontend release SHA와 current/previous SHA 상태를 바꾸지 않는다. Observability Compose는 named volume을 자동 삭제하지 않는다. 저장소 준비 자체의 복구는 이 변경을 되돌리는 일반 revert PR만 사용하며 reset·rebase·force push는 사용하지 않는다. 실제 운영 rollback은 Observability service와 SG ingress만 분리해 되돌리고, 기존 Production deploy/rollback 절차에는 개입하지 않는다.
