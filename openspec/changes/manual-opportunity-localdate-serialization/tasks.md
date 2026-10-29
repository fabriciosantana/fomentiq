## 1. LocalDate persistence regression
- [x] 1.1 Add PostgreSQL/Testcontainers regression coverage for create/reload, deadline update, null deadline, ISO-8601 JSONB, and custom sections/fields.
- [x] 1.2 Configure the JDBC adapter's Jackson 2 mapper copy with JavaTimeModule and ISO-string date output; verify deserialization through the persisted content path.
- [x] 1.3 Run backend clean verify, manual-editorial offline E2E, OpenSpec strict, and independent read-only review; record the results.
