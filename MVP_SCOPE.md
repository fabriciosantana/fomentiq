# MVP Scope — Fomentiq

## Dentro do MVP

### Fonte
- Finep apenas.

### Capacidades
- monitoramento agendado;
- descoberta de chamadas;
- download de páginas e documentos;
- armazenamento bruto;
- hash e versionamento;
- extração estruturada;
- proveniência;
- validação de campos críticos;
- catálogo;
- busca e filtros;
- detalhe da oportunidade;
- histórico de versões;
- operação local por Docker Compose.

### Usuário
- usuário interno de leitura do catálogo, sem multi-tenant complexo;
- ingestão por scheduler ou comando interno, sem endpoint administrativo;
- sem login no primeiro slice; autenticação obrigatória quando operações administrativas forem expostas em mudança aprovada.

## Fora do MVP

- marcações pessoais “acompanhar”, “descartar”, “qualificar” e observações;
- login e área/endpoint administrativo no primeiro slice;
- matching com perfil de empresa;
- score de aderência;
- Proposal Copilot;
- Compliance Matrix completa;
- workflow de submissão;
- CRM;
- execução financeira;
- prestação de contas;
- marketplace de ICTs;
- billing/SaaS;
- multi-tenant empresarial completo;
- aplicativo mobile;
- integrações com dezenas de fontes;
- automação de envio de proposta.

## Critério de encerramento do MVP

O MVP termina quando for possível demonstrar ao cliente:

> “Esta chamada foi encontrada automaticamente, seus documentos foram preservados e versionados, os principais requisitos foram transformados em dados estruturados e cada dado importante pode ser conferido na fonte.”

A chamada de referência é PROINFRA 2024 Expansão (Finep ID `751`), conforme [contrato da golden fixture](FINEP_GOLDEN_FIXTURE.md). O pacote congelado é pré-requisito do parser; sua definição documental não equivale à captura concluída.
