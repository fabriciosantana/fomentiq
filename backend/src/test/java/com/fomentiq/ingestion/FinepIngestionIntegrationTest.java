package com.fomentiq.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.documents.domain.CallDocumentRepository;
import com.fomentiq.documents.domain.CallDocumentVersionRepository;
import com.fomentiq.extraction.domain.ExtractedFieldRepository;
import com.fomentiq.extraction.domain.ValidationStatus;
import com.fomentiq.funding.domain.CallForProposalsRepository;
import com.fomentiq.funding.domain.CallVersion;
import com.fomentiq.funding.domain.CallVersionRepository;
import com.fomentiq.ingestion.application.FinepIngestionOutcome;
import com.fomentiq.ingestion.application.FinepIngestionService;
import com.fomentiq.ingestion.domain.IngestionRunRepository;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshotRepository;
import com.fomentiq.extraction.FixtureStructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionFailure;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.domain.ExtractedField;
import com.fomentiq.storage.application.ArtifactStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = {com.fomentiq.FomentiqApplication.class, FinepIngestionIntegrationTest.TestBeans.class})
class FinepIngestionIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fomentiq")
            .withUsername("fomentiq")
            .withPassword("fomentiq");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired FinepIngestionService ingestion;
    @Autowired FixtureSourceConnector.FixtureControl control;
    @Autowired ExtractionControl extractionControl;
    @Autowired InMemoryArtifactStore storage;
    @Autowired CallForProposalsRepository calls;
    @Autowired CallVersionRepository callVersions;
    @Autowired CallDocumentRepository documents;
    @Autowired CallDocumentVersionRepository documentVersions;
    @Autowired IngestionRunRepository runs;
    @Autowired SourceSnapshotRepository snapshots;
    @Autowired ExtractedFieldRepository extractedFields;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void resetFixtureControls() {
        jdbc.execute("TRUNCATE TABLE evidence, extracted_field, extraction_execution, call_document_version, call_document, "
                + "source_snapshot, call_version, ingestion_run, call_for_proposals, funding_program, funding_agency CASCADE");
        control.synthetic = false;
        control.failedDocumentId = "";
        extractionControl.fail = false;
        extractionControl.missingEvidenceField = "";
        storage.clear();
    }

    @Test
    void fixturePagesAreReplayedOfflineAsSanitizedHtmlAlongsideApiJson() throws java.io.IOException {
        String discoveryHtml = new String(new ClassPathResource(
                "fixtures/finep/proinfra-2024-expansao-751/discovery.html").getContentAsByteArray(), StandardCharsets.UTF_8);
        String callHtml = new String(new ClassPathResource(
                "fixtures/finep/proinfra-2024-expansao-751/call.html").getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(discoveryHtml).contains("<!DOCTYPE html>").doesNotContain("<script", "CLIENT_SECRET");
        assertThat(callHtml).contains("751092", "PROINFRA").doesNotContain("<script", "CLIENT_SECRET");
        assertThat(ingestion.ingest().detectedCount()).isEqualTo(1);
    }

    @Test
    void discoversHistoricalIdentityPersistsSnapshotsAndAllFixtureDocumentsOffline() {
        FinepIngestionOutcome result = ingestion.ingest();

        assertThat(result.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(result.detectedCount()).isEqualTo(1);
        assertThat(result.changedCount()).isEqualTo(19);
        assertThat(calls.findByAgencyAcronymAndExternalId("FINEP", "751")).isPresent()
                .get().extracting(call -> call.getPortalEntryId()).isEqualTo("751092");
        assertThat(calls.count()).isEqualTo(1);
        assertThat(callVersions.count()).isEqualTo(1);
        assertThat(documents.count()).isEqualTo(18);
        assertThat(documentVersions.count()).isEqualTo(18);
        assertThat(documentVersions.findAll()).allSatisfy(version ->
                assertThat(version.getContentHash()).matches("[0-9a-f]{64}"));
        assertThat(storage.objects().entrySet().stream().filter(entry -> entry.getKey().endsWith(".pdf")).toList())
                .hasSize(18).allSatisfy(entry -> assertThat(entry.getKey())
                        .isEqualTo("content/" + sha256(entry.getValue()) + ".pdf"));
        assertThat(snapshots.findByIngestionRun_Id(result.runId())).hasSize(3);
        CallVersion currentVersion = callVersions.findTopByCall_IdOrderByVersionNumberDesc(
                calls.findByAgencyAcronymAndExternalId("FINEP", "751").orElseThrow().getId()).orElseThrow();
        assertThat(extractedFields.findAll().stream().filter(field -> field.getFieldName().equals("trl")))
                .singleElement().extracting(field -> field.getValidationStatus()).isEqualTo(ValidationStatus.MISSING);
        assertThat(extractedFields.findAll().stream().filter(field -> field.getFieldName().equals("deadline")))
                .singleElement().satisfies(field -> {
                    assertThat(field.getCallVersionId()).isEqualTo(currentVersion.getId());
                    assertThat(field.getIngestionRunId()).isEqualTo(result.runId());
                    assertThat(field.getCreatedAt()).isNotNull();
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence e "
                        + "JOIN extracted_field ef ON ef.id = e.extracted_field_id "
                        + "JOIN call_version cv ON cv.id = ef.call_version_id "
                        + "JOIN call_document_version cdv ON cdv.id = e.document_version_id "
                        + "JOIN call_document cd ON cd.id = cdv.document_id AND cd.call_id = cv.call_id "
                        + "WHERE ef.ingestion_run_id = ?", Integer.class, result.runId()))
                .isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM extracted_field ef "
                        + "JOIN ingestion_run ir ON ir.id = ef.ingestion_run_id "
                        + "JOIN call_version cv ON cv.id = ef.call_version_id "
                        + "WHERE ef.created_at IS NOT NULL AND ef.extraction_method IS NOT NULL", Integer.class))
                .isEqualTo(extractedFields.count());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM extraction_execution WHERE ingestion_run_id = ?",
                Integer.class, result.runId())).isEqualTo(1);
        assertThat(storage.objects()).hasSize(21);
        assertThat(runs.findById(result.runId())).get().extracting(run -> run.getStatus())
                .isEqualTo(IngestionRunStatus.SUCCEEDED);
    }

    @Test
    void repeatedFixtureIsIdempotentAndChangedSyntheticCallPreservesPriorVersion() {
        FinepIngestionOutcome first = ingestion.ingest();
        var originalFields = extractedFields.findAll();
        var originalCallVersionId = originalFields.getFirst().getCallVersionId();
        var originalFieldIds = originalFields.stream().map(ExtractedField::getId).toList();
        var originalEvidenceIds = jdbc.queryForList("SELECT e.id FROM evidence e "
                + "JOIN extracted_field ef ON ef.id = e.extracted_field_id WHERE ef.call_version_id = ?",
                java.util.UUID.class, originalCallVersionId);
        var originalAttribution = originalFields.stream().collect(java.util.stream.Collectors.toMap(
                field -> field.getFieldName(), field -> field.getId() + ":" + field.getCreatedAt() + ":" + field.getIngestionRunId()));
        FinepIngestionOutcome repeated = ingestion.ingest();
        assertThat(repeated.changedCount()).isZero();
        assertThat(callVersions.count()).isEqualTo(1);
        assertThat(documentVersions.count()).isEqualTo(18);
        assertThat(extractedFields.count()).isEqualTo(originalFields.size());
        assertThat(extractedFields.findAll()).allSatisfy(field ->
                assertThat(field.getId() + ":" + field.getCreatedAt() + ":" + field.getIngestionRunId())
                        .isEqualTo(originalAttribution.get(field.getFieldName())));

        control.synthetic = true;
        FinepIngestionOutcome changed = ingestion.ingest();
        assertThat(changed.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(changed.changedCount()).isEqualTo(1);
        assertThat(callVersions.count()).isEqualTo(2);
        assertThat(calls.count()).isEqualTo(1);
        assertThat(calls.findByAgencyAcronymAndExternalId("FINEP", "751").orElseThrow().getTitle())
                .contains("ALTERAÇÃO SINTÉTICA");
        assertThat(callVersions.findAll()).extracting(CallVersion::getVersionNumber).containsExactlyInAnyOrder(1, 2);
        assertThat(callVersions.findAll()).extracting(CallVersion::getContentHash).doesNotHaveDuplicates();
        assertThat(first.runId()).isNotEqualTo(changed.runId());
        var changedCallVersionId = callVersions.findTopByCall_IdOrderByVersionNumberDesc(
                calls.findByAgencyAcronymAndExternalId("FINEP", "751").orElseThrow().getId()).orElseThrow().getId();
        assertThat(extractedFields.findAll()).filteredOn(field -> field.getCallVersionId().equals(originalCallVersionId))
                .extracting(ExtractedField::getId).containsExactlyInAnyOrderElementsOf(originalFieldIds);
        assertThat(extractedFields.findAll()).filteredOn(field -> field.getCallVersionId().equals(changedCallVersionId))
                .hasSize(ExtractionSchemaV1.FIELDS.size()).allSatisfy(field -> {
                    assertThat(field.getIngestionRunId()).isEqualTo(changed.runId());
                    assertThat(field.getCreatedAt()).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT e.id FROM evidence e "
                + "JOIN extracted_field ef ON ef.id = e.extracted_field_id WHERE ef.call_version_id = ?",
                java.util.UUID.class, originalCallVersionId)).containsExactlyInAnyOrderElementsOf(originalEvidenceIds);
        assertThat(runs.findById(changed.runId()).orElseThrow().getStatus()).isEqualTo(IngestionRunStatus.SUCCEEDED);
    }

    @Test
    void criticalValueWithoutUsableEvidenceCannotBePersistedAsValidated() {
        extractionControl.missingEvidenceField = "totalBudget";

        FinepIngestionOutcome result = ingestion.ingest();

        assertThat(result.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        ExtractedField budget = extractedFields.findAll().stream()
                .filter(field -> field.getFieldName().equals("totalBudget")).findFirst().orElseThrow();
        assertThat(budget.getNormalizedValue()).isNotBlank();
        assertThat(budget.getValidationStatus()).isNotEqualTo(ValidationStatus.VALIDATED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence WHERE extracted_field_id = ?",
                Integer.class, budget.getId())).isZero();
    }

    @Test
    void documentFailureIsRecordedAsPartialRunAndDoesNotEraseOtherDocuments() {
        control.failedDocumentId = "814330";
        FinepIngestionOutcome result = ingestion.ingest();

        assertThat(result.status()).isEqualTo(IngestionRunStatus.PARTIAL);
        assertThat(result.documentFailureCount()).isEqualTo(1);
        assertThat(documentVersions.count()).isEqualTo(17);
        assertThat(runs.findById(result.runId()).orElseThrow().getErrorSummary())
                .contains("1 call document(s)").doesNotContain("token", "secret");
    }

    @Test
    void storageFailureLeavesAnAuditableFailedRun() {
        storage.failWrites(true);
        FinepIngestionOutcome result = ingestion.ingest();

        assertThat(result.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(runs.findById(result.runId())).isPresent();
        assertThat(runs.findById(result.runId()).orElseThrow().getFinishedAt()).isNotNull();
        assertThat(runs.findById(result.runId()).orElseThrow().getErrorSummary())
                .contains("IllegalStateException").doesNotContain("secret", "token");

        storage.failWrites(false);
        FinepIngestionOutcome retried = ingestion.ingest();
        assertThat(retried.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(calls.count()).isEqualTo(1);
        assertThat(callVersions.count()).isEqualTo(1);
        assertThat(runs.findById(retried.runId()).orElseThrow().getStatus()).isEqualTo(IngestionRunStatus.SUCCEEDED);
    }

    @Test
    void databasePersistenceFailureRollsBackCallUnitAndCommitsFailedRunIndependently() {
        jdbc.execute("CREATE FUNCTION fail_source_snapshot_insert() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'controlled database failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_source_snapshot_insert BEFORE INSERT ON source_snapshot "
                + "FOR EACH ROW EXECUTE FUNCTION fail_source_snapshot_insert()");

        FinepIngestionOutcome failed;
        try {
            failed = ingestion.ingest();
        } finally {
            jdbc.execute("DROP TRIGGER fail_source_snapshot_insert ON source_snapshot");
            jdbc.execute("DROP FUNCTION fail_source_snapshot_insert()");
        }

        assertThat(failed.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(runs.findById(failed.runId())).isPresent().get().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(IngestionRunStatus.FAILED);
            assertThat(run.getFinishedAt()).isNotNull();
            assertThat(run.getErrorSummary()).contains("failed").doesNotContain("controlled database", "SQLSTATE");
        });
        assertThat(calls.count()).isZero();
        assertThat(callVersions.count()).isZero();
        assertThat(snapshots.count()).isZero();
        assertThat(documents.count()).isZero();
        assertThat(documentVersions.count()).isZero();
        assertThat(extractedFields.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM extraction_execution", Integer.class)).isZero();
        assertThat(storage.objects()).hasSize(1);

        FinepIngestionOutcome retried = ingestion.ingest();
        assertThat(retried.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(calls.count()).isEqualTo(1);
        assertThat(callVersions.count()).isEqualTo(1);
        assertThat(documents.count()).isEqualTo(18);
        assertThat(documentVersions.count()).isEqualTo(18);
        assertThat(snapshots.findByIngestionRun_Id(retried.runId())).hasSize(3);
        assertThat(storage.objects()).hasSize(21);
    }

    @Test
    void extractionFailureLeavesSourceArtifactsAndFailedRunAvailableForRetry() {
        extractionControl.fail = true;
        FinepIngestionOutcome failed = ingestion.ingest();

        assertThat(failed.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(runs.findById(failed.runId()).orElseThrow().getFinishedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM extraction_execution WHERE ingestion_run_id = ?",
                String.class, failed.runId())).isEqualTo("FAILED");
        assertThat(snapshots.findByIngestionRun_Id(failed.runId())).hasSize(3);
        assertThat(storage.objects()).hasSize(21);
        assertThat(extractedFields.count()).isZero();

        extractionControl.fail = false;
        FinepIngestionOutcome retried = ingestion.ingest();
        assertThat(retried.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(extractedFields.count()).isEqualTo(
                com.fomentiq.extraction.application.ExtractionSchemaV1.FIELDS.size());
        assertThat(calls.count()).isEqualTo(1);
        assertThat(callVersions.count()).isEqualTo(1);
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean FixtureSourceConnector.FixtureControl fixtureControl() {
            return new FixtureSourceConnector.FixtureControl();
        }
        @Bean ExtractionControl extractionControl() { return new ExtractionControl(); }
        @Bean @Primary FixtureSourceConnector fixtureConnector(ObjectMapper mapper,
                                                                 FixtureSourceConnector.FixtureControl control) {
            return new FixtureSourceConnector(mapper, control);
        }
        @Bean @Primary InMemoryArtifactStore memoryStorage() { return new InMemoryArtifactStore(); }
        @Bean @Primary StructuredExtractionProvider fixtureExtraction(ObjectMapper mapper, ExtractionControl control) {
            StructuredExtractionProvider fixture = new FixtureStructuredExtractionProvider(mapper);
            return request -> {
                if (control.fail) throw new StructuredExtractionFailure("fixture", "fixture-v1",
                        "controlled provider failure");
                var response = fixture.extract(request);
                if (control.missingEvidenceField.isBlank()) return response;
                var fields = new java.util.HashMap<>(response.fields());
                var candidate = fields.get(control.missingEvidenceField);
                fields.put(control.missingEvidenceField, new StructuredExtractionProvider.Candidate(
                        candidate.value(), candidate.rawValue(), java.util.List.of(), candidate.conflicts()));
                return new StructuredExtractionProvider.ProviderResponse(response.provider(), response.model(),
                        response.schemaVersion(), response.rawResponse(), response.refusal(), response.incomplete(),
                        response.inputTokens(), response.outputTokens(), java.util.Map.copyOf(fields));
            };
        }
    }

    static class ExtractionControl { private volatile boolean fail; private volatile String missingEvidenceField = ""; }
    static class InMemoryArtifactStore implements ArtifactStore {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();
        private volatile boolean fail;
        @Override public String put(String key, byte[] content, String mediaType) {
            if (fail) throw new IllegalStateException("controlled storage failure");
            data.put(key, content.clone());
            return key;
        }
        void failWrites(boolean enabled) { fail = enabled; }
        Map<String, byte[]> objects() { return data; }
        void clear() { data.clear(); fail = false; }
    }
}
