# Backend Architecture V2 baseline and guardrails

Task ID: `BACKEND-REFACTOR-V2-001` (Program T01) · Master #339 · Issue #340 · 저장소 변경.

Inventory 기준은 `90657b188b23f9405cf7ae469cf25b4db9ffee12`이다. 손으로 작성한
`backend/src/main/java/com/pawcycle/backend/**/*.java`만 세며 generated Q-type, test,
build output을 제외한다. 이 문서는 V2의 canonical baseline과 선택 기준이다.
기존 `backend-persistence-convergence.md`는 이전 전환의 계약·evidence로 보존한다.
이 baseline은 전체 Program의 완료 선언이 아니다.

## Current Delta

이미 main에 반영된 catalog/cart/checkout/payment JPA 전환과 004의 Order read/Quick
Reorder 분리를 다시 구현하지 않는다. T01은 inventory, dependency freeze, 선택 기준과
상품 비교 facet read 하나의 Querydsl pilot만 추가한다. API/schema/index, lock,
transaction ownership, idempotency, provider I/O, package topology는 변경하지 않는다.

## Source inventory

| 영역 | Java files | feature 밖 root files |
| --- | ---: | ---: |
| catalog | 210 | 별도 topology migration 대상 아님 |
| member | 34 | 별도 topology migration 대상 아님 |
| commerce | 199 | 93 |
| subscription | 93 | 43 |
| recommendation | 22 | 22 |
| interaction | 9 | 9 |
| common / foundation / application entry point | 8 / 9 / 1 | 해당 없음 |
| 합계 | 585 | |

아래 수치는 해당 symbol/annotation이 존재하는 **source file 수**다. query 수나
unique runtime bean 수가 아니다. `@Query`와 native, programmatic/declarative
transaction은 중첩될 수 있다. 단순 검색은 raw persistence leakage 판정을 대신하지 않는다.

| 기준 | files |
| --- | ---: |
| JdbcTemplate | 35 |
| JpaRepository | 36 |
| EntityManager | 7 |
| @Query | 25 |
| nativeQuery=true 또는 createNativeQuery | 8 |
| TransactionTemplate | 13 |
| @Transactional | 64 |
| Map<String, Object> | 11 |
| JPA Tuple | 2 |
| Object[] | 1 |

Master Issue의 JdbcTemplate 34개와 실제 35개의 차이는 004에서 분리된
`QuickReorderPersistenceAdapter`를 별도 파일로 집계한 데 있다. T01은 production
source 파일 수를 늘리지 않는다. Querydsl facet pilot 이후에도 비교 facts native query와
EntityManager는 남으므로 위 file-level counts는 같다.

### Runtime JdbcTemplate debt: 26 files

이 목록은 **현재 존재하는 debt를 동결한 inventory**다. 각 항목이 최종적인 KEEP
승인을 받았다는 뜻이 아니다. 새 JDBC 경로는 별도 이유와 regression evidence가 필요하다.

| 경로 / classes | 현재 유지 이유 / 후속 경계 |
| --- | --- |
| `interaction/InteractionEventPersistenceAdapter` | event write/read 모델, T03/T10 이후 독립 검증 |
| `recommendation/RecommendationQueryAdapter` | recommendation projection·조건 조합, T05 |
| `commerce/order/persistence/{OrderPersistenceAdapter, QuickReorderPersistenceAdapter}` | 004 책임 분리 완료; read typed projection T04, reorder lock/replay/cart mutation T06 |
| `commerce/payment/persistence/PaymentReconciliationPersistenceAdapter` | provider unknown/recovery와 multi-phase transaction, T06 |
| `commerce/cancellation/persistence/CancellationPersistenceAdapter` | order/payment/inventory 보상, T07 |
| `commerce/refund/persistence/RefundPersistenceAdapter` | refund claim/finalize/provider failure 경계, T07 |
| `commerce/returning/persistence/ReturnPersistenceAdapter` | return state/stock/refund 연계, T07 |
| `commerce/notification/persistence/NotificationPersistenceAdapter` | notification mutation와 cross-feature projection, T07 |
| `commerce/operations/persistence/OperationsQueryRepository` | commerce/subscription union 운영 read, T05 |
| `commerce/membership/persistence/{MembershipPersistenceAdapter, MembershipEvaluationPersistenceAdapter}` | membership projection/평가 snapshot·history·coupon 원자성, T05/T06 |
| `commerce/metrics/persistence/CommerceMetricsQueryRepository` | 집계 정확성과 read model, T05 |
| `subscription/persistence/RepeatCommerceQueryRepository` | repeat purchase read, T08 |
| `subscription/persistence/{SubscriptionAggregatePersistence, SubscriptionAggregateQueryPersistence, SubscriptionAggregateWritePersistence}` | aggregate facade/query/write, T08 read → T09 command |
| `subscription/persistence/{SubscriptionBillingPersistence, SubscriptionBillingRetryPersistence}` | billing claim/retry/order 결합, T09 |
| `subscription/persistence/SubscriptionDeliveryReminderPersistence` | reminder selection/claim, T09 |
| `subscription/persistence/{SubscriptionIdempotencyCleanupPersistence, SubscriptionIdempotencyReservationPersistence}` | retention/reservation/unique/lock protocol, T09 |
| `subscription/persistence/SubscriptionMetricsQueryRepository` | subscription aggregate metrics read, T08 |
| `subscription/persistence/{SubscriptionOrderPersistence, SubscriptionSchedulePersistence, SubscriptionShippingPersistenceAdapter}` | schedule/order/shipping 상태·CAS·lock 순서, T09 |

