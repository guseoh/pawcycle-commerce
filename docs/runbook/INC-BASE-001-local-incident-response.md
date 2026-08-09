# INC-BASE-001 Local 장애 감지·복구 기준선

## 목적과 경계

OBS-BASE-001 local stack에서 Backend unavailable, MySQL 연결 실패, reconciliation 실패를 감지하고 원인을 구분한 뒤 정상 복귀를 확인한다. 이 절차는 local-integration 전용이며 Production·Cloud·AWS, alert 임계값, 자동 복구, Scheduler cadence 변경을 정의하지 않는다. Secret은 출력하지 않고 기존 `.env.local`만 사용한다.

기준 환경은 2026-08-09 UTC, Docker Engine 28.5.1, MySQL 8.4.10, Prometheus scrape interval 15초·timeout 10초다. 실행 전 `OBS-BASE-001-local-observability.md`에 따라 전체 stack과 Grafana Dashboard를 준비한다.

```powershell
$ComposeFiles = @('-f', 'compose.yaml', '-f', 'compose.observability.yaml')
$ProxyPort = (docker compose --env-file .env.local @ComposeFiles port proxy 80).Split(':')[-1]
$ProxyUrl = "http://127.0.0.1:$ProxyPort"
docker compose --env-file .env.local @ComposeFiles ps
```

정상 기준은 MySQL·Backend가 `healthy`, Backend `/actuator/health`가 `UP`, Prometheus `up{job="pawcycle-backend"}`가 `1`, Proxy 상품 API가 `200`인 상태다. 운영자의 첫 확인 순서는 다음과 같다.

1. `docker compose ps`로 Backend와 MySQL process·health를 구분한다.
2. Prometheus target의 `health`와 `lastError`를 확인한다.
3. Backend container 내부 `/actuator/health`를 10초 이내로 확인한다.
4. Backend scrape가 유지되면 Hikari와 reconciliation failure metric, 관련 Backend log를 확인한다.

## 판정 기준

| 시나리오 | 장애 판정 | 원인 구분 | 정상 복귀 |
| --- | --- | --- | --- |
| Backend unavailable | Backend container가 stopped이고 `up=0`; Proxy API `502` | MySQL은 healthy | Backend healthy, health `UP`, `up=1`, Proxy `200` |
| MySQL 연결 실패 | MySQL container가 stopped; Backend health·scrape·Proxy가 10초 안에 응답하지 않음 | Backend process는 running이고 Prometheus `lastError`는 scrape timeout | MySQL과 Backend healthy, health `UP`, `up=1`, Proxy `200` |
| reconciliation 실패 | disposable stack에서 `pawcycle_subscription_reconciliation_failures_total` 증가와 fixture ID의 lock-timeout log가 함께 존재 | 공유 DB가 아니라 단일 ACTIVE fixture의 row lock | lock 제거 후 다음 실행 완료, failure `0`, 오류 log 없음, fixture 불변 |

이 값들은 local 판정 조건이며 Production alert 임계값이 아니다. Grafana의 `Backend scrape availability`, `Hikari connections`, `Reconciliation totals` 패널에서 같은 Prometheus 신호를 확인한다.

## Backend unavailable

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 backend
# 15초 scrape 1회 이후 container, Prometheus target과 10초 이내 Proxy 502를 확인한다.
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
docker compose --env-file .env.local @ComposeFiles start backend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 backend
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
```

실측에서는 `2026-08-09T05:40:37Z`에 target `down`, Proxy `502`, Backend stopped, MySQL healthy를 확인했다. `2026-08-09T05:41:25Z`에 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## MySQL 연결 실패

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 mysql
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 mysql backend
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
```

실측에서는 `2026-08-09T05:42:30Z`에 Backend process가 running인 상태에서 Prometheus target `down`, `lastError=context deadline exceeded`, health와 Proxy timeout, MySQL stopped를 확인했다. `2026-08-09T05:43:16Z`에 기존 Backend process가 DB 연결을 회복해 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## Reconciliation 실패

공유 local DB에서는 장애를 재현하지 않는다. `compose.incident.yaml`은 실행마다 고유 Compose project를 사용하고 MySQL·Prometheus·Grafana data mount를 project-scoped disposable volume으로 교체한다. 기존 `pawcycle-local-integration-*` named volume은 mount·삭제하지 않는다.

