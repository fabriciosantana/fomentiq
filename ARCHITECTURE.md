# Architecture — Fomentiq

## 1. Estilo arquitetural

**Monólito modular** para o MVP.

Motivos:
- equipe inicial pequena;
- domínio ainda em descoberta;
- transações e consistência mais simples;
- menor custo operacional;
- possibilidade de extrair serviços quando houver evidência de necessidade.

## 2. Visão

```text
React SPA
   |
   v
Spring Boot API
   |
   +-- Funding Catalog Module
   +-- Ingestion Module
   +-- Documents Module
   +-- Extraction Module
   +-- Search Module
   |
   +-- PostgreSQL
   +-- pgvector (opcional no MVP, habilitado na infra)
   +-- S3-compatible object storage
   +-- Scheduler / background jobs
```

## 3. Backend

### Java / Spring Boot

Sugestão:
- Java 25 LTS ou versão LTS adotada pela equipe no bootstrap;
- Spring Boot estável atual;
- Spring Web;
- Spring Data JPA;
- Flyway;
- Bean Validation;
- Spring Security quando houver operações administrativas expostas (fora do primeiro slice);
- Actuator;
- Testcontainers.

### Organização

```text
backend/src/main/java/.../
  funding/
  ingestion/
  documents/
  extraction/
  search/
  shared/
```

Regra: módulos se comunicam por interfaces de aplicação, não por acesso arbitrário a repositórios internos.

## 4. Frontend

- React + TypeScript.
- Vite como build tool, salvo decisão posterior documentada.
- React Router.
- TanStack Query para estado servidor.
- Biblioteca de componentes acessível e consistente.
- Testes unitários e de componentes.
- Playwright para fluxo crítico e2e.

## 5. Persistência

### PostgreSQL
Fonte de verdade para dados estruturados.

### Object storage
Preserva:
- HTML bruto;
- PDFs;
- anexos;
- texto extraído;
- artefatos intermediários relevantes.

Nunca depender do link externo como única cópia de evidência.

## 6. Busca

MVP:
- full-text PostgreSQL quando suficiente.

Evolução:
- pgvector para similaridade e RAG.

Não introduzir Elasticsearch/OpenSearch no MVP sem requisito mensurado.

## 7. Extração por IA

### Princípio

`LLM interpreta; regra valida; fonte comprova.`

A camada de IA deve receber documento + schema e retornar saída tipada.

A aplicação deve:
- validar tipos;
- validar limites plausíveis;
- exigir evidência para campos críticos;
- marcar conflito;
- preservar raw output para auditoria técnica quando apropriado.

### Abstração

Criar interface `StructuredExtractionProvider`.

O domínio não conhece OpenAI, Anthropic ou outro fornecedor.

### Provider inicial

OpenAI será a implementação real inicial, via `OpenAiStructuredExtractionProvider` na infraestrutura. Credenciais, modelo e limites operacionais são lidos exclusivamente do ambiente: `OPENAI_API_KEY`, `OPENAI_MODEL`, `EXTRACTION_TIMEOUT_SECONDS` e `EXTRACTION_MAX_RETRIES`. Estes dois últimos nomes são configuração da aplicação. O modelo é definido na configuração da demonstração e deve suportar o schema de saída escolhido; não há modelo ou chave hardcoded. Registrar identificador do modelo, versão do schema e correlação com `IngestionRun` nos metadados técnicos, nunca a chave.

Configuração ausente/inválida ou falha da API deve falhar explicitamente a extração, preservando os artefatos brutos; não substituir por valores simulados. Testes automatizados usam provider determinístico, sem acesso à OpenAI. A demonstração real é uma verificação separada e requer ambiente configurado.

Cada `ExtractedField` persiste `createdAt` UTC e `ingestionRunId` obrigatório, inclusive nos reprocessamentos. Valores/estados anteriores e respectivas evidências são preservados; repetições idênticas não duplicam fatos nem alteram a execução de origem.

## 8. Ingestão

Adapters por fonte:

```text
SourceConnector
  discover()
  fetchCall()
  discoverDocuments()
  fetchDocument()
```

Primeiro adapter: `FinepSourceConnector`, com referência PROINFRA 2024 Expansão (ID `751`). O [contrato da golden fixture](FINEP_GOLDEN_FIXTURE.md) define a captura obrigatória antes do parser.

Evitar framework genérico de scraping antes de entender pelo menos 2–3 fontes.

## 9. Jobs

No MVP:
- scheduler Spring;
- lock distribuído preparado para múltiplas instâncias;
- registro `IngestionRun`.

Evoluir para fila/worker dedicado somente se volume ou latência justificar.

## 10. Segurança

MVP:
- primeiro slice: leitura do catálogo e ingestão agendada/interna, sem login ou endpoint administrativo;
- autenticação obrigatória antes de expor operações administrativas em mudança futura;
- não expor endpoints operacionais de escrita nem Actuator administrativo sem proteção;
- secrets via ambiente;
- nenhuma credencial no Git;
- validação de downloads;
- limites de tamanho;
- proteção contra SSRF ao buscar URLs descobertas;
- sanitização de HTML.

Futuro multi-tenant:
- isolamento por tenant;
- RBAC;
- trilha de auditoria.

## 11. Observabilidade

- logs JSON estruturados;
- correlation ID;
- métricas de ingestão;
- métricas de extração;
- taxa de falhas por fonte;
- Actuator health;
- tracing quando houver ambiente distribuído.

## 12. Dev environment

Docker Compose deve fornecer:
- PostgreSQL + pgvector;
- MinIO (ou equivalente S3 local);
- backend;
- frontend opcionalmente em modo container, sem prejudicar hot reload local.

## 13. CI

Pull request:
- backend format/lint;
- backend tests;
- frontend lint/typecheck/tests;
- OpenSpec validate;
- build completo;
- testes e2e do fluxo crítico quando aplicável.

## 14. ADRs

Decisões arquiteturais relevantes devem ser registradas em `docs/adr/`.
