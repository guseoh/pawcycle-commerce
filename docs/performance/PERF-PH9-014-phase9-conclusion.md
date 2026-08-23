# PERF-PH9-014 Phase 9 증거 종합과 종료 판정

## 작업 경계

- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 역할: Tech Lead 판정, Platform/SRE 증거 해석
- 대상: `GET /api/products`와 PERF-PH9-001~011
- 제외: 애플리케이션·DB·인프라 변경, k6/JFR 실행, PERF-PH9-010·011 재실행, Production·Cloud 실행

## 최종 결정

Phase 9 안에서 추가 애플리케이션 최적화를 구현하지 않는다. CPU가 처리량의 중요한 부분 원인이고 CPU2.0에서도 서버 내부 saturation이 남는다는 시스템 수준 증거는 충분하지만, 현재 객체 변환·collection 처리·직렬화 중 하나를 최우선 hotspot으로 선택할 attribution evidence는 부족하다. 근거 없이 후보 하나를 선택하면 동일 조건 Before/After가 없는 추측 기반 변경이 되므로 Phase 9의 마지막 최적화로 정당화할 수 없다.

이 결정은 residual saturation이 없다는 뜻이 아니다. 병목의 존재와 특정 코드 hotspot의 식별을 구분한 no-change 판정이다.

## Phase 9 증거 종합

| 작업 | 확인된 증거 | 이 판정에서의 의미 |
| --- | --- | --- |
| PH9-001 | 제한 없는 로컬 기준 약 249.96 RPS. DB/Hikari가 주 병목이라는 신호는 약함 | 이후 제한 환경 결과의 해석 기준이지만 서로 다른 resource envelope 수치를 직접 동등 비교하지 않음 |
| PH9-004 | Tomcat128 조건에서 심각한 saturation | request concurrency와 제한된 backend 처리 능력 사이의 queueing 확인 |
| PH9-005·006 | CPU와 memory 변화가 실제 성능에 인과 영향 | resource envelope가 결과를 지배하는 요인 중 하나임을 확인 |
| PH9-007 | 목록 DB read와 scalar snapshot materialization만 read-only transaction에 남기도록 범위 축소. 처리량 개선은 약하고 SQL은 계속 저렴함 | transaction 밖 grouping/DTO 조립 자체를 제거하거나 이동하면 크게 개선된다는 근거가 없음 |
| PH9-009 | Hikari 10→20에서 처리량 악화 | connection pool capacity 확대를 해법에서 제외. 높은 pending만으로 DB capacity 병목을 단정할 수 없음 |
| PH9-010 | CPU2.0에서 약 193.49 RPS, dropped 5,808, p50 약 4,582ms, p95 약 6,855ms, p99 약 8,591ms, Hikari pending peak 116, SQL mean 약 0.68ms. PH9-007 대비 RPS 약 25.5% 증가 | CPU는 중요한 부분 원인이지만, 저렴한 SQL과 함께 높은 queueing·latency가 남아 애플리케이션/JVM/framework 내부 비용의 분해가 필요함 |
| PH9-011 | warm-up 완료 뒤 measurement가 최소 37초 진행되고 출력상 250 iters/s 유지. 정상 aggregate와 `diagnostic-summary.json`은 없고 JFR은 Duration 0s, ExecutionSample 0 | 부분 k6 출력은 완료 성능 결과가 아니며 JFR은 hotspot attribution에 사용할 수 없음. Outcome C — profiling evidence insufficient |

PH9-010의 CPU 인과관계는 특정 Java method나 allocation site를 지목하지 않는다. PH9-011이 그 구분을 위해 준비됐지만 유효 sample을 남기지 못했으므로, PH9-001~010의 시스템 지표만으로 mapping, Hibernate materialization, Jackson, GC, lock 또는 framework dispatch 중 하나를 우선순위 1로 확정할 수 없다.

## 현재 상품 목록 요청 경로

1. `ProductController.products()`가 `ProductQueryService.findProducts()` 결과를 그대로 반환한다.
2. transaction 밖의 `ProductQueryService`가 `ProductListReader.read()`를 호출한다.
3. `ProductListReader.read()`의 read-only transaction 안에서 공개 상품 조회와 ACTIVE SKU batch 조회를 실행한다. 상품이 있으면 현재 계약은 정확히 2 queries다.
4. 같은 transaction 안에서 JPA entity를 `ProductSnapshot`과 `SkuSnapshot` scalar record로 materialize한다. 상품 목록은 snapshot 생성과 ID 목록 생성을 위해 각각 순회한다.
5. transaction 밖에서 SKU snapshot을 product ID로 `LinkedHashMap<Long, List<SkuSnapshot>>`에 grouping한다.
6. product snapshot마다 SKU를 `SkuPrice`로 mapping하고 별도 `anyMatch`로 구독 가능 여부를 계산한 뒤 `ProductSummary`와 `ProductListView`를 만든다.
7. Controller에는 별도 변환이나 custom serializer가 없으며 Spring MVC/Jackson이 record response를 JSON으로 직렬화한다.

API shape, 공개 상태, 정렬, 빈 배열 및 2-query 계약은 통합 테스트로 보호된다.

## 검토한 기존 구조 후보

### Entity snapshot과 최종 DTO의 중복 materialization 제거

