# ADR 0005: Configurable embedding providers and exact authorized vector retrieval

Status: implemented; real provider quality and project connectivity require separate smoke tests.

## Decision

Use a small `EmbeddingProvider` interface in a dedicated package. Spring selects one adapter at startup: `none`, `local-hash`, `ollama`, or `vertex`. No network call or Google credential lookup occurs at startup for none/local providers. Unknown provider configuration fails startup. There is no automatic fallback or per-request provider selection.

`none` preserves keyword-only operation. `local-hash` generates deterministic hashed word features for offline pipeline development; it is not a learned semantic model. Ollama supports real local models through `/api/embed` (default `nomic-embed-text:v1.5`). Vertex uses regional publisher-model `predict`, explicit retrieval tasks and Google ADC (default `gemini-embedding-001`). A future provider needs an adapter and wiring; it does not need a new ingestion or retrieval implementation.

The database remains `vector(768)`. Adapters validate output dimensions, finite values and nonzero magnitude, and normalize vectors. Model, provider, revision and task/prefix conventions define a fingerprinted vector space. Document rows retain human-readable configuration; chunks retain model, dimensions and space ID. Query vectors can only search documents/chunks in the same space. Changes to local model weights under the same tag require a new operator revision. Different dimensions require a planned schema/index migration, not just an environment change.

## Ingestion and transaction boundaries

The authorized facade runs before embedding. Parsing/chunking stays deterministic and makes no model calls. A short read-only transaction checks document metadata, processing configuration, space, expected chunk count, actual count and vector availability. A matching snapshot returns UNCHANGED without inference.

For changed state, all vectors are generated outside the write transaction. The writer then locks the source, repeats the comparison, and atomically persists document metadata, citations, chunks and vectors. A model failure leaves stored data unchanged; a SQL failure rolls back replacement. Concurrent requests may both pay for inference; one commits and the other observes UNCHANGED. This is durable idempotency, not exactly-once inference. Concurrent source creation handles both deterministic primary-key and logical scope-key conflicts, then looks up and locks the full source identity. Source-wide locking remains conservative and can be refined if measured contention warrants it.

Missing vectors or chunks trigger a rebuild even when text is unchanged. Provider switches re-embed on explicit re-ingestion. Backfill is not automatic; old-space chunks remain usable for keyword search but are excluded from semantic search in the new space. Only the current document/chunk set is retained. Switching to none and re-ingesting replaces embeddings with nulls; it does not keep a multi-provider archive. Upstream versions remain opaque: stale-update ordering is not solved by this milestone.

## Retrieval

`POST /v1/search` accepts mode `keyword` (default) or `semantic`. The same trusted caller and lifecycle/ACL predicates apply. A materialized eligible-row CTE precedes cosine ranking and LIMIT, providing exact retrieval even though an HNSW index exists. Approximate indexing is deferred until recall and selective ACL behavior can be measured. Empty caller groups skip database and inference calls.

Semantic score is cosine similarity, not confidence, probability, or a guaranteed relevant match. No semantic cutoff is selected without evaluation. Keyword and vector scores are not directly comparable; hybrid fusion is a later decision. Source citations retain their existing representation and validation rules.

## Failure, cost, and security

Embeddings are opt-in. Enabling a remote adapter sends authorized document chunks and search queries to that configured provider. Endpoint configuration belongs to operators, never request bodies. Ollama HTTP is restricted to loopback; remote endpoints require HTTPS. Redirects are disabled. Google tokens are sent only to the constructed regional Vertex endpoint, never to Ollama.

Input is bounded to 32,000 Java UTF-16 units per embedding and 16 chunks per enabled ingestion by default (configurable 1–64). These are workload limits, not tokenizer estimates. Vertex autoTruncate and Ollama truncate are false; provider token limits produce an explicit failure instead of silently embedding a prefix. Over-limit inputs/rejected requests return sanitized 422; credential, transport and invalid-response failures return 503. Oversized documents need deliberate chunk configuration or a future asynchronous pipeline.

Inference timeout is configurable (1–60 seconds in application configuration), attempts 1–3. Defaults are 15 seconds and two attempts. Only network failures, 429 and selected transient 5xx responses are retried, with a short bounded delay. These bounds apply per inference, not to the entire multi-chunk request or Google ADC refresh. Credential refresh uses the authentication library's behavior. There is no circuit breaker, durable job queue, adaptive quota scheduling or distributed rate limiter yet. Repeated inference attempts may incur duplicate charges.

The embedding subsystem supports local operation. End-user authentication remains Identity Platform; this milestone does not introduce an offline identity bypass.

## Validation

Generated/test vectors and local mock HTTP servers cover provider contracts, truncation, invalid dimensions, retries, timeouts and sanitized errors. Real PostgreSQL tests prove model-call transaction separation, backfill, missing-vector repair, duplicate ingestion, rollback of vectors/citations, space incompatibility, and SQL authorization before LIMIT. Authenticated HTTP tests exercise local-hash mode. These prove mechanics, not semantic retrieval quality or live provider availability.
