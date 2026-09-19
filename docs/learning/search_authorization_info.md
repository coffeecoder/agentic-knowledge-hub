# Keyword Search and Authorization

This milestone retrieves persisted evidence with PostgreSQL full-text search. It does not generate an answer, call an embedding model, or call an agent. Authentication is now provided by [Identity Platform](gcp_identity_security_info.md).

## 1. Run the local learning mode

Start Docker Desktop and PostgreSQL, then start or restart the Java application:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The `dev` profile exposes Swagger only. Both APIs require a verified ID token with administrator-issued permissions. Follow the [identity setup guide](gcp_identity_security_info.md); the former simulated development identity has been removed.

## 2. Ingest and search synthetic evidence

```bash
curl -X POST http://localhost:8000/v1/documents/text \
  -H "Authorization: Bearer $AKH_ID_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"external_id":"search-guide","title":"Cloud Run Guide","text":"Cloud Run hosts the deployment API.","allowed_groups":["support"]}'

curl -X POST http://localhost:8000/v1/search \
  -H "Authorization: Bearer $AKH_ID_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"query":"Cloud Run deployment","limit":5}'
```

You can use [Swagger](http://localhost:8000/docs) for the same calls after restarting the updated application. The search response contains a `results` array. Each item includes `chunk_id`, `document_id`, `external_id`, `content`, `score`, and the persisted `citation` object. Outer identity fields use snake_case; citation fields retain the existing Java citation representation, such as `sourceType`, `sourceVersion`, and `headingPath`.

Limits default to 5 and must be between 1 and 20. Query text must be nonblank, contain no NUL character, and be no longer than 500 Java UTF-16 code units. No match, only stop words, or a caller with no groups yields an empty array. Invalid requests return 422; missing/invalid tokens return 401; insufficient permissions return 403; database failure returns a sanitized 503. Query text and source content are not logged by the search code.

The request contains no authoritative identity fields. Extra fields are ignored for compatibility; supplying `tenant_id`, `groups`, `X-Tenant-ID`, or `X-Groups` does not change the authenticated caller.

## 3. Read the implementation in order

1. [SearchController](../../src/main/java/com/agenticknowledgehub/api/SearchController.java) maps HTTP and obtains the caller from a provider.
2. [SecurityConfiguration](../../src/main/java/com/agenticknowledgehub/config/SecurityConfiguration.java) validates tokens and maps trusted claims to the caller.
3. [RetrievalService](../../src/main/java/com/agenticknowledgehub/retrieval/RetrievalService.java) validates the request and coordinates retrieval.
4. [ChunkSearchRepository](../../src/main/java/com/agenticknowledgehub/retrieval/ChunkSearchRepository.java) defines the required search operation.
5. [JdbcChunkSearchRepository](../../src/main/java/com/agenticknowledgehub/persistence/JdbcChunkSearchRepository.java) applies eligibility, full-text matching, ranking, and limiting in one parameterized SQL statement.

These boundaries demonstrate constructor injection, dependency inversion, and an adapter for the database. The identity provider reads Spring Security’s validated authentication; a Java record containing identity fields is not itself evidence of authentication.

## 4. Authorization policy

A result must satisfy all of the following:

- Source tenant equals the trusted caller tenant.
- Source status is ACTIVE.
- Document is not deleted.
- Chunk is active.
- At least one document group overlaps a caller group.

Empty document permissions mean deny access. They do not mean public. To make earlier synthetic documents searchable, re-ingest them with the intended `allowed_groups`; persistence detects that permission change even if the text stays identical. An empty caller group list also receives no results. Matching is exact and case-sensitive for tenant/group identifiers.

Authentication asks who the caller is. Authorization asks what that identity may read. SQL filtering now uses token-derived identity, and publisher authorization protects ingestion.

## 5. Why filters must precede the result limit

Suppose the top five unrestricted matches all belong to another tenant. Fetching those five and filtering in Java can both expose unauthorized evidence to application layers and leave the authorized user with no results even when permitted matches exist.

Our SQL includes tenant, group, and lifecycle predicates in the result-producing query before ORDER BY and LIMIT. It never sends an unrestricted top-k list to Java. SQL specifies logical eligibility, not a guaranteed physical predicate-evaluation order: the PostgreSQL optimizer can choose different scan/join orders without changing the permitted result set.

One SQL statement also reads document permissions and chunks from the same statement snapshot. A concurrent permission change becomes visible according to PostgreSQL transaction semantics; this is not a promise to retract results already returned to a client.

## 6. PostgreSQL search concepts

- `to_tsvector('english', content)` normalizes content into searchable lexemes.
- `websearch_to_tsquery('english', query)` accepts search-style input, including quoted phrases, OR, and minus terms.
- `@@` tests whether the vector matches the query.
- `ts_rank_cd` ranks matches using cover density; its score is not a probability or AI confidence.
- `ORDER BY score DESC, chunk_id ASC` makes equal-score ordering deterministic.
- The existing GIN expression index matches `to_tsvector('english', content)`.

These functions are described in the [PostgreSQL text-search documentation](https://www.postgresql.org/docs/16/textsearch-controls.html). English stemming and stop words make this different from substring search. This implementation does not provide typo tolerance, multilingual configuration, semantic similarity, or pagination.

No schema migration was necessary. A read-only EXPLAIN on the current small local database chose source/document/chunk identity indexes with text matching as a filter. PostgreSQL does not have to choose GIN for every query. Revisit indexes with representative data and `EXPLAIN (ANALYZE, BUFFERS)` before making performance claims; EXPLAIN alone reports estimates, not measured latency.

The repository sets a five-second JDBC query timeout. Input/result bounds limit work but do not replace production rate limits or workload tuning.

## 7. Tests and interview exercises

```bash
./mvnw -Dtest=SearchIntegrationTest,SearchApiTest,RetrievalServiceTest test
./mvnw verify
```

Docker is required for disposable PostgreSQL integration tests. The full suite also verifies default-profile denial and HTTP database-outage behavior.

The tests check that high-ranking unauthorized chunks do not consume the limit; tenant and group boundaries hold; empty permissions, deleted documents, inactive chunks/sources are excluded; citations survive retrieval; ties are deterministic; invalid queries fail; and spoofed identity fields/headers are ignored. Malformed stored citation data and database failures produce safe failures rather than exposing diagnostic content.

Exercises:

1. Use an appropriately granted publisher to create support and admin fixtures. The learning support publisher must receive 403 when attempting to assign admin permissions.
2. As an authorized source publisher, re-ingest a document with support permission and repeat the search. Explain why the stored metadata changed without a text change.
3. Omit the token and observe 401; use a reader token for ingestion and observe 403. Explain the distinction.
4. Compare keyword and quoted-phrase searches. Explain why a relevance score cannot establish factual correctness.
5. Explain how the JWT identity adapter validates issuer, audience, signature, and trusted claim mapping rather than decode arbitrary claims.

Configurable embeddings and semantic search are now available; see the [embedding guide](embeddings_semantic_search_info.md). Next milestones include retrieval-quality evaluation, hybrid retrieval, grounded generation and bounded agent tools. The existing authorization boundary must remain effective when vector retrieval is introduced.

Return to the [learning index](README.md).
