# Agentic Knowledge Hub

Agentic Knowledge Hub is a citation-first knowledge platform for developers, architects, business analysts, QA engineers, production-support teams and AI agents. It ingests heterogeneous content, detects changes, creates provenance-rich chunks, stores embeddings, retrieves only authorized evidence and generates grounded answers.

![Complete architecture](docs/complete-architecture.png)

## Phase 1 scope

- Local PostgreSQL 16 with pgvector
- PDF, DOCX, Markdown and plain-text parsing
- Deterministic semantic chunking
- Source checksums and version-aware document models
- Citation metadata for page, heading and line anchors
- FastAPI service skeleton
- Database schema and unit tests

GitHub, Wiki and SharePoint connectors, Vertex AI embeddings, hybrid retrieval and Cloud Run deployment follow in later milestones.

## Local prerequisites

- Python 3.11 or newer
- Docker Desktop
- Git
- Google Cloud CLI for later GCP phases

## Start locally

```bash
cp .env.example .env
docker compose up -d postgres
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
uvicorn akh.api.main:app --reload
```

Open `http://localhost:8000/docs` and test `GET /health/live`.

## Validate

```bash
python -m unittest discover -s tests -p 'test_*.py'
python -m compileall -q src tests
```

## Security

Use only synthetic or public content in this portfolio repository. Never add HP, Ascendion, AAVA, customer, credential or personally identifiable data.

## Design

- [High-level design](docs/high-level-design.pdf)
- [Complete architecture](docs/complete-architecture.png)

## Learning notes

- [Technical learning index](docs/learning/README.md)
- [Docker reference](docs/learning/docker_info.md)
