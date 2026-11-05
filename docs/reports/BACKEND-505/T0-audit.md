# BACKEND-QUALITY-000 (T0): implementation boundary audit

Baseline: main `1d7425a6d8beed0b73a6d1560a3146947cb5dd8c` (2026-11-05 KST).
Execution: repository change; audit only, no runtime change. Parent: #505.

## Completed scope excluded

- #413, #339 and #371 are closed: keep feature/layer topology, JPA/Querydsl,
  nine isolated JDBC owners, five intentional maintenance edges and typed results.
- #442 is closed: Category/Product/SKU PATCH validation, Product transitions and
  SKU price invariants are not new work. Existing AdminCatalogMutationService
  transaction ownership and ProductListCacheInvalidator.afterCommit are reused.
- #449 is closed as a scoped audit: sale-condition runtime extraction was deferred.
  Its SQL/list versus command differences and characterization remain inputs.
- #417 NORMAL/PROCESSING recovery is excluded. No new recovery transition.

## Remaining Delta and preservation boundaries

| Task | Evidence / minimum Delta | Existing protection / decision |
| --- | --- | --- |
| T1 | CheckoutApplicationService.calculateDiscount combines lookup/calculation. Creation/Command availableVersion and PetPlan.validateAvailable repeat sale checks. Command.skip and reconciliation call automation.firstFutureDate, which delegates to Processor. | Extract only equal pure rules. Keep lookup, error order, inclusive sale dates, SQL filtering/paging, clock and schedule lock owners. |
| T2 | Support combines JSON/hash, ETag, numeric/date/error handling. Command has string allowlist/switch. IdempotencyReservationPersistence imports SubscriptionOperationResult. | HTTP Controller validates missing/malformed/overflow If-Match before service/replay; valid stale versions are checked after replay. Keep six nullable request fields, bytes/hash, response envelope, CAS/history/snapshot transaction. No nine-handler expansion. |
| T3 | Normal completion uses PaymentEntity mutations through PaymentPersistenceAdapter. Reconciliation and SubscriptionBillingPersistence use PaymentPersistenceQueries bulk writes; expiry uses PaymentEntity.markExpired. | Entity guards alone cannot protect bulk writes. Audit all callers before changing guards. Normal success notifies before cart/membership; reconciliation consumes ONE_TIME cart and evaluates membership before notification, and Billing failure follows a separate path. Blanket finalizer extraction is rejected. Existing TossPaymentAdapter is already an interface; do not duplicate it. |
| T4 | ProductImageAdminPersistence.create/update/delete performs validation, entity mutation/flush and cache scheduling. | Keep product/image lookup and validation precedence, absent/null PATCH, MAIN unique translation, Admin mutation+audit transaction and afterCommit behavior. Do not rework #442. |
| T5 | AggregatePersistence inherits QueryPersistence and delegates writes. CheckoutResponse.get(String) is documented legacy test compatibility. | Composition DEFER until concrete cost is shown. Search every legacy accessor caller before removal. Keep named maintenance exceptions; add only scoped architecture guards. |

## Concrete execution paths

- Subscription: Controller.validateIfMatchSyntax -> command -> fingerprint ->
  lockOwnedSubscription -> reserve/replay -> parseEtag/version -> HELD/command ->
  CAS -> command history -> query detail -> durable response update, one transaction.
- Payment confirm: prepare transaction -> Toss.confirm outside transaction ->
  completion transaction. Reconcile suspends ambient transaction, starts/queries/
  completes separately; Billing failure follow-up remains separate.
- PaymentPersistenceQueries: processing/succeeded/failed update by id;
  unknown additionally requires PROCESSING. mutate flushes then clears the context.
  Changing predicates or replacing bulk writes with entity writes needs MySQL proof.
- Expiry: CheckoutExpirationProcessor -> locked payment -> inventory/coupon release
  -> CheckoutExpirationPersistenceAdapter.markExpired. Preserve per-payment owner.
- Catalog: AdminCatalogMutationService -> existing facade/image persistence ->
  flush -> invalidateAfterCommit registration -> audit -> transaction commit.

## Validation plan and risks

Reuse SubscriptionRequestBoundaryTests (64 combinations), CommandStateTests,
IdempotencyConcurrency/Command integration, CheckoutIdempotency/CouponClock,
PaymentReconciliation/PaymentCartConcurrency, InventoryProtocol,
CatalogAdminCommandTransaction, BackendArchitecture and PersistenceResultBoundary.
Task-specific pure rules need boundary tests; persistence/locking changes require
disposable MySQL 8.4 and failure/concurrency evidence. CI is per final task HEAD.
No historical PASS is reported as a fresh execution.

This audit does not certify every payment writer or every dependency edge: T3 must
finish the complete caller/state matrix before a state guard change. No performance
benefit, Production verification, merge readiness or policy approval is claimed.

Wiki candidate: journal/lesson on preserving HTTP prevalidation versus durable
replay ordering and distinguishing bulk writes from entity guards. No external
Wiki write is included in #505; retain candidate here for the final handoff.

CI evidence: initial conventions failed on Task ID format, then required PR body
labels. Both metadata causes are corrected; no runtime tests are claimed for T0.
