# Bootstrap Prompt for Codex — Fomentiq

Use este prompt na primeira sessão do Codex dentro do repositório.

---

Leia integralmente, nesta ordem:

1. `AGENTS.md`
2. `PROJECT_CONTEXT.md`
3. `PRODUCT_REQUIREMENTS.md`
4. `MVP_SCOPE.md`
5. `DOMAIN_MODEL.md`
6. `ARCHITECTURE.md`
7. `BACKLOG.md`
8. `DECISIONS.md`
9. `openspec/changes/finep-opportunity-ingestion/`

Também descubra as skills repo-local em `.agents/skills/`.

Objetivo desta sessão: preparar o repositório **Fomentiq** para o primeiro vertical slice `finep-opportunity-ingestion` usando OpenSpec e as regras do projeto.

NÃO implemente o produto ainda.

Faça primeiro:

1. examine o estado atual do repositório;
2. identifique conflitos, lacunas ou decisões técnicas ainda necessárias;
3. verifique se a estrutura OpenSpec está compatível com a versão instalada;
4. valide a consistência entre PRD, MVP, domínio, arquitetura e o primeiro change;
5. proponha um plano de implementação incremental para o vertical slice;
6. liste comandos e ferramentas que pretende usar;
7. identifique riscos que possam invalidar a abordagem;
8. apresente os critérios objetivos de conclusão do primeiro ciclo.

Não expanda escopo. Não adicione matching, Proposal Copilot, multi-tenant, billing ou gestão financeira.

Se houver uma decisão realmente bloqueante que altere requisitos, arquitetura ou task breakdown, destaque-a antes de implementar. Caso contrário, faça uma recomendação explícita e prossiga apenas quando eu disser para iniciar o apply.

O princípio do projeto é:

`LLM interpreta; regra valida; fonte comprova.`

Quando eu autorizar a implementação, siga:

`SPEC → PLAN → IMPLEMENT → TEST → REVIEW → VERIFY AGAINST SPEC → FIX → VERIFY`.

Ao final de cada ciclo, informe:
- arquivos alterados;
- testes executados;
- resultado do OpenSpec verify/validate;
- riscos ou débito técnico criado;
- próximo menor passo recomendado.
