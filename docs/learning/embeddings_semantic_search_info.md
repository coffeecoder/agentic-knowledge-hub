# Configurable Embeddings, Vertex AI, and Semantic Search

## 1. What is an embedding?

An embedding is a numeric representation of text produced by a model. Similar meaning can produce nearby vectors even when exact words differ. Our database stores 768 numbers per chunk. Semantic search embeds the query, ranks eligible chunks by cosine similarity, and returns their original text and citations. It does not generate an answer or run an agent.

Our existing keyword search remains the default. A useful evaluation pair is a document about storing secrets and a query about protecting application credentials. Actual retrieval quality must be measured; a high cosine score alone proves neither relevance nor correctness.

## 2. Provider configuration

| `EMBEDDING_PROVIDER` | Behavior | Credentials |
| --- | --- | --- |
| `none` (default) | Keyword-only; semantic requests return 503 | No embedding credentials |
| `local-hash` | Deterministic hashed word vectors for pipeline tests; no learned semantics | None |
| `ollama` | Real local model through Ollama | No Google ADC |
| `vertex` | Google Vertex AI text embeddings | Application Default Credentials |

Configuration is selected at startup. Never take a provider URL or model choice from an untrusted document. There is no silent cloud-to-local fallback: it could silently change quality and mix incompatible vectors.

The application still uses Identity Platform for end-user authentication in every mode. Local embeddings do not bypass JWT validation or make the complete identity flow air-gapped.

## 3. Start without cloud model access

From the repository root, with Docker Desktop running:

```bash
docker compose up -d postgres
EMBEDDING_PROVIDER=local-hash SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Use your Identity Platform ID token in Swagger as before. This mode tests vector persistence and retrieval without downloading model weights. It mainly captures word overlap; do not use it to demonstrate synonym understanding or production semantic quality.

For real local semantic embeddings, install Ollama from its official distribution, start it, and pull the model:

```bash
ollama pull nomic-embed-text:v1.5
EMBEDDING_PROVIDER=ollama SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Ollama must be listening at `http://localhost:11434`; the application calls `/api/embed`. `OLLAMA_EMBEDDING_URL` changes the endpoint; remote endpoints require HTTPS. Running the Java application inside a container changes what localhost means; keep Java on the host for this learning setup or provision an HTTPS endpoint. The model is downloaded once; subsequent inference is local. The script does not automatically install or download it.

