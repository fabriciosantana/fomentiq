package com.fomentiq.api;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fomentiq.FomentiqApplication;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = FomentiqApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpportunityApiIntegrationTest {
    private static final UUID AGENCY = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROGRAM = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CALL = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CALL = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID VERSION_ONE = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_TWO = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID DOCUMENT = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_DOCUMENT = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID DOCUMENT_VERSION_ONE = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT_VERSION_TWO = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_DOCUMENT_VERSION = UUID.fromString("60000000-0000-0000-0000-000000000003");
    private static final UUID RUN = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID DEADLINE_FIELD = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CONFLICT_FIELD = UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final UUID MISSING_FIELD = UUID.fromString("80000000-0000-0000-0000-000000000003");
    private static final UUID THEME_FIELD = UUID.fromString("80000000-0000-0000-0000-000000000004");
    private static final UUID REGION_FIELD = UUID.fromString("80000000-0000-0000-0000-000000000005");
    private static final UUID EVIDENCE_ONE = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID EVIDENCE_TWO = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID CROSS_CALL_EVIDENCE = UUID.fromString("90000000-0000-0000-0000-000000000003");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fomentiq").withUsername("fomentiq").withPassword("fomentiq");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE evidence, extracted_field, extraction_execution, call_document_version, call_document, "
                + "source_snapshot, call_version, ingestion_run, call_for_proposals, funding_program, funding_agency CASCADE");
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        jdbc.update("INSERT INTO funding_agency (id,name,acronym,official_url) VALUES (?,?,?,?)",
                AGENCY, "Financiadora de Estudos e Projetos", "FINEP", "https://finep.gov.br");
        jdbc.update("INSERT INTO funding_program (id,agency_id,name,description,instrument_types) "
                        + "VALUES (?,?,?, ?,ARRAY['SUBVENTION'])",
                PROGRAM, AGENCY, "PROINFRA", "Pesquisa e infraestrutura");
        insertCall(CALL, "751", "751092", "PROINFRA 2024 Expansão", "OPEN", null, now);
        insertCall(OTHER_CALL, "999", "999092", "Zeta Pesquisa", "CLOSED", "2026-04-01", now);
        jdbc.update("INSERT INTO call_version (id,call_id,version_number,content_hash,detected_at) VALUES (?,?,?,?,?)",
                VERSION_ONE, CALL, 1, "a".repeat(64), Timestamp.from(now.minusSeconds(10)));
        jdbc.update("INSERT INTO call_version (id,call_id,version_number,content_hash,detected_at,supersedes_version_id) "
                        + "VALUES (?,?,?,?,?,?)", VERSION_TWO, CALL, 2, "b".repeat(64), Timestamp.from(now), VERSION_ONE);
        jdbc.update("INSERT INTO call_version (id,call_id,version_number,content_hash,detected_at) VALUES (?,?,?,?,?)",
                UUID.fromString("40000000-0000-0000-0000-000000000003"), OTHER_CALL, 1, "c".repeat(64), Timestamp.from(now));
        jdbc.update("INSERT INTO ingestion_run (id,source_type,started_at,status) VALUES (?,?,?,'SUCCEEDED')",
                RUN, "FINEP", Timestamp.from(now));
        jdbc.update("INSERT INTO call_document (id,call_id,external_id,document_type,title,source_url) VALUES (?,?,?,?,?,?)",
                DOCUMENT, CALL, "notice-751", "NOTICE", "Edital", "https://finep.gov.br/edital.pdf?token=not-public"
                        + "&X-Amz-Signature=signed-secret&X-Amz-Credential=credential-secret&download=true");
        jdbc.update("INSERT INTO call_document_version (id,document_id,content_hash,storage_key,media_type,source_url,downloaded_at) "
                        + "VALUES (?,?,?,?,?,?,?)", DOCUMENT_VERSION_ONE, DOCUMENT, "d".repeat(64), "private/key/1",
                "application/pdf", "https://finep.gov.br/edital.pdf?token=not-public&X-Amz-Signature=signed-secret"
                        + "&X-Amz-Credential=credential-secret&download=true",
                Timestamp.from(now.minusSeconds(10)));
        jdbc.update("INSERT INTO call_document_version (id,document_id,content_hash,storage_key,media_type,source_url,downloaded_at) "
                        + "VALUES (?,?,?,?,?,?,?)", DOCUMENT_VERSION_TWO, DOCUMENT, "e".repeat(64), "private/key/2",
                "application/pdf", "https://finep.gov.br/edital.pdf?token=not-public&X-Amz-Signature=signed-secret"
                        + "&X-Amz-Credential=credential-secret&download=true", Timestamp.from(now));
        jdbc.update("INSERT INTO call_document (id,call_id,external_id,document_type,title,source_url) VALUES (?,?,?,?,?,?)",
                OTHER_DOCUMENT, OTHER_CALL, "notice-999", "NOTICE", "Edital de outra oportunidade",
                "https://finep.gov.br/outro-edital.pdf");
        jdbc.update("INSERT INTO call_document_version (id,document_id,content_hash,storage_key,media_type,source_url,downloaded_at) "
                        + "VALUES (?,?,?,?,?,?,?)", OTHER_DOCUMENT_VERSION, OTHER_DOCUMENT, "f".repeat(64),
                "private/other-opportunity", "application/pdf", "https://finep.gov.br/outro-edital.pdf", Timestamp.from(now));
        field(DEADLINE_FIELD, "deadline", "2026-12-15T17:00:00-03:00", "2026-12-15", "VALIDATED", now);
        field(CONFLICT_FIELD, "eligibility", "[\"ICT pública\",\"ICT privada\"]", null, "CONFLICT", now);
        field(MISSING_FIELD, "trl", null, null, "MISSING", now);
        field(THEME_FIELD, "technologyThemes", "[\"Pesquisa\",\"Saúde\"]", null, "EXTRACTED", now);
        field(REGION_FIELD, "regions", "{\"scope\":\"Brasil\"}", null, "EXTRACTED", now);
        evidence(EVIDENCE_ONE, DEADLINE_FIELD, DOCUMENT_VERSION_TWO,
                "Prazo prorrogado para 15 de dezembro de 2026", 15, "8.1");
        evidence(EVIDENCE_TWO, CONFLICT_FIELD, DOCUMENT_VERSION_TWO, "São elegíveis ICTs públicas", 4, "3.1");
        evidence(CROSS_CALL_EVIDENCE, DEADLINE_FIELD, OTHER_DOCUMENT_VERSION,
                "Trecho vinculado indevidamente a outro edital", 9, "9.1");
    }

    @Test
    void paginatesFiltersAndSearchesWithHistoricalAndPortalIdentity() throws Exception {
        JsonNode filtered = json(get("/api/opportunities?page=0&size=1&q=PROINFRA&status=OPEN"
                + "&deadlineFrom=2026-12-01&deadlineTo=2026-12-31&instrument=SUBVENTION&region=Brasil&theme="
                + encode("Saúde"), 200));
        assertThat(filtered.path("content").size()).isEqualTo(1);
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.path("totalPages").asInt()).isEqualTo(1);
        assertThat(filtered.path("content").get(0).path("externalId").asText()).isEqualTo("751");
        assertThat(filtered.path("content").get(0).path("portalEntryId").asText()).isEqualTo("751092");
        assertThat(filtered.path("content").get(0).path("deadline").asText()).isEqualTo("2026-12-15");
        JsonNode page = json(get("/api/opportunities?q=pesquisa&page=1&size=1", 200));
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("page").asInt()).isEqualTo(1);
        JsonNode injectedText = json(get("/api/opportunities?q=" + encode("' OR 1=1 --"), 200));
        assertThat(injectedText.path("totalElements").asLong()).isZero();
        get("/api/opportunities?size=101", 400);
    }

    @Test
    void detailContainsCurrentFieldsStatesAndFieldBoundEvidenceButNotExecutionInternals() throws Exception {
        String body = get("/api/opportunities/" + CALL, 200);
        JsonNode detail = mapper.readTree(body);
        assertThat(detail.path("opportunity").path("externalId").asText()).isEqualTo("751");
        assertThat(detail.path("opportunity").path("portalEntryId").asText()).isEqualTo("751092");
        assertThat(detail.path("currentCallVersion").asInt()).isEqualTo(2);
        JsonNode deadline = field(detail, "deadline");
        assertThat(deadline.path("validationStatus").asText()).isEqualTo("VALIDATED");
        assertThat(deadline.path("evidence").get(0).path("extractedFieldId").asText())
                .isEqualTo(DEADLINE_FIELD.toString());
        assertThat(deadline.path("evidence").size()).isEqualTo(1);
        assertThat(deadline.path("evidence").get(0).path("locator").path("segmentId").asText())
                .isEqualTo("golden-p15");
        JsonNode conflict = field(detail, "eligibility");
        assertThat(conflict.path("validationStatus").asText()).isEqualTo("CONFLICT");
        assertThat(conflict.path("value").isArray()).as("conflict value: %s", conflict).isTrue();
        assertThat(conflict.path("value").get(0).asText()).isEqualTo("ICT pública");
        JsonNode missing = field(detail, "trl");
        assertThat(missing.path("validationStatus").asText()).isEqualTo("MISSING");
        assertThat(missing.path("value").isNull()).isTrue();
        assertThat(field(detail, "technologyThemes").path("validationStatus").asText()).isEqualTo("EXTRACTED");
        assertThat(body).doesNotContain("not-public", "signed-secret", "credential-secret", "private/key",
                "private/secret", "oauth-secret", "response_body", "input_tokens", "output_tokens");
        get("/api/opportunities/" + UUID.randomUUID(), 404);
    }

    @Test
    void exposesPagedCallDocumentAndDocumentVersionHistoryWithSanitizedLinks() throws Exception {
        JsonNode callVersions = json(get("/api/opportunities/" + CALL + "/versions?page=0&size=1", 200));
        assertThat(callVersions.path("totalElements").asLong()).isEqualTo(2);
        assertThat(callVersions.path("content").get(0).path("versionNumber").asInt()).isEqualTo(2);
        JsonNode documents = json(get("/api/opportunities/" + CALL + "/documents", 200));
        assertThat(documents.path("content").get(0).path("versionCount").asInt()).isEqualTo(2);
        assertThat(documents.path("content").get(0).path("sourceUrl").asText())
                .isEqualTo("https://finep.gov.br/edital.pdf?download=true");
        assertThat(documents.path("totalElements").asLong()).isEqualTo(1);
        JsonNode versions = json(get("/api/opportunities/" + CALL + "/documents/" + DOCUMENT + "/versions", 200));
        assertThat(versions.path("totalElements").asLong()).isEqualTo(2);
        assertThat(versions.path("content").get(0).path("sourceUrl").asText())
                .isEqualTo("https://finep.gov.br/edital.pdf?download=true");
        get("/api/opportunities/" + CALL + "/documents/" + UUID.randomUUID() + "/versions", 404);
        get("/api/opportunities/" + CALL + "/documents/" + OTHER_DOCUMENT + "/versions", 404);
    }

    @Test
    void evidenceIsPagedAndOnlyContainsFieldLinkedSourceLocators() throws Exception {
        JsonNode evidence = json(get("/api/opportunities/" + CALL + "/evidence?field=deadline", 200));
        assertThat(evidence.path("totalElements").asLong()).isEqualTo(1);
        assertThat(evidence.path("content").get(0).path("extractedFieldId").asText())
                .isEqualTo(DEADLINE_FIELD.toString());
        assertThat(evidence.path("content").get(0).path("fieldName").asText()).isEqualTo("deadline");
        assertThat(evidence.path("content").get(0).path("page").asInt()).isEqualTo(15);
        JsonNode allEvidence = json(get("/api/opportunities/" + CALL + "/evidence", 200));
        assertThat(allEvidence.path("totalElements").asLong()).isEqualTo(2);
        get("/api/opportunities/" + CALL + "/evidence?size=101", 400);
        get("/api/opportunities/" + UUID.randomUUID() + "/evidence", 404);
    }

    @Test
    void onlyPublishesReadOnlyOperationsAndOpenApiContract() throws Exception {
        get("/api/opportunities", "POST", 405);
        get("/api/admin/ingestion", 404);
        get("/api/ingestion/reprocess", "POST", 404);
        String contract = get("/openapi.yaml", 200);
        Map<?, ?> spec = new Yaml().load(contract);
        assertThat(spec.get("openapi")).isEqualTo("3.1.0");
        Map<?, ?> paths = (Map<?, ?>) spec.get("paths");
        assertThat(paths.keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("/api/opportunities", "/api/opportunities/{callId}",
                        "/api/opportunities/{callId}/versions", "/api/opportunities/{callId}/documents",
                        "/api/opportunities/{callId}/documents/{documentId}/versions",
                        "/api/opportunities/{callId}/evidence", "/openapi.yaml");
        assertThat(paths.values().stream().flatMap(path -> ((Map<?, ?>) path).keySet().stream())
                .map(Object::toString).toList()).allMatch("get"::equals);
        Map<?, ?> parameterDefinitions = (Map<?, ?>) ((Map<?, ?>) spec.get("components")).get("parameters");
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities", List.of("q", "status", "deadlineFrom", "deadlineTo",
                "instrument", "region", "theme", "page", "size"), List.of("200", "400"));
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities/{callId}", List.of("callId"),
                List.of("200", "400", "404"));
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities/{callId}/versions", List.of("callId", "page", "size"),
                List.of("200", "400", "404"));
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities/{callId}/documents", List.of("callId", "page", "size"),
                List.of("200", "400", "404"));
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities/{callId}/documents/{documentId}/versions",
                List.of("callId", "documentId", "page", "size"), List.of("200", "400", "404"));
        assertContractOperation(paths, parameterDefinitions, "/api/opportunities/{callId}/evidence",
                List.of("callId", "field", "page", "size"),
                List.of("200", "400", "404"));
        assertContractOperation(paths, parameterDefinitions, "/openapi.yaml", List.of(), List.of("200"));
        assertThat(spec.containsKey("security")).isFalse();
        assertThat(((Map<?, ?>) spec.get("components")).containsKey("securitySchemes")).isFalse();
        assertThat(new ClassPathResource("static/openapi.yaml").exists()).isTrue();
    }

    private void insertCall(UUID id, String externalId, String portalEntryId, String title,
                            String status, String deadline, Instant now) {
        jdbc.update("INSERT INTO call_for_proposals (id,program_id,agency_id,external_id,portal_entry_id,title,summary,"
                        + "instrument_type,status,deadline,official_url,detected_at,last_checked_at) "
                        + "VALUES (?,?,?,?,?,?,?,'SUBVENTION',?,?,?, ?,?)", id, PROGRAM, AGENCY, externalId,
                portalEntryId, title, "Chamada de pesquisa e inovação", status,
                deadline == null ? null : java.sql.Date.valueOf(java.time.LocalDate.parse(deadline)),
                "https://finep.gov.br/calls/" + externalId, Timestamp.from(now), Timestamp.from(now));
    }

    private JsonNode field(JsonNode detail, String name) {
        for (JsonNode field : detail.path("fields")) if (name.equals(field.path("name").asText())) return field;
        throw new AssertionError("Field missing from opportunity detail: " + name);
    }

    private JsonNode json(String body) throws Exception { return mapper.readTree(body); }

    private String get(String path, int expectedStatus) throws Exception { return get(path, "GET", expectedStatus); }

    private String get(String path, String method, int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if ("POST".equals(method)) request.POST(HttpRequest.BodyPublishers.noBody()); else request.GET();
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("HTTP response for %s %s", method, path).isEqualTo(expectedStatus);
        return response.body();
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private void assertContractOperation(Map<?, ?> paths, Map<?, ?> parameterDefinitions, String path,
                                         List<String> expectedParameters,
                                         List<String> expectedResponses) {
        Map<?, ?> pathItem = (Map<?, ?>) paths.get(path);
        Map<?, ?> operation = (Map<?, ?>) pathItem.get("get");
        Object parameterValue = operation.get("parameters");
        List<?> parameterList = parameterValue instanceof List<?> values ? values : List.of();
        List<String> parameters = parameterList.stream()
                .map(parameter -> (Map<?, ?>) parameter)
                .map(parameter -> parameter.containsKey("$ref")
                        ? (Map<?, ?>) parameterDefinitions.get(((String) parameter.get("$ref"))
                                .substring(((String) parameter.get("$ref")).lastIndexOf('/') + 1))
                        : parameter.get("name").toString())
                .map(parameter -> parameter instanceof Map<?, ?> referenced
                        ? referenced.get("name").toString() : parameter.toString())
                .toList();
        assertThat(parameters).containsExactlyElementsOf(expectedParameters);
        assertThat(((Map<?, ?>) operation.get("responses")).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrderElementsOf(expectedResponses);
    }

    private void field(UUID id, String name, String value, String rawValue, String status, Instant now) {
        jdbc.update("INSERT INTO extracted_field (id,call_version_id,field_name,normalized_value,raw_value,"
                        + "validation_status,extraction_method,created_at,ingestion_run_id) VALUES (?,?,?,?,?,?,? ,?,?)",
                id, VERSION_TWO, name, value, rawValue, status, "fixture", Timestamp.from(now), RUN);
    }

    private void evidence(UUID id, UUID field, UUID documentVersion, String quote, int page, String section) {
        jdbc.update("INSERT INTO evidence (id,extracted_field_id,document_version_id,source_url,page,section,"
                        + "quote_fragment,locator_json) VALUES (?,?,?,?,?,?,?,?::jsonb)", id, field,
                documentVersion, "https://finep.gov.br/edital.pdf?token=not-public&download=true", page,
                section, quote, "{\"page\":" + page + ",\"segmentId\":\"golden-p15\","
                        + "\"storageKey\":\"private/secret\",\"token\":\"oauth-secret\"}");
    }
}
