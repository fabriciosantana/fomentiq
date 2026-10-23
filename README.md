# Fomentiq — Bootstrap Pack

Este pacote contém a definição inicial de produto e engenharia da **Fomentiq — Plataforma de Inteligência e Gestão de Fomento à Inovação**.

## Identidade

**Fomentiq** — Plataforma de Inteligência e Gestão de Fomento à Inovação.

Repositório recomendado: `fomentiq`.

## Ordem de leitura

1. `PROJECT_CONTEXT.md`
2. `PRODUCT_VISION.md`
3. `PERSONAS.md`
4. `JOBS_TO_BE_DONE.md`
5. `USER_JOURNEYS.md`
6. `PRODUCT_REQUIREMENTS.md`
7. `MVP_SCOPE.md`
8. `DOMAIN_MODEL.md`
9. `ARCHITECTURE.md`
10. `DECISIONS.md`
11. `BACKLOG.md`
12. `AGENTS.md`
13. `BOOTSTRAP_PROMPT.md`

## OpenSpec

O pacote já contém o primeiro change:

`openspec/changes/finep-opportunity-ingestion/`

Fluxo esperado:

```text
explore → propose → apply → verify → archive
```

Antes da implementação, instale/inicialize OpenSpec conforme a versão usada no ambiente e valide o change preparado. Não sobrescreva os arquivos sem comparar o que o comando de inicialização pretende gerar.

## Skills Codex

Skills repo-local em `.agents/skills/`:

- `product-discovery`
- `spec-driven-change`
- `code-change-verification`

## Primeiro objetivo de desenvolvimento

Demonstrar uma chamada Finep ingerida ponta a ponta, com documentos, versionamento, extração estruturada, proveniência, API e interface web.

## Decisões para o primeiro slice

- Golden fixture: [PROINFRA 2024 Expansão — Finep 751](FINEP_GOLDEN_FIXTURE.md); pacote congelado com 18 PDFs, HTMLs saneados, JSONs oficiais, hashes e valores de referência; acesso recuperado no novo portal (registro `751092`).
- Extração: OpenAI atrás de `StructuredExtractionProvider`, configurada por ambiente; regressão offline com provider determinístico.
- Auditoria: `ExtractedField.createdAt` e referência obrigatória a `IngestionRun`.
- Catálogo de leitura e ingestão agendada/interna, sem login ou endpoint administrativo.
- Visibilidade: repositório privado conforme confirmação do proprietário em 2026-10-23; decisão concluída, conforme [D-013](DECISIONS.md#d-013--visibilidade-do-repositório).
