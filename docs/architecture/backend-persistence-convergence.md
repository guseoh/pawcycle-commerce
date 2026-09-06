# Backend persistence convergence

작업 ID: `BACKEND-REFACTOR-003` · 등급: 고위험 · 실행 구분: 저장소 변경 · 역할: Backend Engineer

작업 기준: `refactor/backend-internal-convergence`, 기준점 `origin/main`.

이번 변경은 HTTP 계약, 상품 동작, schema migration, frontend, infra, production 실행을 변경하지 않고 customer catalog·cart·checkout·payment callback·billing preparation·delivery·review/문의의 persistence 경계를 JPA 중심으로 정리한다.

## 완료된 전환

- `ProductQueryService`는 단일 생성자와 authoritative `ProductDiscoveryReader`/`ProductDetailContentReader` 경로만 사용한다. nullable legacy/cache fallback은 없다.
- 상품 discovery/comparison, 상세 섹션/trust, review/문의 읽기·mutation은 JPA repository 또는 `EntityManager` typed entity/Tuple 경계를 사용한다.
- cart/wishlist read와 checkout/payment callback/expiration은 JPA entity state transition, typed projection, pessimistic lock, 조건부 `@Modifying` update를 사용한다.
- catalog admin은 `BrandAdminPersistence`, `ProductImageAdminPersistence`, `ProductOptionAdminPersistence`, `CatalogFacetAdminPersistence`로 분리하고 기존 `CatalogAdminPersistence` 진입점은 compatibility facade로 유지한다.
- billing preparation claim은 준비/결제수단 entity와 조건부 claim/revoke update로 전환했다. subscription schedule cross-table bulk update 한 곳은 아래 예외로 남긴다.
- delivery preparing의 중복 callback 원자성은 `DeliveryRepository` 내부 JPA native upsert로 보존했다. `find-or-save`로 바꾸면 unique 충돌 경합 의미가 달라진다.
- review summary의 product별 lazy cache도 기존 MySQL `ON DUPLICATE KEY UPDATE` 의미를 JPA repository native upsert로 보존해 동시 최초 생성 시 unique race를 만들지 않는다.
- category facet 배정은 기존 atomic upsert 의미를 유지하고, catalog discovery의 category/parent/facet/option 읽기는 batch fetch로 수렴해 JDBC join에서 JPA로 옮길 때 생길 수 있는 N+1 회귀를 막는다.

## Lock / concurrency 보존 계약

### Category facet 제거

기존 JDBC `removeCategoryFacet`의 lock 순서를 변경하지 않는다.

1. Product 전체를 `id` 순서로 pessimistic lock한다.
2. 대상 category의 `category_facets` scope를 `facet_definition_id` 순서로 lock한다.
3. 대상 category의 Product가 사용하는 해당 facet definition의 `product_facet_values` 존재 여부를 locking read로 확인한다.
4. 사용 중이면 `CATEGORY_FACET_IN_USE`, 아니면 category facet 배정을 삭제한다.

현재 `ProductRepository.findAllForUpdate`, `CategoryFacetRepository.findAllForUpdate`, `ProductFacetValueRepository.findAllByProductIdInAndDefinitionForUpdate`는 이 기존 순서와 범위를 보존하기 위한 것이다. 전체 Product lock과 relation join lock을 좁히는 것은 성능 개선이 아니라 concurrency protocol 변경이므로 이번 refactor correction에서 수행하지 않는다. 이 protocol은 `CatalogFacetConcurrencyIntegrationTests`로 set/remove 및 category-change/remove 경쟁을 검증한다.

### Checkout / Payment

- Checkout은 member/cart lock 이후 CartItem을 lock하고, 해당 SKU와 Product도 pessimistic locking read로 확보한 뒤 snapshot과 구매 가능성 검증을 진행한다. 기존 `cart + cart_items + skus + products ... FOR UPDATE`가 보호하던 write 대상 범위를 JPA 전환 후에도 유지하는 것이 목적이다.
- Payment provider-order 조회는 Payment와 Order를 같은 locking query에서 fetch해 기존 join `FOR UPDATE` 범위를 유지한다.
- Payment 성공 후 Cart가 아직 없을 수 있는 경로는 `carts.member_id` unique key에 대한 native atomic ensure 뒤 Cart row를 pessimistic lock한다. concurrent first creation에서 duplicate-key failure가 발생하지 않는지 별도 integration test로 보호한다.

