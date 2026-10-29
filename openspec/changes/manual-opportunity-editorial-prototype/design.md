# Design

## Context

See `proposal.md` for the product motivation. The production application and approved prototype are preserved as-is. This prototype must be independently launchable in a Codespace and must not share runtime state or routes with the existing application.

## Goals / Non-Goals

**Goals:**
- Demonstrate a concise authoring path and faithful editorial preview using realistic but fictional data.
- Make custom sections and fields visible in the public page in administrator-defined order.
- Allow visual feedback on drafting and publication without persisting data outside the page session.

**Non-Goals:**
- Production-ready persistence, APIs, authentication, authorization, audit history, or concurrency control.
- Ingestion, extraction, evidence review, real opportunity data, external links beyond clearly marked fictional examples, or production application changes.

## Decisions

1. **Standalone static prototype:** use HTML, CSS, and browser JavaScript in a dedicated `prototypes/manual-opportunity-editorial/` directory. Reuse the repository's installed Playwright tooling only for local checks; do not add a runtime dependency or connect this directory to the Vite application. A static prototype is easier to launch and discard after visual evaluation than a second app stack.
2. **Session-only demonstration state:** store edits in JavaScript memory for the current page session and initialize from fictional examples. Simulated publish/unpublish changes a local status and public visibility only. Reloading resets the demonstration, making the lack of persistence explicit.
3. **One guided workspace with progressive disclosure:** the editor presents basic opportunity information first, then custom sections/fields and a public preview. Section and field controls remain compact and support keyboard-accessible move/remove actions. This avoids exposing JSON or data-model terminology.
4. **Small fixed field-type set:** short text, long text, date, currency, link, and list have corresponding inputs and public renderers. Values are rendered as escaped text through DOM text APIs; links are rendered only for valid HTTP(S) URLs with credentials excluded.
5. **Published-only public catalog:** the prototype catalog/detail navigation derives from local examples whose simulated state is published; drafts remain visible in administration and preview but not public routes. The preview clearly labels unpublished content as a preview.
6. **Browser-level checks without new packages:** a small Playwright smoke test uses the repository's existing frontend Playwright installation and a local static server. Checks cover the admin flow, custom content/order, public visibility, and a narrow viewport.

## Risks / Trade-offs

- [A prototype may be mistaken for a production workflow] → show a persistent “Demonstração — dados fictícios; alterações não são salvas” notice and make simulation explicit in action labels.
- [Examples may imply real funding terms] → use clearly fictional institution/program names and dates, and avoid recognizable real call details.
- [Preview and public page may diverge] → use the same section/field renderer for both, with only the preview banner and unpublished-state context differing.
- [Static prototype may not reflect eventual application constraints] → keep behavior deliberately limited and list persistence, validation, security, and API decisions as pending for a later approved implementation.

## Migration Plan

Not applicable. The prototype is not deployed or connected to the existing application and can be removed/replaced after visual review without data migration.
