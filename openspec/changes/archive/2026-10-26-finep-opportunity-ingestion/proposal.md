# Proposal: Finep Opportunity Ingestion

## Why

O MVP precisa provar a tese central do produto: uma oportunidade de fomento deve ser automaticamente detectada, preservada, transformada em dados estruturados e exibida com proveniência verificável.

A Finep será a primeira fonte porque oferece chamadas relevantes para PD&I empresarial e possui variedade suficiente de páginas, editais, anexos e retificações para validar o modelo.

## What changes

Esta mudança adiciona o primeiro vertical slice do produto:

- contrato mínimo para conectores de fonte;
- conector Finep para descoberta e captura de uma chamada configurada;
- armazenamento bruto de página e documentos;
- hash e versionamento;
- persistência de chamada, versões e documentos;
- extração estruturada dos campos MVP com adaptador inicial OpenAI configurado por ambiente;
- auditoria de campos por timestamp e execução de ingestão;
- proveniência para campos críticos;
- validação determinística básica;
- API de catálogo e detalhe;
- UI com lista, detalhe, documentos e evidências.

## Capabilities

- `opportunity-ingestion`

## Out of scope

- crawling irrestrito de todo o site da Finep;
- matching com empresas;
- Proposal Copilot;
- compliance da proposta;
- execução financeira;
- múltiplas agências;
- multi-tenancy comercial;
- login, área administrativa e endpoints HTTP de disparo/reprocessamento;
- marcações de acompanhamento/qualificação e observações pessoais.

## Success

A chamada PROINFRA 2024 Expansão (Finep ID `751`) deve completar o fluxo fonte → armazenamento → extração → banco → API → UI, sem perda de proveniência dos campos críticos. A regressão usa o pacote congelado definido em [FINEP_GOLDEN_FIXTURE.md](../../../FINEP_GOLDEN_FIXTURE.md), com provider determinístico; uma execução real separada verifica Finep e OpenAI. A captura foi concluída em 2026-10-23 no novo portal (registro `751092`, ID histórico `751`), incluindo HTMLs saneados, respostas oficiais de API e 18 PDFs. O parser e os testes da aplicação permanecem por implementar.
