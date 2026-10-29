# Manual Opportunity Authoring and Editorial Catalog Prototype

## ADDED Requirements

### Requirement: Isolated demonstration workspace
The prototype MUST identify itself as a demonstration using fictional data and MUST state that changes exist only in the current browser session. It MUST operate independently from the production frontend and MUST NOT call application APIs or external services.

#### Scenario: Administrator opens the prototype
- **WHEN** an administrator opens the standalone prototype
- **THEN** the interface identifies the data as fictional demonstration content
- **AND** explains that edits and publication are simulated and are not persisted

### Requirement: Manual opportunity editing
The prototype MUST let an administrator create and edit an opportunity with title, responsible institution, summary, deadline, status, and official URL. It MUST support adding, removing, and reordering custom sections and custom fields without requiring knowledge of JSON, schemas, internal identifiers, or database structure.

#### Scenario: Administrator creates a custom section and field
- **WHEN** an administrator adds a section and a field with a name, supported type, and value
- **THEN** the field appears under that section in the editor and preview
- **AND** its displayed order follows the administrator's section and field order

### Requirement: Supported custom field types
The prototype MUST provide type-appropriate controls and public rendering for short text, long text, date, currency, link, and list fields. It MUST preserve the entered values when sections or fields are reordered.

#### Scenario: Administrator edits supported values
- **WHEN** the administrator enters values for the supported field types
- **THEN** the preview renders each value readably using Brazilian Portuguese formatting where applicable
- **AND** list entries and links are presented as readable content rather than raw object data

### Requirement: Preview and simulated publication
The prototype MUST provide an editorial preview before publication and MUST simulate publish and unpublish actions without transmitting or persisting opportunity data. The preview MUST be faithful to the public detail presentation.

#### Scenario: Administrator previews and publishes an opportunity
- **WHEN** the administrator reviews an opportunity and simulates publication
- **THEN** the same editorial page layout is shown in preview
- **AND** the opportunity becomes visible in the prototype public catalog for the current session
- **AND** the interface makes clear that publication is simulated

### Requirement: Public catalog visibility
The prototype public catalog MUST display only demonstration opportunities in the published state. Its detail page MUST present title and summary prominently, key facts together, and ordered custom sections as editorial content rather than a card for every field.

#### Scenario: Visitor browses published opportunities
- **WHEN** a visitor opens the prototype catalog
- **THEN** only locally published demonstration opportunities are listed
- **AND** selecting one opens its editorial detail page with sections in configured order
- **AND** drafts remain unavailable through the public catalog navigation

### Requirement: Responsive and accessible navigation
The prototype MUST provide simple navigation between administration, preview, catalog, and opportunity detail; controls MUST have accessible names and remain usable at narrow viewport widths.

#### Scenario: Visitor uses the prototype on a narrow screen
- **WHEN** the viewport width is reduced to a mobile size
- **THEN** the navigation and editorial content reflow without horizontal page overflow
- **AND** the administrator can still operate section, field, preview, and publication controls