## 시간 계약

`product_detail_sections`의 `DATETIME(6)` persistence는 기존 `Timestamp.from(Instant)` 의미 및 `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`와 맞추기 위해 UTC를 명시한다. JPA `LocalDateTime` write와 API `Instant` read 변환 모두 `ZoneOffset.UTC`를 사용하며 JVM `systemDefault()`에 의존하지 않는다.

## 잔여 JDBC 판정

아래 잔여 경로는 이번 변경에서 동작을 보존하기 위해 명시적으로 분류했다. 새 persistence를 추가할 때 이 목록에서 임의로 제외하지 않는다.

### DEFERRED

- `catalog/maintenance/**`, `foundation/bootstrap/**`: local fixture/import/realism correction 전용. 운영 요청 경로가 아니며, fixture 모델과 import schema를 함께 설계하는 별도 작업에서 전환한다.
- `subscription/migration/**`, `subscription/performance/**`: 일회성 migration 또는 measurement harness. 측정 SQL과 migration compatibility를 분리 검증한 뒤 전환한다.
- `subscription/persistence/**`: subscription aggregate의 상태 전이·idempotency·schedule/order 생성이 여러 legacy 테이블을 같은 transaction과 lock 순서로 다룬다. 별도 subscription persistence migration에서 entity aggregate와 lock-order 테스트를 먼저 승인한다.
- `commerce/membership/**`, `commerce/metrics/**`: membership 평가/운영 지표 projection은 공통 entity mapping과 집계 정확성 검증이 필요한 별도 범위다.

### EXCEPTION

- `commerce/payment/persistence/PaymentReconciliationPersistenceAdapter.java`: provider recovery/reconciliation debt는 이번 작업에서 수정하지 않는다.
- `commerce/cancellation/**`, `commerce/refund/**`, `commerce/returning/**`: after-sales 보상·재고·상태 전이는 결제/배송과 함께 lock ordering 및 failure recovery를 독립 검증해야 한다.
- `commerce/order/persistence/OrderPersistenceAdapter.java`: order read와 quick-reorder idempotency/cart mutation이 한 adapter에 결합되어 있어 typed projection과 cart lock 분리 후 전환한다.
- `commerce/notification/**`, `commerce/operations/**`: subscription schedule을 포함한 cross-domain 운영 projection이다.
- `interaction/**`, `recommendation/**`: event/recommendation projection은 commerce aggregate persistence와 독립된 read/write 모델이다.
- billing registration의 `subscription_schedules` JOIN UPDATE: subscription schedule entity mapping 승인 전까지 EntityManager native query로 atomicity를 보존한다.

## 리뷰에서 의도적으로 변경하지 않은 항목

- `ProductRepository.findAllForUpdate`의 전체 Product lock과 `ProductFacetValueRepository.findAllByProductIdInAndDefinitionForUpdate`의 relation locking 범위는 기존 JDBC concurrency protocol 보존을 위해 유지한다. lock 범위 축소는 별도 측정·correctness 작업에서만 다룬다.
- `DeliveryEntity.cancelledAt`은 현재 application transition에 `CANCELLED` command가 없고 기존 JDBC 역시 `DELIVERED`가 아닌 transition에 `failed_at`을 기록했다. 도달하지 않는 새 상태를 이번 persistence refactor에서 임의 추가하지 않는다.

검증 전제: disposable local MySQL 8.4와 테스트 전용 placeholder credential로 schema validation 및 전체 backend test를 실행했다. 테스트 종료 후 컨테이너는 제거했으며 schema 변경이나 Production/Cloud/운영 DB 실행은 하지 않았다.
