# INC-BASE-001 Local 장애 감지·복구 기준선

## 목적과 경계

OBS-BASE-001 local stack에서 Backend unavailable, MySQL 연결 실패, reconciliation 실패를 감지하고 원인을 구분한 뒤 정상 복귀를 확인한다. 이 절차는 local-integration 전용이며 Production·Cloud·AWS, alert 임계값, 자동 복구, Scheduler cadence 변경을 정의하지 않는다. Secret은 출력하지 않고 기존 `.env.local`만 사용한다.

기준 환경은 2026-08-09 UTC, Docker Engine 28.5.1, MySQL 8.4.10, Prometheus scrape interval 15초·timeout 10초다. 실행 전 `OBS-BASE-001-local-observability.md`에 따라 전체 stack과 Grafana Dashboard를 준비한다.

```powershell
$ComposeFiles = @('-f', 'compose.yaml', '-f', 'compose.observability.yaml')
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
| reconciliation 실패 | health `UP`, `up=1`인 상태에서 `pawcycle_subscription_reconciliation_failures_total` 증가와 실패 log가 함께 존재 | 전체 Backend·MySQL 장애가 아니라 해당 reconciliation transaction 실패 | 원인 제거 후 다음 허용된 실행이 완료되고 새 failure 증가가 없음 |

이 값들은 local 판정 조건이며 Production alert 임계값이 아니다. Grafana의 `Backend scrape availability`, `Hikari connections`, `Reconciliation totals` 패널에서 같은 Prometheus 신호를 확인한다.

## Backend unavailable

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 backend
# 15초 scrape 1회 이후 container, Prometheus target, Proxy 응답을 확인한다.
docker compose --env-file .env.local @ComposeFiles start backend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 backend
```

실측에서는 `2026-08-09T05:40:37Z`에 target `down`, Proxy `502`, Backend stopped, MySQL healthy를 확인했다. `2026-08-09T05:41:25Z`에 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## MySQL 연결 실패

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 mysql
# Backend health와 Proxy 요청은 각각 10초를 넘기지 않고 확인한다.
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 mysql backend
```

실측에서는 `2026-08-09T05:42:30Z`에 Backend process가 running인 상태에서 Prometheus target `down`, `lastError=context deadline exceeded`, health와 Proxy timeout, MySQL stopped를 확인했다. `2026-08-09T05:43:16Z`에 기존 Backend process가 DB 연결을 회복해 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## Reconciliation 실패

active local fixture가 있을 때만 수행한다. 다음 SQL은 한 row를 조회 lock으로 90초 보유한 뒤 `ROLLBACK`하며 데이터를 변경하지 않는다. MySQL `innodb_lock_wait_timeout=50`과 기존 즉시 reconciliation 실행을 사용하고 Scheduler cadence를 바꾸지 않는다. `LOCK_ACQUIRED`를 확인한 뒤 Backend를 재시작한다.

```powershell
$SqlPath = Join-Path $env:TEMP 'inc-base-001-reconciliation-lock.sql'
$Sql = @"
SELECT CONNECTION_ID();
SELECT GET_LOCK('inc-base-001-reconciliation',0);
START TRANSACTION;
SELECT id FROM subscriptions WHERE mvp2_managed=true AND status='ACTIVE' ORDER BY id LIMIT 1 FOR UPDATE;
SELECT 'LOCK_ACQUIRED';
SELECT SLEEP(90);
ROLLBACK;
SELECT RELEASE_LOCK('inc-base-001-reconciliation');
"@
[IO.File]::WriteAllText($SqlPath, $Sql, [Text.UTF8Encoding]::new($false))
docker cp $SqlPath pawcycle-local-integration-mysql-1:/tmp/inc-base-001-reconciliation-lock.sql
docker exec -d pawcycle-local-integration-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --unbuffered < /tmp/inc-base-001-reconciliation-lock.sql > /tmp/inc-base-001-reconciliation-lock.out 2>&1'
docker exec pawcycle-local-integration-mysql-1 sh -c 'cat /tmp/inc-base-001-reconciliation-lock.out'
docker compose --env-file .env.local @ComposeFiles restart --timeout 10 backend
```

failure metric과 `Lock wait timeout exceeded` log를 확인한 뒤 lock output의 마지막 두 값이 `0`, `1`인지 확인한다. 이는 `SLEEP` 완료와 named lock release 성공을 뜻한다. 그 전에 복구 재시작을 수행하지 않는다. release 후 다음 명령으로 기존 즉시 reconciliation 실행을 사용한다.

```powershell
docker compose --env-file .env.local @ComposeFiles restart --timeout 10 backend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 backend
```

실측에서는 `2026-08-09T05:43:58Z` 기준 executions `1`, failures `0`에서 시작했다. `2026-08-09T05:45:41Z`에 health `UP`, `up=1`, executions `1`, processed `4`, failures `1`과 subscription `3`의 `Lock wait timeout exceeded` log를 확인했다. lock holder는 `ROLLBACK`과 named lock release를 완료했다. Backend를 재시작해 기존 즉시 실행을 사용한 `2026-08-09T05:46:30Z`에는 health `UP`, `up=1`, executions `1`, processed `4`, failures `0`이었다. 재시작으로 counter가 reset되므로 복구 판정은 과거 failure 값의 감소가 아니라 원인 제거 후 새 failure 증가가 없는지로 판단한다.

## 복구와 중단

모든 시나리오 후 MySQL·Backend·Prometheus·Grafana·Proxy가 정상인지 다시 확인한다. lock fixture는 90초 뒤 스스로 `ROLLBACK`·release되며 데이터 변경이 없어야 한다. active fixture가 없거나 lock release를 확인할 수 없거나 데이터 변경이 필요하면 reconciliation 재현을 수행하지 않고 중단한다.

최종 검증에서 Grafana health `ok`, Dashboard UID `pawcycle-local-observability`, 13개 panel과 `Backend scrape availability`의 `up{job="pawcycle-backend"}` query를 확인했다. Java 25와 격리된 MySQL 8.4에서 `ObservabilityIntegrationTests` 2개가 통과했다. 최종 local stack은 MySQL·Backend·Proxy healthy, Prometheus·Grafana running 상태였다.

저장소 변경의 rollback은 Dashboard와 이 Runbook을 일반 revert하는 것이다. local stack 종료는 두 Compose 파일을 함께 지정한 `docker compose down`을 사용하고 named volume은 삭제하지 않는다.
