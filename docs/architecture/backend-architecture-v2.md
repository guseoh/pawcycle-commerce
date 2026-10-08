# Backend Architecture V2 baseline and guardrails

Current Task: `BACKEND-REFACTOR-V2-007` (Program T07) · Master #339 · Issue #352 · 고위험 저장소 변경.
T02 topology: `BACKEND-REFACTOR-V2-002` · Issue #342 / PR #343.
T01 baseline: `BACKEND-REFACTOR-V2-001` · Issue #340 / PR #341.

Inventory 기준은 `90657b188b23f9405cf7ae469cf25b4db9ffee12`이다. 손으로 작성한
`backend/src/main/java/com/pawcycle/backend/**/*.java`만 세며 generated Q-type, test,
build output을 제외한다. 이 문서는 V2의 canonical baseline과 선택 기준이다.
기존 `backend-persistence-convergence.md`는 이전 전환의 계약·evidence로 보존한다.
이 baseline은 전체 Program의 완료 선언이 아니다.

## T01 baseline Delta

이미 main에 반영된 catalog/cart/checkout/payment JPA 전환과 004의 Order read/Quick
Reorder 분리를 다시 구현하지 않는다. T01은 inventory, dependency freeze, 선택 기준과
상품 비교 facet read 하나의 Querydsl pilot만 추가한다. API/schema/index, lock,
transaction ownership, idempotency, provider I/O, package topology는 변경하지 않는다.

## T01 source inventory

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
| `commerce/cart/persistence/CartRepository` | absent cart의 MySQL atomic ensure; find/save 대체 시 unique race |
| `commerce/delivery/persistence/DeliveryRepository` | callback 중복의 atomic delivery ensure |
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
기존 application → api DTO 의존은 T01의 125개, T02에서 드러난 14개와 T03의 47개 edge로 동결한다.

## Target package / dependency contract

기본은 `feature/{api,application,domain,persistence,infrastructure}`다.
Commerce feature는 cart, checkout, order, payment, inventory, coupon, billing, delivery,
cancellation, refund, returning, membership, notification, audit, operations, metrics다.
Subscription은 api/application/domain/persistence/automation을 분리하며 migration/performance는
운영 요청 경로와 격리한다. Recommendation/interaction도 같은 layer 원칙을 적용한다.
Commerce package move는 T02, Subscription/Recommendation/Interaction은 T03에서 수행한다.

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
Commerce feature application `cancellation.CancellationService`, `delivery.DeliveryService`,
`payment.PaymentReconciliationService`, `refund.RefundService`, `returning.ReturnService`,
`billing.SubscriptionBillingProcessor`, `billing.SubscriptionBillingRetryProcessor`;
Subscription automation `SubscriptionOrderProcessor`, application `SubscriptionReconciliationApplicationService`;
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
- persistence → 내부 api 금지 (`persistence-api`)
- normal runtime → maintenance/bootstrap/migration/performance 금지
- handwritten non-persistence → Querydsl/Q-type 금지

api Entity 규칙은 return만이 아니라 필드·signature·generic dependency도 제한한다. API에서
직접 Entity를 사용하는 새 우회도 막는다. T02는 Commerce root production class가 0임을
검증하여 layer 판별 범위를 넓힌다. T03도 Subscription/Recommendation/Interaction의 exact
root production class를 금지한다. 네 영역 모두 root가 0이며 legacy layer coupling은 아래 baseline으로 동결한다.
reflection/string-based runtime lookup, JSON raw mapping, transaction correctness, 성능은 이
dependency guard로 증명하지 않으며 해당 contract/MySQL test가 담당한다.

`src/test/resources/architecture/legacy-dependencies.txt`에 baseline 204개 edge를 고정한다:
application → api 186, api → persistence 1(CouponView 위치), persistence → api 12,
runtime → isolated 5(CLI entry point와 import facade).
domain-adapter/application-sql/api-storage/Querydsl 예외는 없다.
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
병합 승인이 아니다. T01/T02/T03 복구는 코드/build/test/document 변경의 일반 revert이며
DB/data/운영 rollback은 필요하지 않다. T04는 T03 PR이 merge된 뒤에만 시작한다.

## T02 Commerce topology Delta

기준 main은 `873f144e93979d3c40612fc4abdbb1895986fbd3` (T01 merge)다. 이번 Delta는
root class 이동, dependent import, discovery regression, guard와 이 문서 갱신만 포함한다.
JDBC/JPA/Querydsl 전환, query rewrite, DTO/Entity 재설계, transaction·lock·idempotency·provider
동작 변경, schema/index/migration과 T03 package normalization은 제외한다.

### Before / after

| 범위 | Before | After |
| --- | ---: | ---: |
| Commerce root direct production Java | 93 | 0 |
| Commerce production Java | 199 | 198 |
| Member production Java | 34 | 35 |
| Backend handwritten production Java | 585 | 585 |

92개는 Commerce feature/layer로 이동한다. `AddressCreatedResponse` 한 개는 실제 유일한
소비자인 `MemberAddressController`의 `member.address.api`로 이동한다. API body/JSON과
bounded context의 runtime ownership은 그대로다.

| Feature | root에서 이동한 class | 이동 후 feature total |
| --- | ---: | ---: |
| audit | 3 | 7 |
| billing | 17 | 20 |
| cancellation | 2 | 5 |
| cart | 7 | 17 |
| checkout | 8 | 20 |
| common | 3 | 3 |
| coupon | 7 | 19 |
| delivery | 4 | 8 |
| inventory | 2 | 10 |
| membership | 6 | 16 |
| metrics | 1 | 2 |
| notification | 3 | 7 |
| operations | 1 | 4 |
| order | 7 | 18 |
| payment | 8 | 15 |
| refund | 6 | 9 |
| returning | 3 | 5 |
| returnrequest | 1 | 3 |
| wishlist | 3 | 10 |
| 합계 | 92 | 198 |

### Classification / visibility

DTO는 실제 소비 feature의 `api`, service/processor/trigger는 기존 runtime feature의
`application`, repository는 `persistence`에 둔다. Toss의 payment/billing/refund 10개 type은
각 feature의 `infrastructure.toss`로 이동한다. provider interface, HTTP/client와 fallback은
보존한다. `CommerceException`은 `common.error`, handler는 `common.api`에 두고
`@RestControllerAdvice(basePackages = "com.pawcycle.backend.commerce")`를 그대로 유지한다.
공유 `ReasonRequest`는 cancellation/delivery/return API가 사용하므로 `common.api`에 둔다.

옮긴 JPA Entity 22개 중 domain 9개는 CartEntity/CartItemEntity, CommerceOrderEntity/
CommerceOrderItemEntity, CouponEntity/MemberCouponEntity, DeliveryEntity, PaymentEntity와
WishlistItemEntity다. 앞의 8개는 상태·행위 또는 aggregate 의미가 있고 WishlistItemEntity와
identity는 application에서 직접 사용하는 feature model이다. CartItemId/WishlistItemId도
같은 domain에 둔다. 나머지 mapping-only Entity 13개는 feature persistence에 둔다:
AdminAuditLogEntity, BillingPaymentMethodEntity, BillingPaymentMethodPreparationEntity,
CheckoutIdempotencyEntity, MemberMembershipEntity, MembershipGradeEntity,
MembershipHistoryEntity, NotificationEntity, OrderCancellationEntity, OrderReturnEntity,
RefundEntity, SubscriptionOrderContextEntity, SubscriptionShippingSnapshotEntity.
InventoryEntity/InventoryMovementEntity는 이미 feature-local이다. Commerce JPA Entity는
24개로 동일하며 JPA annotation/table/column/relation을 변경하지 않는다.

SubscriptionBilling service/processor/retry/trigger는 기존 Commerce billing runtime owner이므로
`commerce.billing.application`에 둔다. SubscriptionOrderContextEntity와
SubscriptionShippingSnapshotEntity는 application의 직접 사용 없이 DB context/snapshot을
매핑하므로 기존 Commerce order 소유권 안의 `order.persistence`에 둔다. T03/T09 bounded
context 재설계를 선행하지 않는다. AdminOrderQueryService와 CommerceMetrics도 기존
order/metrics runtime owner의 application으로 이동한다.

