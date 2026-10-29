# Spec Delta

## Purpose

Give reviewers a localized, focused interface for examining and correcting opportunity fields while preserving structured rules, source relationships, and the distinction between human publication and machine extraction.

## ADDED Requirements

### Requirement: Localize curation labels and values in Brazilian Portuguese
The curation interface SHALL present field names, validation and revision states, messages, controls, dates, money, and percentages in Brazilian Portuguese without changing persisted property names, API/schema contracts, or original source evidence.

#### Scenario: Reviewer views a field
- **WHEN** a reviewer opens an opportunity field in the curation interface
- **THEN** the field name and state use a centralized friendly Portuguese label while technical property names remain available only where needed for audit

#### Scenario: Reviewer views a typed value
- **WHEN** a date, money amount, percentage, or revision timestamp is displayed
- **THEN** it uses an appropriate pt-BR format and preserves its original structured value for editing and persistence

### Requirement: Review one selected field at a time
The curation interface SHALL show a concise field summary and open an editor only for the selected field, including its current effective value, technical state, source, approved revision when present, criticality, and review progress.

#### Scenario: Reviewer selects a field
- **WHEN** a reviewer chooses a field from an opportunity summary
- **THEN** only that field's editor is open and its current value, extraction state, publication origin, and approved revision are distinguishable

#### Scenario: Critical field and review progress are displayed
- **WHEN** an opportunity contains critical fields and human-reviewed fields
- **THEN** critical fields are visibly highlighted and the interface reports review progress without changing technical validation states

### Requirement: Edit supported structured fields with typed controls
The curation interface SHALL provide accessible field-appropriate controls for supported structured values and SHALL preserve the complete structure, nulls, conditions, exceptions, and list entries unless the reviewer deliberately changes them.

#### Scenario: Reviewer edits a composite rule
- **WHEN** a reviewer edits eligibility, counterpart, regions, project funding, required documents, or evaluation criteria
- **THEN** controls expose every existing component and condition, and saving retains untouched components without replacing the value with raw JSON

#### Scenario: Reviewer edits scalar and list fields
- **WHEN** a reviewer edits a supported date, money, boolean, text, TRL range, URL list, or text list
- **THEN** the interface uses a typed control and submits a value compatible with the existing API field schema

#### Scenario: Current value is null or absence is confirmed
- **WHEN** a field value is null or the reviewer selects confirmed absence
- **THEN** the editor presents an explicit empty/absence state without inventing a value or citation

### Requirement: Display responsible agencies without a redundant free-text override
The curation interface SHALL display responsible agencies from the existing structured opportunity-agency relation and SHALL NOT offer a free-text agency edit that can contradict that relation.

#### Scenario: Opportunity has multiple responsible agencies
- **WHEN** an opportunity has more than one associated responsible agency
- **THEN** all associated agencies and their existing roles are displayed as read-only relationship data

#### Scenario: Agency extraction differs from agency relations
- **WHEN** an extracted agency field is present alongside responsible agency relations
- **THEN** the interface identifies the displayed relationship as responsible agencies and does not let a text edit silently change the structured relation

### Requirement: Bind document version selection to its document
The curation interface SHALL ensure that a selected document version belongs to the currently selected document and opportunity, clearing an incompatible version when the document changes.

#### Scenario: Reviewer changes the selected document
- **WHEN** a reviewer selects a different document
- **THEN** the prior version selection is cleared and only versions of the newly selected document can be submitted

### Requirement: Preserve machine extraction and documentary evidence
Human curation display and editing SHALL preserve original extracted values, technical states, existing evidence, and literal source content, and SHALL NOT present a human decision as an AI validation state.

#### Scenario: Reviewer publishes a human value
- **WHEN** an approved human value is shown in the curation interface
- **THEN** it is distinguished from the unchanged original extraction and technical validation state

#### Scenario: Evidence has no supporting literal quote
- **WHEN** an evidence locator has no preserved quote
- **THEN** the interface does not invent a citation or imply that the locator proves the displayed value
