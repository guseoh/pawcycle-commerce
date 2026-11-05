# BACKEND-QUALITY-003: Payment boundaries

Baseline main 1d7425a6, parent #505; no dependency on T1/T2.

## Purpose

Move the existing TossPaymentAdapter contract, including existing nested result and
capability methods, into application ownership. Keep implementation profiles,
provider calls, error/result mapping and HTTP fields unchanged. No second Gateway.

## Evidence: state-path audit

| Owner | Existing admission / change | Decision |
| --- | --- | --- |
| PaymentApplicationService.confirm | Lock owned payment/order; matching SUCCEEDED+PAID identity/amount replay, otherwise READY+PAYMENT_PENDING -> PROCESSING through PaymentPersistenceAdapter/PaymentEntity. Provider call between transactions. | KEEP |
| Normal finalization | Payment lock; PROCESSING only. UNKNOWN, SUCCEEDED or FAILED through Entity; success includes inventory/order/delivery/coupon/notification/cart/membership in existing order. | KEEP |
| PaymentReconciliationService | NOT_SUPPORTED; start lock admits UNKNOWN or BILLING PROCESSING; increments attempt; provider query outside transactions; completion rechecks locked state. | KEEP |
| Reconciliation writers | PaymentReconciliationPersistenceAdapter -> PaymentPersistenceQueries.succeeded/failed; bulk by id under caller lock. mutate flushes and clears. ONE_TIME cart conditional; Billing failure delegates after completion to explicit failure/retry path. | KEEP predicates, no Entity-guard substitution |
| SubscriptionBillingProcessor | lockWork READY -> processing bulk; provider I/O after prepare transaction. completeSuccess locks PROCESSING before bulk success; unknown bulk is conditional on PROCESSING. | KEEP |
| SubscriptionBillingRetryProcessor | lockFailureAttempt requires PROCESSING before failed bulk; retry requires FAILED, attempt limit and ordered inventory reservations; new attempt via PaymentEntity.billing. | KEEP |
| CheckoutExpirationProcessor | Locks payment/order, admits READY+PAYMENT_PENDING; releases inventory/coupon, then CheckoutExpirationPersistenceAdapter -> Entity.markExpired. | KEEP |
| Creation | CheckoutPersistenceAdapter -> new PaymentEntity; SubscriptionOrderPersistence and SubscriptionBillingRetryPersistence -> PaymentEntity.billing. | KEEP |

State mutation inventory: PaymentEntity methods; PaymentPersistenceAdapter wrappers;
PaymentPersistenceQueries.processing/unknown/succeeded/failed; wrappers in
SubscriptionBillingPersistence/SubscriptionBillingRetryPersistence and
PaymentReconciliationPersistenceAdapter; CheckoutExpirationPersistenceAdapter.
No schema, temporal binding, lock order, transaction propagation or SQL change.

## Risk and trade-offs

Normal success notifies before cart/membership, reconciliation evaluates membership
before notification, Billing additionally releases missing-method holds. A universal
completion helper would conceal these differences: REJECT for this Task. Existing
InventoryService already owns the identical stock/movement operation: KEEP.
Additional state enums/guards DEFER: existing caller guards and bulk paths must not
be tightened into new rejection semantics without a demonstrated valid-path gap.
#417 NORMAL PROCESSING recovery stays excluded, not resolved by this refactoring.
Controller still queries the application-owned capability contract; a one-method
wrapper service would add indirection without independent policy and is DEFER.

## Verification

Run provider unit tests, normal purchase/reconciliation/Billing/expiry and physical
lock regressions on isolated MySQL, plus architecture checks; record actual results
in the PR. Runtime provider implementations are not invoked against real services.
Rollback is a source revert; no data migration. Production remains unverified.

Local verification: 112 tests PASS, 0 failures/errors/skips on isolated MySQL 8.4;
build -x test PASS. Full CI and required physical-lock gate remain PR HEAD evidence.