### Non-request JDBC candidates: 9 files

T11에서 entry point와 runtime isolation을 확정한다. 대량 import/migration/measurement의
JDBC 사용 자체는 제거 목표가 아니다. 이 분류만으로 Production 실행 권한을 부여하지 않는다.

- `catalog/maintenance/persistence/CustomerCatalogImportPersistence`
- `catalog/maintenance/persistence/CustomerCatalogRealismCorrectionPersistence`
- `catalog/maintenance/persistence/DemoCatalogImportPersistence`
- `catalog/maintenance/persistence/ProductDetailSectionFixturePersistence`
- `foundation/bootstrap/LocalCustomerCatalogV3FixtureService`
- `foundation/bootstrap/LocalQaBootstrapService`
- `foundation/bootstrap/LocalQaSubscriptionFixtureService`
- `subscription/migration/LegacySubscriptionMigrationProcessor`
- `subscription/performance/SubscriptionBurstMeasurementService`

### EntityManager and native boundaries

EntityManager 7개는 모두 persistence package에 있다:
`catalog/engagement/persistence/ProductEngagementPersistence`,
`catalog/product/persistence/{ProductComparisonQueryRepository, ProductDiscoveryQueryRepository}`,
`commerce/billing/persistence/BillingPersistenceAdapter`,
`commerce/cart/persistence/CartQueryRepository`,
`commerce/checkout/persistence/CheckoutPersistenceAdapter`,
`commerce/wishlist/persistence/WishlistQueryRepository`.

| Native-containing class | 유지하는 의미 |
| --- | --- |
| `commerce/CartRepository` | absent cart의 MySQL atomic ensure; find/save 대체 시 unique race |
| `commerce/DeliveryRepository` | callback 중복의 atomic delivery ensure |
| `catalog/admin/persistence/CategoryFacetRepository` | category facet atomic upsert |
| `catalog/engagement/persistence/ProductReviewSummaryRepository` | 최초 summary cache 생성의 atomic upsert |
| `catalog/engagement/persistence/ProductEngagementPersistence` | engagement 집계·write SQL; 개별 statement는 후속 convergence 대상 |
| `catalog/product/persistence/ProductComparisonQueryRepository` | facts scalar subqueries/availability/price snapshot; T01에서는 facet read만 pilot |
| `catalog/product/persistence/ProductDiscoveryQueryRepository` | 측정된 page-first query와 SQL shape/hint; 성능 근거 없이 변경 금지 |
| `commerce/billing/persistence/BillingPersistenceAdapter` | subscription_schedules cross-table JOIN UPDATE 원자성 |

### Raw mapping / projection debt

`QuickReorderPersistenceAdapter`는 SQL Map을 내부에서 파싱하고 typed `ReorderResult`를
반환한다. application에 raw JDBC Map을 직접 반환하지 않지만 내부 raw mapping debt가
남아 있다. `SubscriptionAggregateQueryPersistence`도 Map parsing과 Object[] parameter
조립을 포함하며 typed subscription projections를 반환한다. Object[] 검색 1개는 이
parameter 조립이므로 그 자체로 result leakage라고 주장하지 않는다.

