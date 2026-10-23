# Golden fixture Finep — PROINFRA 2024 Expansão

## Identidade escolhida

- Chamada: **CHAMADA PÚBLICA MCTI/FINEP/FNDCT – INFRAESTRUTURA DE PESQUISA – PROINFRA 2024 EXPANSÃO**.
- Agência: Finep; identificador externo: `751`.
- Identificador local da fixture: `finep-proinfra-2024-expansao-751`.
- URL oficial histórica: <http://www.finep.gov.br/chamadas-publicas/chamadapublica/751>.
- Rota oficial alternativa indexada: <https://finep.gov.br/component/chamadas_publicas/chamadapublica/751>.
- Referência institucional que confirma identidade e link: [divulgação da UFES](https://prppg.ufes.br/conteudo/chamada-publica-mctifinepfndct-infraestrutura-de-pesquisa-proinfra-2024-expansao).

A chamada permite exercitar página, edital e anexos de infraestrutura de pesquisa. É uma referência histórica de regressão, não uma promessa de oportunidade aberta ou cobertura de todos os formatos Finep. Prazos de seleções internas das universidades não são prazos da Finep.

## Estado da captura

**Seleção definida; pacote ainda não congelado.** Na consulta de 2026-10-23, as rotas oficiais acima retornaram HTTP 404; a rota via `legacy.finep.gov.br` redirecionou para `/oportunidades/chamadapublica/751`, também indisponível. Não foram criados snapshots, PDFs ou hashes fictícios. A referência institucional confirma a escolha, mas não substitui o HTML oficial para testar o parser Finep.

Antes de implementar o parser (task 3.3), resolver o acesso à página oficial ou recuperar uma cópia oficial preservada com origem verificável e concluir a task 3.2. Se isso não for possível, revisar explicitamente a seleção e os artefatos OpenSpec; não trocar de chamada silenciosamente.

## Contrato de congelamento

Destino previsto: `backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751/`.

O pacote deve conter:

1. HTML bruto da página da chamada e da entrada de descoberta que a referencia, preservando links originais.
2. PDF do edital e pelo menos um anexo oficial; preservar também retificações, FAQ e resultados vinculados que integrem a captura, classificados separadamente. Registrar no manifesto todos os links descobertos e o motivo de eventuais downloads não concluídos.
3. `manifest.json` com ID da fixture/chamada, versão do pacote e, por arquivo, caminho local, URL original e final, instante de captura UTC, tipo de mídia, tamanho, SHA-256 e tipo documental. Documentar a origem de cópias recuperadas e não confundir recuperação com data de publicação.
4. `expected.json` revisado contra os documentos: valores brutos e normalizados, estados de validação e locators de evidência para os campos MVP, incluindo prazo e orçamento. Ausências ficam `MISSING`; não inventar TRL, contrapartida ou requisitos de ICT. Não usar saída de LLM sem revisão como oráculo.
5. Resposta tipada de provider de teste determinístico compatível com o schema, sem credenciais ou dependência da API OpenAI.
6. Variante sintética separada para testar alteração de conteúdo/versionamento, identificada como teste; jamais modificar o original para simular retificação oficial.

O conjunto só é considerado congelado após verificar identidade, legibilidade dos PDFs, hashes, correspondência HTML → documentos e evidências dos valores esperados. Consultar D-013 antes de adicionar o pacote ao repositório.

## Uso em regressão e demonstração

- CI executa descoberta, parsing, persistência, API e UI a partir dos arquivos locais, relógio fixo e provider determinístico, sem rede Finep/OpenAI.
- Repetir a mesma entrada não duplica chamada, versões ou fatos; a variante alterada cria versão e preserva a anterior.
- Atualizar a fixture exige novo pacote/manifesto revisado; não regenerar silenciosamente durante testes.
- Uma execução real separada usa o conector Finep e o adaptador OpenAI para demonstrar a integração. Falhas de rede não devem ser apresentadas como sucesso de ingestão real.
