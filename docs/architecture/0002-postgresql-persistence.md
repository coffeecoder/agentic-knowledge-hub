# ADR 0002: Transactional current-document persistence

Status: implemented. Supersedes the stateless database behavior in ADR 0001.

## Context and decision

Repeated ingestion previously returned CHANGED because no checksum or chunks were stored. Store one current document per `(source_id, external_id)` and replace its chunks atomically using Spring JDBC and a Spring-managed transaction. Retain `source_version` as opaque metadata rather than a historical primary key.

The source is identified by configured tenant, source type, and source name. New source/document UUIDs are deterministic; existing UUIDs are retained during upsert. Chunk IDs hash length-framed tenant/source/type/external ID/version/processing-version/ordinal/full-content-hash components to avoid namespace collisions and ambiguous concatenation. Processing version contains both an explicit parser/chunker revision and chunk window settings.

## Boundaries

`IngestionService` hashes, parses, and chunks without a connection. `TransactionalDocumentWriter` owns the consistency decision and transaction. `DocumentRepository` is its persistence contract; `JdbcDocumentRepository` uses parameterized SQL and batch writes on Spring's transaction-bound connection. The API only maps input, results, and sanitized errors.

The separate non-final writer bean permits class-based transaction proxying and avoids self-invocation. The preparation service catches database/transaction exceptions after the proxy returns or fails, including commit failures, and replaces sensitive diagnostics with a safe domain exception. HTTP maps that exception to 503. No in-memory success fallback exists.

## Concurrency and failure behavior

The writer uses READ COMMITTED and a 15-second timeout. It inserts the source with ON CONFLICT DO NOTHING, then locks that source row FOR UPDATE. This protects the first-insert race as well as updates, across threads/processes. Under this lock it compares content hash, processing version, title, MIME type, upstream version, URL, normalized permission groups, metadata JSONB, and deletion status.

If unchanged, no document/chunk writes occur. Otherwise it upserts the document, deletes the prior chunks, and batch-inserts the replacement set with citations and word counts. A failure rolls back the entire transaction. Database readers cannot see the uncommitted intermediate deletion; multi-statement readers still need their own suitable snapshot/transaction if they require a consistent view across statements.

Source-level serialization reduces throughput for a hot source but is easy to reason about. A future finer-grained locking design must still cover missing documents. Parsing on every retry is a deliberate cost of keeping preparation outside locks; an optimized precheck would still need a locked recheck to avoid races.

A lost response after a successful commit can be retried safely. Concurrent different inputs use serialized last-writer behavior; there is no upstream ordering policy. Permissions are stored but are not yet authenticated or enforced by a retrieval service. Empty input returns EMPTY and leaves current state intact.

## Schema ownership and upgrade

Flyway owns V1 initial creation and V2 persistence changes. Compose no longer mounts an initialization SQL file. `database/schema.sql` remains a legacy reference. Existing databases require explicit adoption with `--akh.database.adopt-legacy=true`: compare public tables, columns/defaults/nullability, constraints, indexes, and triggers against V1 in a rollback-only scratch schema, then baseline at V1 and migrate. Verification requires permission to create a scratch schema and assumes the application schema is public. A drifted schema is not automatically repaired.

V2 rejects duplicate logical documents before changing constraints. It adds `processing_version`, adds nullable `word_count` for historical rows, and makes `token_count` nullable. Historical counts are not guessed; new writes populate true word counts and leave token counts/embeddings unknown. Startup requires database access and successful migrations. Flyway clean and automatic baselining are disabled.

## Consequences

Atomic replacement does not retain historical citations. Updated chunk IDs and data may invalidate references to prior evidence. Future auditable historical answers need immutable revisions and a current-version pointer. No embeddings, model calls, authenticated tenant routing, retrieval, distributed event publication, or historical ordering are added in this milestone.

## Evidence

`PersistenceIntegrationTest` covers durable retries through new service instances, replacement, citation JSON round-trip, metadata/processing updates, empty input, scope isolation, concurrent duplicate insertion, and injected SQL failure after deletion. `MigrationIntegrationTest` covers fresh startup, explicit adoption with preserved rows, drift rejection, and duplicate-version refusal. API tests cover persisted retries and sanitized runtime outage behavior. Testcontainers isolates all test data from the developer's Compose database.
