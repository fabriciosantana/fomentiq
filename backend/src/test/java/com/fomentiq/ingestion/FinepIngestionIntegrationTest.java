package com.fomentiq.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.documents.domain.CallDocumentRepository;
import com.fomentiq.documents.domain.CallDocumentVersionRepository;
import com.fomentiq.extraction.domain.ExtractedFieldRepository;
import com.fomentiq.extraction.domain.ValidationStatus;
import com.fomentiq.funding.domain.CallForProposalsRepository;
import com.fomentiq.funding.domain.CallVersion;
import com.fomentiq.funding.domain.CallVersionRepository;
import com.fomentiq.ingestion.application.FinepIngestionOutcome;
import com.fomentiq.ingestion.application.FinepIngestionConstraints;
import com.fomentiq.ingestion.application.IngestionLimits;
import com.fomentiq.ingestion.application.FinepIngestionService;
import com.fomentiq.ingestion.application.IngestionPersistence;
import com.fomentiq.ingestion.application.SourceConnector;
import com.fomentiq.ingestion.domain.IngestionRunRepository;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshotRepository;
import com.fomentiq.extraction.FixtureStructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionFailure;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.domain.ExtractedField;
import com.fomentiq.storage.application.ArtifactStore;
import com.fomentiq.storage.infrastructure.InMemoryPrivateAuditArchive;
import com.fomentiq.audit.application.AuditPackageVerifier;
import com.fomentiq.audit.infrastructure.AuditPackageExporter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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
        registry.add("fomentiq.audit.code-revision", () -> "0000000000000000000000000000000000000000");
    }

    @Autowired FinepIngestionService ingestion;
    @Autowired IngestionPersistence ingestionPersistence;
    @Autowired FixtureSourceConnector.FixtureControl control;
    @Autowired CnpqFixtureSourceConnector cnpqFixture;
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
    @Autowired InMemoryPrivateAuditArchive auditArchive;
    @Autowired AuditPackageExporter auditExporter;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetFixtureControls() {
        jdbc.execute("TRUNCATE TABLE evidence, extracted_field, extraction_execution, call_document_version, call_document, "
                + "source_snapshot, call_version, ingestion_run, call_for_proposals, funding_program, funding_agency CASCADE");
        control.synthetic = false;
        control.failedDocumentId = "";
        extractionControl.fail = false;
        extractionControl.missingEvidenceField = "";
        extractionControl.omittedField = "";
        storage.clear();
        auditArchive.clear();
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
    void ingestsFinepAndCnpqInTheSameDatabaseWithoutIdentityCollisionAndReplaysIdempotently() throws Exception {
        FinepIngestionOutcome finep = ingestion.ingest();
        FinepIngestionOutcome cnpq = ingestion.ingest(cnpqFixture);

        assertThat(finep.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(cnpq.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(calls.count()).isEqualTo(2);
        var finepCall = calls.findByAgencyAcronymAndExternalId("FINEP", "751").orElseThrow();
        var cnpqCall = calls.findByAgencyAcronymAndExternalId("CNPq", "30/2026").orElseThrow();
        assertThat(finepCall.getPortalEntryId()).isEqualTo("751092");
        assertThat(cnpqCall.getPortalEntryId()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_source_reference r JOIN source_publisher p "
                + "ON p.id = r.publisher_id WHERE (p.code = 'FINEP' AND r.external_id = '751' "
                + "AND r.portal_entry_id = '751092') OR (p.code = 'CNPQ' AND r.external_id = '30/2026')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT a.acronym FROM call_funding_agency cfa JOIN funding_agency a "
                        + "ON a.id = cfa.agency_id WHERE cfa.call_id = ? ORDER BY cfa.is_primary DESC, a.acronym",
                String.class, cnpqCall.getId())).containsExactly("CNPq", "CAPES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_snapshot ss JOIN call_source_reference r "
                + "ON r.id = ss.source_reference_id WHERE r.call_id = ?", Integer.class, cnpqCall.getId())).isEqualTo(3);
        assertThat(documents.count()).isEqualTo(23);
        assertThat(documentVersions.count()).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN extracted_field ef "
                + "ON ef.id = e.extracted_field_id JOIN call_version cv ON cv.id = ef.call_version_id "
                + "JOIN call_document_version dv ON dv.id = e.document_version_id JOIN call_document d "
                + "ON d.id = dv.document_id AND d.call_id = cv.call_id WHERE cv.call_id = ?", Integer.class,
                cnpqCall.getId())).isPositive();

        var cnpqAudit = auditExporter.export(cnpq.runId());
        byte[] cnpqPackage = auditArchive.read(cnpqAudit.archiveKey());
        assertThat(cnpqAudit.verification().valid()).isTrue();
        assertThat(new AuditPackageVerifier(objectMapper).verify(cnpqPackage).valid()).isTrue();
        Map<String, byte[]> cnpqArchiveFiles = zipEntries(cnpqPackage);
        JsonNode sourceReferences = objectMapper.readTree(cnpqArchiveFiles.get("source/source-references.json"));
        assertThat(sourceReferences.get(0).path("publisher_code").asText()).isEqualTo("CNPQ");
        assertThat(sourceReferences.get(0).path("external_id").asText()).isEqualTo("30/2026");
        assertThat(cnpqArchiveFiles.keySet()).anyMatch(path -> path.endsWith(".docx"));

        long versionsBeforeReplay = callVersions.count();
        long documentsBeforeReplay = documents.count();
        FinepIngestionOutcome replay = ingestion.ingest(cnpqFixture);
        assertThat(replay.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(callVersions.count()).isEqualTo(versionsBeforeReplay);
        assertThat(documents.count()).isEqualTo(documentsBeforeReplay);
        assertThat(calls.count()).isEqualTo(2);
    }

    @Test
    void discoveryFailureIsRecordedAgainstSelectedSourceWithoutLosingCommittedOtherSourceData() {
        FinepIngestionOutcome finep = ingestion.ingest();
        SourceConnector failingCnpq = new SourceConnector() {
            @Override public String sourceType() { return "CNPQ"; }
            @Override public List<SourceCall> discover() { throw new IllegalStateException("controlled fixture failure"); }
            @Override public DocumentCatalog discoverDocuments(SourceCall call) { throw new UnsupportedOperationException(); }
            @Override public DownloadedDocument download(SourceDocument document) { throw new UnsupportedOperationException(); }
        };

        FinepIngestionOutcome failed = ingestion.ingest(failingCnpq);

        assertThat(finep.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(failed.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(runs.findById(failed.runId())).get().satisfies(run -> {
            assertThat(run.getSourceType()).isEqualTo("CNPQ");
            assertThat(run.getErrorSummary()).contains("CNPQ");
        });
        assertThat(calls.findByAgencyAcronymAndExternalId("FINEP", "751")).isPresent();
        assertThat(calls.count()).isEqualTo(1);
    }

    @Test
    void identicalAgencyAndExternalIdFromAnotherPublisherDoesNotMergeCalls() {
        ingestion.ingest(cnpqFixture);
        var cnpqCall = calls.findByAgencyAcronymAndExternalId("CNPq", "30/2026").orElseThrow();
        jdbc.update("INSERT INTO source_publisher (id, code, name, official_url) VALUES (?,?,?,?)",
                UUID.randomUUID(), "CNPQ-ALT", "Alternate public catalog", "https://www.gov.br/cnpq");
        byte[] snapshot = "alternate publisher snapshot".getBytes(StandardCharsets.UTF_8);
        SourceConnector.SourceCall alternatePublisherCall = new SourceConnector.SourceCall("30/2026", null,
                "Same agency and source ID, distinct publisher", null, "OPEN", null, null, null,
                URI.create("https://www.gov.br/cnpq/alternate/30-2026"),
                URI.create("https://www.gov.br/cnpq/alternate"), snapshot, "text/html",
                URI.create("https://www.gov.br/cnpq/alternate/30-2026"), snapshot, "text/html", "CNPq", List.of());

        IngestionPersistence.CallIdentity separate = ingestionPersistence.upsertCall("CNPQ-ALT",
                alternatePublisherCall, Instant.parse("2026-10-27T12:00:00Z"));

        assertThat(separate.callId()).isNotEqualTo(cnpqCall.getId());
        assertThat(calls.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_source_reference WHERE external_id = '30/2026'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void changedCnpqFixtureVariantAddsCallVersionAndPreservesThePreviousVersion() {
        FinepIngestionOutcome initial = ingestion.ingest(cnpqFixture);
        var cnpqCall = calls.findByAgencyAcronymAndExternalId("CNPq", "30/2026").orElseThrow();
        var previousVersion = callVersions.findTopByCall_IdOrderByVersionNumberDesc(cnpqCall.getId()).orElseThrow();
        UUID sourceReferenceId = snapshots.findByIngestionRun_Id(initial.runId()).getFirst().getSourceReferenceId();
        SourceConnector changedFixtureVariant = new SourceConnector() {
            @Override public String sourceType() { return "CNPQ"; }
            @Override public ArtifactOrigin artifactOrigin() { return ArtifactOrigin.RECONSTRUCTED; }
            @Override public List<SourceCall> discover() {
                SourceCall original = cnpqFixture.discover().getFirst();
                byte[] changedSnapshot = (new String(original.callSnapshot(), StandardCharsets.UTF_8)
                        + "\\nSynthetic versioning test variant").getBytes(StandardCharsets.UTF_8);
                return List.of(new SourceCall(original.externalId(), original.portalEntryId(), original.title(),
                        original.summary(), original.status(), original.publicationDate(), original.openingDate(),
                        original.deadline(), original.canonicalUri(), original.discoveryUri(),
                        original.discoverySnapshot(), original.discoveryMediaType(), original.callUri(),
                        changedSnapshot, original.callMediaType(), original.primaryAgencyAcronym(),
                        original.additionalFundingAgencyAcronyms()));
            }
            @Override public DocumentCatalog discoverDocuments(SourceCall call) {
                return cnpqFixture.discoverDocuments(call);
            }
            @Override public DownloadedDocument download(SourceDocument document) {
                return cnpqFixture.download(document);
            }
        };

        FinepIngestionOutcome changed = ingestion.ingest(changedFixtureVariant);

        assertThat(initial.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(changed.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
        assertThat(calls.count()).isEqualTo(1);
        assertThat(callVersions.countByCall_Id(cnpqCall.getId())).isEqualTo(2);
        var currentVersion = callVersions.findTopByCall_IdOrderByVersionNumberDesc(cnpqCall.getId()).orElseThrow();
        assertThat(currentVersion.getId()).isNotEqualTo(previousVersion.getId());
        assertThat(currentVersion.getVersionNumber()).isEqualTo(previousVersion.getVersionNumber() + 1);
        assertThat(jdbc.queryForObject("SELECT supersedes_version_id FROM call_version WHERE id = ?",
                UUID.class, currentVersion.getId())).isEqualTo(previousVersion.getId());
        assertThat(callVersions.findById(previousVersion.getId())).isPresent();
        assertThat(snapshots.findByIngestionRun_Id(changed.runId()))
                .allSatisfy(snapshot -> assertThat(snapshot.getSourceReferenceId())
                        .isEqualTo(sourceReferenceId));
    }

    @Test
    void exportsCompleteOfflineAuditPackageWithVerifiableHashesAndReferences() throws Exception {
        FinepIngestionOutcome outcome = ingestion.ingest();
        assertGoldenCriticalExtraction(outcome.runId());

        var exported = auditExporter.export(outcome.runId());
        byte[] packageBytes = auditArchive.read(exported.archiveKey());
        AuditPackageVerifier.VerificationResult offline = new AuditPackageVerifier(objectMapper).verify(packageBytes);

        assertThat(exported.verification().valid()).as("audit package gaps: %s", exported.verification().gaps()).isTrue();
        assertThat(offline.valid()).isTrue();
        assertThat(offline.filesVerified()).isGreaterThan(30);
        assertThat(exported.archiveSha256()).matches("[0-9a-f]{64}");
        assertThat(storage.objects().entrySet().stream().filter(entry -> entry.getKey().endsWith(".pdf")))
                .hasSize(18);
        assertThat(auditArchive.artifacts().keySet()).anyMatch(key -> key.startsWith("extraction-input/"));
        assertThat(new String(packageBytes, StandardCharsets.ISO_8859_1)).doesNotContain("sk-live-")
                .doesNotContain("Authorization: Bearer");
    }

    private void assertGoldenCriticalExtraction(UUID runId) throws Exception {
        JsonNode expectedRoot;
        try (var input = new ClassPathResource(
                "fixtures/finep/proinfra-2024-expansao-751/expected.json").getInputStream()) {
            expectedRoot = objectMapper.readTree(input);
        }
        Map<String, JsonNode> expected = new TreeMap<>();
        expectedRoot.path("fields").forEach(item -> expected.put(item.path("fieldName").asText(), item));
        Map<String, String> persisted = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT field_name, normalized_value FROM extracted_field WHERE ingestion_run_id = ?", runId)) {
            persisted.put((String) row.get("field_name"), (String) row.get("normalized_value"));
        }
        for (String field : List.of("deadline", "totalBudget", "eligibility", "counterpart", "trl", "requiresIct")) {
            JsonNode expectedValue = expected.get(field).path("normalizedValue");
            String actual = persisted.get(field);
            if (expectedValue.isNull()) {
                assertThat(actual).as("golden missing value for %s", field).isNull();
            } else {
                assertThat(actual).as("golden value for %s", field).isNotNull();
                if (expectedValue.isTextual()) assertThat(actual).isEqualTo(expectedValue.asText());
                else {
                    JsonNode actualValue = objectMapper.readTree(actual);
                    if (actualValue.isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) actualValue).remove("clauses");
                    }
                    assertThat(actualValue).isEqualTo(expectedValue);
                }
            }
        }
        assertThat(jdbc.queryForObject("SELECT validation_status FROM extracted_field WHERE ingestion_run_id = ? "
                + "AND field_name = 'trl'", String.class, runId)).isEqualTo("MISSING");
        for (String field : List.of("eligibility", "counterpart")) {
            assertThat(jdbc.queryForObject("SELECT validation_status FROM extracted_field WHERE ingestion_run_id = ? "
                    + "AND field_name = ?", String.class, runId, field))
                    .as("compound legal rule %s is never auto-validated", field)
                    .isIn("EXTRACTED", "CONFLICT");
        }

        for (String field : List.of("deadline", "totalBudget", "eligibility", "counterpart", "requiresIct")) {
            JsonNode evidence = expected.get(field).path("evidence");
            for (JsonNode item : evidence) {
                int page = item.path("page").asInt(-1);
                String hash = item.path("sha256").asText();
                String quote = item.path("quoteFragment").asText("");
                if (page < 1 || hash.isBlank()) continue;
                if (quote.isBlank()) {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN extracted_field ef "
                            + "ON ef.id=e.extracted_field_id JOIN call_document_version cdv "
                            + "ON cdv.id=e.document_version_id WHERE ef.ingestion_run_id=? "
                            + "AND ef.field_name=? AND e.page=? AND cdv.content_hash=?", Integer.class,
                            runId, field, page, hash))
                            .as("do not present an unquoted locator as literal field evidence for %s page %s", field, page)
                            .isZero();
                    continue;
                }
                JsonNode captured = objectMapper.readTree(auditArchive.artifacts().entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("extraction-input/")).findFirst().orElseThrow().getValue());
                String selectedPages = java.util.stream.StreamSupport.stream(captured.path("excerpts").spliterator(), false)
                        .map(excerpt -> excerpt.path("title").asText() + ":p" + excerpt.path("page").asInt())
                        .toList().toString();
                String persistedEvidence = jdbc.queryForList("SELECT e.page, cdv.content_hash, e.quote_fragment FROM evidence e "
                                + "JOIN extracted_field ef ON ef.id = e.extracted_field_id JOIN call_document_version cdv "
                                + "ON cdv.id = e.document_version_id WHERE ef.ingestion_run_id = ? AND ef.field_name = ?",
                        runId, field).toString();
                String inputDiagnostics = "fields=" + captured.fieldNames().toString() + ",excerptNode="
                        + captured.path("excerpts").getNodeType() + ",count=" + captured.path("excerpts").size()
                        + ",coverageGaps=" + captured.path("coverage").path("gaps").size();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN extracted_field ef "
                        + "ON ef.id = e.extracted_field_id JOIN call_document_version cdv "
                        + "ON cdv.id = e.document_version_id WHERE ef.ingestion_run_id = ? "
                        + "AND ef.field_name = ? AND e.page = ? AND cdv.content_hash = ?", Integer.class,
                        runId, field, page, hash)).as("golden evidence %s page %s; selected %s", field, page,
                        selectedPages + ", " + inputDiagnostics + ", evidence=" + persistedEvidence).isPositive();
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN extracted_field ef "
                + "ON ef.id=e.extracted_field_id WHERE ef.ingestion_run_id=? AND ef.field_name='regions' "
                + "AND e.page=12", Integer.class, runId))
                .as("do not persist the unquoted budget-clause locator as region evidence")
                .isZero();
    }

    @Test
    void auditExportIsIncompleteAndCannotBeVerifiedWhenAnOriginalArtifactIsMissing() {
        FinepIngestionOutcome outcome = ingestion.ingest();
        storage.removeFirstPdf();

        var exported = auditExporter.export(outcome.runId());

        assertThat(exported.verification().integrityValid()).isTrue();
        assertThat(exported.verification().complete()).isFalse();
        assertThat(exported.verification().gaps()).contains("package_marked_incomplete");
    }

    @Test
    void offlineVerifierRejectsTamperedAndMissingPackageArtifacts() throws Exception {
        FinepIngestionOutcome outcome = ingestion.ingest();
        byte[] packageBytes = auditArchive.read(auditExporter.export(outcome.runId()).archiveKey());
        AuditPackageVerifier verifier = new AuditPackageVerifier(objectMapper);

        byte[] tampered = rewriteZip(packageBytes, "source/snapshots.json", false);
        byte[] missing = rewriteZip(packageBytes, "extraction/provider-input-excerpts.json", true);
        byte[] unmanifested = addUnmanifestedFileAndUpdateChecksums(packageBytes);
        byte[] traversal = addPathTraversalEntry(packageBytes);
        byte[] missingExecutionRun = removeExecutionRunAttributionAndRefreshInventories(packageBytes, objectMapper);
        byte[] changedSnapshot = mutateSnapshotBytesAndRefreshInventories(packageBytes, objectMapper);

        assertThat(verifier.verify(tampered).integrityValid()).isFalse();
        assertThat(verifier.verify(missing).valid()).isFalse();
        assertThat(verifier.verify(unmanifested).valid()).isFalse();
        assertThat(verifier.verify(unmanifested).gaps()).contains("archive_file_not_in_manifest_inventory");
        assertThat(verifier.verify(traversal).valid()).isFalse();
        assertThat(verifier.verify(traversal).gaps()).contains("archive_format_invalid");
        assertThat(verifier.verify(missingExecutionRun).valid()).isFalse();
        assertThat(verifier.verify(missingExecutionRun).gaps()).contains("execution_run_attribution_invalid");
        assertThat(verifier.verify(changedSnapshot).valid()).isFalse();
        assertThat(verifier.verify(changedSnapshot).gaps()).contains("source_snapshot_hash_mismatch");
    }

    @Test
    void incompleteHistoricalRunRemainsReconstructedAndCannotBeVerifiedComplete() throws Exception {
        FinepIngestionOutcome outcome = ingestion.ingest();
        String inputKey = auditArchive.artifacts().keySet().stream()
                .filter(key -> key.startsWith("extraction-input/")).findFirst().orElseThrow();
        auditArchive.remove(inputKey);
        auditArchive.remove("run-origin/" + outcome.runId() + ".json");

        var historical = auditExporter.export(outcome.runId());
        JsonNode manifest;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(auditArchive.read(historical.archiveKey())))) {
            ZipEntry entry;
            do { entry = zip.getNextEntry(); } while (entry != null && !entry.getName().equals("manifest.json"));
            manifest = objectMapper.readTree(zip.readAllBytes());
        }
        assertThat(historical.verification().complete()).isFalse();
        assertThat(manifest.path("sourceOrigin").asText()).isEqualTo("RECONSTRUCTED");
        assertThat(manifest.path("complete").asBoolean()).isFalse();
        assertThat(manifest.path("gaps").toString()).contains("run_origin_missing", "provider_input_artifact_missing");
        assertThat(historical.verification().gaps()).contains("run_code_revision_missing_or_invalid");
    }

    @Test
    void brokenEvidenceLocatorPreventsPackageCompleteness() {
        FinepIngestionOutcome outcome = ingestion.ingest();
        jdbc.update("UPDATE evidence SET locator_json = '{}'::jsonb");
        var invalidLocator = auditExporter.export(outcome.runId());
        assertThat(invalidLocator.verification().complete()).isFalse();
        assertThat(invalidLocator.verification().gaps()).anyMatch(code -> code.contains("locator"));
    }

    @Test
    void structuredResultThatViolatesCapturedSchemaCannotProduceCompletePackage() {
        extractionControl.omittedField = "trl";
        FinepIngestionOutcome outcome = ingestion.ingest();
        assertThat(outcome.status()).isEqualTo(IngestionRunStatus.FAILED);

        var exported = auditExporter.export(outcome.runId());

        assertThat(exported.verification().complete()).isFalse();
        assertThat(exported.verification().gaps()).contains("structured_result_schema_invalid");
    }

    private static byte[] rewriteZip(byte[] source, String target, boolean remove) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] contents = input.readAllBytes();
                if (entry.getName().equals(target)) {
                    if (remove) continue;
                    contents[0] = (byte) (contents[0] ^ 1);
                }
                entries.put(entry.getName(), contents);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] addUnmanifestedFileAndUpdateChecksums(byte[] source) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) entries.put(entry.getName(), input.readAllBytes());
        }
        byte[] extra = "{}".getBytes(StandardCharsets.UTF_8);
        entries.put("additional/unlisted.json", extra);
        String current = new String(entries.get("inventory/SHA256SUMS"), StandardCharsets.UTF_8);
        entries.put("inventory/SHA256SUMS", (current + sha256(extra) + "  " + extra.length
                + "  additional/unlisted.json\n").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] addPathTraversalEntry(byte[] source) throws Exception {
        Map<String, byte[]> entries = zipEntries(source);
        entries.put("../escape.json", "{}".getBytes(StandardCharsets.UTF_8));
        return writeZip(entries);
    }

    private static byte[] removeExecutionRunAttributionAndRefreshInventories(byte[] source,
                                                                              ObjectMapper mapper) throws Exception {
        Map<String, byte[]> entries = zipEntries(source);
        var executions = (com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(
                entries.get("extraction/execution-history.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) executions.get(0)).remove("ingestionRunId");
        entries.put("extraction/execution-history.json", mapper.writeValueAsBytes(executions));
        return refreshManifestAndChecksums(entries, mapper);
    }

    private static byte[] mutateSnapshotBytesAndRefreshInventories(byte[] source, ObjectMapper mapper) throws Exception {
        Map<String, byte[]> entries = zipEntries(source);
        JsonNode snapshots = mapper.readTree(entries.get("source/snapshots.json"));
        String archivePath = snapshots.get(0).path("archivePath").asText();
        byte[] changed = entries.get(archivePath).clone();
        changed[0] = (byte) (changed[0] ^ 1);
        entries.put(archivePath, changed);
        return refreshManifestAndChecksums(entries, mapper);
    }

    private static byte[] refreshManifestAndChecksums(Map<String, byte[]> entries, ObjectMapper mapper) throws Exception {
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(entries.get("manifest.json"));
        var inventory = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("files");
        for (var itemNode : inventory) {
            var item = (com.fasterxml.jackson.databind.node.ObjectNode) itemNode;
            byte[] content = entries.get(item.path("path").asText());
            item.put("length", content.length).put("sha256", sha256(content));
        }
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        StringBuilder checksums = new StringBuilder();
        entries.entrySet().stream().filter(entry -> !entry.getKey().equals("inventory/SHA256SUMS"))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> checksums.append(sha256(entry.getValue()))
                        .append("  ").append(entry.getValue().length).append("  ").append(entry.getKey()).append('\n'));
        entries.put("inventory/SHA256SUMS", checksums.toString().getBytes(StandardCharsets.UTF_8));
        return writeZip(entries);
    }

    private static Map<String, byte[]> zipEntries(byte[] source) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) entries.put(entry.getName(), input.readAllBytes());
        }
        return entries;
    }

    private static byte[] writeZip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
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
    void aggregateDocumentLimitRollsBackBusinessRowsAndLeavesFailedRunAuditable() {
        Instant started = Instant.now();
        var acquisition = new IngestionLimits(18, 2L * 1024 * 1024, 2L * 1024 * 1024,
                1024 * 1024, 2L * 1024 * 1024, 2L * 1024 * 1024, 64 * 1024,
                150, 4, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(45),
                started.plusSeconds(900), new IngestionLimits.RequestCounter());
        var provider = new StructuredExtractionProvider.ExecutionLimits(1, 64_000, 12_000,
                Duration.ofSeconds(60), started.plusSeconds(900));
        var constraints = new FinepIngestionConstraints("751", "751092",
                URI.create("https://www.finep.gov.br/e/chamada-publica/222684/751092"), acquisition, provider);

        FinepIngestionOutcome outcome = ingestion.ingest(constraints);

        assertThat(outcome.status()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(calls.count()).isZero();
        assertThat(callVersions.count()).isZero();
        assertThat(documents.count()).isZero();
        assertThat(documentVersions.count()).isZero();
        assertThat(snapshots.count()).isZero();
        assertThat(runs.findById(outcome.runId()).orElseThrow().getStatus()).isEqualTo(IngestionRunStatus.FAILED);
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
                .contains("1 call document(s)", "814330:UNSUPPORTED_CONTENT_TYPE")
                .doesNotContain("token", "secret");
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
        @Bean CnpqFixtureSourceConnector cnpqFixtureConnector(ObjectMapper mapper) {
            return new CnpqFixtureSourceConnector(mapper);
        }
        @Bean @Primary InMemoryArtifactStore memoryStorage() { return new InMemoryArtifactStore(); }
        @Bean @Primary InMemoryPrivateAuditArchive memoryAuditArchive() { return new InMemoryPrivateAuditArchive(); }
        @Bean @Primary StructuredExtractionProvider fixtureExtraction(ObjectMapper mapper, ExtractionControl control) {
            StructuredExtractionProvider fixture = new FixtureStructuredExtractionProvider(mapper);
            return new StructuredExtractionProvider() {
                @Override public ProviderDescriptor describe(ExtractionRequest request) {
                    return fixture.describe(request);
                }

                @Override public ProviderResponse extract(ExtractionRequest request) {
                    if (control.fail) throw new StructuredExtractionFailure("fixture", "fixture-v1",
                            "controlled provider failure");
                    var response = fixture.extract(request);
                    var fields = new java.util.HashMap<>(response.fields());
                    if (!control.omittedField.isBlank()) fields.remove(control.omittedField);
                    if (control.missingEvidenceField.isBlank() && control.omittedField.isBlank()) return response;
                    var candidate = fields.get(control.missingEvidenceField);
                    if (candidate != null) fields.put(control.missingEvidenceField, new StructuredExtractionProvider.Candidate(
                            candidate.value(), candidate.rawValue(), java.util.List.of(), candidate.conflicts()));
                    return new StructuredExtractionProvider.ProviderResponse(response.provider(), response.model(),
                            response.schemaVersion(), response.rawResponse(), response.refusal(), response.incomplete(),
                            response.inputTokens(), response.outputTokens(), java.util.Map.copyOf(fields),
                            response.effectiveParameters());
                }
            };
        }
    }

    static class ExtractionControl {
        private volatile boolean fail;
        private volatile String missingEvidenceField = "";
        private volatile String omittedField = "";
    }
    static class InMemoryArtifactStore implements ArtifactStore {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();
        private volatile boolean fail;
        @Override public String put(String key, byte[] content, String mediaType) {
            if (fail) throw new IllegalStateException("controlled storage failure");
            data.put(key, content.clone());
            return key;
        }
        @Override public byte[] get(String key) {
            byte[] value = data.get(key);
            if (value == null) throw new IllegalStateException("test_artifact_missing");
            return value.clone();
        }
        void failWrites(boolean enabled) { fail = enabled; }
        Map<String, byte[]> objects() { return data; }
        void removeFirstPdf() {
            data.keySet().stream().filter(key -> key.endsWith(".pdf")).findFirst().ifPresent(data::remove);
        }
        void clear() { data.clear(); fail = false; }
    }
}
