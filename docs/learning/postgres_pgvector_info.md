# PostgreSQL, pgvector, and Transactional Ingestion

This milestone implements durable document and chunk storage. It does not yet implement embeddings, vector retrieval, or answer generation.

## 1. Follow one ingestion

```text
HTTP request
  → IngestionService: hash, parse, chunk
  → Spring transaction proxy
      → TransactionalDocumentWriter
          → ensure and lock source
          → compare persisted state
          → upsert document, delete old chunks, insert replacement chunks
      → COMMIT or ROLLBACK
  → HTTP response
```

Read [IngestionService](../../src/main/java/com/agenticknowledgehub/ingestion/IngestionService.java), [TransactionalDocumentWriter](../../src/main/java/com/agenticknowledgehub/ingestion/TransactionalDocumentWriter.java), and [JdbcDocumentRepository](../../src/main/java/com/agenticknowledgehub/persistence/JdbcDocumentRepository.java) in that order.

The repository interface demonstrates dependency inversion: orchestration depends on the persistence operations it needs, while the JDBC adapter knows PostgreSQL syntax. SQL stays visible because locking, JSONB, and batched inserts are important to this use case; an ORM is not required for sound domain boundaries.

## 2. Identity is different from content

- Source identity: configured tenant + source type + source name.
- Document identity: source UUID + external ID.
- Content checksum: SHA-256 of the original source bytes.
- Processing version: parser/chunker revision plus window/overlap settings.
- Chunk ID: deterministic hash of source scope, document identity information, version, processing version, ordinal, and chunk content hash.

The unchanged decision also compares title, MIME type, URL, version, permissions, metadata, and deletion state. Equal bytes are insufficient if permissions or citation metadata have changed. Group order and duplicate group names are normalized; JSONB comparison ignores object-key order.

Interview exercise: explain why two tenants uploading a document called `runbook` must not share a chunk primary key, even if both texts are identical. Then explain why a model embedding change may require reprocessing without any source-byte change.

## 3. ACID with a concrete failure

| Property | Meaning here | Evidence |
|---|---|---|
| Atomicity | Document update and chunk replacement succeed together | A trigger rejects the second chunk; all earlier writes roll back |
| Consistency | Logical documents and chunk ordinals remain unique | Database unique constraints complement service checks |
| Isolation | Concurrent writes have controlled interaction | Source row locking under READ COMMITTED |
| Durability | Committed state is not held in Java process memory | A fresh service instance reads the persisted checksum |

A transaction does not mean every read sees the same snapshot at every isolation level. READ COMMITTED obtains a snapshot per statement. The source lock coordinates the writers in this application. A future retrieval operation that makes several related queries may need its own consistent snapshot or a single joined query.

## 4. Why the first insertion needs special care

Locking a document row with SELECT FOR UPDATE cannot lock a row that does not exist. This implementation first ensures a source row exists and locks it. A concurrent insertion waits on the conflict/lock; after the earlier writer commits, the later writer reads the committed document and returns UNCHANGED.

The tradeoff is that different documents within a source also serialize during writes. Parsing stays outside the lock. A future design can use document-scoped coordination, but it must preserve protection for missing rows and be tested under concurrency.

`@Transactional` provides the boundary; the unique constraint, stored comparison, and locking provide retry/concurrency safety. None of these is a substitute for the others.

## 5. Spring transaction traps

- The non-final writer is a Spring bean reached through a class-based proxy. The service calls that bean instead of invoking another method on itself.
- Directly constructing the writer in application code bypasses interception. The integration test asserts that the actual bean is proxied and then proves rollback using PostgreSQL.
- The writer's JDBC exceptions are unchecked, so they trigger rollback. The service translates failures only after they have passed through the proxy, including commit failures.
- Catching a database error inside the transactional method and returning success could violate the response contract.
- Pool connections are reused; they are not one global shared transaction. Each concurrent request gets its own transaction-bound connection.
- A SQL rollback cannot undo an external model API call or Pub/Sub publication. Those need separate retry/consistency designs in later milestones.

Read the [Spring transaction implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html) for the proxy mechanism.

## 6. Migrations and the existing Docker volume

Flyway stores applied versions and checksums. V1 creates the initial schema, and V2 changes logical-document uniqueness and count/version fields. Editing an applied migration is not an upgrade strategy; create a new migration.