**Visibility 변경은 0개**다. package-private mapping은 repository/adapter와 co-location한다.
provider test 5개와 SubscriptionBillingProcessorTests 1개도 구현 package로 옮겨 기존
package-private constructor/helper 접근을 유지한다. method/field/class modifier를 넓히지 않는다.

### Baseline change evidence

T01의 131개 edge는 유지한다. **RESOLVED 0 / RECLASSIFIED 14 / NEW VIOLATION 0**이며
baseline은 145개다. 다음 표의 source는 모두 `commerce.<feature>.application`, target은
`commerce.<feature>.api`다. Before의 `root`는 `com.pawcycle.backend.commerce`를 뜻한다.
새 layer segment가 기존 DTO coupling을 탐지하게 하므로 14개를 명시적 diff로 추가했다.
테스트가 baseline을 생성하거나 승인하지 않는다.

| After source → target | Before dependency |
| --- | --- |
| audit.AdminAuditService → AdminAuditResponse | root source → 기존 audit.api target |
| billing.BillingApplicationService → BillingPreparationResponse | 기존 billing.application source → root target |
| billing.BillingMethodQueryService → BillingMethodResponse | root source → root target |
| cancellation.CancellationService → CancellationResponse | root source → 기존 cancellation.api target |
| checkout.CheckoutIdempotencyService → CheckoutResponse | root source → 기존 checkout.api target |
| coupon.CouponAdminApplicationService → CouponRequest | 기존 coupon.application source → root target |
| delivery.DeliveryService → DeliveryResponse | root source → 기존 delivery.api target |
| membership.MembershipAdminApplicationService → MembershipGradeRequest | 기존 membership.application source → root target |
| notification.NotificationService → NotificationResponse | root source → 기존 notification.api target |
| operations.OperationsQueryService → OperationsPendingResponse | root source → 기존 operations.api target |
| order.AdminOrderQueryService → AdminOrderResponse | root source → 기존 order.api target |
| payment.PaymentReconciliationService → PaymentReconciliationResponse | root source → 기존 payment.api target |
| refund.RefundService → RefundResponse | root source → 기존 refund.api target |
| returning.ReturnService → ReturnResponse | root source → 기존 returning.api target |

기준 main과 T02의 compiled production을 동일 ArchUnit importer로 비교했다. class/nested/Q-type의
package 이동을 정규화한 전체 dependency graph는 **817개 class / 7,602개 edge**로 동일하다.
추가/삭제 edge는 각각 0개이며 위 14개는 모두 Before graph에서 직접 확인했다. package/import를
제외한 기존 production/test body 비교도 동일하다(guard assertion 추가는 의도한 test 변경).
SQL/JPQL/native string, bean name/조건/scheduler, transaction/lock/idempotency와 HTTP/JSON
annotation/body는 그대로다. guard만으로 runtime correctness를 주장하지 않는다.

### Verification / remaining debt

`CommerceTopologyIntegrationTests`는 기존 Entity 24개의 이름을 metamodel과 대조하고 기존
Commerce JpaRepository 18개가 각각 한 개씩 등록되는지 확인한다. expiration processor와
exception handler bean도 확인한다. 기존 Commerce/MySQL regression과 전체 Backend test는
context/bootstrap, controller/JSON, lock/rollback/replay/provider 경계를 검증한다.
clean compileJava의 Q-type 재생성, compileTestJava, architecture guard, Commerce MySQL와
T01 facet pilot, full Backend tests, build, diff check, repository validator, CI를 완료 조건으로 둔다.
실행 결과/CI/최종 HEAD는 Draft PR에 기록한다.

JdbcTemplate 35, JpaRepository 36, EntityManager 7, native-containing class 8,
TransactionTemplate 13 등의 T01 persistence inventory는 그대로다. 새 Querydsl query/Q-type
사용을 추가하지 않는다. application → api 139개 debt와 나머지 legacy edge는 후속 convergence
대상이다. T02는 topology 정규화와 탐지 범위 확대이며 이 debt의 해결 선언이 아니다.
Draft PR 생성·CI 확인 후 STOP한다. Ready/merge/CodeRabbit 요청과 Production 실행은 하지 않는다.

## T03 Subscription / Recommendation / Interaction topology Delta

기준 main은 `5c077b878593dfc994dd10f23f0d7fdc9d864d60` (T02 merge)다. T03는 74개 root
class를 기존 runtime 책임에 따라 layer로 이동한다. 새 domain/persistence/provider abstraction,
query rewrite, projection/DTO field/type 변경, transaction·lock·idempotency·scheduler·AI·metrics
동작 변경과 T04 이후 persistence convergence는 제외한다. 일반 저장소 변경이며 Production 실행은 없다.

| 범위 | production Java Before / After | root Before | root After |
| --- | ---: | ---: | ---: |
| Subscription | 93 / 93 | 43 | 0 |
| Recommendation | 22 / 22 | 22 | 0 |
| Interaction | 9 / 9 | 9 | 0 |
| Commerce (T02 보존) | 198 / 198 | 0 | 0 |
| Backend 합계 | 585 / 585 | | |

### Classification

Subscription projection 17개는 SubscriptionAggregatePersistence/SubscriptionAggregateQueryPersistence
또는 SubscriptionIdempotencyReservationPersistence가 생성·반환하는 read/storage model이다.
PetProjection의 profileComplete helper, Schedule/Snapshot 구조도 그대로 두고
`subscription.persistence.projection`으로 이동한다. domain behavior를 새로 설계하지 않는다.
application 9개와 API 4개는 기존 HTTP/use-case 책임을 드러내고 automation 12개는 기존
scheduler/processor/trigger/metrics 실행 owner를 유지한다. LegacySubscriptionPreflight는 기존
migration package에 co-location한다. SubscriptionApiException의 HTTP status/code 계약과 기존
application/API coupling은 유지한다.

RecommendationCandidate/Brand/Category/MemberSignals/TrendScore는 JDBC/AI/API 구현 자체가
아닌 추천 계산의 공유 model이므로 domain에 둔다. RecommendationAiClient와 중첩
AiRecommendation은 기존 provider-facing application boundary다. ReasonSelection/Policy와
exception은 application, HTTP DTO/controller/advice는 API, JdbcTemplate adapter는 persistence,
AI 설정/응답은 infrastructure.ai, Micrometer 세부사항은 infrastructure.metrics에 둔다.
record shape와 AI prompt/model/property/fallback을 바꾸지 않는다.

Interaction은 API 5개, application 3개와 persistence adapter 1개로 충분하다. EventType은
service 내부 validation 분류이므로 application에 둔다. 별도 domain/infrastructure나 port를 추가하지
않으며 INSERT/ON DUPLICATE KEY와 batch rollback/duplicate protocol을 보존한다.

### T03 class move mapping

Before는 모두 `com.pawcycle.backend.<area>.<Class>` root다. 아래 After에는
`com.pawcycle.backend.` prefix를 붙인다.