`ProductComparisonQueryRepository`/`ProductDiscoveryQueryRepository`의 JPA Tuple은
persistence 내부에서 typed Row/Facts로 변환된다. pilot은 facet scalar `List<?>` cast만
제거하며 facts Tuple과 측정된 discovery SQL은 보존한다.

11개 Map<String,Object> source files의 나머지는 TossTestPaymentAdapter의 provider JSON,
InteractionEventRequest/InteractionService의 event payload, 4개 catalog import persistence,
LocalQaSubscriptionFixtureService, LegacySubscriptionMigrationProcessor다. payload Map과
import mapping을 일반 HTTP response의 raw persistence leakage와 혼동하지 않는다.
기존 application → api DTO 의존은 아래 125개 dependency edge로 별도 동결한다.

## Target package / dependency contract

기본은 `feature/{api,application,domain,persistence,infrastructure}`다.
Commerce feature는 cart, checkout, order, payment, inventory, coupon, billing, delivery,
cancellation, refund, returning, membership, notification, audit, operations, metrics다.
Subscription은 api/application/domain/persistence/automation을 분리하며 migration/performance는
운영 요청 경로와 격리한다. Recommendation/interaction도 같은 layer 원칙을 적용한다.
package move는 T02/T03 범위이며 T01에서 수행하지 않는다.

| Layer | 책임 / dependency |
| --- | --- |
| api | HTTP·validation·인증 context·DTO. application 호출; persistence/JPA Entity/JdbcTemplate/EntityManager/Querydsl 참조 금지 |
| application | use case·transaction·domain 조합. domain/persistence/infrastructure 호출 가능; api DTO/직접 SQL 도구 참조 금지 |
| domain | entity/value/invariant/state transition. pragmatic JPA mapping 허용; api/infrastructure 의존 금지 |
| persistence | JPA/JPQL/lock/typed projection/선택적 Querydsl/이유 있는 native SQL. raw result를 경계 밖에 노출하지 않음 |
| infrastructure | Toss/AI/Redis 세부사항. provider DTO를 domain/API로 누출하지 않음 |
| automation | scheduling/trigger; use case의 transaction·lock ownership을 우회하지 않음 |
| maintenance/bootstrap/migration/performance | 명시적 실행 도구. normal runtime이 직접 의존하지 않음 |

단일 concrete adapter가 충분하면 불필요한 port/interface를 만들지 않는다. read model은
feature-local Row/View/record를 사용하고 HTTP DTO와 분리한다. OSIV=false는 유지한다.

## Persistence decision matrix

| 선택 | 적용 기준 | 금지 / 필요한 evidence |
| --- | --- | --- |
| Spring Data JPA | CRUD, id/unique lookup, association, entity transition, optimistic version, pessimistic row lock | JDBC 의미를 추정해 대체하지 않음; lock/flush/missing-row/rollback은 MySQL regression |
| derived query / JPQL @Query | 짧고 고정된 조건·집계 | 짧은 query를 Querydsl로 강제 전환하지 않음 |
| Querydsl | optional filter, sort, multi-join typed read, 반복 query family의 조합 가치 | API predicate binder/QuerydslPredicateExecutor 금지; typed result·기존 결과/SQL semantics·query count 비교 |
| native SQL | vendor atomic upsert/CAS/locking, 측정된 hint/shape, bulk/import/migration | 개별 이유·owner·보호 test 필요; JPA 순도를 위해 atomicity를 제거하지 않음 |
| JdbcTemplate | bulk/maintenance/bootstrap/migration/performance, 아직 검증 전인 runtime protocol | normal customer path는 제거 또는 검증된 이유를 가진 allowlist; 0개 자체가 목표가 아님 |

## Transaction / high-risk boundary inventory

`TransactionTemplate` 13개 owners:
Commerce root `CancellationService`, `DeliveryService`, `PaymentReconciliationService`,
`RefundService`, `ReturnService`, `SubscriptionBillingProcessor`, `SubscriptionBillingRetryProcessor`;
Subscription root `SubscriptionOrderProcessor`, `SubscriptionReconciliationApplicationService`;
layered application `BillingApplicationService`, `CheckoutApplicationService`,
`OrderApplicationService`, `PaymentApplicationService`.
`@Transactional` 64개는 owner 64개를 의미하지 않는다. persistence read-only와 application
command가 섞여 있다. 최종 목표는 application use-case ownership이며 아래 protocol 확인
없이 TransactionTemplate을 annotation으로 일괄 치환하지 않는다.

