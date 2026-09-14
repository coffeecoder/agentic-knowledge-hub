# Agentic Knowledge Hub

Agentic Knowledge Hub is a citation-first knowledge platform for developers, architects, business analysts, QA engineers, production-support teams and AI agents. Its target architecture ingests heterogeneous content, detects changes, creates provenance-rich chunks, stores embeddings, retrieves only authorized evidence and generates grounded answers.

The application is implemented in **Java 21 and Spring Boot 4.1.1**, with Maven Wrapper 3.9.11. It is also a practical learning project for senior-architect interviews covering Spring, design principles, database transactions, GCP, and Agentic AI.

![Complete architecture](docs/complete-architecture.png)

## Implemented foundation

- `GET /health/live`: process liveness, independent of PostgreSQL
- `POST /v1/documents/text`: text parsing, SHA-256 checksums, deterministic word-window chunks, and citation metadata
- Text/Markdown, PDF (PDFBox), and DOCX (Apache POI) parsers at the service layer
- Citation ID validation against an application-owned manifest
- Local PostgreSQL 16 with pgvector and an initial schema, started independently using Compose
- Java unit tests, HTTP integration tests, and a reproducible executable JAR build

**Ingestion is currently stateless.** No JDBC connection, stored checksum, chunk persistence, embedding call, retrieval, authentication, or agent execution is implemented yet. Repeating the same HTTP request still returns `CHANGED`. The service can return `UNCHANGED` only when a previous hash is supplied internally by a caller. The next milestone is transactional PostgreSQL persistence.

The architecture image and design PDF describe the target platform; references to the original Python stack are historical. The [Java migration decision](docs/architecture/0001-java-spring-migration.md) records the current implementation and tradeoffs.

## Local prerequisites

- JDK 21 (build target and CI runtime; the code also builds with a compatible newer JDK)
- Git
- Internet access for the first Maven wrapper/dependency download
- Docker Desktop for PostgreSQL or container execution
- Google Cloud CLI only for later GCP milestones

Maven does not need a separate installation. Run `./mvnw` on macOS/Linux or `mvnw.cmd` on Windows. Check `java -version` and `./mvnw --version` if Java or Maven startup fails. Point `JAVA_HOME` to your chosen JDK when multiple installations exist.

## Start locally

From the checkout root:

```bash
./mvnw spring-boot:run
```

If port `8000` is occupied by the earlier Python process, stop that process or run with `PORT=8001 ./mvnw spring-boot:run` and use the corresponding port in your URLs.

Open [interactive API documentation](http://localhost:8000/docs). The OpenAPI document is at [openapi.json](http://localhost:8000/openapi.json).

```bash
curl http://localhost:8000/health/live
curl -X POST http://localhost:8000/v1/documents/text \
  -H 'Content-Type: application/json' \
  -d '{"external_id":"runbook","title":"Runbook","text":"Cloud Run hosts the API.","source_version":"1","allowed_groups":["support"]}'
```

Response fields retain the Python API's snake_case names: `status`, `content_hash`, `chunk_count`, and `chunk_ids`. Nonempty text returns `CHANGED`; empty or whitespace-only text returns `EMPTY` with no chunks. Missing required fields or malformed JSON return a sanitized HTTP 422 response. Omitted or null `source_version` defaults to `1`; omitted or null `allowed_groups` defaults to an empty list. Unknown request fields are ignored. PDF and DOCX parsers are not exposed as upload endpoints yet.

To prepare the database for the next milestone:

```bash
docker compose up -d postgres
docker compose ps
docker compose exec postgres psql -U akh -d akh
```

The current API does not require a running database. Compose only starts PostgreSQL; it does not start the Java application.

## Configuration

Spring reads environment variables; it does **not** automatically load `.env`. `.env.example` documents current and reserved settings. Set them through your shell, IDE, or container configuration. For example:

```bash
PORT=8080 CHUNK_SIZE_WORDS=550 CHUNK_OVERLAP_WORDS=90 ./mvnw spring-boot:run
```

Defaults are port `8000`, chunk size `550` words, and overlap `90` words. Invalid chunk configuration fails startup. Word counts are not model token counts. Database and GCP settings in the example file are reserved and unused by this baseline. Never commit real credentials.

## Validate

```bash
./mvnw spotless:apply
./mvnw verify
git diff --check
```

`verify` compiles Java, runs unit and HTTP integration tests, packages the executable JAR, and checks Java formatting. Tests use synthetic documents and need neither PostgreSQL nor GCP. CI runs the same verification on JDK 21. PostgreSQL integration tests will be introduced with persistence.

## Package or run in Docker

```bash
./mvnw verify
java -jar target/agentic-knowledge-hub-0.1.0.jar
```

The multi-stage Dockerfile builds with JDK 21 and runs with a non-root JRE 21 user:

```bash
docker build -t agentic-knowledge-hub:local .
docker run --rm -p 8000:8080 agentic-knowledge-hub:local
```

The container defaults to `PORT=8080`, accepts an override, and listens on all interfaces. This prepares the HTTP application for Cloud Run's container model; deployment, IAM, secrets, and production readiness remain later milestones.

## Security and current limitations

Use only synthetic or public content in this portfolio repository. Never add HP, Ascendion, AAVA, customer, credential or personally identifiable data. The local API is unauthenticated and must not be exposed as an enterprise service before authorization and input/resource limits are implemented.

Retrieved content is untrusted data. Citation validation checks IDs, not factual entailment or citation coverage. Parser anchors identify source sections; overlapping chunks inherit those section-level anchors. PDF layout extraction may differ from the former Python parser, and OCR, DOCX tables, and embedded objects are not supported. Chunk IDs preserve the baseline algorithm and are not yet source/tenant scoped; that must be addressed before multi-source persistence.

## Design

- [High-level design](docs/high-level-design.pdf)
- [Complete architecture](docs/complete-architecture.png)
- [Java migration decision and compatibility boundaries](docs/architecture/0001-java-spring-migration.md)

## Learning notes

- [Technical learning index](docs/learning/README.md)
- [Java, Spring, and senior-architect learning path](docs/learning/java_spring_architect_info.md)
- [Docker reference](docs/learning/docker_info.md)
- [Git and GitHub reference](docs/learning/git_github_info.md)

The former Python implementation is preserved in Git history at commit `ba9447f`; it is no longer the active build. Do not use `pip`, `uvicorn`, or Python test commands to run this Java version.
