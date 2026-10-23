# Decisions

## D-001 — Produto não é apenas agregador
**Decisão:** adotar **Fomentiq** como nome oficial do produto e o descriptor **Plataforma de Inteligência e Gestão de Fomento à Inovação**.

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
**Decisão:** provider de extração atrás de interface.

## D-010 — Agentic engineering governada
**Decisão:** OpenSpec para mudanças; AGENTS.md curto; skills para workflows repetíveis; implementação somente após spec e tasks.


## D-00 — Nome oficial do produto

**Decisão:** Nome oficial: **Fomentiq**.

**Descriptor:** Plataforma de Inteligência e Gestão de Fomento à Inovação.

**Racional:** o nome mantém associação semântica com fomento e inteligência, sem limitar o produto a editais ou a uma única etapa do ciclo de financiamento da inovação.