| 경계 | 현재 확인 / 다음 write migration의 보호 기준 |
| --- | --- |
| Checkout / Inventory | CheckoutApplicationService의 transaction; member/cart/items 이후 SKU/Product/inventory 및 coupon lock. PERF shared-SKU 직렬화, reservation/release와 movement 원자성 보존. CheckoutIdempotencyIntegrationTests 및 CommercePurchaseIntegrationTests 유지 |
| Quick Reorder | OrderApplicationService transaction; member → idempotency result → cart → source items join locking read. absent cart 생성/unique member, replay key/source-order 일치, cart mutation/result 저장·rollback 보존 |
| Payment / reconciliation | PaymentApplicationService prepare/finalize transaction 사이 provider I/O 분리. payment+order join lock, absent cart atomic ensure, provider unknown/recovery 보존. PaymentCartConcurrencyIntegrationTests, PaymentReconciliationServiceTests |
| Catalog admin | 전체 Product id순 lock → CategoryFacet definition순 → ProductFacetValue relation lock. missing relation 확인과 remove/set 경합 보존. CatalogFacetConcurrencyIntegrationTests, CategoryFacetUpsertConcurrencyIntegrationTests |
| Cancellation / Refund / Return / Delivery | 기존 service transaction과 claim/provider/finalize/stock 보상 경계 유지. T07 전 lock 순서/unique/missing row/retry/rollback을 실제 구현별로 다시 분석. RefundServiceTests/ReturnServiceTests는 unit evidence이며 전체 lock protocol 증거를 대신하지 않음 |
| Subscription command | SubscriptionCommandApplicationService @Transactional; reservation에서 owned subscription → command-key row lock. creation은 member → creation-key row lock. missing-key 동작과 unique keys 보존. SubscriptionIdempotencyConcurrencyIntegrationTests, IdempotencyCleanupConcurrencyIntegrationTests |
| Subscription order / billing / retry | 기존 processor의 programmatic transaction phases와 schedule/order/billing claim 유지. SubscriptionOrderAutomationServiceIntegrationTests 및 billing unit evidence 보존; T09에서 MySQL concurrent claim/rollback/duplicate order 경계를 구체화 |

표는 T01에서 확인한 baseline과 **추가 검증이 필요한 경계**를 구분한다. after-sales/billing의
unit test 존재만으로 모든 concurrency가 검증됐다고 주장하지 않는다. 각 high-risk write
migration 직전에 owner → lock order → missing-row → unique → idempotency → retry → rollback
inventory를 실제 코드·migration·MySQL test로 고정해야 한다.

## Querydsl pilot decision

**KEEP — facet read 한 곳과 build foundation에 한정한다.**

OpenFeign `io.github.openfeign.querydsl:querydsl-jpa:7.7`과 동일 version `querydsl-apt:7.7:jpa`를
사용한다. Spring Boot 4.1.0 / Java 25 / Hibernate 7.4.1.Final은 기존 dependency management를
유지한다. 별도 Querydsl Gradle plugin, Q-source 수동 copy, IDE-only generation은 없다.
Gradle compileJava annotation processor가 `build/generated/sources/annotationProcessor/java/main`
아래 Q-type을 생성하며 clean이 제거한다. IntelliJ는 Gradle project reload와 Gradle build를
사용한다. 실제 IntelliJ UI에서 import 여부는 별도 확인 대상이다.

양방향 Member/MemberAddress의 static Q initialization cycle을 피하기 위해
`-Aquerydsl.createDefaultVariable=false`를 사용한다. persistence에서 `new QType("alias")`를
생성하고 Q-type을 public application/API 경계에 전달하지 않는다.

Pilot은 `ProductComparisonQueryRepository.findFacets`의 3-table inner join을 typed Q path와
String projection으로 표현한다. 기존 product id predicate, definition id → option display
order → option id 정렬, key/value concat, empty result를 보존한다. key/value는 migration과
mapping 모두 NOT NULL이므로 nullable concat은 reachable behavior가 아니다. 새로운 query,
sort/filter/HTTP DTO/transaction을 추가하지 않는다. 기존 native facts와 discovery는 유지한다.

