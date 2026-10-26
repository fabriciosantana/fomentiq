# Reproducing the MVP demonstration

## Offline, deterministic demonstration

The offline path uses the frozen `proinfra-2024-expansao-751` fixture, `FixtureSourceConnector`, and `FixtureStructuredExtractionProvider`. It never contacts Finep or OpenAI. Ingestion, PostgreSQL/Flyway, the S3-compatible artifact store, the read API, and the browser UI are the real application components.

Prerequisites: Docker with Compose, Java/Maven as configured by the repository, Node/npm, and permission for Playwright to launch its installed Chromium browser. Install the browser once with `cd frontend && npx playwright install chromium`. Run from the repository root:

```sh
cd frontend && npm ci
cd ..
./scripts/run-offline-mvp-e2e.sh
```

Each invocation generates a unique Compose project name and uses isolated PostgreSQL and object-storage volumes. Defaults are ports 15432 (PostgreSQL), 19000 (S3 API), and 19001 (storage console); override `POSTGRES_PORT`, `S3_PORT`, and `S3_CONSOLE_PORT` if those host ports are occupied. The script rejects an already healthy backend on port 8080, starts its own backend with test-classpath-only fixture overrides, and Playwright always starts a fresh Vite server on port 4173. It stops the backend and runs `docker compose down --volumes --remove-orphans` for only its generated project on exit, including failure/interrupt cleanup. The project volumes, temporary logs/classpath file, and Playwright artifacts/traces are removed; no existing Compose project or other run's output directory is targeted. Do not put credentials or tokens into tracked files or reports. Compose defaults are local-only development values, not production credentials.

The API test covers the persisted historical call identity `751` and portal ID `751092`, catalog navigation, the fixture's `EXTRACTED` and `MISSING` field states, and a rendered evidence quote/link. The test does not simulate the public Finep portal or the paid provider.

## Separate live Finep/OpenAI demonstration

Live execution is a separate, opt-in operational exercise and was **not run** during the offline verification. It requires explicit authorization for external Finep access and any paid OpenAI request, network/DNS access, a currently functioning public Finep portal/document OAuth flow, and an `OPENAI_API_KEY` configured only in the process environment. `OPENAI_MODEL` defaults to `gpt-6-luna`; reasoning effort defaults to `low`. Never pass credential values as command-line arguments or include environment dumps in logs.

Live execution can fail if Finep changes its public page, OAuth bootstrap/token flow, permitted host list, document API, or document formats. The connector fails closed on policy violations or incompatible flow changes. Provider requests are limited by the configured excerpt selection and extraction limits; failures, rate limits, refusal, incomplete output, and schema rejection are surfaced without fixture fallback in production. Results can vary with source updates and provider behavior. Review outputs/evidence before relying on any extracted fact.

Before a live run, agree on the single opportunity/documents and the request budget. Record UTC start/end time, configured model and schema version, ingestion run ID/status, document/field counts, provider input/output token usage when reported, and failures. Estimate cost using the current official OpenAI model pricing at run time: multiply reported input and output tokens by their respective published per-token rates, convert units as specified by the pricing page, and record the pricing URL/date and arithmetic. Report unavailable usage as unavailable; do not infer tokens from payload size. Do not log the API key, OAuth client secret/token, raw authorization headers, or sensitive provider payloads. No cost estimate is applicable to the deterministic offline run (provider token counts are zero).
