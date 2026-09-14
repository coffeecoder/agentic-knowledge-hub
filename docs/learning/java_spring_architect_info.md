# Java, Spring, and Senior-Architect Learning Path

Use this project to explain decisions, demonstrate failure cases, and discuss tradeoffs. Completing a chapter is not the same as implementing the corresponding production capability. The Java migration is the current baseline; database persistence and cloud/AI integrations are still planned.

## 1. Read the application in request order

1. [KnowledgeHubApplication](../../src/main/java/com/agenticknowledgehub/KnowledgeHubApplication.java) starts Spring Boot. Auto-configuration supplies the embedded HTTP server and JSON conversion.
2. [IngestionConfiguration](../../src/main/java/com/agenticknowledgehub/config/IngestionConfiguration.java) is the composition root: it creates parsers, the chunker, and the service. A composition root centralizes wiring instead of hiding object creation throughout business logic.
3. [DocumentController](../../src/main/java/com/agenticknowledgehub/api/DocumentController.java) validates HTTP input and maps request/response objects. It does not parse text or perform SQL.
4. [IngestionService](../../src/main/java/com/agenticknowledgehub/ingestion/IngestionService.java) coordinates hashing, parsing, and chunking. It currently holds no document state.
5. [ParserRegistry](../../src/main/java/com/agenticknowledgehub/parsers/ParserRegistry.java) selects the first supporting parser.
6. [SemanticChunker](../../src/main/java/com/agenticknowledgehub/chunking/SemanticChunker.java) creates overlapping word windows and deterministic identifiers, preserving source anchors.
7. [CitationManifest](../../src/main/java/com/agenticknowledgehub/citations/CitationManifest.java) checks answer references against application-owned citation IDs. It is tested independently and is not yet part of an answer-generation endpoint.

Exercise: trace a request in your debugger. Explain which objects Spring creates once, which objects are created per request, and why shared service instances are safe only when their state and collaborators are safe for concurrent use. Current parsers create request-local buffers and binary-document objects.

## 2. Design principles and patterns actually present

| Concept | Project example | Tradeoff to explain |
|---|---|---|
| Single responsibility | Controller handles HTTP; parser extracts sections; chunker creates windows | Boundaries need to reflect reasons to change, not just small file sizes |
| Strategy | `DocumentParser` implementations for text, PDF, and DOCX | New formats can implement the interface without changing ingestion logic |
| Registry / ordered selection | `ParserRegistry` chooses the first matching parser | Order matters if more than one parser supports an input |
| Constructor injection | Service and controller receive collaborators | Makes dependencies explicit and supports unit testing |
| Inversion of control | Spring invokes bean creation and injects dependencies | Framework wiring belongs at the boundary; core processing stays plain Java |
| Open/closed principle | Add a parser and register it | Composition can change while orchestration stays stable |
| Encapsulation | Records copy lists and byte arrays | Records alone do not make nested objects deeply immutable |
| Separation of concerns | Domain models differ from HTTP DTOs | Some mapping code buys independence from the external API contract |

Dependency injection and the dependency inversion principle are related but different. Injection describes how dependencies arrive. Dependency inversion concerns which abstractions high-level policy depends upon. The parser interface demonstrates an abstraction boundary; not every current collaborator is behind an interface. Introduce a repository port when persistence gives us a concrete reason.

Planned patterns include Repository for durable document access, Adapter for cloud providers, and possibly Outbox for reliable database-to-event publication. Do not describe those as implemented today. Avoid Factory, Builder, or microservice layers until they solve a specific problem.

Interview drill: explain why `new TextParser()` in the composition root is acceptable, while creating a JDBC connection inside every controller method would scatter infrastructure policy.

## 3. Spring topics to demonstrate

- `@SpringBootApplication`: bootstrapping, component scanning, and auto-configuration.
- `@Configuration` and `@Bean`: explicit construction and dependency wiring. `proxyBeanMethods=false` is appropriate here because bean methods receive dependencies as arguments rather than invoking one another.
- `@RestController`: HTTP mapping and serialization, not a business-service boundary.
- `@Valid` with Jakarta validation: input checks before service execution.
- `@RestControllerAdvice`: consistent sanitized failures without exposing rejected document content.
- Singleton scope: one bean per application context by default, not one globally across Cloud Run instances.
- External configuration: `PORT` and chunk-size settings come from the environment. Spring does not automatically read this project's `.env` file.
- Unit versus integration tests: direct constructors for domain tests; a real random-port Spring server for API compatibility.

Future exercises: add typed `@ConfigurationProperties`, readiness checks, metrics, and repository injection when those features are introduced. Explain why liveness should not simply fail whenever a downstream database is temporarily unavailable.

## 4. Database transactions: next implementation milestone

The present service's optional previous hash is not durable idempotency. Restarting the application or sending a request through another instance gives it no stored history.

The intended persistence flow must load the stored checksum, coordinate concurrent writes, and commit the document plus all chunks atomically. Review the replacement/versioning decision before changing the schema.

| Topic | Interview explanation | Project experiment |
|---|---|---|
| Atomicity | The whole transaction commits or rolls back | Fail the second chunk insert and prove old data survives |
| Consistency | Constraints and application rules preserve valid state | Enforce document identity and chunk ordinal uniqueness |
| Isolation | Concurrent transactions need defined interaction | Send two identical first ingestions simultaneously |
| Durability | A committed result survives application restart | Restart the service and retry the same content |
| Idempotency | Repeating an operation does not duplicate its durable effect | First call changes state; identical retry leaves it unchanged |
| Locking | Coordinate writers; a missing row cannot be row-locked | Test the first-insert race, not only updates |
| Optimistic concurrency | Detect a conflicting version and retry/reject | Compare a revision before updating |
| Migration | Evolve existing state explicitly | Upgrade an existing volume without deleting it |