The defaults use `search_document: ` and `search_query: ` input prefixes. These conventions belong to the selected model. Another Ollama model may require different prefixes; configure `akh.embedding.ollama.document-prefix` and `akh.embedding.ollama.query-prefix` accordingly and verify that it returns exactly 768 dimensions. See [Ollama's embed API](https://docs.ollama.com/api/embed) and the [Nomic model](https://ollama.com/library/nomic-embed-text).

## 4. Vertex AI setup

Use the existing project `agentic-knowledge-hub-learning`. Check billing is linked, the Vertex AI API is enabled, and the chosen embedding model is available in the selected region before a small live test. The default regional location is `us-central1`; do not assume the earlier `asia-south1` setting supports every model. See [model locations](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/locations).

From a machine with Google Cloud CLI installed:

```bash
brew update
brew install --cask gcloud-cli

gcloud auth login
gcloud config set project agentic-knowledge-hub-learning
gcloud services enable aiplatform.googleapis.com --project=agentic-knowledge-hub-learning
gcloud auth application-default login
gcloud auth application-default set-quota-project agentic-knowledge-hub-learning
```

The Homebrew command is the shortest setup path on the Apple Silicon development Mac used for this project. Open a new Terminal window after installation if `gcloud` is still not found.

ADC must exist on the machine running Java. Running these only in Cloud Shell does not authenticate your Mac. `gcloud auth login` authenticates the CLI, while `gcloud auth application-default login` creates credentials for Java client libraries. These are deliberately separate credential stores.

Verify ADC without printing the access token:

```bash
test -f "$HOME/.config/gcloud/application_default_credentials.json" \
  && echo "ADC file exists"

gcloud auth application-default print-access-token >/dev/null \
  && echo "ADC is ready"
```

Do not display, share, upload or commit `application_default_credentials.json`; it contains refresh credentials. Do not download service-account keys for this exercise. A future Cloud Run service should use an attached service account with the required Vertex prediction and quota-project permissions. [Google ADC documentation](https://docs.cloud.google.com/docs/authentication/application-default-credentials)

Start the application:

```bash
EMBEDDING_PROVIDER=vertex \
VERTEX_PROJECT_ID=agentic-knowledge-hub-learning \
VERTEX_LOCATION=us-central1 \
VERTEX_EMBEDDING_MODEL=gemini-embedding-001 \
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The adapter uses one input per prediction, distinguishes document/query retrieval tasks and requests 768 dimensions. It sends the explicit quota-project header, as we learned when configuring Identity Platform claims. Identity Platform ID tokens authenticate the user to our API; ADC authenticates our backend to Vertex. The Web API key used for sign-in is not an embedding credential. See [Vertex embedding requests](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api).

Enabling Vertex sends document chunks and queries to Google and can incur charges. Start with synthetic content and a small number of calls. No live cloud calls are part of `mvnw verify`.

### ADC troubleshooting learned during setup

| Symptom | Meaning | Action |
| --- | --- | --- |
| `command not found: gcloud` | Google Cloud CLI is not installed or not on `PATH` | Install `gcloud-cli` with Homebrew and open a new Terminal |
| `Your default credentials were not found` | CLI login may exist, but the ADC file does not | Run `gcloud auth application-default login` on the machine running Java |
| ADC works in Cloud Shell but Java fails locally | Cloud Shell and the Mac have separate filesystems and credentials | Configure ADC in the Mac Terminal |
| Quota-project warning or rejection | User ADC needs a project for quota/billing attribution | Run `gcloud auth application-default set-quota-project agentic-knowledge-hub-learning` |
| Semantic endpoint returns sanitized 503 | The adapter could not authenticate, reach Vertex, use the selected model/location, or parse a valid vector | Check ADC, API enablement, billing, IAM, region and model configuration; never log tokens or provider response bodies |

The Web API key used for Identity Platform email/password sign-in is unrelated to ADC. The ID token authenticates the human caller to this API; ADC authenticates the Java workload to Vertex AI.

## 5. Exercise ingestion and search

Authorize in [Swagger](http://localhost:8000/docs), then ingest:

```json
{
  "external_id": "embedding-demo-001",
  "title": "Protect Application Credentials",
  "text": "Store application passwords and API credentials in Secret Manager. Applications retrieve secrets using their service identity instead of embedding passwords in source code.",
  "source_version": "1",
  "allowed_groups": ["support"]
}
```

First ingestion returns CHANGED; an identical retry returns UNCHANGED without another embedding call. Semantic search:

```json
{"query":"How can I protect application credentials?","limit":5,"mode":"semantic"}
```

For keyword search, omit `mode` or use `"mode":"keyword"`. Test paraphrases with Vertex or Ollama; use overlapping words for local-hash tests. Results always include persisted citations. Scores from the two modes use different scales. Semantic search can return weak matches because we have not selected an evaluation-backed score threshold.

### Live validation completed for this project

The following path was verified manually with synthetic content:

1. `local-hash` ingestion returned a searchable 768-dimensional vector without cloud credentials.
2. Semantic and keyword searches both returned the permitted document and citation metadata.
3. ADC was created on the Mac and associated with quota project `agentic-knowledge-hub-learning`.
4. Vertex AI was selected with `gemini-embedding-001` in `us-central1`.
5. Re-ingestion changed vector space from `local-hash` to Vertex rather than mixing vectors.
6. A paraphrased semantic query returned the expected Secret Manager evidence.

This proves live connectivity and the end-to-end control flow. It does not establish production retrieval quality, latency, cost, quota capacity or behavior on a representative corpus. Those require a repeatable evaluation dataset and measurements.

## 6. Inspect persisted vectors without printing their values

In psql:

```sql
SELECT d.external_id, d.embedding_configuration, d.chunk_count,
       c.ordinal, c.embedding_model, c.embedding_dimensions,
       c.embedding IS NOT NULL AS has_vector
FROM documents d
JOIN chunks c USING (document_id)
ORDER BY d.external_id, c.ordinal;
```

The space ID fingerprints provider/model/revision/task conventions; matching dimensions alone is insufficient. Switching to another provider excludes old vectors from semantic search until you re-ingest the documents. Keyword search continues to work. Bump `EMBEDDING_REVISION` if weights or preprocessing change under the same model identifier. Re-ingestion then returns CHANGED even for unchanged text.

V3 preserves existing rows and adds embedding metadata; never delete the Docker volume to adopt it. Unclassified legacy vectors are retained but excluded from managed semantic search. This milestone keeps one current vector space per document, not multiple model versions. Switching to none and re-ingesting clears that document's embeddings.

## 7. Transactions and design principles

Read the provider interface, the adapters, then `IngestionService`, `TransactionalDocumentWriter` and `JdbcVectorSearchRepository`.

1. **Dependency inversion:** services use `EmbeddingProvider`; only adapters know Vertex/Ollama payloads and credentials.
2. **Strategy and adapter:** Spring selects a provider strategy. Each adapter translates the common contract to its external API. We avoid a full agent framework for a small embedding operation.
3. **Short transactions:** a read-only check finishes before inference. The source-locked write transaction rechecks state and commits documents, chunks, vectors and citations together.
4. **Idempotency:** a checksum alone cannot detect missing vectors, changed processing or a provider switch. The comparison includes metadata, space identity, chunk count and vector completeness.
5. **Concurrent retries:** two requests may both call the model, but the locked recheck prevents duplicate committed replacements. Exactly-once billing is not guaranteed.
6. **Authorization:** only eligible tenant/group/lifecycle rows enter the materialized SQL candidate set. Exact cosine ranking and LIMIT operate on that set. Empty groups trigger no model call.
7. **Index tradeoff:** exact search is predictable for a small corpus. The existing HNSW index is not used for this exact path. Approximate search needs a separate recall/latency evaluation, especially with selective ACLs. [pgvector documentation](https://github.com/pgvector/pgvector)

Cloud inference is not covered by PostgreSQL rollback. We therefore prepare vectors first and only commit a complete set. A provider failure leaves existing evidence unchanged. If SQL fails after some chunks are inserted, the transaction restores the previous document, chunks, citations and vectors.

## 8. Limits, failures and tests

- Enabled ingestion allows 16 chunks by default; configure `EMBEDDING_MAX_CHUNKS` between 1 and 64.
- Each embedding input is capped at 32,000 Java UTF-16 units. This is a workload cap, not a token count. Providers enforce their own model context limits with truncation disabled.
- A word-based chunk may exceed a model token limit. Reduce chunk size or design a tokenizer-aware pipeline; never silently truncate evidence.
- Default inference timeout is 15 seconds, with at most two attempts. Configure `EMBEDDING_TIMEOUT_SECONDS` (1–60) and `EMBEDDING_ATTEMPTS` (1–3). These are per-call bounds, so multi-chunk requests can take longer; ADC refresh has separate library behavior.
- 400/413/422 provider rejections or local input limits yield sanitized 422. A 400 may reflect provider/model configuration as well as input; inspect safe configuration first. Other permanent failures, invalid vectors and exhausted transient failures yield 503. No upstream response body or document text is returned.
- There is no automatic fallback, background backfill, asynchronous job queue, circuit breaker, semantic threshold or hybrid score fusion yet.

```bash
./mvnw spotless:apply
./mvnw verify
git diff --check
```

Tests use mock HTTP servers, synthetic vectors and disposable PostgreSQL. They check API contracts, limits, normalization, failures, retries, model switches, completeness repair, transaction separation, concurrent duplicates, rollback and authorization. Live semantic quality must be assessed using a small question/evidence dataset, including paraphrases, irrelevant queries and forbidden documents.

Interview exercise: explain why an ID token differs from ADC, why embedding vectors need a model identity, why a model call should not hold a row lock, and why exact vector search is a useful baseline before HNSW tuning.

Additional interview points:

- **Why does switching providers return CHANGED?** The text can remain identical while the derived vector representation changes. Model identity is part of durable processing state.
- **Why is ADC preferable to a downloaded service-account key?** Local user ADC supports development; managed runtime identity supports deployment without long-lived key files.
- **Why keep keyword search?** It is deterministic and strong for exact identifiers, error codes and product names. Semantic search helps with paraphrases; mature retrieval often combines both after evaluation.
- **Why avoid model calls inside the write transaction?** Network latency and retries would hold locks and database connections, increasing contention and failure impact.
- **Why can two concurrent requests incur two Vertex calls?** Database locking provides one committed replacement, but it cannot make an external model API exactly-once. A durable work queue or request-deduplication protocol would be needed for stronger cost control.

Return to the [learning index](README.md).
