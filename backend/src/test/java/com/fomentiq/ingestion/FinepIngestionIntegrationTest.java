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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = {com.fomentiq.FomentiqApplication.class, FinepIngestionIntegrationTest.TestBeans.class})
@Transactional
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
        control.synthetic = false;
        control.failedDocumentId = "";
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
        assertThat(storage.objects().entrySet().stream().filter(entry -> entry.getKey().contains("/documents/")).toList())
                .hasSize(18).allSatisfy(entry -> assertThat(entry.getKey())
                        .endsWith("/" + sha256(entry.getValue()) + ".pdf"));
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM extraction_execution WHERE ingestion_run_id = ?",
                Integer.class, result.runId())).isEqualTo(1);
        assertThat(storage.objects()).hasSize(21);
        assertThat(runs.findById(result.runId())).get().extracting(run -> run.getStatus())
                .isEqualTo(IngestionRunStatus.SUCCEEDED);
    }

    @Test
    void repeatedFixtureIsIdempotentAndChangedSyntheticCallPreservesPriorVersion() {
        FinepIngestionOutcome first = ingestion.ingest();
        FinepIngestionOutcome repeated = ingestion.ingest();
        assertThat(repeated.changedCount()).isZero();
        assertThat(callVersions.count()).isEqualTo(1);
        assertThat(documentVersions.count()).isEqualTo(18);

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
        @Bean @Primary FixtureSourceConnector fixtureConnector(ObjectMapper mapper,
                                                                 FixtureSourceConnector.FixtureControl control) {
            return new FixtureSourceConnector(mapper, control);
        }
        @Bean @Primary InMemoryArtifactStore memoryStorage() { return new InMemoryArtifactStore(); }
        @Bean @Primary com.fomentiq.extraction.application.StructuredExtractionProvider fixtureExtraction(
                ObjectMapper mapper) { return new FixtureStructuredExtractionProvider(mapper); }
    }

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