| After package | Count | Classes |
| --- | ---: | --- |
| `interaction.api` | 5 | `InteractionBatchRequest`, `InteractionController`, `InteractionErrorResponse`, `InteractionEventRequest`, `InteractionExceptionHandler` |
| `interaction.application` | 3 | `InteractionEventType`, `InteractionException`, `InteractionService` |
| `interaction.persistence` | 1 | `InteractionEventPersistenceAdapter` |
| `recommendation.api` | 6 | `ProductRecommendationController`, `RecommendationController`, `RecommendationExceptionHandler`, `RecommendationItem`, `RecommendationItemCategory`, `RecommendationResponse` |
| `recommendation.application` | 7 | `ProductRecommendationService`, `ReasonSelection`, `RecommendationAiClient`, `RecommendationException`, `RecommendationPetNotFoundException`, `RecommendationPolicy`, `RecommendationService` |
| `recommendation.domain` | 5 | `RecommendationBrand`, `RecommendationCandidate`, `RecommendationCategory`, `RecommendationMemberSignals`, `RecommendationTrendScore` |
| `recommendation.infrastructure.ai` | 2 | `RecommendationAiConfiguration`, `RecommendationAiResponse` |
| `recommendation.infrastructure.metrics` | 1 | `RecommendationMetrics` |
| `recommendation.persistence` | 1 | `RecommendationQueryAdapter` |
| `subscription.api` | 4 | `RepeatCommerceController`, `SubscriptionApiException`, `SubscriptionController`, `SubscriptionExceptionHandler` |
| `subscription.application` | 9 | `PetPlanApplicationService`, `SubscriptionApplicationSupport`, `SubscriptionCommandApplicationService`, `SubscriptionCreationApplicationService`, `SubscriptionOperationResult`, `SubscriptionQueryApplicationService`, `SubscriptionReconciliationApplicationService`, `SubscriptionResult`, `SubscriptionService` |
| `subscription.automation` | 12 | `ScheduleReconciliationTrigger`, `SchedulingConfiguration`, `SubscriptionAutomationBatchResult`, `SubscriptionDeliveryReminderProcessor`, `SubscriptionIdempotencyCleanupProcessor`, `SubscriptionIdempotencyCleanupResult`, `SubscriptionIdempotencyCleanupService`, `SubscriptionMetrics`, `SubscriptionOrderAutomationMetrics`, `SubscriptionOrderAutomationService`, `SubscriptionOrderAutomationTrigger`, `SubscriptionOrderProcessor` |
| `subscription.migration` | 1 | `LegacySubscriptionPreflight` |
| `subscription.persistence.projection` | 17 | `AddonSkuProjection`, `CommandHistoryProjection`, `NextDeliveryProjection`, `PageProjection`, `PendingSubscriptionChange`, `PetProjection`, `PlanVersionProjection`, `ProcessedScheduleProjection`, `ScheduleAddonProjection`, `ScheduleProjection`, `ScheduleViewProjection`, `StoredIdempotencyResult`, `SubscriptionItemDetailProjection`, `SubscriptionItemProjection`, `SubscriptionProjection`, `SubscriptionSnapshot`, `SubscriptionSnapshotBase` |

### T03 visibility inventory

**52개 명시적 선언 / 18개 top-level type**에서 필요한 public 접근만 추가한다. package-private
co-location으로 해결 가능한 test는 application/api/automation에 옮긴다. SubscriptionServiceIntegrationTests는
기존 gauge refresh helper를 직접 사용하는 automation package에 둔다. 독립 AI-disabled test method는
RecommendationAiConfigurationTests로 옮겨 configuration class/bean method를 package-private로 유지한다.
이 test method의 기존 body는 그대로다. 총 11개 기존 test class를 co-location한다.

public record의 implicit canonical constructor와 accessor도 외부 Java package에서 접근 가능해진다.
Candidate/Response의 명시적 canonical constructor 외 record canonical constructor는 기존 component
shape를 유지한다. RecommendationPetNotFoundException의 implicit default constructor도 public
class visibility를 따른다. RecommendationAiClient.AiRecommendation의 중첩 contract는 변경하지 않는다.
이 Java surface 확대는 새 HTTP endpoint/JSON field 추가를 의미하지 않는다.

SubscriptionReconciliationApplicationService constructor는 외부 직접 생성 consumer가 없어
package-private를 유지하고 class/reconcileActiveSubscriptions만 public으로 바꾼다. Recommendation
service/query adapter와 Interaction adapter constructor도 기존 visibility를 유지한다. 아래 목록이 전체
명시적 변경이며 method body/signature type/annotation은 동일하다.

후속 correction에서 PetPlanApplicationService class와 6개 operation은 같은 application package의
SubscriptionService만 사용함을 확인하여 package-private로 복원했다. constructor도 기존
package-private다. RecommendationMetrics constructor 역시 package-private로 복원하고 test source의
동일 metrics package에 RecommendationMetricsFixture를 두었다. application test는 이 helper로
실제 SimpleMeterRegistry instance를 사용하며 metric 이름/tag/counter assertion은 그대로다.
Spring @Component 생성과 실제 cross-package 호출 operation의 public 접근은 유지한다.

| Type | Public declarations (기존 type/parameter 보존) | Reason |
| --- | --- | --- |
| `interaction.persistence.InteractionEventPersistenceAdapter` | `public class InteractionEventPersistenceAdapter`<br>`public boolean petBelongsToMember(long petId, long memberId)`<br>`public boolean productExists(long productId)`<br>`public void insert(InteractionRecord event)`<br>`public record InteractionRecord` | application이 기존 adapter operation/record 접근 |
| `recommendation.api.RecommendationItem` | `public record RecommendationItem`<br>`public RecommendationItem` | application이 기존 HTTP DTO를 생성/반환 |
| `recommendation.api.RecommendationItemCategory` | `public record RecommendationItemCategory(long categoryId, String name, String slug) {}` | application이 기존 HTTP DTO를 생성/반환 |
| `recommendation.api.RecommendationResponse` | `public record RecommendationResponse(String requestId, List<RecommendationItem> products)`<br>`public RecommendationResponse(List<RecommendationItem> products)`<br>`public RecommendationResponse` | application이 기존 HTTP DTO를 생성/반환 |
| `recommendation.application.ProductRecommendationService` | `public class ProductRecommendationService`<br>`public RecommendationResponse popular(String petType, int limit)`<br>`public RecommendationResponse trending(String petType, int limit)`<br>`public RecommendationResponse related(long productId, int limit)`<br>`public RecommendationResponse complementary(long productId, int limit)` | API/automation이 application service operation 접근 |
| `recommendation.application.RecommendationAiClient` | `public interface RecommendationAiClient` | infrastructure 구성과 application 호출 boundary |
| `recommendation.application.RecommendationException` | `public class RecommendationException extends RuntimeException`<br>`public int status()`<br>`public String code()`<br>`public RecommendationException(int status, String code, String message)` | API validation constructor와 advice status/code 접근 |
| `recommendation.application.RecommendationPetNotFoundException` | `public class RecommendationPetNotFoundException extends RuntimeException {}` | persistence 생성과 API advice type 접근 |
| `recommendation.application.RecommendationService` | `public class RecommendationService`<br>`public RecommendationResponse recommend(long memberId, long petId)` | API/automation이 application service operation 접근 |
| `recommendation.domain.RecommendationBrand` | `public record RecommendationBrand(long brandId, String name, String slug) {}` | persistence/application/AI에서 공유 model·delta 접근 |
| `recommendation.domain.RecommendationCandidate` | `public record RecommendationCandidate`<br>`public RecommendationCandidate`<br>`public RecommendationCandidate` | persistence/application/AI에서 공유 model·delta 접근 |
| `recommendation.domain.RecommendationCategory` | `public record RecommendationCategory(long categoryId, String name, String slug) {}` | persistence/application/AI에서 공유 model·delta 접근 |
| `recommendation.domain.RecommendationMemberSignals` | `public record RecommendationMemberSignals` | persistence/application/AI에서 공유 model·delta 접근 |
| `recommendation.domain.RecommendationTrendScore` | `public record RecommendationTrendScore(long recent, long previous)`<br>`public long delta()` | persistence/application/AI에서 공유 model·delta 접근 |
| `recommendation.infrastructure.metrics.RecommendationMetrics` | `public class RecommendationMetrics`<br>`public void success()`<br>`public void fallback()`<br>`public <T> T recordAiCall(Supplier<T> call)` | application/infrastructure 간 실제 production 호출 |
| `recommendation.persistence.RecommendationQueryAdapter` | `public class RecommendationQueryAdapter`<br>`public Map<Long, Long> coPurchaseCounts(long productId)`<br>`public String findOwnedPetType(long memberId, long petId)`<br>`public List<RecommendationCandidate> findPurchasableCandidates(String petType)`<br>`public Set<Long> activeSubscriptionProductIds(long memberId, long petId)`<br>`public Set<Long> exposedProductIds(long memberId, int days)`<br>`public RecommendationMemberSignals memberSignals(long memberId, long petId)`<br>`public Map<Long, Long> popularScores(String petType)`<br>`public Map<Long, RecommendationTrendScore> trendScores(List<Long> productIds, LocalDate today)`<br>`public List<String> subscriptionCategorySlugs(long memberId, long petId)`<br>`public List<String> purchaseCategorySlugs(long memberId)`<br>`public List<String> wishlistCategorySlugs(long memberId)` | application이 기존 adapter operation/record 접근 |
| `subscription.application.SubscriptionReconciliationApplicationService` | `public class SubscriptionReconciliationApplicationService`<br>`public void reconcileActiveSubscriptions()` | API/automation이 application service operation 접근 |
| `subscription.automation.SubscriptionMetrics` | `public Timer.Sample startReconciliation()`<br>`public void finishReconciliation(Timer.Sample sample, int processed, int failures)` | application reconciliation이 automation metrics를 호출 |

