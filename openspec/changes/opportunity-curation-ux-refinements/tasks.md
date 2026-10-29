# Tasks

## 1. Shared pt-BR presentation

- [x] 1.1 Add centralized labels for extraction fields, validation states, revision states, and value qualifiers; verify public and administrative components render friendly Portuguese labels without changing API payload names.
- [x] 1.2 Add display-only formatting for dates/date-times, currencies, and percentages; verify nulls and original typed values remain unchanged when displayed and edited.

## 2. Focused administrative review

- [x] 2.1 Replace all-fields-expanded editing with a concise field summary and a selected-field editor; show effective/original values, technical state, publication origin, approved revision, criticality, and homologation progress, verified by component tests.
- [x] 2.2 Present responsible agencies from the existing relationship as read-only data, including multiple agencies, and exclude the extracted `agency` text from free-text editing; verify the API/model remains unchanged.

## 3. Typed editors for existing field shapes

- [x] 3.1 Replace raw JSON textareas with typed controls for all currently supported scalar, list, and structured field shapes; verify dates, money, booleans, lists, TRL, nulls, and absence submission against current payload shapes.
- [x] 3.2 Add editable controls for eligibility, counterpart ranges/exemptions, regions, project funding tiers, required documents, and evaluation criteria; tests must prove unedited branches, conditions, exceptions, and null values are preserved.
- [x] 3.3 Bind the selected document version to the current document and localize status, feedback, and accessible control labels; verify changing documents clears the prior version and no stale version can be submitted.

## 4. Integrated verification

- [x] 4.1 Add component coverage for multiple agencies, composite rules, null values, confirmed absence, approved human values, and critical review progress; run frontend lint, typecheck, tests, and production build.
- [x] 4.2 Run the isolated offline Playwright curation flow, backend regression suite, Compose and strict OpenSpec validation; record the results and independent review in the verification report.