Spring's normal proxy-based transaction interception does not intercept a method's call to another method on the same object. The transaction boundary must actually pass through the proxy. By default, unchecked exceptions trigger rollback; checked exceptions need an explicit rollback policy when appropriate. Catching and swallowing an exception can also change the outcome. A service made `final` today may need adjustment when introducing class-based transactional proxies. [Spring transaction annotations](https://docs.spring.io/spring/reference/6.2/data-access/transaction/declarative/annotations.html).

A database transaction cannot roll back an already-completed external model API call. Keep remote work outside long-held database locks and design retries deliberately. For a future Pub/Sub publisher, discuss an outbox rather than claiming a single local transaction atomically commits both SQL and a network publish.

Important schema questions: What is a logical document? Are source versions ordered? Do we keep historical evidence? How are tenant and source identities included in IDs? How do metadata-only or permission changes affect the unchanged decision? Are empty replacements allowed? Are words being mislabeled as model tokens?

## 5. GCP learning milestones

These are planned integrations, not deployed resources.

| Service / concept | Role in this project | Architect-level question |
|---|---|---|
| Cloud Run | Run the stateless Spring HTTP container | How do concurrency, instance limits, startup time, and connection pools interact? |
| Artifact Registry | Store versioned container images | How do you promote and roll back a known image? |
| Cloud SQL for PostgreSQL | Durable documents, chunks, and vectors | What is the connection budget, backup strategy, and recovery objective? |
| Cloud Storage | Potential raw-source storage | How are object versions, retention, and source provenance linked? |
| Pub/Sub | Potential asynchronous ingestion delivery | What happens after duplicate or delayed delivery? |
| Vertex AI | Hosted embeddings and generation | How do latency, quota, model version, embedding dimension, and cost affect design? |
| IAM and service accounts | Workload access to GCP services | How is least privilege separated from end-user document authorization? |
| Secret Manager | Runtime secret management where needed | How are credentials injected and rotated without committing them? |
| Logging, Monitoring, Trace | Operational observability | Which metrics reveal ingestion failures without logging confidential content? |

Cloud Run accepts containers in different languages; Java is a valid choice. Its container must listen on the configured port and appropriate interface. This Dockerfile supplies a non-root runtime and uses `PORT`; it does not configure cloud IAM or deploy resources. [Cloud Run container contract](https://docs.cloud.google.com/run/docs/container-contract).

Connection budgeting exercise: with a hypothetical maximum of 10 application instances and a pool maximum of 5 connections per instance, budget up to 50 application connections, plus migrations, administration, other workloads, and deployment overlap. A local pool limit is not a global limit. [Cloud SQL connection management](https://docs.cloud.google.com/sql/docs/postgres/manage-connections).

## 6. Agentic AI and RAG concepts

The current project is an ingestion foundation. It is not yet a RAG answering system or an autonomous agent.

A planned RAG path is: authorize the query → retrieve permitted evidence → assemble the citation manifest → call the model → validate citations and answer policy. An agent adds decisions and tool calls around a workflow; those tool calls require application-owned permissions and bounded execution.

- **Chunking:** decide where evidence begins and ends. Overlap helps retain context but increases duplication and embedding cost.
- **Embeddings:** represent text for similarity search. Similarity does not establish permission or truth.
- **Hybrid retrieval:** combine keyword and vector signals when appropriate. Apply authorization before selecting evidence.
- **Grounding:** connect claims to evidence. A valid citation ID alone does not prove the claim follows from the source.
- **Prompt injection:** source text may contain instructions. Treat it as untrusted evidence and never allow it to authorize tools or override policy.
- **Tool execution:** enforce identity, permission, validation, timeouts, budgets, and approval rules outside the model.
- **Evaluation:** measure retrieval relevance, citation coverage/correctness, unsupported claims, latency, and cost with synthetic or authorized datasets.
- **Model lifecycle:** changing embedding models or dimensions can require re-embedding and index migration; do not silently mix incompatible vectors.

Spring AI can supply model integrations, but adding a library does not solve provenance, authorization, or reliable state changes. Keep those rules in application-owned code. [Spring AI reference](https://docs.spring.io/spring-ai/reference/api/index.html).

## 7. Recommended hands-on sequence

1. Run `./mvnw verify`; explain the controller/service/parser boundaries and the recorded chunk-ID compatibility test.
2. Implement PostgreSQL persistence with explicit identity, transactions, migrations, rollback tests, and concurrency tests.
3. Add authenticated identity and authorization-aware retrieval; test cross-tenant and cross-group isolation.
4. Add embeddings, retrieval, and citation-grounded answers with evaluation fixtures.
5. Package and deploy to Cloud Run with Cloud SQL, workload identity, and observability; document cost and recovery assumptions.
6. Add asynchronous ingestion only when needed; exercise retries, deduplication, stale updates, and dead-letter handling.
7. Add bounded agent tools with explicit permissions and auditable outcomes.

For every milestone, write a short decision record: problem, options, chosen approach, consequences, failure modes, verification, and what would make you reconsider. In an interview, demonstrate one failure experiment and explain the tradeoff rather than listing annotations or cloud services.

Return to the [learning index](README.md).
