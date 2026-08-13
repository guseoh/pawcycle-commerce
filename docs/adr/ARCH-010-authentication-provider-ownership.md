# ARCH-010 Authentication Provider Ownership

## Status

Accepted (`MVP3-CLEANUP-002`)

## Decision

`MemberAuthenticationProvider` owns member lookup, BCrypt comparison, unknown-member dummy-hash
comparison, and construction of the authenticated session-safe principal with its `USER` or
`ADMIN` authority. `AuthenticationManager` dispatches credential verification to that provider.

`AuthApplicationService` preserves LoginRequest validation and, only after successful manager
authentication, invokes the configured session strategy before saving the `SecurityContext` to
the HTTP session. The configured logout handler remains responsible for CSRF token, security
context, session, and `JSESSIONID` cleanup.

## Consequences

The existing session, CSRF, JSON error, URI, response, role, and production smoke contracts do
not change. Invalid credentials remain a single generic authentication failure after exactly one
BCrypt comparison, including for unknown members.
