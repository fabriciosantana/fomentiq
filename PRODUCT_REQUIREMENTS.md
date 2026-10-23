# Product Requirements Document (PRD) — Fomentiq

## 1. Objetivo do MVP

Validar que a **Fomentiq** consegue monitorar a Finep e converter uma chamada pública real em informação estruturada, versionada, pesquisável e rastreável.

## 2. Requisitos funcionais MVP

### RF-01 — Catálogo de oportunidades
O sistema deve listar chamadas detectadas da Finep com status, título, programa, prazo e instrumento.

### RF-02 — Detecção de chamada
O sistema deve identificar uma chamada nova a partir de fonte configurada.

### RF-03 — Snapshot de fonte
O sistema deve preservar o conteúdo bruto relevante usado na detecção.

### RF-04 — Documentos
O sistema deve identificar, baixar e registrar edital, anexos, FAQ, retificações e resultados quando vinculados à chamada.

### RF-05 — Versionamento
O sistema deve detectar alteração de conteúdo e criar nova versão sem sobrescrever a anterior.

### RF-06 — Extração estruturada
O sistema deve extrair, quando disponíveis:
- órgão;
- programa;
- instrumento;
- título;
- descrição;
- status;
- abertura;
- prazo;
- orçamento total;
- faixa por projeto;
- público elegível;
- regiões;
- TRL;
- necessidade de ICT;
- contrapartida;
- áreas tecnológicas;
- despesas elegíveis;
- critérios de avaliação;
- documentos exigidos;
- links oficiais.

### RF-07 — Proveniência
Cada campo crítico extraído deve guardar referência à evidência de origem.

Campos críticos no MVP:
- prazo;
- orçamento;
- elegibilidade;
- contrapartida;
- TRL;
- necessidade de ICT.

### RF-08 — Estado de validação
Campos críticos devem possuir estado: `EXTRACTED`, `VALIDATED`, `CONFLICT`, `MISSING`.

### RF-09 — Busca e filtros
Usuário deve pesquisar por texto e filtrar por status, prazo, instrumento, região e tema.

### RF-10 — Página de detalhe
A página deve apresentar resumo, requisitos, documentos, histórico de versões e proveniência.

### RF-11 — Link para fonte
A interface deve permitir abrir a página/documento original.

### RF-12 — Auditoria mínima
Mudanças de dados estruturados devem possuir timestamp, origem e processo responsável. Cada `ExtractedField`, inclusive com estado `MISSING`, deve registrar `createdAt` em UTC e `ingestionRunId` obrigatório da execução que o produziu, além de `callVersionId`, `extractionMethod` e evidências quando houver valor extraído. A execução abrange coleta, extração e validação, inclusive reprocessamento interno. Mudanças de valor ou estado preservam o registro anterior; repetição idêntica não duplica fatos nem troca sua autoria original.

## 3. Requisitos não funcionais

### RNF-01 — Rastreabilidade
Nenhum campo crítico exibido como fato pode existir sem fonte registrada.

### RNF-02 — Idempotência
Reprocessar a mesma versão não pode criar duplicidades.

### RNF-03 — Segurança
Segredos não podem ser versionados. Acesso administrativo deve exigir autenticação quando operações administrativas forem expostas. O primeiro slice oferece leitura do catálogo e ingestão agendada/interna, sem endpoint administrativo, login ou interface de administração. Expor disparo de ingestão, reprocessamento ou edição exige mudança aprovada com autenticação antes da exposição.

### RNF-04 — Testabilidade
Parser, normalização, persistência e API devem ter testes automatizados.

### RNF-05 — Observabilidade
Jobs devem registrar início, término, resultado, duração e erro.

### RNF-06 — Portabilidade
Ambiente local deve subir por Docker Compose.

### RNF-07 — Evolução
Domínio não deve depender diretamente de um provedor específico de LLM.

## 4. Regras de negócio MVP

- Uma oportunidade pertence a um `FundingProgram` quando a relação é conhecida.
- Uma `CallForProposals` pode possuir múltiplos documentos e versões.
- Documento novo com conteúdo diferente gera `CallDocumentVersion`.
- Retificação não apaga o edital original.
- Resultado não altera retroativamente os requisitos originais.
- Dados conflitantes devem ser marcados e não silenciosamente reconciliados.

## 5. Critérios de aceite do MVP

- Chamada PROINFRA 2024 Expansão (Finep ID `751`) ingerida ponta a ponta, conforme [contrato da golden fixture](FINEP_GOLDEN_FIXTURE.md); regressão offline e demonstração real são verificações separadas.
- Pelo menos dois documentos associados quando existirem na fonte.
- Alteração simulada gera versão nova.
- Prazo e orçamento exibem evidência clicável.
- API documentada via OpenAPI.
- UI responsiva e funcional.
- Pipeline automatizado de testes passa.
