# ADR 0004: Identity Platform authentication and publisher authorization

Status: implemented; live project configuration and sign-in must be verified separately.

## Decision

Use Spring Security's resource-server support to verify Google Identity Platform ID tokens. Trust the configured project's SecureToken issuer and audience, RS256 signatures from Google's fixed JWK endpoint, and mandatory subject/time claims. Do not trust generic Google OAuth access tokens, Cloud Run invocation tokens, decoded unsigned JWTs, email addresses, or caller-supplied tenant/group headers.

Administrator-issued `akh` custom claims contain an application tenant, read groups, permissions, writable sources and assignable groups. Missing/malformed application claims confer no authorities. Authentication failures return sanitized 401 responses; insufficient application permissions return 403. `/health/live` stays public. The dev profile exposes Swagger only; it never supplies a simulated API identity. All other routes are denied.

Search retains its parameterized SQL eligibility filters. HTTP ingestion goes through `AuthorizedIngestionService`, which requires `knowledge:write`, a grant for the configured source, and permission to assign every requested document group. Tenant comes from the verified token. Publishers can replace any document in their granted source within their tenant, including documents they cannot read. This is an explicit source-wide administrative publishing capability, not per-document ownership. A reader cannot ingest. Empty ACLs are allowed and make documents unreadable through search.

The internal ingestion service still supports trusted server-configured source scope for tests/internal callers. HTTP must use the authorized facade and explicit token-derived scope. No public API accepts arbitrary source scope.

## Transactions and tradeoffs

Authorization precedes parsing and persistence. The existing source lock, durable comparison, and atomic replacement transaction are unchanged. The scope used for IDs, locking and persistence is the same trusted scope. A denied request does not create a source or alter a document. No per-document ownership check is claimed, so there is no unlocked ownership check that races the write transaction.

Claims are snapshots. Public-key verification does not contact the user database on each request to check revocation or account disablement. An already-issued token may retain access until expiry (plus 60 seconds of allowed clock skew). Immediate revocation would require an additional revocation/policy lookup and its availability design. Stored document ACL updates are consulted by each search statement.

JWK retrieval adds a network dependency on cache misses; Spring/Nimbus caches signing keys and supports Google key rotation. No downloaded service-account key is needed by the application. The test decoder uses generated RSA keys with the same validators, so tests exercise cryptography without cloud credentials; they do not prove live Google key rotation or account configuration.

CSRF protection is disabled because this stateless API accepts credentials only in the Authorization bearer header and does not use browser session/cookie authentication. Revisit that decision if cookies are introduced. No permissive CORS policy is added.

The Cloud Shell administration helper preserves unrelated custom claims and reads back the assigned application namespace. Its read/modify/write is not atomic against another administrator's simultaneous claim update; serialize administrative changes. It is intentionally outside the runtime application and does not grant GCP IAM roles.

## Verification

Real HTTP tests verify signature, issuer, audience, required times/subject, expiry, missing tokens, missing/read-only permissions, disallowed source/ACL writes, spoof resistance, tenant isolation, durable retries, and changed-content replacement. Existing PostgreSQL rollback/concurrency tests remain active. Production-profile docs are denied; dev-profile APIs still require authentication.
