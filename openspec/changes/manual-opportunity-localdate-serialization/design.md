# Design: LocalDate in editorial JSONB

`JdbcManualOpportunityStore` receives the application's `com.fasterxml.jackson.databind.ObjectMapper`, which is Jackson 2. The application defines that mapper in `JsonConfiguration` with `new ObjectMapper()` and no modules. The project separately has Spring/Jackson 3 components; those are not the type used by this JDBC adapter.

Keep the fix local to the persistence adapter: copy the injected Jackson 2 mapper, register `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`, and disable `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` on the copy. This preserves other mapper configuration while guaranteeing JSONB `LocalDate` values are ISO-8601 strings. The same mapper copy reads the persisted content and its existing `deadline` string is parsed as `LocalDate`.

No database change is needed: the JSONB content shape already contains `deadline`, and null remains JSON null. API DTO shape and date semantics remain unchanged.
