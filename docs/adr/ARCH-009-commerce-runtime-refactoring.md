# ARCH-009 Commerce Runtime Refactoring

## Context

MVP3 Commerce API-006/API-007 and Flyway V1–V19 are already released contracts. Runtime code
had accumulated a single `CommerceService`, controller-owned transactions, and duplicated
inventory/membership state mutations, making parity changes difficult to review.

## Decision

- Keep the API and the V1–V19 schema unchanged. Add JPA entity/repository mappings for Commerce
  tables and run Hibernate in validate mode; generated columns are read-only.
- Keep explicit SQL only where it expresses required locking/CAS, operations union reads,
  maintenance/migrations, or subscription-v2 compatibility. JPA associations are lazy and do not
  cascade across aggregates.
- Move inventory movements, membership evaluation, checkout expiry processing, and HTTP adapters
  into responsibility-specific application services/controllers. State change and its required
  movement/audit remain inside the caller's transaction; Provider I/O remains outside it.
- For the six admin mutations (inventory adjust, coupon create/update/issue, membership grade
  create, and membership evaluate), `AdminCommerceService` is the transaction owner. It invokes
  the mutation and `AdminAuditService.append` within the same `@Transactional` boundary, so an
  audit failure rolls back the mutation. `AdminCommerceController` remains an HTTP adapter;
  audit-log listing remains read-only through `AdminAuditService`.
- Characterization tests, not JDBC call counts, protect HTTP results, final rows, Provider calls,
  idempotency, and inventory invariants before conversion.

## Consequences

This is a runtime-only cleanup and can be reverted as application code; no migration is needed.
`CLEANUP-002` may complete remaining typed response projections and repository adoption, while
`CLEANUP-003` may remove the remaining approved JDBC boundary exceptions after parity evidence.