`ProductComparisonQuerydslIntegrationTests`는 이전 SQL과 동일 MySQL fixture에서 결과를
비교하고 Unicode/colon/empty value, 동률 sort, 여러 definition, 다른 product/없는 product를
검증한다. test-only StatementInspector로 이전처럼 단일 SELECT/동일 3-table join shape이며
추가 entity fetch나 FOR UPDATE가 없음을 확인한다. 이는 query-count/shape 비교이며 부하
benchmark를 대신하지 않는다. 문자열 DB 이름/cast 대신 mapped association과 typed scalar를 사용하는 가치는
확인하되, 성능 개선을 주장하지 않는다. optional-filter query 전면 확장·동적 read model은
각 후속 Task의 Delta에서 판단한다. 짧은 고정 JPQL에 대한 기존 선택은 유지한다.

Sources: [Spring Data Querydsl annotation processing](https://docs.spring.io/spring-data/jpa/reference/repositories/core-extensions.html),
[OpenFeign Querydsl 7.7](https://github.com/OpenFeign/querydsl/releases/tag/7.7).

## Architecture guard decision

**KEEP — ArchUnit 1.5.0 test dependency와 명시적 dependency-edge baseline.**

`BackendArchitectureTests`는 Gradle production class output을 import한다. 모든 rule은
main source 전체에 적용하며 package segment로 layer를 판정한다. 테스트와 library jar는
origin에 포함하지 않는다. generated Q Entity/Embeddable BeanPath는 origin에서 제외하지만,
handwritten code의 Q-type/Querydsl 사용은 persistence 밖에서 금지한다.

- application의 JdbcTemplate/EntityManager와 application → api 금지
- domain → api/infrastructure 금지
- api → persistence와 api의 Entity/SQL 도구 참조 금지
- normal runtime → maintenance/bootstrap/migration/performance 금지
- handwritten non-persistence → Querydsl/Q-type 금지

api Entity 규칙은 return만이 아니라 필드·signature·generic dependency도 제한한다. API에서
직접 Entity를 사용하는 새 우회도 막는다. flat root class는 아직 layer 판별이 되지 않으므로
T02/T03 package normalization이 enforcement 범위를 넓힌다. 이 staged 한계를 숨기지 않는다.
reflection/string-based runtime lookup, JSON raw mapping, transaction correctness, 성능은 이
dependency guard로 증명하지 않으며 해당 contract/MySQL test가 담당한다.

`src/test/resources/architecture/legacy-dependencies.txt`에 baseline 131개 edge를 고정한다:
application → api 125, api → persistence 1(CouponView 위치), runtime → isolated 5(CLI entry
point와 import facade). domain-adapter/application-sql/api-storage/Querydsl 예외는 없다.
기존 edge가 사라지면 baseline에서 삭제해야 한다. 신규 edge 또는 사라진 edge를 테스트가
자동 승인하거나 baseline에 쓰지 않는다. 실패 diagnostic만 build/reports에 저장한다.
새 예외 추가는 이유와 해당 Task evidence를 동반한 명시적 diff review가 필요하다.

CI는 기존 Backend lane의 `gradlew test`에서 guard와 MySQL pilot을 함께 실행한다.
새 workflow나 별도 flaky service gate는 추가하지 않는다. 재현 명령:

```powershell
cd backend
.\gradlew.bat clean compileJava compileTestJava
.\gradlew.bat test --tests '*BackendArchitectureTests'
# SPRING_DATASOURCE_*는 disposable MySQL의 테스트 설정으로 제공
.\gradlew.bat test --tests '*ProductComparisonQuerydslIntegrationTests' --tests '*CatalogExpansionReviewRegressionIntegrationTests'
.\gradlew.bat test
.\gradlew.bat build -x test
```

Source: [ArchUnit 1.5.0](https://github.com/TNG/ArchUnit/releases/tag/v1.5.0).
실제 실행 결과와 CI/head는 PR에 기록한다. 이 문서의 KEEP은 Production Verified나
병합 승인이 아니다. T01 복구는 코드/build/test/document 변경의 일반 revert이며
DB/data/운영 rollback은 필요하지 않다. T02는 T01 PR이 merge된 뒤에만 시작한다.
