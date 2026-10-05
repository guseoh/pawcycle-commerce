# I10K Query Performance Improvement #1

- 작업 ID: PERF-I10K-001
- 작업 등급: 고위험
- 실행 구분: 실제 운영 실행
- main SHA: `09d012af1b579ea9ccf27218e21cb8af5d05ee87`
- 실행일: 2026-10-05 KST

## 목적

기존 I10K 50 RPS Before에서 지배적인 COUNT_QUERY 비용을 개선한다. 변경은 unfiltered NEWEST/RECOMMENDED count SQL의 `JOIN_PREFIX(p)` 제거 하나다. `NO_BNL(c, b)`, `FORCE INDEX(PRIMARY)`, list SQL, schema, dataset과 resource 설정은 유지한다.

## 명시적 승인 근거

사용자의 “I10K Query Performance Improvement #1 — Continuation Delta”는 기존 Before 재사용, read-only SQL 분석, 최소 변경, 기능 확인 후 기존 isolated runtime에 후보 적용, 동일 50 RPS After 및 KEEP일 때 Draft PR 생성을 명시적으로 승인했다. 대상은 synthetic I10K isolated schema/backend이며 Production 적용은 승인 범위에서 제외됐다.

## 적용 전 확인

기존 Before를 재실행하지 않았다. runtime class의 unfiltered count SQL 상수가 저장소 코드와 일치했다. 기존 runtime은 healthy, restart 0, OOMKilled false, dataset `catalog-core-10k-v1`이었다. 기존 runner/one-shot launcher와 approved source archive를 사용했다. workload는 `GET /api/products`, 50 RPS, warm-up 30초와 measurement 2분이다.

## 결과 또는 증거

현재 plan은 products의 PRIMARY 강제 table scan 10,000행 뒤 brand/category PRIMARY lookup을 각각 10,000회 수행한다. COUNT 완료 시간은 22.0 ms이고 join output estimate 195행에 비해 actual은 10,000행이다.

`JOIN_PREFIX(p)`만 제외하면 작은 active brand/category 집합 4행을 hash build하고 products를 한 번 scan한다. brand 1행/loop 1, category 5행 중 active 4행/loop 1, products 10,000행/loop 1이다. 기존처럼 products는 table scan이며 새 index는 없다. After load 후 EXPLAIN ANALYZE는 5.77 ms다. output estimate는 6.11행으로 실제 10,000행을 여전히 과소평가한다. 통계나 다른 hint는 수정하지 않았다.

| 지표 | Before | After |
| --- | ---: | ---: |
| actual RPS | 49.95 | 49.975 |
| expected status error rate | 0 | 0 |
| dropped iterations | 0 | 0 |
| p50 ms | 73.205 | 65.961 |
| p95 ms | 311.507 | 89.694 |
| p99 ms | 550.828 | 116.473 |
| COUNT_QUERY 평균 ms | 25.652 | 5.972 |
| LIST_QUERY 평균 ms | 6.876 | 4.177 |
| ROW_MAPPING 평균 ms | 0.0189 | 0.0231 |
| REPOSITORY_TOTAL 평균 ms | 32.560 | 10.198 |
| Hikari acquire 평균 ms | 10.516 | 0.0046 |
| Hikari pending 관측 max | 10 | 0 |
| Hikari active 관측 max | 10 | 2 |
| Tomcat busy 관측 max | 21 | 3 |
| process CPU 사용률 평균 | 2.75% | 7.38% |
| JVM heap used 관측 max MiB | 143.72 | 116.67 |
| GC pause 증가량 ms | 49 | 81 |
| GC count 증가량 | 30 | 34 |

COUNT 평균 76.7%, repository 평균 68.7%, p95 71.2%, p99 78.9%, EXPLAIN 실행 시간 73.8% 감소했다. 기능 동일성과 API 악화 없음이 확인돼 **KEEP**으로 판정했다.