`verify-inc-base-001.ps1`은 migration과 기존 local bootstrap 후 ACTIVE subscription 한 건과 미래 schedule 한 건을 생성한다. row lock을 확보한 뒤 Backend를 시작해 lock timeout을 관측하고, 원인을 제거한 뒤 Backend 재시작으로 기존 즉시 reconciliation 실행을 사용한다. Scheduler cadence는 변경하지 않는다.

```powershell
Set-Location infra/local-integration
& ./incident/verify-inc-base-001.ps1 -EnvFile ./.env.local
```

다른 worktree의 `.env.local`을 사용할 때는 절대 경로만 전달하며 파일을 복사하거나 출력하지 않는다. incident override는 subscription reset을 `false`로 고정한다. 실패 관측 deadline은 Backend 기동 180초 + 첫 Scheduler 실행 여유 15초 + 실제 `innodb_lock_wait_timeout` + Prometheus scrape interval 15초 + scrape timeout 10초다. lock holder는 여기에 cleanup 안전 여유 20초를 더해 대기하며, 신호 확인 직후 root session에서 `KILL`한 뒤 최대 10초 동안 session 종료를 확인해 transaction rollback을 보장한다. recovery log cursor는 초 미만 UTC 정밀도를 유지한다.

성공 출력은 다음 계약을 모두 포함한다.

- `FAILURE_EVIDENCE`: failure `>=1`, target `UP`, fixture subscription ID와 일치하는 `Lock wait timeout exceeded` log
- `RECOVERY_EVIDENCE`: executions `>=1`, failures `0`, target `UP`, 복구 이후 reconciliation 오류 log 없음
- `GRAFANA_EVIDENCE`: datasource UID, Dashboard UID, panel `13`
- `FIXTURE_DATA_UNCHANGED=PASS`
- `DISPOSABLE_CLEANUP=PASS`

`2026-08-09` disposable 재검증에서는 ACTIVE subscription `1` 한 건으로 failures `1`, target `UP`, lock wait `50`초 log를 확인했다. 원인 제거 후 executions `1`, failures `0`, target `UP`과 새 failure 없음, Grafana datasource·Dashboard·13개 panel, fixture 불변과 전용 container·volume·network 정리를 확인했다.

스크립트는 첫 `up` 전부터 cleanup 필요 상태를 기록하고 성공·실패 모두에서 전용 project에만 `down --volumes --remove-orphans --rmi local`을 실행한다. 실행 전 merged Compose model에 disposable volume 세 개만 존재하는지 확인하며, 종료 후 전용 container·volume·network 부재와 시작 전후 공유 named volume의 존재·생성 시각 불변을 확인한다. cleanup 오류를 숨기지 않고, 실행 오류와 동시에 발생하면 두 원인을 함께 보고한다. 전용 stack 정리나 lock-session rollback을 확인할 수 없으면 공유 stack으로 우회하지 않고 중단한다.

## 복구와 중단

모든 시나리오 후 MySQL·Backend·Prometheus·Grafana·Proxy가 정상인지 다시 확인한다. shared DB에 row lock이나 fixture를 만들지 않는다. disposable stack이 기존 shared volume을 mount하거나 current fixture가 변경되거나 cleanup이 실패하면 추가 lock/isolation 보강 없이 중단한다.

Grafana health `ok`, datasource UID `pawcycle-prometheus`, Dashboard UID `pawcycle-local-observability`, 13개 panel과 `Backend scrape availability`의 `up{job="pawcycle-backend"}` query를 확인한다. Java 25와 격리된 MySQL 8.4에서 `ObservabilityIntegrationTests` 2개가 통과해야 한다.

저장소 변경의 rollback은 Dashboard, Runbook, disposable Compose override와 fixture script를 일반 revert하는 것이다. shared local stack 종료는 `docker compose --env-file .env.local -f compose.yaml -f compose.observability.yaml down`을 사용하고 named volume은 삭제하지 않는다. disposable project는 검증 스크립트가 출력한 project name으로만 정리한다.