현재 ACTIVE SKU마다 `SkuSnapshot` 뒤 `SkuPrice`가 생성되고 중간 SKU 목록이 grouping된다. 이 경계를 합치면 객체와 collection 수를 줄일 가능성은 있다. 그러나 PH9-007은 DB transaction을 scalar snapshot 생성까지만 제한하기 위해 이 경계를 의도적으로 만들었고, 그 변경의 처리량 효과는 약했다. 경계를 다시 이동하거나 snapshot 구조를 최종 response에 결합하면 allocation 감소 가능성과 transaction hold time·계층 책임의 trade-off가 생긴다. 유효 CPU/allocation stack 없이 어느 쪽이 우세한지 판단할 수 없어 구현하지 않는다.

### 반복 순회 결합

상품 snapshot과 product ID 생성을 한 loop로 합치거나, SKU price mapping과 `anyMatch`를 한 loop로 합칠 수 있다. 이는 코드에서 확인 가능한 반복 작업이지만 catalog cardinality, 해당 단계의 CPU 비중, allocation pressure가 측정되지 않았다. 작은 loop 합치기를 residual saturation의 최우선 원인으로 설명할 수 없어 구현하지 않는다.

### Repository projection 또는 직렬화 변경

JPA projection은 entity materialization을 줄일 가능성이 있고 custom serialization은 response 생성 비용을 바꿀 수 있다. 전자는 현재 2-query의 query·mapping 계약을 변경하고, 후자는 public API와 framework 경계 위험을 높인다. 어느 단계에도 attribution evidence가 없어 Phase 9 후보에서 제외한다.

## PH9-011 harness/JFR lifecycle 판정

JFR은 JVM startup에서 `duration=300s`로 시작된다. 그러나 wrapper의 `Wait-JfrArtifact`는 container artifact가 non-empty인지 `test -s`로만 확인한다. JFR 파일은 recording 종료 전에도 non-empty일 수 있으므로, diagnostic 경로가 정상 summary 없이 끝난 뒤 `finally`가 실행되면 아직 열려 있는 recording을 `docker cp`할 수 있다.

이번 artifact의 Duration 0s와 ExecutionSample 0은 이 completion gate가 recording 완료를 증명하지 못했음을 확인한다. 파일 존재·크기, copy 성공, `jfr summary` command 성공은 300초 recording의 정상 finalization과 sample coverage를 대신하지 못한다. 따라서 artifact는 hotspot 분석에 사용하지 않고 PH9-011 first-result도 재실행하지 않는다.

후속 harness는 새 profiling 승인과 분리해 다음을 먼저 증명해야 한다.

- recording duration 종료 또는 명시적 dump·stop의 완료 상태를 확인한 뒤에만 artifact를 복사한다.
- copied artifact에서 duration과 필요한 event coverage가 0이 아님을 validation한다.
- diagnostic child가 aggregate/summary 없이 끝나도 lifecycle metadata와 실패 원인을 보존한다.
- workload 시작 뒤 NEVER RERUN, host local temp, artifact recovery와 CPU2.0 rollback 경계를 유지한다.

이 문서는 harness를 수정하거나 새로운 first-result를 승인하지 않는다.

## 남아 있는 병목 판정

- CPU 증가는 처리량을 개선했으므로 CPU pressure는 실제 부분 원인이다.
- SQL mean은 약 0.68ms이고 Hikari20은 악화됐으므로 DB query 실행 시간이나 pool capacity를 주 병목으로 선택할 근거는 없다.
- CPU2.0에서도 dropped iteration과 수 초의 tail latency, Hikari pending이 남아 request 내부 처리 지연과 queueing은 해소되지 않았다.
- 남은 비용이 JPA materialization, snapshot/DTO 조립, Jackson serialization, GC/allocation, JVM/framework scheduling 또는 lock/contention 중 어디에 집중되는지는 미확인이다.

## Phase 10으로 넘길 구조적 질문

1. 유효한 CPU·allocation·GC·monitor event coverage에서 `/api/products` stack의 시간과 allocation은 Hibernate materialization, snapshot/DTO 조립, Jackson serialization, framework/runtime 중 어디에 분포하는가?
2. SQL 실행은 저렴한데 Hikari pending이 커지는 구간에서 connection acquire wait, transaction hold, 두 query 실행, entity materialization, commit/close 시간을 어떻게 분리할 것인가?
3. Tomcat128과 CPU2.0 조건에서 runnable thread queue, context switching, GC pause, monitor/lock contention 중 tail latency를 설명하는 신호가 있는가?
4. 실제 Phase 9 fixture의 product/SKU cardinality와 response bytes에서 중간 snapshot·grouping·JSON serialization 비용은 요청 CPU의 유의미한 비율인가?
5. completion-aware JFR lifecycle과 aggregate 보존을 독립 검증한 뒤에도 profiling 재시도가 필요한가? 필요하다면 기존 PH9-011 재실행이 아니라 별도 승인된 새 작업·새 first-result로 어떻게 경계를 고정할 것인가?

## 검증과 실행 경계

- `ProductListReaderTests`와 `ProductQueryServiceTests` 9개는 통과해 scalar snapshot, 빈 목록의 second query 생략, grouping/response 조립과 read-only transaction 경계를 확인했다.
- `ProductApiIntegrationTests` 7개는 local host에 JDBC URL이 제공되지 않아 ApplicationContext 시작 전 실패했다(`'url' must start with "jdbc"`). local MySQL은 host port를 publish하지 않으며, 코드 실패로 판정하지 않는다. 2-query·JSON 계약의 CI 확인은 repository MySQL service 환경에 남긴다.
- compile/test class 생성, 문서·commit·PR validator와 `git diff --check`를 실행한다.
- k6, JFR, PERF-PH9-010·011 및 추가 성능 workload는 실행하지 않는다.
- CI 성공은 Production Verified 또는 성능 개선 증거로 표현하지 않는다.
