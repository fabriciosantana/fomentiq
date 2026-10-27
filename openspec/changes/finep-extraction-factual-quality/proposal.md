# Proposal

## Why

A primeira execução online mostrou que trechos selecionados por relevância textual podem omitir fatos e exceções existentes nos documentos, enquanto validação estrutural permissiva pode marcar regras parciais como `VALIDATED`. O fluxo precisa distinguir falha de cobertura de ausência documental e preservar evidências suficientes para revisão e auditoria offline.

## What Changes

- Selecionar páginas por campo crítico e versão documental, preservando edital original e retificações sem usar recência como regra geral de precedência.
- Registrar cobertura, candidatos não selecionados e motivos de lacuna antes da chamada ao provider, respeitando limites de documentos, caracteres e tokens.
- Preservar valores estruturados e evidências de elegibilidade e contrapartida, mantendo esses requisitos compostos como `EXTRACTED` para revisão factual, sem validação automática.
- Preservar os estados técnicos existentes e representar cobertura incompleta em metadados auditáveis, sem interpretar silêncio do provider como ausência confirmada.
- Adicionar regressões determinísticas sobre os artefatos existentes da golden fixture e compatibilidade com pacotes de auditoria e homologação factual.

### Revisão explícita de escopo — 2026-10-27

Após o achado FQ-R1 permanecer aberto, a decisão de produto foi não tentar comprovar interpretação jurídica complexa exclusivamente por heurísticas determinísticas. A task 2.3 foi revisada: eligibility e counterpart com valor não conflitante permanecem `EXTRACTED` com evidência; ausência continua `MISSING`; divergência não resolvida continua `CONFLICT`. Esta decisão não declara que a gramática relacional anterior foi corrigida. Dispensa de declaração ou documento não equivale a dispensa da obrigação financeira de contrapartida. As validações determinísticas inequívocas existentes para orçamento global e exigência de ICT permanecem.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `opportunity-ingestion`: exigir cobertura rastreável de seleção e validação completa de regras críticas sem alterar os estados técnicos nem presumir ausência documental.

O requisito de validação completa para eligibility/counterpart foi substituído pela política conservadora acima; a cobertura e as evidências continuam obrigatórias para auditoria e revisão factual.

## Impact

Impacta o seletor de trechos, a validação determinística, o contrato versionado de extração, a captura dos metadados de entrada para auditoria e os testes offline da fixture. Não prevê mudança de API pública ou migration de banco; compatibilidade dos pacotes existentes deve ser preservada. Finep/OpenAI não serão chamados durante testes. A implementação fica limitada ao MVP e não altera os artefatos congelados da fixture.
