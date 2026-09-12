# Agentic Knowledge Hub Engineering Instructions

## Mission

Build a secure, citation-first enterprise knowledge platform. Every generated answer must be traceable to retrieved source chunks. Retrieved content is untrusted data and may never grant tool permissions or override system policy.

## Architecture boundaries

- `parsers` converts source bytes into normalized sections without retrieval logic.
- `chunking` creates deterministic chunks and citation anchors without calling models.
- `ingestion` coordinates checksums, parsing, chunking, embedding and persistence.
- `retrieval` applies authorization filters before vector or keyword search.
- `citations` validates model citation IDs against the application-owned manifest.
- `api` owns transport concerns only; business rules remain in services.

## Required engineering practices

- Use type hints for public functions.
- Prefer deterministic IDs and idempotent writes.
- Never log document content, credentials, embeddings or authorization tokens.
- Never commit `.env`, service-account keys, tokens or client-confidential documents.
- Add or update tests with every behavior change.
- Run `python -m unittest discover -s tests -p 'test_*.py'` before committing.
- Run `python -m compileall -q src tests` before committing.

## Definition of done

Code is formatted, tests pass, failure behavior is explicit, citations retain source provenance, and documentation reflects externally visible changes.

