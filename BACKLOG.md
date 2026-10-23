# Initial Backlog — Fomentiq

## Epic 0 — Foundation

### B-001 Repository bootstrap
- monorepo `backend/`, `frontend/`, `docs/`, `openspec/`;
- Docker Compose;
- CI;
- lint/test/build;
- health endpoints.

### B-002 Database foundation
- PostgreSQL;
- Flyway;
- entidades iniciais;
- Testcontainers.

### B-003 Object storage
- interface de armazenamento;
- MinIO local;
- hash SHA-256;
- metadata.

## Epic 1 — Finep ingestion

### B-101 Source connector contract
Definir interface mínima de conectores.

### B-102 Finep discovery
Detectar lista configurada de chamadas.

### B-103 Finep call fetch
Capturar página e metadados brutos.

### B-104 Document discovery
Descobrir links de edital/anexos/retificações.

### B-105 Document download
Baixar, armazenar, hashear e deduplicar.

### B-106 Version detection
Criar versão quando conteúdo mudar.

### B-107 Ingestion observability
Registrar runs e falhas.

## Epic 2 — Structured extraction

### B-201 Extraction schema
Schema tipado dos campos MVP.

### B-202 Provider abstraction
`StructuredExtractionProvider`.

### B-203 Evidence extraction
Associar campo a locator documental.

### B-204 Deterministic validation
Validações de datas, dinheiro, TRL e presença de evidência.

### B-205 Conflict handling
Marcar conflito sem sobrescrever silenciosamente.

## Epic 3 — API

### B-301 List calls
Paginação, ordenação e filtros.

### B-302 Call details
Resumo + campos + evidências.

### B-303 Documents endpoint
Documentos e versões.

### B-304 History endpoint
Histórico da chamada.

### B-305 OpenAPI
Documentar e testar contrato.

## Epic 4 — Frontend

### B-401 Opportunity catalog
Lista responsiva.

### B-402 Filters
Status, prazo, instrumento, região, tema.

### B-403 Opportunity detail
Cards e requisitos.

### B-404 Evidence viewer
Abrir fonte/trecho relacionado.

### B-405 Version history
Timeline de mudanças.

## Epic 5 — Verification

### B-501 Golden fixture
Snapshot versionado de uma chamada Finep para testes.

### B-502 End-to-end ingestion test
Fixture → DB → API.

### B-503 UI e2e
Catálogo → detalhe → evidência.

### B-504 Demo script
Roteiro reproduzível para cliente.

## Ordem sugerida

`B-001 → B-002 → B-003 → B-101 → B-102 → B-103 → B-104 → B-105 → B-106 → B-107 → B-201...`
