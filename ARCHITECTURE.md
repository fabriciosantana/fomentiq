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

Antes do provider, PDFs preservados são convertidos em texto por página e um seletor prioriza rerratificações, edital e comunicados, aplicando limites de documentos, trechos e caracteres. O provider recebe somente os trechos selecionados; cada citação precisa apontar para um segmento enviado e conter uma passagem literal desse segmento.

### Abstração

Criar interface `StructuredExtractionProvider`.

O domínio não conhece OpenAI, Anthropic ou outro fornecedor.

### Provider inicial

OpenAI será a implementação real inicial, via `OpenAiStructuredExtractionProvider` na infraestrutura, utilizando Responses API e Structured Outputs com JSON Schema estrito. Credenciais, modelo e limites operacionais são lidos exclusivamente do ambiente: `OPENAI_API_KEY`, `OPENAI_MODEL`, `EXTRACTION_REASONING_EFFORT`, `EXTRACTION_TIMEOUT_SECONDS` e `EXTRACTION_MAX_RETRIES`. Os defaults são modelo `gpt-6-luna` e reasoning effort `low`; o modelo é configurável e não fica hardcoded no adapter. Registrar identificador do modelo, versão do schema, tokens de entrada/saída e correlação com `IngestionRun` nos metadados técnicos, nunca a chave.

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

Primeiro adapter: `FinepSourceConnector`, com referência PROINFRA 2024 Expansão (ID `751`). O [contrato da golden fixture](FINEP_GOLDEN_FIXTURE.md) documenta a captura concluída antes do parser. No novo portal, o registro `751092` mantém `databaseId=751`; preservar essa identidade. A lista documental é carregada por API: preservar respostas JSON usadas na descoberta, além dos PDFs. O endpoint documental atualmente depende do fluxo OAuth público anunciado pela página; o adaptador o obtém em runtime, não persiste credenciais/tokens e falha explicitamente se o contrato mudar. Testes usam snapshots saneados e respostas locais; não dependem de acesso Finep ou OpenAI.

Evitar framework genérico de scraping antes de entender pelo menos 2–3 fontes.

### Fronteiras transacionais

O orquestrador de ingestão não mantém uma transação abrangente. Um componente Spring separado inicia e finaliza `IngestionRun` em transações `REQUIRES_NEW`, e cada chamada descoberta é persistida em sua própria unidade transacional `REQUIRES_NEW`. Falhas SQL propagam para fora da unidade, que sofre rollback antes de o orquestrador gravar o status `FAILED`; erros de documento tratados como recuperáveis podem produzir `PARTIAL`. Assim uma falha numa chamada não deixa suas escritas relacionais parcialmente confirmadas nem impede a gravação independente do resultado da execução.

O PostgreSQL e o object storage S3-compatible não compartilham transação. Um objeto cujo upload terminou antes de um rollback SQL pode ficar órfão; conteúdo content-addressed permite reutilizá-lo em retry. O MVP não faz compensação nem limpeza automática desses objetos.

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
- armazenamento local S3-compatible: RustFS no ambiente atual; MinIO ou outro equivalente continua permitido pela arquitetura;
- backend;
- frontend opcionalmente em modo container, sem prejudicar hot reload local.

Configuração específica do serviço (imagem, endpoint e credenciais) permanece na infraestrutura/Compose. A aplicação deve usar a abstração S3-compatible e não depender de APIs proprietárias do RustFS, permitindo substituí-lo sem alterar o domínio.

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
