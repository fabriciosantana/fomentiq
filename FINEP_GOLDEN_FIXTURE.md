# Golden fixture Finep — PROINFRA 2024 Expansão

## Identidade e decisão

Mantida a **CHAMADA PÚBLICA MCTI/FINEP/FNDCT – INFRAESTRUTURA DE PESQUISA – PROINFRA 2024 EXPANSÃO**. Os artefatos oficiais foram recuperados no novo portal em **2026-10-23**; não foi necessário trocar de chamada.

- Agência: Finep; ID histórico (`databaseId`): `751`.
- ID do registro no novo portal (`id`): `751092`.
- Identificador da fixture: `finep-proinfra-2024-expansao-751`.
- [Página oficial atual](https://www.finep.gov.br/e/chamada-publica/222684/751092).
- [Metadados oficiais da chamada](https://www.finep.gov.br/o/c/chamadapublicas/751092): comprovam a associação `databaseId=751` → `id=751092`.
- [Entrada de oportunidades](https://www.finep.gov.br/oportunidades) e [consulta configurada de descoberta](https://www.finep.gov.br/o/c/chamadapublicas?filter=databaseId%20eq%20751&pageSize=100).
- Lista de documentos usada pela página: `https://www.finep.gov.br/o/c/chamadapublicas/751092/documentos?pageSize=500`.

A URL histórica `/chamadas-publicas/chamadapublica/751` permaneceu indisponível na consulta. O conector deve usar a rota atual e os IDs comprovados, sem tratar a migração de portal como uma nova oportunidade. A chamada está **encerrada**, conforme `call.json`; acessibilidade não significa inscrições abertas.

## Pacote congelado

[Diretório da fixture](backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751/) — versão 1, capturada em 2026-10-23.

- `call.html` e `discovery.html`: snapshots HTML saneados, conforme ressalva abaixo.
- `call.json`, `discovery.json` e `documents.json`: respostas oficiais preservadas. A consulta de descoberta retorna uma chamada, e a lista de documentos retorna 18 entradas, sem paginação restante.
- `pdf/`: **18 PDFs**, todos obtidos por GET com HTTP 200 e abertos para extração de texto: edital original, sete anexos, duas versões rerratificadas, dois comunicados de rerratificação, telas do FAP, quatro resultados e um comunicado de esclarecimento. Total dos PDFs: **5.406.555 bytes**.
- `manifest.json`: URL de origem/final, instante UTC de captura, tipo de mídia, tamanho, SHA-256 e classificação por artefato; páginas e ID de entrada para cada PDF. Inclui todos os links documentais retornados pela API. Formatos editáveis alternativos são inventariados e explicitamente excluídos da captura; o PDF de cada entrada está preservado.
- `expected.json`: candidatos de referência revisados contra as fontes, com valores, evidências por arquivo/hash/página/seção, histórico de prazo e relógio fixo. São asserções mínimas de regressão, não uma análise exaustiva de elegibilidade/compliance.
- `provider-response.json`: resposta determinística sintética com os candidatos da fixture. O formato é local à fixture; a adaptação à interface/schema de produção pertence às tasks 4.1–4.3. Não é uma resposta real da OpenAI.
- `synthetic/call-changed.json`: variante explicitamente sintética, com título alterado para testar nova versão. O original permanece intacto.

### Arquivos principais

| Documento | Arquivo local | Páginas |
| --- | --- | --- |
| Edital original | `pdf/12_12-2024_EDIP2024_EditaL.pdf` | 27 |
| Anexo I — Minuta de Convênio | `pdf/12_12_2024_EDIP_2024_AI.pdf` | 21 |
| Anexos II a VII | `pdf/12_12_2024_EDIP_2024_AII.pdf` a `pdf/12_12_2024_EDIP_2024_AVII.pdf` | 3, 5, 3, 1, 1, 1 |
| Primeira rerratificação | `pdf/23_12_2024_Exp_2024_1a_Rerratificacao.pdf` | 27 |
| Segunda rerratificação | `pdf/07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf` | 27 |
| Comunicado da segunda rerratificação | `pdf/07_04_2025_Edital_PROINFRA2024-Expansao_comunicado.pdf` | 1 |

As URLs oficiais completas de download de cada arquivo estão no [manifesto](backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751/manifest.json), ligadas à entrada correspondente de `documents.json`.

## Particularidades da captura e do parser

A lista de documentos é carregada por JavaScript via API do portal Liferay; não está pronta no HTML inicial. Capturar apenas o HTML perderia os links. O conector deve preservar também as respostas usadas para descoberta e metadados/documentos. A consulta documental foi feita pelo mesmo fluxo de token usado pela página pública; o GET sem esse fluxo retornou 403. Os PDFs foram baixados sem autenticação. Não interpretar esse 403 isolado como ausência de documentos.

O HTML recebido contém credenciais do próprio portal em scripts. Para respeitar a regra de não versionar segredos, **todos os elementos `script` foram removidos** e valores de parâmetros de autenticação foram ocultados nos HTMLs da fixture. O manifesto registra o hash/tamanho dos bytes recebidos e dos bytes armazenados, além da transformação. Os HTMLs são derivados saneados, não cópias brutas byte a byte. Os JSONs oficiais e PDFs preservados não receberam essa transformação. Nenhum token, header de autorização ou credencial foi incluído no pacote.

Essa exceção é restrita aos HTMLs de teste; não autoriza remover evidência documental em produção. O HTML saneado mantém o conteúdo e a URL canônica; as respostas JSON permitem testar o fluxo dinâmico offline sem executar os scripts ou solicitar tokens. A implementação do acesso ao portal permanece na task do conector, com configuração operacional fora do código e sem credenciais copiadas da fixture.

## Valores de referência e proveniência

| Campo | Referência | Evidência |
| --- | --- | --- |
| Prazo original | 25/04/2025, 17h Brasília | Edital original, p. 15, itens 9.1 e 9.3 |
| Prazo após segunda rerratificação | 06/06/2025, 17h Brasília (`2025-06-06T17:00:00-03:00`) | Segunda rerratificação, p. 15, itens 8.1 e 8.3; comunicado, p. 1 |
| Orçamento | Até R$ 500.000.000,00 | Segunda rerratificação, p. 12, item 5.1 |
| Elegibilidade | ICTs públicas ou privadas qualificadas como ICES; observar exclusões e demais condições | Segunda rerratificação, p. 4, itens 3.1–3.9 |
| Necessidade de ICT | ICT executora; instituição de apoio obrigatória para executora pública federal | Segunda rerratificação, pp. 3–4, itens 2.1.15, 3.1 e 3.5 |
| Contrapartida | Condicional ao tipo/localização da instituição; isenções expressas | Segunda rerratificação, p. 14, itens 7.1–7.3 |
| TRL | `MISSING`; não inventar faixa | Faixa não especificada nas três versões do edital preservadas |

A alteração do prazo possui ato explícito de rerratificação; preservar as datas anteriores e a justificativa de sucessão. Não aplicar uma regra genérica de “o arquivo mais recente vence”. Resultados não substituem requisitos do edital. Os candidatos da fixture ficam `EXTRACTED` (ou `MISSING`); `VALIDATED` depende dos validadores da aplicação, ainda não implementados.

## Verificação e uso

A captura e a referência de regressão estão concluídas: identidade, vínculo entre API e PDFs, integridade SHA-256, abertura dos 18 PDFs e evidências dos campos críticos foram conferidos. O bloqueio de acesso aos artefatos está resolvido. A implementação do parser, do provider de teste tipado e dos testes de integração continua nas respectivas tasks; não foi executada nesta revisão.

- CI deve usar apenas arquivos locais, relógio fixo e provider determinístico, sem Finep/OpenAI.
- Repetir o pacote não duplica chamadas/versões/fatos; a variante sintética deve produzir mudança de hash/versão.
- Atualização da fixture exige nova versão e revisão do manifesto/expectativas; nunca regenerar silenciosamente em testes.
- A demonstração real Finep/OpenAI é separada e não pode ser substituída por sucesso da fixture offline.
