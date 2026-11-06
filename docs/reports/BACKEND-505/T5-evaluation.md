# BACKEND-QUALITY-005: residual structure decisions

Baseline main 1d7425a6. Parent #505; independent of unmerged T1-T4.

## Purpose

Implement only the demonstrated type-safety improvement; record other structural
choices without introducing a blanket architecture or duplicate pending changes.

## Evidence and decisions

| Candidate | Actual source/consumer evidence | Decision |
| --- | --- | --- |
| CheckoutResponse.get(String) | CheckoutResponse is constructed/returned by CheckoutController; no other Java source references the response type. CommercePurchaseIntegrationTests uses CommerceFacade -> CommercePayload, not this response accessor. Full source/test compilation and HTTP checkout regression protect removal. | REMOVE unused Object-returning accessor; retain record accessors/constructors/capability and JSON |
| AggregatePersistence inheritance | Public facade inherits query methods and delegates writes. Current callers already use that contract; no concrete defect or reduced consumer surface demonstrated. | DEFER composition, keep facade API |
| Nine command handlers | Central transaction/replay/version/history owner remains necessary; one class per enum adds registration/context without demonstrated independent changes. | DEFER |
| New command/quantity/money value models | Six nullable command fields participate in stored fingerprint. Additional mapping/validation could change error precedence and bytes. Existing exact numeric conversion remains. | DEFER until invariant duplication justifies cost |
| Factories | SubscriptionDetailResponse.from and PaymentEntity.billing already encapsulate meaningful construction; snapshot storage is centralized. | KEEP; REJECT factory-per-class expansion |
| Generic payment finalizer | Existing NORMAL/reconciliation/Billing operation order and side effects differ; InventoryService already shares stock/movement. | REJECT blanket finalizer; KEEP existing narrow collaboration |
| Global architecture prohibitions | Five intentional maintenance edges, isolated JDBC and cross-feature transaction collaborations are approved. | KEEP baseline; REJECT blanket bans |
| Solved boundary guards | T2 PR #508 protects replay store against Application result dependency; T4 owns its scoped image/cache guard. | Do not duplicate unmerged code in this branch |
| Explicit local types | T2 uses explicit Detail types at modified call sites. Global var replacement has no coherent task benefit. | KEEP scoped typing; REJECT repository-wide formatting churn |

## Risk and validation

No production caller of the removed accessor was found; reflection/out-of-repository
Java consumers are not exhaustively knowable. It is not part of the HTTP contract.
No API field/schema/transaction/state/Provider change. Verify full source/test
compilation and existing CheckoutIdempotencyIntegrationTests HTTP response/replay.
Record actual executions in the PR, never reuse historical PASS as new evidence.
Revert this commit to restore the accessor; no data rollback.

Wiki candidate: lesson on distinguishing the real CheckoutResponse from the
similarly used test CommercePayload before deleting compatibility APIs. No external
Wiki publication is authorized; retain as a candidate only.

Local evidence: full source/test compilation and selected architecture/serialization
checks PASS. Fresh task-specific MySQL HTTP checkout/replay run: 16 PASS,
0 failure/error/skip. Full CI remains final PR HEAD evidence.
