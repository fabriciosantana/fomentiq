# Project Context — Fomentiq

## 0. Identidade do produto

- **Nome:** Fomentiq
- **Descriptor:** Plataforma de Inteligência e Gestão de Fomento à Inovação
- **Nome curto do repositório:** `fomentiq`
- **Posicionamento em inglês:** Innovation Funding Intelligence

## 1. Contexto

**Fomentiq** é uma plataforma de software para transformar oportunidades de fomento à inovação em dados estruturados, decisões explicáveis e fluxos operacionais gerenciáveis.

O problema inicial observado é simples: empresas e ICTs precisam acompanhar dezenas de fontes, ler PDFs extensos, interpretar critérios jurídicos e técnicos, identificar aderência, organizar documentação e, caso aprovadas, gerir execução técnica, financeira e prestação de contas.

A plataforma deve evoluir de um radar de oportunidades para um sistema completo de Grant Lifecycle Management.

## 2. Tese do produto

Não competir como “mais um agregador de editais”.

Proposta de valor:

> Encontramos oportunidades, explicamos se uma organização pode participar, ajudamos a estruturar a proposta e acompanhamos o projeto até a prestação de contas.

A vantagem competitiva de longo prazo está na base estruturada e versionada formada pela relação entre:

`organização × oportunidade × requisito × proposta × resultado × projeto × execução × evidência`.

## 3. Visão modular de longo prazo

1. **Radar** — monitoramento de fontes e oportunidades.
2. **Matching** — aderência entre organização e instrumento de fomento.
3. **Proposal** — preparação de proposta, compliance e submissão.
4. **Execution** — gestão técnica e financeira do projeto aprovado.
5. **Intelligence** — análises históricas, tendências e estratégia de financiamento da inovação.

## 4. Estratégia de MVP

O MVP NÃO implementa o ciclo completo. O primeiro objetivo é provar que conseguimos transformar uma chamada real da Finep em dados:

- detectados automaticamente;
- estruturados;
- versionados;
- rastreáveis até a fonte;
- pesquisáveis;
- exibidos em interface web.

Primeiro vertical slice:

`Finep → detectar chamada → baixar página/documentos → versionar → extrair → validar → persistir → exibir → mostrar proveniência`.

## 5. Público inicial

- Empresas médias e grandes com agenda de inovação.
- ICTs privadas e públicas.
- Consultorias de inovação e escritórios de projetos.
- NITs, universidades e instituições de pesquisa.

## 6. Princípios de produto

- **Fonte antes da IA:** todo dado crítico deve apontar para sua evidência documental.
- **LLM interpreta; regra valida; fonte comprova.**
- **Versão importa:** edital, FAQ, retificação e resultado não são documentos intercambiáveis.
- **Explicabilidade:** matching e compliance precisam explicar o motivo da decisão.
- **Automação com supervisão:** decisões de alto impacto devem permitir revisão humana.
- **Segurança por padrão:** dados de clientes e propostas são confidenciais.
- **Arquitetura simples primeiro:** monólito modular antes de microserviços.

## 7. Stack alvo

- Frontend: React + TypeScript.
- Backend: Java + Spring Boot.
- Banco: PostgreSQL.
- Busca semântica: pgvector quando necessária.
- Documentos: object storage S3 compatível.
- Jobs: scheduler/worker integrado inicialmente ao backend; extrair serviço somente quando necessário.
- IA: camada de provedores desacoplada do domínio.
- Observabilidade: logs estruturados, métricas e tracing desde o MVP.

## 8. Fontes iniciais

Prioridade após Finep:

1. Finep.
2. CNPq.
3. BNDES.
4. Embrapii.
5. FAP-DF.

Depois: MCTI, MDIC, Petrobras, SENAI, FAPs, bancos regionais, Diário Oficial e fontes internacionais.

## 9. Fronteira de responsabilidade

O sistema é ferramenta de inteligência e gestão. Não substitui análise jurídica, contábil, regulatória ou técnica quando estas forem legalmente necessárias.

## 10. Definição de sucesso do primeiro ciclo

Uma chamada pública real da Finep deve aparecer na interface com:

- metadados principais;
- documentos associados;
- histórico de versões;
- valores, prazos, público elegível e critérios extraídos;
- indicação da origem de cada campo crítico;
- possibilidade de abrir a fonte original;
- testes automatizados cobrindo parsing, persistência e API.
