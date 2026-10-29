# Cadastro manual e catálogo editorial — protótipo

Protótipo visual independente da aplicação Fomentiq. Todos os exemplos são fictícios. Alterações, prévia, publicação e retirada de publicação existem somente na memória desta aba e desaparecem ao recarregar.

## Abrir no Codespace

Na raiz do repositório:

```bash
cd /workspaces/fomentiq/prototypes/manual-opportunity-editorial
python3 -m http.server 4179 --bind 0.0.0.0
```

Abra a porta **4179** na aba Ports do Codespace. O protótipo começa na central administrativa. Não precisa iniciar Docker, backend ou frontend da Fomentiq.

Para encerrar, pressione `Ctrl+C` no terminal que iniciou o servidor.

## Roteiro rápido

1. Na central, use **Nova oportunidade** ou edite o rascunho fictício.
2. Confira título, instituição, resumo, prazo, situação e URL oficial.
3. Abra **Conteúdo**, adicione/reordene seções e inclua campos dos seis tipos disponíveis.
4. Abra **Prévia pública** para conferir o texto como o visitante verá.
5. Use **Publicar demonstração**; volte ao catálogo público e abra a oportunidade.
6. Retire a publicação e confirme que ela deixa de aparecer no catálogo.

Links com domínio `example.org` são exemplos demonstrativos e não apontam para editais reais.

## Verificação automatizada

Na raiz do repositório:

```bash
node prototypes/manual-opportunity-editorial/smoke.mjs
```

O smoke usa o Playwright já instalado em `frontend/`; não instala pacotes nem inicia serviços de aplicação.
