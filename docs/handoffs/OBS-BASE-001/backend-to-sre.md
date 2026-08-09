# OBS-BASE-001 Backend → Platform/SRE 인수인계

## Phase A 결과

- Backend에 Spring Boot Actuator와 Micrometer Prometheus registry를 추가했다.
- HTTP/JVM/GC/thread/process/system CPU/JDBC-Hikari 기본 metric은 Spring Boot binder를 사용한다.
- custom metric은 reconciliation과 idempotency cleanup service의 실제 실행 경계에서 기록한다.
- `/actuator/health`와 `/actuator/prometheus`만 read-only로 활성화하고 web discovery와 JMX 노출은 비활성화했다.
- Actuator 경로는 Backend 내부 포트에서 익명 GET을 허용하지만 internet-facing Proxy route에는 추가하지 않는다.

## Prometheus metric 계약

| Micrometer name | Prometheus name 또는 suffix | tag | 의미 |
| --- | --- | --- | --- |
| `pawcycle.subscription.reconciliation.executions` | `_total` | 없음 | reconciliation 실행 수 |
| `pawcycle.subscription.reconciliation.processed` | `_total` | 없음 | 실행에서 처리 시도한 활성 구독 수 |
| `pawcycle.subscription.reconciliation.failures` | `_total` | 없음 | 개별 구독 또는 실행 전역 실패 수 |
| `pawcycle.subscription.reconciliation.duration` | `_seconds_count`, `_seconds_sum` | 없음 | reconciliation 실행 시간 |
| `pawcycle.subscription.idempotency.cleanup.executions` | `_total` | `result=success|failure` | cleanup 실행 결과 수 |
| `pawcycle.subscription.idempotency.cleanup.duration` | `_seconds_count`, `_seconds_sum` | 없음 | cleanup 실행 시간 |
| `pawcycle.subscription.idempotency.cleanup.rows` | `_total` | `scope=creation|command`, `operation=repair|delete` | 처리 row 누적 수 |
| `pawcycle.subscription.idempotency.retained.rows` | gauge | `scope=creation|command` | `completed_at IS NOT NULL` 현재 row 수 |
| `pawcycle.subscription.idempotency.cleanup.candidates` | gauge | `scope=creation|command` | 현재 UTC 기준 30일 cutoff보다 이른 row 수 |

custom metric tag에는 `memberId`, `subscriptionId`, Idempotency-Key 또는 다른 개별 식별자를 넣지 않는다.

## Phase A 검증 증거

- Java `25.0.3`과 MySQL `8.4.10`(`utf8mb4`, `utf8mb4_0900_ai_ci`) 격리 환경에서 observability·security·reconciliation·idempotency cleanup 집중 계약을 검증했다.
- `ObservabilityIntegrationTests` 2개는 실제 embedded HTTP 요청을 포함해 통과했고, health·Prometheus 허용과 그 외 Actuator 경로 차단, 기본/custom metric, tag cardinality를 확인했다.
- Backend 전체 154개 중 152개가 통과했다. `ProductionAuthSmokeMemberBootstrapProcessTests` 2개 60초 timeout은 기존 `origin/main` A/B 기준선에서도 동일 재현된 독립 local 환경 실패이며 이번 작업에서 timeout 또는 production-auth 코드를 변경하지 않았다.
- Java 25 `./gradlew build -x test`, `OBS-BASE-001 / 일반 / 저장소 변경` validator와 `git diff --check`가 통과했다.
- Production·Cloud·AWS 실행은 없었다.

## Phase B 사용 경계

- Prometheus는 local Docker network에서 Backend의 `/actuator/prometheus`를 직접 scrape한다.
- Proxy에 `/actuator/**` route를 추가하지 않는다.
- Grafana datasource와 Dashboard는 provisioning 파일로 구성하고 수동 클릭을 완료 조건으로 사용하지 않는다.
- reconciliation은 기존 조건부 trigger 외에 새 cadence를 정하지 않는다.
- idempotency cleanup에는 승인된 runtime trigger와 운영 batch size가 없으므로 Phase B에서 Scheduler·API endpoint·기본 batch size를 추가하지 않는다. cleanup metric의 실제 계측은 Phase A Backend integration test 증거를 사용한다.
- Production Compose, Production healthcheck, AWS, CloudWatch, Alertmanager와 알림 설정은 변경하거나 실행하지 않는다.

## Phase B 완료 증거

- Prometheus Backend target `UP`
- provisioning된 Grafana datasource와 Dashboard
- 테스트 트래픽 전후 HTTP/JVM/DB 및 실행 가능한 custom metric 변화
- local CPU·memory·storage baseline
- Proxy에서 `/actuator/**` 비공개 경계
- Production·Cloud·AWS 실행 없음
