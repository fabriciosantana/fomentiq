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
**Decisão:** usar PROINFRA 2024 Expansão, ID Finep `751`, como golden fixture. Identidade, fontes e contrato de congelamento estão em [FINEP_GOLDEN_FIXTURE.md](FINEP_GOLDEN_FIXTURE.md). Em 2026-10-23, os artefatos foram recuperados no novo portal (registro `751092`, `databaseId=751`) e congelados com manifesto, 18 PDFs e referências de extração. Mantida a chamada; bloqueio de captura resolvido. HTMLs de teste são saneados para excluir scripts com credenciais, com transformação e hashes de origem/armazenamento documentados; JSONs e PDFs preservam os bytes recebidos.

## D-013 — Visibilidade do repositório
**Estado:** concluída pelo proprietário, conforme confirmação em 2026-10-23.

**Histórico:** em 2026-10-23, a consulta inicial via `gh repo view` retornou `PUBLIC` para [fabriciosantana/fomentiq](https://github.com/fabriciosantana/fomentiq). Posteriormente, o proprietário informou que alterou a visibilidade para privado.

**Decisão:** manter o repositório privado. A mudança de visibilidade não desfaz cópias anteriores. Não incluir dados de clientes ou segredos mesmo após a mudança de visibilidade.

**Verificação:** a consulta posterior via `gh repo view fabriciosantana/fomentiq --json nameWithOwner,visibility,url` não conseguiu resolver o repositório com o acesso disponível. A conclusão está baseada na confirmação do proprietário, não em verificação independente da visibilidade pela API. Não há autorização ou execução pendente desta decisão.

## D-014 — Auditoria de extração
**Decisão:** cada `ExtractedField` registra `createdAt` UTC e `ingestionRunId` obrigatório, além de método, versão da chamada e evidências. Reprocessamentos que mudam valores ou estados preservam os registros anteriores. `IngestionRun` abrange coleta, extração e validação no primeiro slice; não criar `ExtractionRun` separado agora.

## D-015 — Autenticação condicionada à administração exposta
**Decisão:** primeiro slice expõe apenas leitura do catálogo e opera ingestão por scheduler ou comando interno. Não inclui login, área administrativa ou endpoint HTTP de disparo/reprocessamento. Autenticação será obrigatória antes de expor operações administrativas em mudança futura aprovada.

## D-016 — Armazenamento S3-compatible no ambiente local
**Decisão:** RustFS é a implementação S3-compatible usada pelo ambiente local de desenvolvimento/MVP. A aplicação deve acessar object storage pela abstração e API S3-compatible, sem depender de APIs proprietárias ou tipos específicos do RustFS. Detalhes do endpoint e credenciais ficam na configuração de infraestrutura/Compose. RustFS pode ser substituído por MinIO, S3, R2 ou outro serviço compatível sem alterar o domínio; a troca deve exigir apenas a configuração e, se necessário, o adaptador de infraestrutura.

## D-017 — Sessão efêmera da API pública de documentos Finep
**Decisão:** o adaptador Finep lê da página oficial os parâmetros do cliente OAuth público, troca-os por bearer token com `client_credentials` e envia o token no header da API de documentos. Credenciais e token existem apenas em memória durante a execução; não são persistidos, logados ou copiados para configuração, fixture ou repositório. A aquisição e o uso ficam restritos à infraestrutura do conector Finep, em memória durante as chamadas HTTP que precisam do token. Os valores não aparecem em logs/exceções e não entram em snapshots, banco ou object storage. O fluxo público pode mudar; ausência ou mudança do contrato deve falhar explicitamente e deixar o `IngestionRun` auditável.

## D-018 — Extração estruturada limitada e auditável
**Decisão:** a implementação inicial usa OpenAI Responses API e Structured Outputs estritos atrás de `StructuredExtractionProvider`, com schema versionado `finep-call-v1`, modelo configurado por `OPENAI_MODEL` (default local `gpt-6-luna`) e reasoning effort configurado por `EXTRACTION_REASONING_EFFORT` (default `low`). Antes de chamar o modelo, a infraestrutura extrai texto de PDFs preservados e seleciona trechos com limites explícitos; não envia o corpus inteiro. Só aceita evidência que cite um segmento enviado e uma frase nele contida literalmente. Respostas brutas, modelo, versão do schema, tokens e `IngestionRun` são auditados sem credenciais. Testes usam exclusivamente fixture local e provider determinístico. `VALIDATED` depende de validação determinística e evidência documental; conflito não é reconciliado por recência genérica.

## D-019 — Transações independentes para auditoria de ingestão Finep
**Decisão:** iniciar e finalizar `IngestionRun` em transações PostgreSQL `REQUIRES_NEW`, por componente Spring separado do orquestrador. Cada chamada é processada em uma unidade de negócio transacional própria. Uma falha SQL sai dessa unidade; o rollback é concluído antes de registrar `FAILED` na transação independente do run. Falha recuperável de documento pode concluir a unidade com `PARTIAL`; falha explícita de extração preserva o registro técnico `FAILED` e encerra o run como `FAILED`. Não são adicionados estados nem schema.

**Limite:** PostgreSQL e object storage S3-compatible não participam de uma transação distribuída. Se o upload de um artefato for concluído e a unidade relacional falhar depois, o objeto content-addressed pode ficar sem referência relacional. O retry pode reutilizar a mesma chave; não há exclusão ou compensação automática.

## D-020 — Runner interno e fail-closed para validação live Finep
**Decisão:** uma validação live da referência Finep será iniciada somente por comando interno explícito com manifesto tipado e identificador de aprovação humana correspondente. Não haverá endpoint HTTP, execução automática no startup normal, scheduler novo ou serviço separado. O guard de isolamento deve executar antes do refresh do contexto Spring e, portanto, antes de Flyway/JPA; deve verificar o JDBC efetivo e a identidade do banco, endpoint/bucket S3-compatible e os recursos Compose realmente montados, recusando alvos ambíguos ou compartilhados. Os limites do manifesto são carregados por toda a aquisição e extração, com cancelamento/rejeição quando excedidos, sem aceitar bytes truncados.

**Limite financeiro (revisado por D-021):** o provider OpenAI não oferece um teto USD por requisição verificável no lado do servidor. O procedimento originalmente exigia esse hard cap; a decisão foi revista para uma validação experimental explicitamente autorizada, com orçamento aprovado, estimativa conservadora, uma tentativa sem retry automático e registro do uso/custo retornado. A execução live continua exigindo aprovação separada do manifesto exato.

## D-021 — Orçamento monitorado para validação experimental Finep/OpenAI
**Decisão:** para uma execução experimental única, não exigir um hard cap financeiro fornecido pelo provider. Exigir no manifesto um orçamento máximo aprovado pelo responsável, modelo/schema, limites rígidos de tokens, uma tentativa total, retries automáticos desabilitados, preços oficiais com data e estimativa prévia abaixo do orçamento. Registrar o uso informado pelo provider e o custo estimado ao final; se uso não for informado ou a estimativa ultrapassar o orçamento, finalizar como `ESCALATE` e não repetir a chamada. Interromper qualquer etapa seguinte antes de uma nova chamada se o orçamento monitorado já tiver sido atingido.

**Limite:** estimativa e monitoramento não garantem o valor efetivamente faturado e não são um bloqueio financeiro do lado servidor. Essa exceção vale apenas para validação experimental isolada, limitada a uma oportunidade, uma tentativa OpenAI e o orçamento exato aprovado no manifesto. Não altera billing, uso operacional, nem autoriza chamadas live por si só. A aprovação explícita do manifesto e da execução permanece um gate separado.

## D-022 — Identidade de publisher e agências responsáveis multifonte
**Decisão:** para o incremento vertical Fomentiq v0.2.0, separar `SourcePublisher`, `CallSourceReference`, `CallForProposals` e `CallFundingAgency`. A referência de origem usa identidade nativa única dentro do publisher; título e hash não são identidade. Preservar UUIDs de chamadas, `call_for_proposals.agency_id` como agência primária e campos singulares da API legada durante a transição. A associação CallFundingAgency contém as agências documentadas para cada chamada. Na chamada CNPq/CAPES MAI/DAI 30/2026, CNPq é publisher e agência primária, CAPES é agência adicional conforme a atribuição orçamentária expressa no edital, e a identidade externa é `30/2026`. A Finep 751/portal 751092 permanece uma chamada e referência Finep únicas.

**Migração:** Flyway preserva registros, UUIDs e colunas/API legadas, faz backfill das referências Finep e agências primárias e adiciona listas de origens e agências. A unicidade legada `(agency_id, external_id)` deixa de representar identidade e é removida; a chave nativa passa a ser `(publisher_id, external_id)` em `CallSourceReference`. Isso permite IDs iguais sob publishers diferentes sem fusão. Snapshot e identidade lógica de documento apontam para CallSourceReference sem quebrar os vínculos existentes à chamada e suas versões.

## D-023 — Adiamento de candidatos a duplicata entre publishers
**Decisão:** a classificação e apresentação de candidatos a duplicata entre publishers não fazem parte do incremento vertical A do catálogo multifonte v0.2.0. Os requisitos e tarefas OpenSpec permanecem registrados para um incremento posterior, mas as tasks 4.1–4.2 ficam explicitamente adiadas e não podem ser marcadas como concluídas. O incremento A mantém identidades de oportunidades separadas e não faz fusão automática. Uma regra de candidatos exige proposta e aprovação próprias.

## D-024 — Sessão Spring Security para curadoria de demonstração
**Decisão:** a curadoria do incremento B usa sessão e autorização do Spring Security somente no perfil isolado `manual-demo`, habilitado por marcador explícito e credenciais de ambiente. A senha é codificada com BCrypt em memória; as ações registram o principal autenticado. Operações administrativas permanecem negadas por padrão fora desse perfil, enquanto leituras públicas do catálogo continuam anônimas. Mutations exigem CSRF; cookies usam HttpOnly e SameSite=Lax, com Secure em HTTPS. A autenticação por OIDC é obrigatória antes de disponibilizar administração em produção.

**Limite:** existe um único administrador da demonstração, sem gestão de usuários/papéis. A configuração não é um mecanismo de identidade de produção e não pode ser habilitada fora dos recursos Compose isolados validados no startup.
