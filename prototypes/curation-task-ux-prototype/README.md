# Protótipo de curadoria por tarefas

Este protótipo é uma exploração visual independente da aplicação Fomentiq. Ele não usa API, autenticação, banco, storage, fixtures ou serviços externos. Todos os nomes, valores, trechos e documentos exibidos são sintéticos.

## Abrir no Codespace

No terminal do Codespace, execute:

```bash
cd /workspaces/fomentiq/prototypes/curation-task-ux-prototype
python3 -m http.server 4177 --bind 0.0.0.0
```

Abra a porta **4177** na aba **Ports** do Codespace e selecione **Open in Browser**. O endereço serve somente os arquivos estáticos deste diretório. Para encerrar, volte ao terminal e pressione `Ctrl+C`.

Também é possível abrir `index.html` diretamente no navegador; o servidor local é preferível para avaliar as rotas por fragmento. Para repetir a verificação automatizada com as dependências de desenvolvimento já instaladas no repositório, execute `node smoke.mjs` neste diretório.

## Roteiro de avaliação

1. **Fila** (`#fila`): pesquise “infraestrutura” ou “Campo Claro”, veja as pendências e selecione **Continuar revisão**.
2. **Visão geral** (`#oportunidade/aurora` ou selecione o segundo cartão): confira prazo, orçamento e as seções “Informações gerais”, “Quem pode participar”, “Condições financeiras” e “Documentos e requisitos”. Use **Revisar informações desta seção** ou **Revisar próxima informação**.
3. **Revisão** (`#revisar/prazo`): leia a informação atual e o trecho sintético ao lado. Experimente confirmar, corrigir e registrar que não localizou a informação. Abra **Ver histórico desta informação**, avance ao resumo e retorne para editar; **Simular registro** altera apenas o estado em memória da aba.
4. Use os links de navegação para voltar à visão geral e à fila. Atualize a página para reiniciar a simulação.

## Diferenças propostas em relação à tela atual

- A entrada começa pela fila de tarefas pendentes, em vez de apresentar detalhes internos da curadoria.
- A visão geral organiza os dados por perguntas do usuário e oferece uma próxima ação explícita.
- Cada revisão apresenta somente uma informação por vez, com sua fonte no mesmo contexto.
- Confirmação, correção e ausência são ações distintas, seguidas por um resumo antes da simulação.
- Histórico e detalhes adicionais ficam recolhidos até serem solicitados.
- Identificadores, nomes de propriedades, JSON, chaves de armazenamento e dados de execução não aparecem.

## Limitações e decisões para a avaliação

- O protótipo não salva, autentica, publica nem altera qualquer dado. O botão **Simular registro** só atualiza a tela nesta aba.
- Valores, citações, documentos e instituições são fictícios; não devem ser usados para decisão de financiamento.
- As telas não validam schema nem reproduzem conflitos, concorrência, permissões ou regras de negócio.
- Aprovação visual não autoriza implementação automaticamente. Após a avaliação, ainda será necessário decidir quais partes de navegação, campos, estados, histórico e publicação devem ser adaptadas à aplicação real.
- Este protótipo não substitui nem remove a tela administrativa existente.
