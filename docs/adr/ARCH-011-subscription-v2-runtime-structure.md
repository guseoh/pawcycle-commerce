# ARCH-011: Subscription V2 runtime structure

## Status

Accepted

## Decision

Subscription V2 keeps its explicit JDBC SQL, `FOR UPDATE`, version compare-and-set writes, and
unique-conflict handling in `V2SubscriptionJdbcStore`.  Pet/Plan, creation, query, command, and
reconciliation each have an application service; those services own the transaction boundary.

`V2SubscriptionService` is a compatibility facade for existing in-process callers only.  It has
no SQL, transaction annotation, or HTTP response construction.  The controller converts the typed
operation result into HTTP status and the existing `Location`, `ETag`, and
`Idempotency-Replayed` headers.

## Consequences

No API, database, migration, locking, idempotency, ordering, date-time, or metric contract changes.
The reconciliation use case preserves its per-subscription `REQUIRES_NEW` transaction boundary.