### T03 baseline evidence

T02의 145개 edge는 유지한다. **RESOLVED 0 / RECLASSIFIED 59 / NEW VIOLATION 0**, After는
204개(application-api 186 / api-persistence 1 / persistence-api 12 / runtime-maintenance 5)다.
새 persistence-api rule은 내부 API로 향하는 같은 feature/다른 feature의 persistence 의존성을
모두 탐지하며 외부 api와 persistence 내부 의존성을 허용하는 regression assertion으로 보호한다.
최초 실행에서 알려진 Subscription 2개 외에 Catalog maintenance 8개와 Commerce 2개도 탐지했다.
추가 10개 edge를 발생시킨 production source는 기준 main과 동일했고 main 소스와 compiled graph에서
각 edge의 존재를 확인한 후에만 baseline에 명시적으로 추가했다. 테스트는 baseline을 수정하지 않는다.
Before와 After의 compiled
production dependency graph는 동일 ArchUnit importer로 캡처했다. class/nested/array element의
package 이동을 정규화하면 **817개 class / 7,602개 edge, 추가 0 / 삭제 0**이다. enum의 `$VALUES`
배열 element 이름 이동도 정규화에 포함한다. 59개 모두 Before graph에서 같은 edge를 확인했다.
기존 705개 production/test source의 body 비교도 명시적 visibility, 독립 AI test co-location과
후속 correction의 test-only metrics helper 생성 호출을 제외하고 동일하다. SQL/row mapping, JPA,
Spring/transaction/schedule annotation, metric/prompt, API/JSON 필드와 기존 metric assertion을
보존했다. baseline은 테스트가 생성/승인하지 않으며 근거가 있는 59개만 명시적으로 추가했다.

아래 source/target에는 `com.pawcycle.backend.` prefix를 붙인다. Subscription target은 원래
`subscription.api`이며 **SubscriptionApiException만 Before `subscription.SubscriptionApiException`**이다.
Recommendation/Interaction target은 전부 Before area root이며 After `<area>.api`다. 각 target list는
개별 Before/After edge를 뜻한다. 모든 항목의 이유는 기존 의존성에 layer 이름이 생겨 guard가 새로
탐지한 application/API DTO·exception coupling이다.

기존 47개 application-api edge는 그대로 유지한다. 추가 12개 persistence-api edge는
다음과 같이 전수 대조했다. Subscription 2개는 main `5c077b878593dfc994dd10f23f0d7fdc9d864d60`에서도
각 persistence class가 `subscription.SubscriptionApiException`을 생성했다. target만
`subscription.api.SubscriptionApiException`으로 이동했으므로 새 semantic coupling이 아니다.
Catalog/Commerce 10개는 origin/target 이름과 body까지 main과 동일하며 새 rule로 기존 debt가 드러난다.

| Before/After source (Subscription target만 위 이동 적용) | Before/After targets | Edge count |
| --- | --- | ---: |
| `subscription.persistence.SubscriptionAggregateQueryPersistence` | `subscription.SubscriptionApiException` → `subscription.api.SubscriptionApiException` | 1 |
| `subscription.persistence.SubscriptionAggregateWritePersistence` | `subscription.SubscriptionApiException` → `subscription.api.SubscriptionApiException` | 1 |
| `catalog.maintenance.persistence.CustomerCatalogImportPersistence` | `catalog.admin.api.BrandCreateRequest`, `catalog.admin.api.CategoryCreateRequest`, `catalog.admin.api.DetailSectionCreateRequest`, `catalog.admin.api.FacetDefinitionCreateRequest`, `catalog.admin.api.FacetOptionCreateRequest`, `catalog.admin.api.ProductCreateRequest`, `catalog.admin.api.SkuCreateRequest` | 7 |
| `catalog.maintenance.persistence.CustomerCatalogImportPersistence$Product` | `catalog.admin.api.DetailSectionCreateRequest` | 1 |
| `commerce.coupon.persistence.CouponPersistenceAdapter` | `commerce.coupon.api.CouponRequest` | 1 |
| `commerce.membership.persistence.MembershipPersistenceAdapter` | `commerce.membership.api.MembershipGradeRequest` | 1 |

| Before source | After source | After targets (Before 위치는 위 규칙) | Edge count |
| --- | --- | --- | ---: |
| `interaction.InteractionService` | `interaction.application.InteractionService` | `interaction.api.InteractionEventRequest` | 1 |
| `recommendation.ProductRecommendationService` | `recommendation.application.ProductRecommendationService` | `recommendation.api.RecommendationItem`, `recommendation.api.RecommendationItemCategory`, `recommendation.api.RecommendationResponse` | 3 |
| `recommendation.RecommendationService` | `recommendation.application.RecommendationService` | `recommendation.api.RecommendationItem`, `recommendation.api.RecommendationItemCategory`, `recommendation.api.RecommendationResponse` | 3 |
| `subscription.PetPlanApplicationService` | `subscription.application.PetPlanApplicationService` | `subscription.api.CreatePetRequest`, `subscription.api.PageResponse`, `subscription.api.PetResponse`, `subscription.api.PlanItemResponse`, `subscription.api.PlanSaleResponse`, `subscription.api.PlanVersionResponse`, `subscription.api.SubscriptionApiException`, `subscription.api.UpdatePetRequest` | 8 |
| `subscription.SubscriptionApplicationSupport` | `subscription.application.SubscriptionApplicationSupport` | `subscription.api.SubscriptionApiException`, `subscription.api.SubscriptionDetailResponse` | 2 |
| `subscription.SubscriptionCommandApplicationService` | `subscription.application.SubscriptionCommandApplicationService` | `subscription.api.SubscriptionApiException`, `subscription.api.SubscriptionCommandRequest` | 2 |
| `subscription.SubscriptionCreationApplicationService` | `subscription.application.SubscriptionCreationApplicationService` | `subscription.api.CreateSubscriptionRequest`, `subscription.api.SubscriptionApiException` | 2 |
| `subscription.SubscriptionOperationResult` | `subscription.application.SubscriptionOperationResult` | `subscription.api.SubscriptionDetailResponse` | 1 |
| `subscription.SubscriptionQueryApplicationService` | `subscription.application.SubscriptionQueryApplicationService` | `subscription.api.CommandHistoryResponse`, `subscription.api.NextDeliveryResponse`, `subscription.api.PageResponse`, `subscription.api.PendingChangeResponse`, `subscription.api.PetResponse`, `subscription.api.ScheduleResponse`, `subscription.api.SubscriptionAddonResponse`, `subscription.api.SubscriptionApiException`, `subscription.api.SubscriptionDetailResponse`, `subscription.api.SubscriptionIssueResponse`, `subscription.api.SubscriptionItemDetailResponse`, `subscription.api.SubscriptionItemResponse`, `subscription.api.SubscriptionSnapshotResponse`, `subscription.api.SubscriptionSummaryResponse` | 14 |
| `subscription.SubscriptionResult` | `subscription.application.SubscriptionResult` | `subscription.api.SubscriptionDetailResponse` | 1 |
| `subscription.SubscriptionService` | `subscription.application.SubscriptionService` | `subscription.api.CreatePetRequest`, `subscription.api.CreateSubscriptionRequest`, `subscription.api.PageResponse`, `subscription.api.PetResponse`, `subscription.api.PlanVersionResponse`, `subscription.api.SubscriptionCommandRequest`, `subscription.api.SubscriptionSummaryResponse`, `subscription.api.UpdatePetRequest` | 8 |
| `subscription.application.SubscriptionConversionApplicationService` | `subscription.application.SubscriptionConversionApplicationService` | `subscription.api.SubscriptionApiException` | 1 |
| `subscription.application.SubscriptionCycleSuggestionApplicationService` | `subscription.application.SubscriptionCycleSuggestionApplicationService` | `subscription.api.SubscriptionApiException` | 1 |

