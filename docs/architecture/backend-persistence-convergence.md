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

검증 전제: local datasource 환경변수와 실행 중인 MySQL 컨테이너가 없어 DB 통합 테스트는 실행하지 않았다. schema 변경이나 infra 기동으로 우회하지 않는다.
