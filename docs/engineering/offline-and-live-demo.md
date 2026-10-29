# Reproducing the MVP demonstration

## Avaliação manual local

Este modo mantém os serviços ativos para navegação manual. Usa a golden fixture Finep e a fixture pública congelada CNPq/CAPES 30/2026 com provider determinístico; não acessa Finep, CNPq nem OpenAI. Os dados ficam em volumes dedicados do projeto Compose `fomentiq-manual-evaluation` e são preservados ao parar os containers.

Pré-requisitos: Docker Compose, Java/Maven, Node/npm e dependências do frontend instaladas (`cd frontend && npm ci`). Abra três terminais na raiz do repositório.

**Terminal 1 — PostgreSQL e armazenamento local:**

```sh
set -a
. ./.env.example
set +a
export POSTGRES_PORT=15432 S3_PORT=19000 S3_CONSOLE_PORT=19001
docker compose --project-name fomentiq-manual-evaluation up -d --wait postgres object-storage
```

**Terminal 2 — backend offline com a chamada de demonstração:**

```sh
set -a
. ./.env.example
set +a
export POSTGRES_PORT=15432 S3_PORT=19000
export DATABASE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
export DATABASE_USERNAME="$POSTGRES_USER" DATABASE_PASSWORD="$POSTGRES_PASSWORD"
export S3_ENABLED=true S3_ENDPOINT="http://127.0.0.1:${S3_PORT}" S3_BUCKET=fomentiq-manual-evaluation
export OPENAI_API_KEY=''
mvn -B -f backend/pom.xml -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/fomentiq-manual-evaluation-classpath
java -cp "backend/target/test-classes:backend/target/classes:$(cat /tmp/fomentiq-manual-evaluation-classpath)" \
  com.fomentiq.ingestion.OfflineE2eApplication
```

**Terminal 3 — frontend:**

```sh
cd frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev -- --host 0.0.0.0 --port 4173 --strictPort
```

Abra `http://localhost:4173` (ou a porta encaminhada pelo ambiente de desenvolvimento). Finep 751 / portal 751092 e CNPq 30/2026 são ingeridas automaticamente no mesmo PostgreSQL ao iniciar o backend. No catálogo, use **Fonte publicadora** (Finep/CNPq) e **Agência responsável** (Finep/CNPq/CAPES); abra cada chamada para consultar documentos, versões e evidências. A repetição do backend com os mesmos volumes é idempotente.
### Acesso administrativo (somente demonstração isolada)