Before measurement: `2026-10-05T13:51:20.006Z`–`13:53:20.001Z`.
After measurement: `2026-10-05T14:19:35.929Z`–`14:21:35.914Z`.
각 measurement window의 기존 runner metric sample 20개를 사용했다. phase/acquire 평균은 window 내부 첫·마지막 sample의 sum/count delta다. peak는 sample 관측 max이며 순간 spike 전체를 보장하지 않는다.

## 적용 후 확인

기존 runtime JAR의 count SQL UTF-8 상수 하나만 교체한 임시 JAR를 read-only mount로 적용했다. archive entry 하나만 바뀌었음을 검증했다. 기존 image, 환경변수, CPU/memory/PID/port/security/network 설정과 dataset은 동일했다. 후보는 healthy, restart 0, OOMKilled false였다. After k6 exit는 0이며 status errors/dropped가 없다.

## 독립 확인

실제 I10K schema의 기존/후보 count는 모두 10,000이다. read-only CTE fixture에서도 PUBLIC만 포함하고 DRAFT, inactive category/brand를 제외해 동일한 count 1을 반환했다. candidate 적용 전후 및 After load 이후 첫 페이지 응답 전체의 canonical JSON SHA-256이 동일하므로 IDs/order, total과 모든 projection이 같다. HTTP 200, 첫 페이지 20개, total 10,000을 확인했다. 고객 데이터나 원시 response/ID는 저장하지 않았다.

`backend/gradlew.bat test --tests '*ProductDiscoveryQueryRepositoryDiagnosticsTests' bootJar --console=plain`은 성공했고 unit tests 7개가 통과했다. 기존 `ProductDiscoveryPageFirstIntegrationTests.unfilteredOrderedPagesPreserveVisibilityTotalsAndEveryProjection`는 PUBLIC/inactive/DRAFT, 모든 projection, NEWEST/RECOMMENDED와 pagination 동일성을 검증하며 Repository Validation CI의 MySQL service에서 전체 Backend 검증과 함께 실행한다.

## 복구·rollback

측정 후 임시 candidate mount를 제거하고 기존 Compose/config/image로 backend만 복구했다. 복구 결과 healthy, restart 0, OOMKilled false, mount 없음, image/환경변수 동일을 확인했다. KEEP은 저장소 변경 유지 판정이며 isolated runtime에 임시 JAR를 계속 유지한다는 의미가 아니다. Production 적용은 수행하지 않았다.

## 미실행 항목

로컬 전체 Backend validation은 직전 실행에서 datasource URL 미설정으로 context 초기화가 실패했다. 실행 중인 repository-owned test MySQL을 발견하지 못해 같은 실패를 반복하지 않고 Draft PR Repository Validation의 CI MySQL service에 full tests/build와 integration tests를 위임한다. Production datasource와 I10K credential을 테스트 환경으로 복사하지 않았다. merge, CodeRabbit 요청, Production 적용은 수행하지 않는다.

## 남은 위험

단일 Before/After이므로 반복 측정의 통계적 유의성이나 더 큰 catalog/cardinality의 성능을 주장하지 않는다. After는 backend를 재생성한 후보이며 30초 warm-up은 동일하지만 control/candidate runtime age는 다르다. process CPU 평균은 2.75%에서 7.38%, GC pause는 49에서 81 ms로 증가했으나 API percentile/pool 대기는 개선됐고 관측 CPU 포화는 없었다. 증가 원인은 확인되지 않았다.

Prometheus의 기존 정상 target은 Production backend다. isolated timer/JVM/Hikari/Tomcat 값은 기존 runner가 해당 backend의 `/actuator/prometheus`에서 자동 수집한 sample이며 Grafana에 isolated 시계열이 있다고 주장하지 않는다. 두 runner invocation 모두 종료에서 `pop_var_context` 오류/exit 1을 반환했다. k6는 각각 exit 0이고 pre/post gate는 통과했다. Before OCI 부가 evidence는 생성됐지만 After OCI aggregate evidence는 생성되지 않아 OCI 비교는 미검증이다. 이 부가 evidence 문제를 위해 Harness를 변경하거나 부하를 재실행하지 않았다. CI 결과는 PR의 현재 HEAD checks로 확인한다.

