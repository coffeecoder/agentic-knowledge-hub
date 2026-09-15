# ADR 0003: Authorization-aware keyword retrieval

Status: implemented. The initial simulated identity described below is superseded by [ADR 0004](0004-identity-platform-authentication.md); current APIs require verified tokens.

## Decision

Add POST /v1/search using PostgreSQL full-text search over stored chunks. Use an identity-provider interface, retrieval service, repository contract, and JDBC adapter. Do not introduce an AI framework for this deterministic database operation.

The explicit dev profile supplies a fixed server-configured principal, tenant, and groups. Without it, identity resolution denies search. HTTP fields and headers cannot supply trusted identity. This is not production authentication; ingestion remains an unauthenticated local tool and the complete application is not production-secure.

## Policy and query

The query joins sources, documents, and chunks; requires the caller tenant, active source/chunk, non-deleted document, and overlapping groups; then ranks and limits eligible matches. Empty document groups deny access. Matching uses the existing English tsvector expression index, websearch_to_tsquery, and ts_rank_cd. Ties use ascending chunk IDs. Scores represent lexical relevance, not correctness.

Query length is bounded at 500 UTF-16 code units, results at 20, and JDBC execution at five seconds. Search returns persisted citations, not generated claims. Corrupt citation deserialization or database access failure is sanitized. SQL eligibility is enforced before the logical top-k selection, while PostgreSQL remains free to choose physical execution order. No post-fetch authorization filtering is used.

## Tradeoffs

Fixed development identity supports local learning and isolation tests without inventing insecure trusted HTTP headers. It cannot distinguish actual users. A future provider must validate credentials and map trusted claims; authenticated write authorization is also required.

No new index or migration is justified by the current data. The local estimated plan preferred existing identity indexes. Re-evaluate with representative cardinalities and measured plans. English lexical retrieval has no semantic matching, typo tolerance, multilingual routing, or cross-request snapshot guarantee.

## Verification

Database tests seed high-ranking unauthorized chunks alongside permitted evidence, exercise limits, scope/lifecycle filters, citation round-trip, relevance and ties. HTTP tests verify configured identity, spoof resistance, request validation, default denial, and database outage handling. Existing ingestion/migration tests continue to run.