For a new database, normal application startup applies both migrations. For an earlier database with no Flyway history, run the explicit adoption command in the [README](../../README.md#start-locally). The verifier creates V1 in a scratch schema, compares structure, rolls it back, and only then permits baselining. A baseline records a starting version; it does not itself validate table structure. [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline).

If multiple upstream versions already exist for one logical document, V2 stops before changing them. Decide which version to retain or adopt a historical-revision design; do not automatically delete rows. No `docker compose down -v` is needed to upgrade.

## 7. Inspect your local state

Use synthetic text in the API documentation at `/docs`. Send the same request twice, then change its text and send it again. Expected statuses: CHANGED, UNCHANGED, CHANGED.

From the project root, open PostgreSQL's terminal:

```bash
docker compose exec postgres psql -U akh -d akh
```

`postgres` is the Compose service, `-U akh` selects the database user, and `-d akh` selects the database. Run the remaining commands inside `psql`. SQL statements end with a semicolon; psql backslash commands do not need one.

List tables and inspect their structures:

```text
\dt
\d documents
\d chunks
```

Inspect sources, documents, and the number of chunks stored for each document:

```sql
SELECT source_id, tenant_id, source_type, name
FROM sources;

SELECT document_id, external_id, title, source_version,
       content_hash, processing_version, updated_at
FROM documents;

SELECT d.external_id, d.title, COUNT(c.chunk_id) AS chunk_count
FROM documents d
LEFT JOIN chunks c ON c.document_id = d.document_id
GROUP BY d.document_id
ORDER BY d.external_id;
```

For your synthetic learning data, inspect the chunk text and stored citation metadata:

```sql
SELECT document_id, ordinal, content, word_count
FROM chunks
ORDER BY document_id, ordinal;

SELECT chunk_id, jsonb_pretty(citation) AS citation
FROM chunks
ORDER BY document_id, ordinal;
```

Inspect migration history, checksums, counts, and citation anchors:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;

SELECT d.document_id, d.external_id, d.source_version, d.content_hash,
       d.processing_version, count(c.chunk_id) AS stored_chunks
FROM documents d LEFT JOIN chunks c USING (document_id)
GROUP BY d.document_id;

SELECT chunk_id, ordinal, word_count, token_count,
       page_number, line_start, line_end
FROM chunks ORDER BY document_id, ordinal;
```

For wide output, enable automatic expanded display:

```text
\x auto
```

Exit psql with:

```text
\q
```

Learning exercise:

1. Ingest a synthetic document through Swagger at `/docs`, then inspect its document and chunk rows.
2. Repeat the same request. It should return `UNCHANGED`, with the same stored chunk count and document `updated_at`.
3. Change the text and submit again. It should return `CHANGED`; the document ID remains stable while its chunks are replaced.

`UNCHANGED` returns zero newly produced chunks, while the database still contains the prior chunk set. Empty input returns EMPTY and does not delete it. Changing source metadata or processing revision causes CHANGED even for equal source bytes.

## 8. pgvector and citation provenance

The vector extension and vector column exist, but embeddings are still null. An available vector index does not mean the application already performs vector search. The current 768-dimensional schema is a future model-selection constraint that may require a migration.

Citation JSON stores source type, title, URL, version, headings, and page/line anchors. These fields are also stored in their existing anchor columns where applicable. Permission groups belong to the document; future retrieval must join and authorize before selecting evidence. A citation ID allow-list checks reference validity, not whether the source actually supports every claim.

The application counts words; it does not run a model tokenizer. New rows set `word_count` and leave `token_count` null. Legacy unknown word counts remain null instead of being fabricated from old token values.

## 9. Run the failure experiments

```bash
./mvnw verify
```

Docker must be running. Testcontainers starts PostgreSQL/pgvector with synthetic credentials on a random port. It creates disposable databases for migration tests and never uses your normal Compose volume. No tests are silently skipped if Docker is absent.

Study [PersistenceIntegrationTest](../../src/test/java/com/agenticknowledgehub/PersistenceIntegrationTest.java). It injects a real database trigger failure, then checks that the old checksum and chunks survived. Study [MigrationIntegrationTest](../../src/test/java/com/agenticknowledgehub/MigrationIntegrationTest.java) for schema evolution and refusal cases. The HTTP outage test checks a real failed connection path and verifies the safe 503 body.

## 10. Architect-level limitations and next questions

- Current-only replacement loses historical evidence. When do audit requirements justify immutable revisions?
- Source version strings are unordered. How will a delayed connector event be rejected rather than overwrite newer content?
- Locking is per source. When does throughput justify more complex coordination?
- A pool of five connections is per application instance, not a global Cloud SQL limit. How will Cloud Run scaling affect the connection budget?
- The local source scope is configured, not authenticated. How will identity, tenant authorization, and source ownership enter the application?
- Parsing is repeated even on an unchanged retry. When is a precheck worth the added race-handling complexity?

Return to the [learning index](README.md).