A administração só existe no perfil explícito manual-demo. A configuração padrão mantém /api/admin/** negado e não cria usuário. Para habilitá-la, use exclusivamente o PostgreSQL e o bucket deste projeto Compose; o backend valida antes de iniciar Flyway a identidade do projeto, as portas loopback, o banco e o bucket. Não reutilize configurações de outros ambientes.

No Terminal 2, antes de iniciar o backend, acrescente:

```sh
export SPRING_PROFILES_ACTIVE=manual-demo
export FOMENTIQ_ADMIN_DEMO_ENABLED=true
export FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT=fomentiq-manual-evaluation
export FOMENTIQ_ADMIN_DEMO_POSTGRES_PORT=15432
export FOMENTIQ_ADMIN_DEMO_S3_PORT=19000
export FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT=19001
export FOMENTIQ_ADMIN_USERNAME=reviewer
read -r -s -p "Senha administrativa temporária: " FOMENTIQ_ADMIN_PASSWORD
printf '\n'
export FOMENTIQ_ADMIN_PASSWORD
# Use false somente se acessar pelo HTTP de loopback localhost; HTTPS mantém true.
export FOMENTIQ_ADMIN_COOKIE_SECURE=true
```

O Spring Security aplica BCrypt à senha em memória e não a registra. Não grave essa senha em arquivos, histórico do shell ou relatórios. No Codespace, mantenha Secure=true e abra o frontend pela URL HTTPS encaminhada pelo Codespaces. Para avaliação estritamente local em http://localhost, defina FOMENTIQ_ADMIN_COOKIE_SECURE=false apenas nesse terminal e mantenha os serviços limitados a loopback.

Após iniciar o frontend, acesse `/admin` e entre com o usuário e a senha provisionados no Terminal 2. Para demonstrar o cadastro editorial, escolha **Nova oportunidade**, preencha título, instituição, resumo e URL oficial, adicione seções e informações tipadas, salve como rascunho e abra **Pré-visualizar**. O rascunho permanece privado. Escolha **Publicar** explicitamente para exibi-lo no catálogo público (`/`); alterações posteriores também exigem **Atualizar publicação** para substituir a versão pública. **Retirar da publicação** remove a página pública sem apagar o rascunho.

A curadoria das oportunidades importadas anteriormente continua disponível em `/admin/curation`; ela mantém seu fluxo de revisão separado do novo cadastro editorial. As telas públicas continuam sem login. OIDC é requisito antes de disponibilizar administração em qualquer ambiente de produção.

O backend falha fechado se o marcador do projeto, JDBC, usuário do banco, endpoint S3-compatible ou bucket não corresponderem ao projeto de demonstração isolado. Para desabilitar imediatamente a administração, pare o backend ou inicie-o sem o perfil manual-demo; não é necessário apagar dados nem volumes.


Para encerrar, use `Ctrl+C` nos terminais do frontend e backend e execute no Terminal 1:

```sh
docker compose --project-name fomentiq-manual-evaluation stop
```

Os volumes persistem para a próxima avaliação. Para remover também os dados locais, use `docker compose --project-name fomentiq-manual-evaluation down --volumes`; isso apaga somente os volumes desse projeto de demonstração. Não use esses comandos se o projeto/volumes forem compartilhados por outra avaliação.

## Offline, deterministic demonstration

The offline path uses the frozen `proinfra-2024-expansao-751` Finep fixture and the reviewed `cnpq/chamada-30-2026` public fixture with deterministic providers. It never contacts Finep, CNPq or OpenAI. Ingestion, PostgreSQL/Flyway, the S3-compatible artifact store, the read API, and the browser UI are the real application components.

Prerequisites: Docker with Compose, Java/Maven as configured by the repository, Node/npm, and permission for Playwright to launch its installed Chromium browser. Install the browser once with `cd frontend && npx playwright install chromium`. Run from the repository root:

```sh
cd frontend && npm ci
cd ..
./scripts/run-offline-mvp-e2e.sh
```

Each invocation generates a unique Compose project name and uses isolated PostgreSQL and object-storage volumes. Defaults are ports 25432 (PostgreSQL), 29000 (S3 API), 29001 (storage console), 18080 (backend), and 14173 (Playwright/Vite); override `E2E_POSTGRES_PORT`, `E2E_S3_PORT`, `E2E_S3_CONSOLE_PORT`, `E2E_BACKEND_PORT`, and `E2E_FRONTEND_PORT` when needed. The script refuses occupied ports, starts its own backend with test-classpath-only fixture overrides, and Playwright always starts a fresh Vite server. It stops the backend and runs `docker compose down --volumes --remove-orphans` for only its generated project on exit, including failure/interrupt cleanup. The project volumes, temporary logs/classpath file, and Playwright artifacts/traces are removed; no existing Compose project or other run's output directory is targeted. Do not put credentials or tokens into tracked files or reports. Compose defaults are local-only development values, not production credentials.

The Playwright flow covers persisted Finep `751` / portal `751092` and CNPq `30/2026` in one database, source filtering, catalog-to-detail navigation, agency distinction, documents, validation states, and evidence. It also exercises the isolated administrator login over HTTPS, verifies session cookie flags, confirms the absence of TRL after full documentary review with a document/version locator and justification, approves that human revision without fabricating a quote, rejects an unsupported deadline proposal, and confirms the public state plus original extraction. The run uses a generated throwaway password and dedicated resources, removes its own project and logs on exit, and does not simulate either live public portal or the paid provider.

## Separate live Finep/OpenAI demonstration

Audit packages and exact extraction inputs are stored in the private filesystem directory configured by `FOMENTIQ_AUDIT_ARCHIVE_DIR`, outside the repository and disposable Compose volumes. Set `FOMENTIQ_AUDIT_READER_GROUP` to the POSIX group authorized to read the archive; configure a dedicated leaf directory beneath an existing parent. The application initializes that leaf with a private marker, and refuses missing, unsafe, symlinked, repository-local, or pre-existing unmarked directories without changing their permissions. Initial retention is five years with no automatic deletion. Do not point this directory at the manual demo's Docker volumes or add its contents to Git.

Live execution is a separate, opt-in operational exercise and was **not run** during the offline verification. It requires explicit authorization for external Finep access and any paid OpenAI request, network/DNS access, a currently functioning public Finep portal/document OAuth flow, and an `OPENAI_API_KEY` configured only in the process environment. `OPENAI_MODEL` defaults to `gpt-6-luna`; reasoning effort defaults to `low`. Never pass credential values as command-line arguments or include environment dumps in logs.

Live execution can fail if Finep changes its public page, OAuth bootstrap/token flow, permitted host list, document API, or document formats. The connector fails closed on policy violations or incompatible flow changes. Provider requests are limited by the configured excerpt selection and extraction limits; failures, rate limits, refusal, incomplete output, and schema rejection are surfaced without fixture fallback in production. Results can vary with source updates and provider behavior. Review outputs/evidence before relying on any extracted fact.

The application captures the exact selected provider inputs before invoking the provider. The run's code revision is recorded from `FOMENTIQ_CODE_REVISION`; set it to the validated source commit before a live run. After the run, while its isolated PostgreSQL and storage are still available, build the backend and export/verify the package with the same isolated database and private archive environment:

```sh
mvn -B -f backend/pom.xml -DskipTests package
java -jar backend/target/fomentiq-backend-0.1.0-SNAPSHOT.jar \
  --fomentiq.audit.export-run=<ingestion-run-uuid>
java -jar backend/target/fomentiq-backend-0.1.0-SNAPSHOT.jar \
  --fomentiq.audit.verify="$FOMENTIQ_AUDIT_ARCHIVE_DIR/packages/<ingestion-run-uuid>.zip"
./scripts/cleanup-finep-live-validation.sh \
  docs/engineering/verification/finep-live-integration-validation-manifest-candidate-2026-10-26.json \
  <ingestion-run-uuid>
```

The cleanup command exports idempotently, independently verifies the archive and its run identity, and removes only the manifest's dedicated Compose project after every gate succeeds. Any export, verification, or resource-identity failure stops before volume removal. Do not use generic `docker compose down --volumes` for a live validation project. The offline verifier mode reads only the package and local verifier code; it does not initialize Spring, PostgreSQL, S3, Finep, or OpenAI. It returns success for integrity and completeness only, not factual approval. Package files remain in the configured private archive, with five-year retention metadata and no automatic deletion.

The first live run (`2e859ec1-3916-4361-b10c-ac454b65061c`) predates durable capture of the exact provider excerpts and complete extraction payload. Those historical artifacts are unavailable after the approved disposable-environment cleanup. The retained PDFs and golden fixture may be used as separately labeled comparators, but replaying selection or extraction now must never be represented as evidence captured during that live run.

Before a live run, agree on the single opportunity/documents and the request budget. Record UTC start/end time, configured model and schema version, ingestion run ID/status, document/field counts, provider input/output token usage when reported, and failures. Estimate cost using the current official OpenAI model pricing at run time: multiply reported input and output tokens by their respective published per-token rates, convert units as specified by the pricing page, and record the pricing URL/date and arithmetic. Report unavailable usage as unavailable; do not infer tokens from payload size. Do not log the API key, OAuth client secret/token, raw authorization headers, or sensitive provider payloads. No cost estimate is applicable to the deterministic offline run (provider token counts are zero).
