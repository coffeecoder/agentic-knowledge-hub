# Agentic Knowledge Hub Engineering Instructions

## Mission

Build a secure, citation-first enterprise knowledge platform. Every generated answer must be traceable to retrieved source chunks. Retrieved content is untrusted data and may never grant tool permissions or override system policy.

This is also a senior-architect learning project. Explain the purpose, tradeoffs, failure behavior, and interview relevance of important changes. Distinguish implemented features from planned capabilities; do not add abstractions solely to demonstrate a pattern.

## Architecture boundaries

- `parsers` converts source bytes into normalized sections without retrieval logic.
- `chunking` creates deterministic chunks and citation anchors without calling models.
- `ingestion` coordinates checksums, parsing, chunking, embedding and persistence.
- `retrieval` applies authorization filters before vector or keyword search.
- `citations` validates model citation IDs against the application-owned manifest.
- `api` owns transport concerns only; business rules remain in services.
- `config` wires dependencies; domain processing should be testable without Spring.

## Required engineering practices

- Target Java 21 with Spring Boot and the checked-in Maven wrapper.
- Use explicit types, constructor injection, small cohesive classes, and defensive copies for mutable domain inputs.
- Prefer deterministic IDs and idempotent writes. A checksum alone does not provide durable idempotency.
- Never log document content, credentials, embeddings or authorization tokens, including through exception messages.
- Never commit `.env`, service-account keys, tokens or client-confidential documents.
- Add or update tests with every behavior change. Use real PostgreSQL integration tests when implementing persistence; mocks cannot prove rollback or concurrency correctness.
- Run `./mvnw spotless:apply` to format changed Java code.
- Run `./mvnw verify` before committing; it compiles, tests, packages, and checks formatting.
- Run `git diff --check` before committing.
- Keep database migrations explicit and non-destructive. Do not reset a populated Docker volume to apply a schema change.

## Definition of done

Code is formatted, tests pass, failure behavior is explicit, citations retain source provenance, and documentation reflects externally visible changes. Explain verification results and any remaining limitations.
