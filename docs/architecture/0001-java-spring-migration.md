# ADR 0001: Java and Spring Boot foundation

Status: accepted for the language migration. PostgreSQL persistence remains a separate milestone.

## Context

The initial Python/FastAPI foundation implemented stateless parsing and chunking. The project owner has a Java background and wants hands-on preparation for senior-architect interviews in Spring, design principles, database transactions, GCP, and Agentic AI. The codebase is still small enough to port before durable state and cloud connectors introduce additional migration risk.

## Decision

Use Java 21 as the compiler target, Spring Boot 4.1.1 for HTTP transport and dependency wiring, Maven Wrapper 3.9.11 for repeatable builds, PDFBox for PDFs, and Apache POI for DOCX. Spring Boot's BOM manages the framework dependency set; independently versioned parser and formatting libraries are pinned in `pom.xml`.

Keep a single application with package boundaries. Core domain records, parsers, chunking, and citation validation do not depend on Spring. Explicit `@Bean` methods compose those objects and inject the ingestion service into the REST controller. This allows fast unit tests without a Spring context.

Do not introduce Spring AI, a JDBC starter, JPA, or cloud SDKs before a feature uses them. A generic vector-store abstraction must not replace application-owned provenance and authorization rules.

## Compatibility

- Preserve `/health/live`, `/v1/documents/text`, `/docs`, and `/openapi.json` entry points.
- Preserve snake_case ingestion response fields and default version `1`.
- Preserve stateless `CHANGED`, caller-hash-based `UNCHANGED`, and `EMPTY` behavior.
- Verify the deterministic chunk-ID algorithm against recorded Python outputs.
- Preserve heading/page/line metadata. PDFBox and PyMuPDF can extract differently from complex page layouts; binary extraction is not promised to be byte-identical.
- Normalize optional JSON nulls to defaults for version and groups. Validation errors remain HTTP 422 but now use a generic body instead of FastAPI's detailed rejected-field structure, preventing content disclosure.
- Serve OpenAPI through Springdoc; the generated specification and Swagger UI assets need not be identical to FastAPI's.
- Preserve PostgreSQL schema and Docker volume. This migration performs no database writes.
- Preserve the earlier source at Git commit `ba9447f`; remove Python source/tests/build metadata from the active checkout to avoid two competing implementations.

## Consequences and tradeoffs

Java improves alignment with the owner's skills and allows familiar transaction and dependency-injection concepts. It introduces Maven configuration, more explicit classes, and a JVM runtime. Python remains useful for later exploratory work, but a second deployed service is not justified by this baseline.

Records provide concise typed data carriers; collections and source bytes are defensively copied. Metadata values are only shallowly copied and should remain simple value data. Constructor injection exposes dependencies. The parser interface is an actual extension point, not an interface added to every class for appearances.

The name `SemanticChunker` is retained for continuity. Its actual algorithm is deterministic word windows within parsed sections, not an embedding-based semantic segmentation model. Section anchors may be broader than an individual window.

## Next decision: PostgreSQL persistence

Implement and review the repository contract, source-scoped identity, schema migration, transactional replacement/versioning policy, and concurrency behavior in Java. The existing schema allows multiple source versions; choosing current-row replacement requires an explicit migration and reconciliation plan. Stored `token_count` also differs from the actual `wordCount` produced today.

`@Transactional` alone will not provide idempotency or protect the first-insert race. Verify first ingestion, unchanged retry across process instances, changed-content replacement, rollback, and concurrent duplicate requests against real PostgreSQL. Avoid remote model calls while holding database locks. Historical answer reconstruction requires retained revisions, beyond current-row replacement.

## Verification

JUnit tests cover ingestion states, deterministic IDs and overlap, text anchors, PDF pages, DOCX headings, malformed inputs, citation allow-list behavior, and HTTP routes. `./mvnw verify` compiles, tests, packages, and checks formatting. The Dockerfile uses JDK/JRE 21 and CI verifies on JDK 21.

## References

- [Spring Boot reference](https://docs.spring.io/spring-boot/)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)
- [Springdoc OpenAPI](https://springdoc.org/)