### T03 validation / remaining debt

root guard는 Commerce/Subscription/Recommendation/Interaction 네 exact package의 production
type 재유입을 차단한다. RuntimeTopologyIntegrationTests는 기존 unconditional runtime component
28개의 단일 bean discovery, disabled AI client 단일 등록, controller route 19개와 advice target,
scheduler expression/전용 scheduler/metrics, 세 automation property의 disabled/enabled condition과
batch/window default를 검증한다. 조건 활성화 검증은 scheduling processor 없는 component-only
context에서 수행하여 job을 실행하지 않는다. 기존 MySQL regression은 query/command/order/
reconciliation/idempotency/billing/interaction duplicate·rollback과 HTTP/JSON을 보호한다.

clean compileJava/Q-type regeneration, compileTestJava, guard, 대상 MySQL, T01 Querydsl/T02
Commerce regression, full Backend, build -x test, diff check와 repository validators/CI를 완료 조건으로
둔다. 실제 실행 결과와 최종 HEAD는 Draft PR에 기록한다. Reflection/string lookup과 transaction
correctness는 dependency guard만으로 증명하지 않는다.

후속 correction은 clean compileJava → compileTestJava, guard 3개, PetPlan unit 1개,
Recommendation service unit 8개, 관련 targeted unit 10개, MySQL integration/topology 59개,
full Backend 432개(실패/오류/skip 0), build -x test를 통과했다. MySQL 8.4.11의 새 disposable
database와 UTC test JVM을 사용했다. 최종 validator/CI 결과와 HEAD는 같은 Draft PR #345에 기록한다.

JPA/Querydsl/native/JDBC는 KEEP이며 새 query를 추가하지 않는다. JdbcTemplate 35,
JpaRepository 36, EntityManager 7, native-containing class 8, TransactionTemplate 13 등 inventory는
그대로다. application/API 186개와 persistence/API 12개 coupling과 raw mapping 부채는 해결하지 않았다.
SubscriptionApiException의 persistence/API 경계 정리는 T10 debt로 남긴다. 또한 package split으로
드러난 아래 의존성도 flat package 시절부터 존재한 coupling이며 이번 correction에서 제거하지 않는다:

- `subscription.application.SubscriptionCommandApplicationService` → `subscription.automation.SubscriptionOrderAutomationService`: 기존 schedule 계산 공유, T09 command/automation convergence debt.
- `subscription.application.SubscriptionReconciliationApplicationService` → `subscription.automation.SubscriptionMetrics`, `subscription.automation.SubscriptionOrderAutomationService`: 기존 metrics와 schedule 계산 공유, T09 command/automation convergence debt.
- `recommendation.application.RecommendationService` → `recommendation.infrastructure.metrics.RecommendationMetrics`: 기존 metrics 호출, T10 application/provider convergence debt.

T04 typed read,
T05 recommendation/query, T08 Subscription read, T09 command/automation, T10 provider 경계가
후속 범위다. T03 Draft PR·CI 확인 뒤 STOP하고 T04는 시작하지 않는다.

## T04 Customer Order read convergence

T01~T03는 merge 완료됐다. T03 #344 / PR #345의 merge commit인
`fbf2e956d87be57644c13f724d1469b71ea1cf7c`를 T04 기준 main으로 고정한다.
이번 Delta는 customer Order read이며 Cart/Wishlist와 Admin Order read는 기존 구현을 유지한다.

### Persistence selection / preserved boundaries

- Cart: 기존 `CartQueryRepository`의 typed JPQL `CartItemRow → CartItemView`를 KEEP한다. production no-op이며 write/lock/version 변경은 없다.
- Wishlist: 기존 `WishlistQueryRepository`의 typed JPQL `WishlistItemRow → WishlistItemView`를 KEEP한다. production no-op이다.
- Order: `OrderPersistenceAdapter`의 JdbcTemplate/ResultSet read 8개를 EntityManager typed JPQL constructor projection으로 전환한다. 기존 `OrderView`와 `Summary`의 공개 shape는 유지한다.
- Querydsl은 확대하지 않는다. member list, owned header, items, latest payment, delivery, cancellation, return, refunds는 각각 고정 predicate/order/limit이며 optional filter나 dynamic sort 조합이 없다. 짧은 JPQL로 기존 query family를 직접 표현하는 편이 단순하다. 새 native SQL은 없다.

list는 member filter와 id DESC, detail은 orderId + memberId ownership을 보존한다. items는
id 순서와 기존 SKU/product/name/price/snapshotQuality snapshot, payment는 attemptNo DESC의
최신 1건, refunds는 attemptNo 순서다. 없는/타 회원 주문은 기존 null → ORDER_NOT_FOUND이며
optional child는 null, items/refunds는 empty list다. nullable restock은 Boolean 그대로다.
snapshot amount/address와 `OrderApplicationService.toResponse()`의 availableActions 및 API JSON은 변경하지 않는다.

기존 JPA mapping에 없던 cancellation.completedAt, return.restock/receivedAt/completedAt,
refund.reconciliationAttempts 5개 attribute만 `insertable=false, updatable=false`로 추가한다.
기존 DB column을 읽으며 entity visibility/getter/association과 write persistence body, schema,
index, migration을 변경하지 않는다. `OrderReadRows`는 persistence-local typed row이며 raw Map,
Object[] 또는 untyped Tuple을 application/API에 추가하지 않는다.

기존 read entry point에는 transaction이 없다. projection은 lazy entity 탐색이 필요 없어 새로운
readOnly transaction을 추가하지 않는다. Application transaction ownership, lock와 idempotency는 유지한다.
`QuickReorderPersistenceAdapter`는 missing-row lock/replay/cart mutation의 T06 경계이므로 변경하지 않는다.

### Timestamp compatibility

