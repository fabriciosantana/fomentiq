# Manual opportunity LocalDate serialization

## Why

Saving a manual opportunity with a non-null deadline currently fails because the Jackson 2 mapper used by the JDBC adapter has no Java Time module.

## What changes

Configure the adapter's existing Jackson 2 mapper copy to serialize and deserialize `LocalDate`, with persisted JSON dates represented as ISO-8601 strings. Add PostgreSQL regression coverage for create, reload, deadline update, null deadline, and custom sections/fields.

## Out of scope

No schema migration, metamodel change, public contract change, frontend redesign, fixture change, or generic Jackson typing configuration.
