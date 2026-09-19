# Technical Learning Notes

This directory is a practical reference for technologies used by Agentic Knowledge Hub. Each note should explain the important concepts, the project's commands, troubleshooting steps, and interview points.

## Available notes

- [GCP Identity Platform and Spring Security](gcp_identity_security_info.md) — token validation, application permissions, publisher scope, and local sign-in

- [Configurable embeddings and semantic search](embeddings_semantic_search_info.md) — Vertex AI, Ollama, offline development, ADC, vector compatibility, transactions, troubleshooting, and live validation

- [Keyword search and authorization](search_authorization_info.md) — trusted identity, permission filters, PostgreSQL ranking, and retrieval tests

- [PostgreSQL, pgvector, and transactions](postgres_pgvector_info.md) — durable ingestion, migrations, source locking, rollback, and concurrency tests

- [Java, Spring, and senior-architect learning path](java_spring_architect_info.md) — code walkthrough, design principles, transaction exercises, GCP, and Agentic AI milestones

- [Docker reference](docker_info.md) — concepts, project commands, PostgreSQL access, macOS troubleshooting, and interview notes
- [Git and GitHub reference](git_github_info.md) — authentication, everyday commands, branches, pull requests, safe undo, security, and interview notes

## Planned notes

- `gcp_info.md` — projects, IAM, Cloud Run, Cloud SQL, Storage, and Pub/Sub
- `terraform_info.md` — infrastructure as code and GCP deployment
- `rag_agentic_ai_info.md` — ingestion, chunking, retrieval, citations, and agents

Keep the root `README.md` focused on running and understanding the application. Store reusable learning material here so it can grow without making the project overview difficult to navigate.

Suggested order: run the [Java project](../../README.md), read the Java/Spring learning path, configure Identity Platform, then work through configurable embeddings and semantic search. Use the Git, Docker and PostgreSQL references during each milestone. Python source is historical; the active implementation is Java.