OrderView/Summary/API의 java.sql.Timestamp 계약은 유지한다. 현재 Hibernate 7.4의
`hibernate.jdbc.time_zone=UTC`는 UTC calendar로 Timestamp를 추출하고 LocalDateTime으로 감싼다.
OrderReadRows는 이 변환의 DATETIME wall value를 복원한 뒤 기존 JDBC getTimestamp의 JVM-local
해석을 적용한다. nullable 값과 microsecond precision을 보존하며 global time-zone 설정은 변경하지 않는다.
UTC와 Asia/Seoul의 별도 test JVM에서 기준 main의 frozen JDBC reference와 typed JPQL 결과 및
HTTP JSON을 비교한다. [Hibernate JDBC time-zone handling](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#basic-datetime-time-zone)은 현재 dependency source와도 대조했다.

### Actual source inventory / architecture Delta

production Java source를 symbol별로 집계한다. generated Q-type은 제외한다.

| Source inventory | Before | After | Delta |
| --- | ---: | ---: | ---: |
| JdbcTemplate | 35 | 34 | -1: customer Order read |
| runtime JdbcTemplate | 26 | 25 | -1 |
| EntityManager | 7 | 8 | +1: OrderPersistenceAdapter |
| `com.querydsl` reference | 1 | 1 | 0: 기존 T01 pilot |
| Backend production Java | 585 | 586 | +1: persistence-local OrderReadRows |

guard의 실제 report는 frozen baseline과 동일한 204개다: application-api 186,
api-persistence 1, persistence-api 12, runtime-maintenance 5.
**RESOLVED 0 / RECLASSIFIED 0 / NEW 0**이며 checked-in baseline을 변경하지 않는다.

### Regression / remaining debt

CustomerOrderReadModelIntegrationTests는 기준 main Order adapter의 test-only frozen JDBC reference와
실제 MySQL 결과를 대조한다. member isolation/id ordering, missing/childless order, FULL/LEGACY_PARTIAL
item snapshot, latest payment/refund attempt ordering, delivery lifecycle, cancellation/return timestamps,
nullable restock과 HTTP JSON/availableActions를 보호한다. Cart/Wishlist typed read도 ownership/order와
cart version 비변경을 검증한다. 기존 CommercePurchaseIntegrationTests와 T01 Querydsl pilot은 유지한다.

clean compile/Q-type regeneration → test compile → architecture guard → T01 pilot → focused read
regression → Commerce purchase → 영향받는 MySQL → full Backend → build → validators → 최신 HEAD
Repository Validation을 완료 조건으로 한다. 실제 실행 결과는 Draft PR에 기록한다.
T05 operations/metrics/membership/recommendation read, T08 Subscription read와 T06/T07의
write/concurrency debt는 남는다. Draft PR 이후 STOP하고 T05는 시작하지 않는다.

## T05 Secondary read persistence boundaries

T04 #346 / PR #347의 merge commit `e6f880dc9952c077f9d10fbff4d897c8c6b3baa5`를
T05 기준 main으로 고정한다. #348의 최신 Delta에 따라 Operations/Metrics/Membership/Recommendation만
다룬다. JDBC 파일 수 감소나 Querydsl 확대 자체를 완료 조건으로 삼지 않는다.

### KEEP / JPA / Querydsl selection

- Operations: 16종 cross-feature 운영 projection의 MySQL `UNION ALL`, correlated `NOT EXISTS`, latest payment subquery, DATE/DATETIME coercion, `CURRENT_TIMESTAMP(6)`와 final `createdAt DESC`를 그대로 KEEP한다. 분해하면 query 수와 조립 복잡성이 늘어난다. `queryForList()` raw Map parsing만 typed `PendingRow` RowMapper로 교체한다. `attemptNo` null, Timestamp와 availableActions 계약은 유지한다. T12 runtime JDBC allowlist 재확인 대상이다.
- Commerce Metrics: single scalar aggregate와 1분 scheduled gauge cache는 JDBC KEEP / production no-op다. 요청 경로가 아니며 raw Map leakage가 없다. JPA 전환으로 count 3개를 만들거나 vendor SQL을 다시 쓰지 않는다. metric name/status 집합/schedule/cache 의미는 유지한다.
- Membership: 고정 mapped read인 `findGrades()`와 `findForMember()`만 EntityManager typed JPQL `MembershipQueryRepository`로 분리한다. application consumer가 cross-package여서 class/constructor/read method는 최소 public이며 entity visibility/getter/association은 확대하지 않는다. customer membership과 admin list만 새 repository를 사용한다. `MembershipPersistenceAdapter.createGrade()`와 기존 Clock/constructor, evaluation/history/coupon/`FOR UPDATE` body는 유지한다.
- Recommendation: optional petType, Product/Category/Brand join, SKU/Inventory EXISTS와 facet ordering이 있는 candidate family는 package-private `RecommendationJpaQueryRepository`의 Querydsl typed constructor rows로 전환한다. mapped co-purchase와 purchase/wishlist category ranking도 이 repository에서 처리한다. `RecommendationQueryAdapter`의 public method shape와 popular score 조합은 유지한다. Q-type/JPAQueryFactory는 persistence 안에 둔다.

Membership은 displayOrder/id 순서, nullable benefitCouponId, current grade/amount, row 없을 때
BASIC fallback과 evaluatedAt Timestamp 계약을 유지한다. T04와 같은 현재 Hibernate UTC JDBC calendar
역변환을 persistence-local row에 적용한다. 기존 application read/write transaction owner와
`open-in-view=false`를 유지하며 새 transaction을 만들지 않는다.

Recommendation은 PUBLIC product, active category/brand, ACTIVE SKU와 available inventory > 0,
optional petType, product id 순서와 facet product/definition/displayOrder/option id 순서를 유지한다.
co-purchase는 DISTINCT order count이며 purchase/wishlist ranking의 기존 join multiplicity와
COUNT DESC/category id tie-break를 보존한다. medical filter/AI/scoring/ranking/exploration 정책은 변경하지 않는다.

### Explicit recommendation residual JDBC

다음은 기존 adapter 안에 KEEP하며 신규 Pet/Subscription/Interaction entity mapping을 추가하지 않는다.

| Residual family | KEEP reason |
| --- | --- |
| findOwnedPetType | pets mapping 없음 |
| activeSubscriptionProductIds / subscriptionCategorySlugs | subscription aggregate mapping 없음; T08 경계 |
| exposedProductIds / interactionCounts | interaction_events mapping 없음; MySQL UTC time-window analytics |
| memberSignals / productCounts / categoryCounts / facetCounts | cross-domain signal aggregates와 DATE_SUB time window |
| popularScores / addPopular | order/cart/wishlist/interaction 여러 source와 window의 기존 합산 |
| trendScores | derived table + UNION ALL |
| filterCounts / filterFacetCounts | JSON_EXTRACT/JSON_UNQUOTE/JSON_TABLE |

16개 residual method body와 Operations SQL은 기준 main과 동일하다. residual JDBC helper 파일 분리는
현재 책임 경계를 개선하지 않아 추가하지 않는다. 기존 Membership write의 persistence-api coupling은 남는다.
Subscription aggregate/read 자체는 blocker가 없어 T08로 유지한다.

### Actual inventory / guard / evidence

generated Q-type을 제외한 production source file 집계다.

| Source inventory | Before | After | Delta |
| --- | ---: | ---: | ---: |
| JdbcTemplate | 34 | 34 | 0: KEEP residual/write files |
| runtime JdbcTemplate | 25 | 25 | 0 |
| EntityManager | 8 | 10 | +2: Membership/Recommendation query repository |
| com.querydsl reference | 1 | 2 | +1: Recommendation query repository |
| Backend production Java | 586 | 588 | +2: query repositories |

Architecture actual report는 frozen baseline 204개와 동일하다: application-api 186,
api-persistence 1, persistence-api 12, runtime-maintenance 5.
**RESOLVED 0 / RECLASSIFIED 0 / NEW 0**이며 baseline을 생성하거나 추가 승인하지 않는다.

test-only frozen main JDBC reference와 실제 MySQL 결과를 비교한다. Operations 16종/type/id/time/attempt,
newer refund와 latest payment exclusion, CURRENT_TIMESTAMP microsecond/null/DATE coercion/actions,
Membership order/coupon/current/fallback/Timestamp/HTTP JSON와 기존 create/evaluate history/coupon/audit,
Recommendation eligibility/order/facets/co-purchase/ranking/popular assembly를 보호한다.
T01 Querydsl, T04 Order, Metrics/application/controller/Commerce regression과 full Backend/build/validators/
최신 HEAD Repository Validation을 완료 조건으로 하며 실제 결과는 Draft PR에 기록한다.

T06/T07 write/concurrency, T08 Subscription read, T09 command/automation, T10 provider/transaction,
T11 maintenance와 T12 residual allowlist 판정은 남는다. Draft PR 이후 STOP하고 T06은 시작하지 않는다.

## T06 Commerce write persistence boundaries

T05 #348 / PR #349의 squash merge `e7833f1fd69cecda2df6cdee2af0f2ea2424214a`를 기준으로
#350의 최신 Delta만 수행한다. 목적은 JDBC 파일 수 감소가 아니라 기존 write protocol을
보존하면서 raw SQL row mapping과 실제 MySQL에서 재현한 cursor 결함을 최소 변경하는 것이다.

### Quick Reorder: typed JDBC / protocol KEEP

`QuickReorderPersistenceAdapter`의 SQL raw Map을 private `IdempotencyRow`, `SourceItem`,
`CartLock`과 typed RowMapper로 바꾼다. nullable inventory는 `getObject(..., Integer.class)`로
보존하고 category boolean/status/quantity는 각 JDBC type으로 읽는다. JSON serialization과
legacy replay 역직렬화용 Map은 SQL raw-row debt와 구분해 유지한다.

기준 main의 모든 SQL 문자열이 동일하며 acquisition 순서는 다음과 같다.

1. member `FOR UPDATE` → member/key replay row `FOR UPDATE`.
2. source order ownership 확인 → member cart `FOR UPDATE` 또는 missing cart INSERT /
   `LAST_INSERT_ID()` → cart id/version `FOR UPDATE`.
3. source order item/product/SKU/category/inventory `ORDER BY item.id FOR UPDATE`.
4. cart item UPDATE 후 missing item INSERT → added item이 있을 때만 cart version 증가 → replay INSERT.

`OrderApplicationService.reorder()`의 TransactionTemplate이 계속 transaction owner다.
동일 key/source는 저장된 response를 재생하며 다른 source는 기존 409, missing member/order는
기존 404다. stock absent/insufficient 및 inactive SKU/product/category skip 순서·수량을 보존한다.
cart contents/version/replay는 한 transaction에서 rollback한다. source 내 duplicate SKU는 기존
`uk_order_items_sku(order_id,sku_id)`가 금지한다. 기존 cart의 같은 SKU는 UPDATE로 합산한다.
새 association/flush/unique constraint 또는 lock 순서·범위 변경은 없다.

### Payment Reconciliation: MySQL fail-before / minimal correction

Production 수정 전에 disposable MySQL 8.4.11에서 `ONE_TIME + NORMAL/UNKNOWN`, order quantity 2,
cart quantity 5, reserved inventory 2를 구성하고 실제 `PaymentReconciliationService.reconcile()`를
실행했다. provider만 대체했으며 provider query가 transaction 밖이고 start attempt 1이 이미
commit됐음을 확인했다. 결과는 `consumeCart()`의 `stream().findFirst()`에서 NPE였다.
RowMapper는 이미 현재 행에 위치하지만 내부 `rs.next()`가 cursor를 다시 이동해 null을 반환하고,
findFirst가 null을 Optional로 만들며 실패한다. 단순 cart 차감 생략으로 끝나는 결함이 아니다.

MySQL 실패 후 상태는 payment UNKNOWN / order PAYMENT_ACTION_REQUIRED / cart quantity 5 /
reserved inventory 2 / reconciliation attempts 1이었다. 즉 completion transaction은 rollback되고
start transaction은 남는다. 이는 로컬 재현 evidence이며 Production 사고·영향 건수는 판정하지 않는다.
수정은 `(rs,rowNumber) -> rs.getInt(1)` 한 줄이며 다른 adapter 내용은 기준 main과 동일하다.
실제 행을 읽어 cart quantity가 3으로 감소하는 pass-after test와 equal/less 시 DELETE를 보호한다.

Provider recovery protocol도 KEEP한다: start payment lock/attempt commit → 외부 query → completion
payment/order join lock → SKU 순서 inventory mutation → status/order/delivery/coupon → ONE_TIME cart
lock/item lock/consume → membership/notification/audit. 두 completion의 잠금 재확인으로 중복 효과를
방지한다. UNKNOWN 또는 BILLING/PROCESSING gate, 10회 cap, unavailable/timeout/unknown와 billing
failure 후속 retry는 기존 동작이다. provider_status의 동적 값과 `RECONCILED_FAILED`도 유지한다.

cart absent는 생성하지 않고, item absent는 skip한다. existing cart는 `updated_at`을 갱신하지만
**version을 증가시키지 않는 기존 reconciliation 의미를 유지**한다. 일반 Payment 경로와의 차이는
향후 계약 확인 책임이며 이번 correction에서 통합하지 않는다. provider 호출을 transaction 안으로
옮기거나 reconciliation SQL을 providerStatus가 다른 entity transition으로 치환하지 않는다.

### Normal Payment / Inventory KEEP and evidence

`PaymentPersistenceAdapter`의 JPA payment/order transition, missing cart atomic ensure/pessimistic lock,
`InventoryService`의 MANDATORY caller transaction, pessimistic lock, version/available CAS와 movement
audit는 production no-op이다. 실제 MySQL regression으로 reserve/deduct/release/restore와 version,
stale CAS, caller rollback, missing inventory, Checkout concurrent reservation과 일반 Payment cart race를 보호한다.

Quick Reorder는 frozen main JDBC reference의 동일 입력을 rollback transaction에서 비교한다:
typed result/stored JSON/cart rows/version parity, mixed eligibility와 source item ordering. legacy가 저장한
response의 application replay/HTTP JSON, first-cart 동시 same-key 요청과 기존 overflow rollback도 검증한다.
Payment Reconciliation은 actual MySQL state/movement/coupon/delivery/membership/notification/audit,
external provider transaction 경계, success/failure/uncertain, completion rollback와 overlapping query를 검증한다.
실물 Toss 호출은 실행하지 않는다.

Source inventory는 JdbcTemplate **34 / runtime 25**, EntityManager **10**, Querydsl production reference **2**,
Backend production Java **588**로 모두 유지한다. JDBC 감소 0도 유효한 수렴이다.
compiled Architecture Guard report는 frozen baseline **204**와 동일하다: application-api 186 /
api-persistence 1 / persistence-api 12 / runtime-maintenance 5. **RESOLVED 0 / RECLASSIFIED 0 / NEW 0**.
baseline을 자동 재생성하거나 추가 승인하지 않는다.

clean compileJava/Q-type regeneration → compileTestJava → Architecture Guard → MySQL targeted →
T01 Querydsl/T04/T05/Commerce/Checkout regression → full Backend → build -x test → validators →
최신 HEAD Repository Validation을 Gate로 사용한다. 실행 결과·fail-before/pass-after·실패 correction과
미실행 review는 Draft PR에 기록한다. 독립 확인은 별도 CI 환경의 최신 HEAD 검증으로 보완하며 외부
AI review submission을 수행한 것으로 표현하지 않는다.

복구 경계는 두 production adapter와 tests/document의 일반 revert이며 schema/data migration은 없다.
배포 뒤 실제로 성공 완료된 대사의 cart 소비를 revert가 되돌리지는 않으므로 운영 데이터 복구는 별도 승인
영역이다. 이번 작업에는 disposable local MySQL만 사용한다. T07 이후/Production/Cloud는 제외한다.
Draft PR에서 STOP하며 Ready 전환, CodeRabbit 요청, merge를 수행하지 않는다.

## T07 After-sales and notification write boundaries

T06 #350 / PR #351의 squash merge `3cdf2481d0f63b1ddc97ea11d79383e9cdeffc2b`를 기준으로
#352의 최신 Delta를 평가했다. `API-007`의 취소/반품 replay 200, member isolation 404, 상태 충돌 409,
provider unavailable 503, refund provider outside transaction, unresolved PROCESSING 유지와 zero amount
provider 생략 계약을 보존한다. T07의 실제 Delta는 **MySQL protocol regression + KEEP/DEFER 판정**이다.
모든 production source와 SQL은 기준 main 그대로이며 JPA 전환 자체나 JDBC 파일 감소를 목표로 삼지 않는다.

### Method-level selection

| 경계 / method family | T07 판정 | 근거 / 후속 책임 |
| --- | --- | --- |
| Cancellation member-order/existing/delivery/success-payment/return absence locks, typed views | JDBC KEEP | 이미 typed records이며 lock scope/order와 missing-row behavior가 명시적이다. entity 도입으로 해결할 raw row debt가 없다. |
| Cancellation create/cancelDelivery/SKU items/createRefund | JDBC KEEP | cancellation unique, timestamp, inventory compensation와 `INSERT...SELECT` refund가 하나의 application transaction이다. 부분 entity mapping/flush 변경의 이점이 입증되지 않았다. |
| Return request locks/create/read | JDBC KEEP | existing replay, cancellation absence, delivery deadline/nullable restock와 SQL cursor 계약이 이미 작고 typed다. |
| Return decide/receive/createRefund | JDBC KEEP | decided/received admin/time 등 command 필드의 entity mapping이 불완전하고 restock/receivedAt/completedAt은 read-only다. JPA 전환은 mapping과 flush 순서를 늘린다. |
| Refund start/retry/reconciliation/completion/read/coupon/source completion | JDBC KEEP | typed projections, status-specific SQL, generated keys/unique, retry INSERT...SELECT와 2-phase provider recovery가 명시적이다. 부분 entity만으로 동등한 command를 단순화하지 못한다. |
| Notification create | native JDBC KEEP | unique event + ON DUPLICATE KEY UPDATE id=id로 동시 dedup와 원래 createdAt을 원자적으로 보존한다. |
| Notification markRead / markAllRead | JDBC KEEP | 각각 한 UPDATE로 member/row count/COALESCE 및 unread predicate를 보장한다. JPA는 readAt mapping과 bulk-write repository/flush 규칙을 추가해야 하며 현재보다 단순하다는 근거가 없다. |
| Notification findByMemberId | JDBC KEEP; read 재평가 DEFER T08 | typed nullable projection + schedule LEFT JOIN은 Subscription read 경계다. T07에서 join이나 entity를 추가하지 않는다. |
| Operations | KEEP / production no-op | controller는 GET-only이며 새 write use case가 없다. command/API를 발명하지 않는다. |

**JPA-convert 0**이다. aggregate JPA write migration의 동등성/유지보수 이점은 이번 범위에서 입증하지
않았으므로 DEFER하며 현재 실행 구현은 KEEP한다. Notification의 두 SQL은 추가 mapping과
bulk-write repository를 도입해 바꾸는 이점이 확인되지 않았다.
최종 residual allowlist 승인은 T12 책임이다.

### Lock, transaction and failure boundaries kept

CancellationService의 TransactionTemplate은 member-scoped order → existing cancellation → delivery →
SUCCEEDED payment → return absence의 FOR UPDATE 순서를 유지한다. cancellation INSERT → delivery
CANCELLED → SKU 순서 inventory restore/movement → refund INSERT...SELECT가 한 transaction이다.
same-order 경쟁 두 요청은 같은 aggregate를 replay하고 refund/각 SKU movement는 한 개다.

Return request도 order → existing return → cancellation absence → delivery 순서다. PREPARING에서는
cancellation만, DELIVERED에서는 return만 허용되므로 둘 다 유효한 가상의 배송 상태를 만들지 않는다.
각 실제 상태에서 cancellation/return 경쟁의 성공 1건·기존 409 1건과 compensation 결과를 검증했다.
return deadline은 deliveredAt + 7 days의 **inclusive** boundary이며 ±1 microsecond를 UTC/Seoul에서
검증했다. 기존 return replay는 restock/decided/received/completed nullable projection을 보존한다.

Return decide의 REQUESTED gate + notification + audit, receive의 APPROVED gate + 선택적 SKU 순서
restore/movement + REFUND_PENDING fields + refund INSERT + audit는 각 transaction에서 유지한다.
approve/reject 경쟁은 decision/notification/audit 한 건이며 receive 경쟁은 refund와 restock 한 번이다.
restock=false는 movement가 없다. refund INSERT 이후 exception, decision/receive audit exception을
주입하여 cancellation/delivery, return fields, stock/version/movement/refund/notification rollback을 확인했다.

Refund process는 READY lock→PROCESSING/processedAt commit → 외부 provider → completion lock의
2-phase protocol이다. process 중복은 409이며 provider write 1회다. reconcile은 PROCESSING/UNKNOWN
start attempt commit → 외부 query → completion이며 겹친 query는 attempts 2 / success effects 1회다.
retry는 FAILED source row lock → existing next-attempt lock → INSERT...SELECT이며 경쟁 두 요청이
같은 next attempt를 반환한다. 새 idempotency key와 source+attempt unique/최대 3회, 10회 reconciliation cap을 유지한다.

provider 미구성 503이나 timeout 후 committed PROCESSING이 남는 것은 승인된 복구 계약이다.
unresolved 결과는 원래 PROCESSING/UNKNOWN을 유지하며 UNKNOWN action notification은 dedup한다.
실패는 TOSS_REJECTED/providerStatus와 REFUND_PENDING source/coupon USED를 유지한다. completion audit
실패는 coupon/source-complete/membership/notification을 rollback하고 PROCESSING start는 남긴다.
그 뒤 reconcile query로 완료하며 새 외부 refund를 발행하지 않는다. zero amount는 provider 접근 자체가 없다.
성공은 source completion/coupon release/membership history/notification과 같은 transaction으로 commit된다.
이미 restore된 재고를 refund completion에서 다시 복원하지 않는다.

generated source_id/succeeded_order_id와 unique를 actual MySQL에서 확인했다. 두 source의 반대 FK는
nullable로 보존한다. 유효 schema는 source에 맞는 FK를 NOT NULL로 요구하여 source_id 자체의 NULL
fixture는 만들 수 없다. nullable sourceId retry guard를 시험하려고 constraints를 끄거나 migration하지 않는다.
두 번째 succeeded attempt의 generated unique 위반은 transaction을 rollback하고 READY를 유지한다.

Notification은 concurrent create dedup/original createdAt, concurrent/repeated markRead의 첫 timestamp
freeze와 false 404 없음, member 404, markAllRead unread-only/member isolation, caller rollback을 검증했다.
fixed clock의 Timestamp microseconds와 schedule reminder LEFT JOIN의 nullable context는 UTC/Seoul에서
동일하다. 목록 HTTP JSON, read/readAll 204와 다른 member 404도 실제 security/controller 경계에서 확인했다.

### Validation and remaining risk

새 tests는 committed isolated fixtures로 구성하고 outer test transaction 없이 실제 services와 worker
transaction을 실행한다. race helper는 CommerceException을 outcome으로 수집하고 각 test에서 기대
status/code를 검증한다. 예상하지 않은 DB/deadlock 오류는 테스트를 실패시킨다.
실패 주입은 spy를 통해 실제 refund INSERT 직후 또는 audit 호출 경계를
검증하며 provider만 mock한다. 실물 Toss는 호출하지 않는다.

최초 Cancellation/Return 16개 중 15개 PASS, refund INSERT 이후 rollback test의 예외 assertion은
Spring Repository exception translation을 반영해 correction했다. 해당 test와 Refund 15개 **16개 PASS**,
Notification/after-sales HTTP **8개 PASS**, 독립 Asia/Seoul JVM/database **11개 PASS**를 확보했다.
생산 동작 수정으로 테스트를 우회하지 않았다. HTTP projection의 replay/nullable/time/DECIMAL, member
404·admin 403·state 409·validation 400을 보호한다.

최초 full 513개 중 6개는 후반 Subscription context 생성에서 MySQL 1040 / Too many connections로
실패했다. disposable server max_connections 151 / Max_used_connections 152를 확인했다. 새 spy/mock
context 두 개를 suite 전체에 cache하면 추가 Hikari pool이 남으므로 해당 두 test class에 AFTER_CLASS
DirtiesContext를 적용해 class 종료 시 연결을 해제한다. production pool/capacity/DB 설정은 바꾸지 않는다.
수정한 두 클래스와 실패했던 기존 클래스 **39개 PASS** 후 동일 server 한도 151의 fresh database에서
full Backend **513개 PASS (failures/errors/skipped 0)**, build -x test PASS를 확보했다.
Architecture 포함 관련 회귀 **87개 PASS**와 PR/task/title/encoding validators를 완료한다.

clean compileJava/Q regeneration/compileTestJava, Architecture Guard, T01/T04/T05/T06/Commerce/Checkout
regression, full Backend, build -x test, validators와 최신 HEAD Repository Validation을 완료 Gate로 사용한다.
실제 full/CI count와 최초 실패 correction·미실행은 Draft PR에 기록한다. frozen architecture baseline을
재생성하지 않는다. production inventory는 JDBC **34 / runtime 25**, EntityManager **10**, Querydsl **2**,
Java **588**로 유지하며 architecture baseline **204 / NEW 0**을 요구한다.

남은 위험은 기존 lock/protocol/부분 JPA mapping과 provider 지연·실물 I/O·운영 DB 영향의 미검증이다.
실제 scheduler 처리나 Subscription read 전환은 수행하지 않았다. 독립 확인은 별도 CI 환경의 최신 HEAD
MySQL 테스트로 보완하며 외부 AI review submission은 미실행으로 기록한다.
복구는 tests/document의 일반 revert이고 production/schema/data 변경은 없다. 향후 제품 코드 rollback이
이미 처리된 외부 환불이나 재고·DB side effect를 자동 보상하지 않으며 Production 복구는 별도 승인 영역이다.
T08 이후/Production/Cloud는 제외하고 Draft PR에서 STOP한다. Ready/CodeRabbit/merge를 수행하지 않는다.
