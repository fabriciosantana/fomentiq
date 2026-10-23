# Domain Model — Fomentiq

## Bounded contexts iniciais

1. Funding Catalog
2. Source Ingestion
3. Document Evidence
4. Extraction & Validation

Matching, Proposal e Execution ficam reservados para fases posteriores.

## Entidades principais

### FundingAgency
Representa órgão/agência financiadora.

Campos principais:
- id
- name
- acronym
- officialUrl

### FundingProgram
Programa de financiamento persistente que pode originar várias chamadas.

- id
- agencyId
- name
- description
- instrumentTypes

### CallForProposals
A chamada específica.

- id
- programId nullable
- agencyId
- externalId
- title
- summary
- instrumentType
- status
- publicationDate
- openingDate
- deadline
- officialUrl
- detectedAt
- lastCheckedAt

### CallVersion
Snapshot lógico da chamada em determinado instante.

- id
- callId
- versionNumber
- contentHash
- detectedAt
- supersedesVersionId nullable

### CallDocument
Documento lógico associado à chamada.

Tipos iniciais:
- NOTICE
- ANNEX
- FAQ
- AMENDMENT
- RESULT_PRELIMINARY
- RESULT_FINAL
- OTHER

### CallDocumentVersion
Versão binária/textual de um documento.

- id
- documentId
- contentHash
- storageKey
- mediaType
- sourceUrl
- downloadedAt
- extractedTextKey nullable

### ExtractedField
Valor estruturado com proveniência.

- id
- callVersionId
- fieldName
- normalizedValue
- rawValue
- validationStatus
- extractionMethod
- createdAt (UTC, obrigatório)
- ingestionRunId (FK obrigatória para IngestionRun)
- confidence nullable

Cada campo pertence à execução que o produziu, inclusive quando `MISSING`. Mudanças de valor ou estado geram novo registro auditável e preservam o anterior; repetições idênticas reutilizam o registro sem alterar sua execução de origem. `Evidence` continua ligada ao registro específico e à fonte preservada.

### Evidence
Evidência associada a um campo.

- id
- extractedFieldId
- documentVersionId nullable
- sourceUrl
- page nullable
- section nullable
- quoteFragment nullable
- locatorJson

### IngestionRun
Execução de coleta, extração e validação, agendada ou iniciada por comando interno. Reprocessamentos criam uma execução própria. Um `IngestionRun` pode produzir muitos `ExtractedField`; cada campo referencia exatamente uma execução. Não há `ExtractionRun` separado no primeiro slice.

- id
- sourceType
- startedAt
- finishedAt
- status
- detectedCount
- changedCount
- errorSummary

## Value Objects

- Money
- DateTimeRange
- TrlRange
- GeographicScope
- EligibilityRule
- CounterpartRule
- TechnologyTheme
- EvidenceLocator

## Entidades futuras reservadas

### Organization
Perfil da empresa/ICT.

### InnovationProfile
Características usadas para matching.

### OpportunityMatch
Resultado explicável de aderência.

### Proposal
Processo de preparação para uma chamada.

### ComplianceRequirement
Requisito da chamada aplicado à proposta.

### FundedProject
Projeto contratado/aprovado.

### WorkPackage / Deliverable / Expense / EvidenceItem
Execução e prestação de contas.

## Regras de identidade

- `FundingProgram` não deve ser confundido com `CallForProposals`.
- Uma chamada pode existir sem programa conhecido.
- `externalId + agency` deve ser preferido como chave natural quando estável.
- Hash nunca substitui ID de domínio; serve para detecção de mudança.
