# Sample Product Architecture

## Query service

The query API runs as a stateless Cloud Run service. It authenticates the caller before constructing retrieval filters.

## Authorization

Tenant, project and group predicates are applied before vector similarity search. Missing access-control metadata results in denial.

## Citations

The application creates a citation manifest from retrieved chunks. The language model may reference only citation identifiers present in that manifest.

