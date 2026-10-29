# Design

## Context

O sistema atual já possui curadoria autenticada e publicação de revisões, mas a avaliação manual pediu uma proposta de interação diferente antes de qualquer alteração na aplicação. Este artefato é uma simulação visual e não se conecta à aplicação.

## Goals / Non-Goals

**Goals:** apresentar três telas navegáveis em pt-BR, com informação priorizada e detalhes progressivos, executáveis localmente no Codespace.

**Non-Goals:** não autenticar, salvar dados, chamar APIs, operar banco/storage ou substituir qualquer tela do produto.

## Decisions

- Usar HTML, CSS e JavaScript nativos para abrir sem instalação de pacotes ou build.
- Navegar por estados/fragmentos de URL mantendo as três telas no mesmo protótipo.
- Armazenar alterações somente na memória da aba e usar dados sintéticos explicitamente sinalizados.
- Mostrar evidência junto do campo em revisão; detalhes de origem e histórico ficam em divulgações progressivas.
- Fornecer uma hierarquia de fila → oportunidade → tarefa de revisão, sem expor nomes de propriedades ou identificadores técnicos.

Alternativa considerada: criar uma rota React dentro do frontend existente. Foi rejeitada porque acoplaria o protótipo à aplicação que não deve ser alterada nesta fase.

## Risks / Trade-offs

- [Uma simulação pode parecer funcionalmente conectada] → faixa persistente “Protótipo visual · dados fictícios · alterações não são salvas” e execução sem chamadas de rede.
- [Comportamento visual pode divergir de contratos reais] → não reutilizar DTOs; marcar conteúdo e referências como sintéticos e decidir integração só após aprovação visual.

## Migration Plan

Não há migração. Remover o diretório do protótipo reverte integralmente o artefato; nenhuma mudança da aplicação é necessária.
