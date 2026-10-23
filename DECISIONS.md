# Decisions

## D-001 — Produto não é apenas agregador
**Decisão:** construir inteligência e gestão do ciclo de fomento, com dados estruturados, versionados e rastreáveis; o MVP prova essa tese com uma fonte e não implementa o ciclo completo.

## D-002 — MVP restrito à Finep
**Decisão:** uma fonte, vertical slice completo.

## D-003 — Monólito modular
**Decisão:** evitar microserviços até haver necessidade mensurada.

## D-004 — Java/Spring Boot no core
**Decisão:** domínio, API, persistência e jobs no backend Java.

## D-005 — React/TypeScript no frontend
**Decisão:** SPA responsiva para primeira versão.

## D-006 — PostgreSQL como fonte de verdade
**Decisão:** usar relacional para domínio e regras; pgvector como extensão, não como banco principal.

## D-007 — Object storage preserva evidências
**Decisão:** arquivos externos devem ser copiados e versionados quando juridicamente/tecnicamente permitido.

## D-008 — Proveniência obrigatória
**Decisão:** prazo, valor, elegibilidade, contrapartida, TRL e ICT não podem ser publicados sem evidência.

## D-009 — IA desacoplada
**Decisão:** OpenAI será o provider inicial da demonstração, implementado por adaptador de `StructuredExtractionProvider`, com configuração exclusivamente por variáveis de ambiente. O domínio permanece independente do fornecedor. Testes de regressão usam provider determinístico, sem chamadas externas.

## D-010 — Agentic engineering governada
**Decisão:** OpenSpec para mudanças; AGENTS.md curto; skills para workflows repetíveis; implementação somente após spec e tasks.


## D-011 — Nome oficial do produto

**Decisão:** Nome oficial: **Fomentiq**.

**Descriptor:** Plataforma de Inteligência e Gestão de Fomento à Inovação.

**Racional:** o nome mantém associação semântica com fomento e inteligência, sem limitar o produto a editais ou a uma única etapa do ciclo de financiamento da inovação.

## D-012 — Chamada de referência Finep
**Decisão:** usar PROINFRA 2024 Expansão, ID Finep `751`, como golden fixture. Identidade, fontes e contrato de congelamento estão em [FINEP_GOLDEN_FIXTURE.md](FINEP_GOLDEN_FIXTURE.md). A seleção está definida; o congelamento dos artefatos ainda está pendente e bloqueia a implementação do parser.

## D-013 — Visibilidade do repositório
**Estado:** concluída pelo proprietário, conforme confirmação em 2026-10-23.

**Histórico:** em 2026-10-23, a consulta inicial via `gh repo view` retornou `PUBLIC` para [fabriciosantana/fomentiq](https://github.com/fabriciosantana/fomentiq). Posteriormente, o proprietário informou que alterou a visibilidade para privado.

**Decisão:** manter o repositório privado. A mudança de visibilidade não desfaz cópias anteriores. Não incluir dados de clientes ou segredos mesmo após a mudança de visibilidade.

**Verificação:** a consulta posterior via `gh repo view fabriciosantana/fomentiq --json nameWithOwner,visibility,url` não conseguiu resolver o repositório com o acesso disponível. A conclusão está baseada na confirmação do proprietário, não em verificação independente da visibilidade pela API. Não há autorização ou execução pendente desta decisão.

## D-014 — Auditoria de extração
**Decisão:** cada `ExtractedField` registra `createdAt` UTC e `ingestionRunId` obrigatório, além de método, versão da chamada e evidências. Reprocessamentos que mudam valores ou estados preservam os registros anteriores. `IngestionRun` abrange coleta, extração e validação no primeiro slice; não criar `ExtractionRun` separado agora.

## D-015 — Autenticação condicionada à administração exposta
**Decisão:** primeiro slice expõe apenas leitura do catálogo e opera ingestão por scheduler ou comando interno. Não inclui login, área administrativa ou endpoint HTTP de disparo/reprocessamento. Autenticação será obrigatória antes de expor operações administrativas em mudança futura aprovada.
