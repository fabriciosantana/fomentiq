package com.fomentiq.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure offline tests. The synthetic package bytes are test mechanics, not Finep/live evidence. */
class FactualReviewValidatorTest {
    private static final List<String> FIELDS = List.of(
            "deadline", "totalBudget", "eligibility", "counterpart", "trl", "requiresIct");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private FactualReviewValidator validator;
    private TestPackage testPackage;

    @BeforeEach
    void setUp() throws Exception {
        validator = new FactualReviewValidator(mapper);
        testPackage = TestPackage.complete(mapper);
    }

    @Test
    void validatesSixCurrentDecisionsAgainstCompleteVersionOnePackageOffline() {
        ObjectNode review = review();
        byte[] before = testPackage.bytes().clone();

        var result = validator.validate(testPackage.bytes(), review, null);

        assertThat(result.valid()).isTrue().as("gaps: %s", result.gaps());
        assertThat(result.packageId()).isEqualTo(testPackage.packageId());
        assertThat(result.packageSha256()).isEqualTo(sha256(before));
        assertThat(testPackage.bytes()).isEqualTo(before);
    }

    @Test
    void rejectsMissingOrDuplicateCriticalFieldDecision() {
        ObjectNode omitted = review();
        ((ArrayNode) omitted.path("decisions")).remove(0);
        ObjectNode duplicate = review();
        ((ArrayNode) duplicate.path("decisions")).set(0,
                ((ArrayNode) duplicate.path("decisions")).get(1).deepCopy());

        assertThat(validator.validate(testPackage.bytes(), omitted, null).valid()).isFalse();
        assertThat(validator.validate(testPackage.bytes(), duplicate, null).gaps())
                .contains("duplicate_field_decision", "six_field_matrix_incomplete");
    }

    @Test
    void rejectsUnsupportedPackageVersionAndIncompletePackage() throws Exception {
        byte[] unsupported = testPackage.withFormatVersion(2);
        byte[] incomplete = testPackage.withComplete(false);

        assertThat(validator.validate(unsupported, review(), null).valid()).isFalse();
        assertThat(validator.validate(incomplete, review(), null).gaps()).contains("audit_package_incomplete");
    }

    @Test
    void technicalMissingCanHaveAnIndependentInconclusiveFactualOutcome() {
        ObjectNode review = review();
        ObjectNode deadline = decision(review, "deadline");
        deadline.put("technicalState", "MISSING");
        deadline.put("outcome", "INCONCLUSIVO");
        deadline.put("rationale", "A ausência no resultado técnico não comprova ausência documental.");

        var result = validator.validate(testPackage.bytes(), review, null);

        assertThat(result.valid()).isTrue().as("gaps: %s", result.gaps());
        assertThat(decision(review, "deadline").path("technicalState").asText()).isEqualTo("MISSING");
    }

    @Test
    void inconclusiveDocumentaryValueNeedsAtLeastOneSourceClaim() {
        ObjectNode review = review();
        ObjectNode deadline = decision(review, "deadline");
        deadline.put("documentaryValue", "Unverified candidate");

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps())
                .contains("inconclusive_value_without_evidence");
    }

    @Test
    void rejectsNonUtcReviewTimestamps() {
        ObjectNode review = review();
        review.put("createdAt", "2026-10-26T12:00:00+03:00");

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps()).contains("review_timestamp_not_utc");
    }

    @Test
    void rejectsCredentialLikeTextInAReviewRecord() {
        ObjectNode review = review();
        decision(review, "deadline").put("rationale", "api_key=sk-test-value-that-must-not-be-stored");

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps())
                .contains("review_sensitive_or_invalid_text");
    }

    @Test
    void confirmedAbsenceRequiresCompleteApplicableDocumentScope() {
        ObjectNode review = review();
        ObjectNode deadline = decision(review, "deadline");
        deadline.put("outcome", "AUSÊNCIA CONFIRMADA");
        deadline.put("rationale", "Pesquisa documental concluída no escopo listado.");
        deadline.with("absenceReview").put("complete", true)
                .put("searchDescription", "Busca no conjunto integral de editais e retificações do pacote.");
        ArrayNode allVersions = deadline.with("absenceReview").putArray("reviewedDocumentVersionIds");
        testPackage.documentVersionIds().forEach(allVersions::add);

        assertThat(validator.validate(testPackage.bytes(), review, null).valid()).isTrue();

        allVersions.removeAll();
        assertThat(validator.validate(testPackage.bytes(), review, null).gaps()).contains("absence_scope_incomplete");
    }

    @Test
    void counterpartOneSupportedBandCannotBeApprovedAsACompleteRule() {
        ObjectNode review = review();
        ObjectNode counterpart = approvedDecision(review, "counterpart");
        ObjectNode claim = addClaim(counterpart, "counterpart-band-1", "One isolated municipal band");
        addCoverageItem(counterpart, "conditions", "band-1", "Municipal band shown", claim);
        addCoverageItem(counterpart, "exceptions", "exceptions-reviewed", "Exceptions reviewed", claim);
        completeConditionalCoverage(counterpart, true);

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps())
                .contains("counterpart_single_band_is_not_complete");
    }

    @Test
    void cannotPassCounterpartByDuplicatingOneBand() {
        ObjectNode review = review();
        ObjectNode counterpart = approvedDecision(review, "counterpart");
        ObjectNode claim = addClaim(counterpart, "counterpart-band-1", "One isolated municipal band");
        addCoverageItem(counterpart, "conditions", "band-1", "Municipal band shown", claim);
        addCoverageItem(counterpart, "conditions", "band-1-copy", "Municipal band shown", claim);
        addCoverageItem(counterpart, "exceptions", "exceptions-reviewed", "Exceptions reviewed", claim);
        completeConditionalCoverage(counterpart, true);

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps())
                .contains("counterpart_branch_coverage_not_distinct");
    }

    @Test
    void approvalAndRejectionNeedDocumentaryEvidenceAndLocator() {
        ObjectNode unsupportedApproval = review();
        approvedDecision(unsupportedApproval, "deadline");
        ObjectNode reviewWithoutClaim = review();
        ObjectNode unsupported = approvedDecision(reviewWithoutClaim, "deadline");
        ObjectNode claim = addClaim(unsupported, "deadline-claim", "Synthetic test value");
        claim.remove("locator");

        ObjectNode rejectedReview = review();
        ObjectNode rejected = decision(rejectedReview, "deadline");
        rejected.put("outcome", "REPROVADO").put("documentaryValue", "Documented contrary value")
                .put("rationale", "The cited source contradicts the extracted value.");
        addClaim(rejected, "deadline-rejection", "Documented contrary value");

        assertThat(validator.validate(testPackage.bytes(), unsupportedApproval, null).gaps())
                .contains("documentary_decision_evidence_missing");
        assertThat(validator.validate(testPackage.bytes(), reviewWithoutClaim, null).gaps())
                .contains("review_schema_required_property_missing");
        assertThat(validator.validate(testPackage.bytes(), rejectedReview, null).valid()).isTrue();
    }

    @Test
    void counterpartApprovalRequiresAllBranchesExceptionsAndExactSourceCitations() {
        ObjectNode review = review();
        ObjectNode counterpart = approvedDecision(review, "counterpart");
        ObjectNode first = addClaim(counterpart, "counterpart-band-1", "First conditional band",
                testPackage.documentVersionId(), TestPackage.QUOTE);
        ObjectNode second = addClaim(counterpart, "counterpart-band-2", "Second conditional band",
                testPackage.documentVersionId(), TestPackage.SECOND_QUOTE);
        addCoverageItem(counterpart, "conditions", "band-1", "First band", first);
        addCoverageItem(counterpart, "conditions", "band-2", "Second band", second);
        addCoverageItem(counterpart, "exceptions", "exception-1", "Explicit exception", second);
        completeConditionalCoverage(counterpart, true);

        assertThat(validator.validate(testPackage.bytes(), review, null).valid()).isTrue();

        ((ObjectNode) first.path("locator")).put("page", 2);
        assertThat(validator.validate(testPackage.bytes(), review, null).gaps()).contains("claim_locator_mismatch");
        ((ObjectNode) first.path("locator")).put("page", 1);
        first.put("quote", "This quote is not present in the packaged document.");
        assertThat(validator.validate(testPackage.bytes(), review, null).gaps()).contains("claim_quote_not_in_document");
    }

    @Test
    void eligibilityApprovalCannotOmitExclusionReview() {
        ObjectNode review = review();
        ObjectNode eligibility = approvedDecision(review, "eligibility");
        ObjectNode claim = addClaim(eligibility, "eligibility-positive", "Eligible applicants");
        addCoverageItem(eligibility, "conditions", "positive", "Positive criterion", claim);
        addCoverageItem(eligibility, "exceptions", "exclusion", "Excluded applicant category", claim);
        completeConditionalCoverage(eligibility, false);

        assertThat(validator.validate(testPackage.bytes(), review, null).gaps())
                .contains("eligibility_exclusions_not_reviewed");
    }

    @Test
    void preservesConflictingClaimsAndSupersededReviewByChecksum() throws Exception {
        ObjectNode first = review();
        ObjectNode deadline = decision(first, "deadline");
        deadline.put("outcome", "INCONCLUSIVO");
        deadline.put("rationale", "Duas alegações incompatíveis permanecem sem resolução.");
        addClaim(deadline, "claim-a", "Value stated in version A", testPackage.documentVersionId(), TestPackage.QUOTE);
        addClaim(deadline, "claim-b", "Different value stated in version B", testPackage.secondDocumentVersionId(),
                TestPackage.SECOND_QUOTE);
        Path history = Files.createTempDirectory("factual-review-history-");
        Path firstPath = history.resolve(first.path("recordId").asText() + ".json");
        byte[] firstBytes = mapper.writeValueAsBytes(first);
        Files.write(firstPath, firstBytes);
        String packageHash = sha256(testPackage.bytes());

        ObjectNode corrected = review();
        corrected.set("supersedes", mapper.createObjectNode().put("recordId", first.path("recordId").asText())
                .put("sha256", sha256(firstBytes)));
        corrected.put("createdAt", "2026-10-26T12:00:00Z");
        var result = validator.validate(testPackage.bytes(), corrected, history);

        assertThat(result.valid()).isTrue().as("gaps: %s", result.gaps());
        assertThat(deadline.path("claims")).hasSize(2);
        assertThat(deadline.path("claims").get(0).path("documentVersionId").asText())
                .isNotEqualTo(deadline.path("claims").get(1).path("documentVersionId").asText());
        assertThat(Files.readAllBytes(firstPath)).isEqualTo(firstBytes);
        assertThat(sha256(testPackage.bytes())).isEqualTo(packageHash);
    }

    @Test
    void rejectsHistoryWhenTheSupersededRecordIsMissingOrChanged() throws Exception {
        ObjectNode prior = review();
        Path history = Files.createTempDirectory("factual-review-history-");
        byte[] priorBytes = mapper.writeValueAsBytes(prior);
        Path priorPath = history.resolve(prior.path("recordId").asText() + ".json");
        Files.write(priorPath, priorBytes);
        ObjectNode next = review();
        next.set("supersedes", mapper.createObjectNode().put("recordId", prior.path("recordId").asText())
                .put("sha256", sha256(priorBytes)));

        Files.writeString(priorPath, "{}");

        assertThat(validator.validate(testPackage.bytes(), next, history).gaps())
                .contains("superseded_review_binding_mismatch");
    }

    @Test
    void offlineCliDoesNotRequireSpringOrExternalServices() throws Exception {
        Path directory = Files.createTempDirectory("factual-review-cli-");
        Path packagePath = directory.resolve("package.zip");
        ObjectNode review = review();
        Path reviewPath = directory.resolve(review.path("recordId").asText() + ".json");
        Files.write(packagePath, testPackage.bytes());
        mapper.writeValue(reviewPath.toFile(), review);

        assertThat(FactualReviewCli.requested(new String[] {"--fomentiq.factual-review.verify=x"})).isTrue();
        assertThat(FactualReviewCli.execute(new String[] {"--fomentiq.factual-review.verify=" + packagePath,
                "--fomentiq.factual-review.record=" + reviewPath})).isZero();
    }

    private ObjectNode review() {
        ObjectNode root = mapper.createObjectNode().put("formatVersion", 1).put("recordId", UUID.randomUUID().toString())
                .put("createdAt", "2026-10-26T12:00:00Z");
        root.set("package", mapper.createObjectNode().put("packageId", testPackage.packageId())
                .put("packageSha256", sha256(testPackage.bytes())).put("manifestSha256", testPackage.manifestSha256())
                .put("formatVersion", 1).put("sourceOrigin", "FIXTURE"));
        root.putNull("supersedes");
        ArrayNode rows = root.putArray("decisions");
        for (String field : FIELDS) {
            rows.addObject().put("fieldName", field).put("extractedFieldId", testPackage.fieldIds().get(field))
                    .putNull("extractedValue").put("technicalState", "MISSING").putNull("documentaryValue")
                    .putArray("claims");
            ObjectNode row = (ObjectNode) rows.get(rows.size() - 1);
            row.putArray("conditions");
            row.putArray("exceptions");
            row.set("coverage", mapper.createObjectNode().put("sourceScopeComplete", false)
                    .put("branchInventoryComplete", false).put("conditionsAndExceptionsReviewed", false)
                    .put("exclusionsReviewed", false));
            row.put("outcome", "INCONCLUSIVO").put("rationale", "The available package does not resolve this field.")
                    .put("reviewedAt", "2026-10-26T12:00:00Z");
            row.set("reviewer", mapper.createObjectNode().put("reviewerId", "reviewer-test")
                    .put("displayName", "Offline test reviewer"));
            ObjectNode absenceReview = mapper.createObjectNode().put("complete", false).put("searchDescription", "");
            absenceReview.putArray("reviewedDocumentVersionIds");
            row.set("absenceReview", absenceReview);
        }
        return root;
    }

    private ObjectNode decision(ObjectNode review, String field) {
        for (JsonNode row : review.path("decisions")) if (field.equals(row.path("fieldName").asText())) return (ObjectNode) row;
        throw new IllegalArgumentException("test field missing");
    }

    private ObjectNode approvedDecision(ObjectNode review, String field) {
        ObjectNode row = decision(review, field);
        row.put("documentaryValue", "Synthetic test value").put("outcome", "APROVADO")
                .put("rationale", "Synthetic evidence scenario for validator testing only.");
        return row;
    }

    private ObjectNode addClaim(ObjectNode decision, String id, String value) {
        return addClaim(decision, id, value, testPackage.documentVersionId());
    }

    private ObjectNode addClaim(ObjectNode decision, String id, String value, String documentVersionId) {
        return addClaim(decision, id, value, documentVersionId, TestPackage.QUOTE);
    }

    private ObjectNode addClaim(ObjectNode decision, String id, String value,
                                String documentVersionId, String quote) {
        ObjectNode claim = decision.withArray("claims").addObject().put("claimId", id).put("value", value)
                .put("documentVersionId", documentVersionId)
                .put("documentSha256", testPackage.documentHash()).put("page", 1)
                .put("quote", quote);
        claim.set("locator", mapper.createObjectNode().put("page", 1).put("paragraph", 1));
        return claim;
    }

    private void addCoverageItem(ObjectNode decision, String section, String id, String statement, ObjectNode claim) {
        decision.withArray(section).addObject().put("id", id).put("statement", statement)
                .putArray("claimIds").add(claim.path("claimId").asText());
    }

    private void completeConditionalCoverage(ObjectNode decision, boolean exclusionsReviewed) {
        ((ObjectNode) decision.path("coverage")).put("sourceScopeComplete", true)
                .put("branchInventoryComplete", true).put("conditionsAndExceptionsReviewed", true)
                .put("exclusionsReviewed", exclusionsReviewed);
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class TestPackage {
        private static final String QUOTE = "Synthetic test citation, not source evidence.";
        private static final String SECOND_QUOTE = "Second synthetic test citation, also not source evidence.";
        private static final String RUN_ID = "11111111-1111-4111-8111-111111111111";
        private static final String CALL_ID = "22222222-2222-4222-8222-222222222222";
        private static final String CALL_VERSION_ID = "33333333-3333-4333-8333-333333333333";
        private static final String DOCUMENT_ID = "44444444-4444-4444-8444-444444444444";
        private static final String AMENDMENT_ID = "44444444-4444-4444-8444-444444444445";
        private static final String DOCUMENT_VERSION_ID = "55555555-5555-4555-8555-555555555555";
        private static final String AMENDMENT_VERSION_ID = "55555555-5555-4555-8555-555555555556";
        private final ObjectMapper mapper;
        private final byte[] pdf;
        private final String documentHash;
        private final Map<String, String> fieldIds;
        private byte[] archive;

        private TestPackage(ObjectMapper mapper, byte[] pdf, String documentHash, Map<String, String> fieldIds) {
            this.mapper = mapper;
            this.pdf = pdf;
            this.documentHash = documentHash;
            this.fieldIds = fieldIds;
            this.archive = build(1, true);
        }

        static TestPackage complete(ObjectMapper mapper) throws Exception {
            byte[] pdf;
            try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.addPage(new PDPage());
                try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.newLineAtOffset(50, 700);
                    content.showText(QUOTE);
                    content.newLineAtOffset(0, -20);
                    content.showText(SECOND_QUOTE);
                    content.endText();
                }
                document.save(output);
                pdf = output.toByteArray();
            }
            Map<String, String> fields = new TreeMap<>();
            for (int index = 0; index < FIELDS.size(); index++) {
                fields.put(FIELDS.get(index), "66666666-6666-4666-8666-" + String.format("%012d", index + 1));
            }
            return new TestPackage(mapper, pdf, hash(pdf), fields);
        }

        byte[] bytes() { return archive; }
        String packageId() { return RUN_ID; }
        String documentVersionId() { return DOCUMENT_VERSION_ID; }
        String secondDocumentVersionId() { return AMENDMENT_VERSION_ID; }
        List<String> documentVersionIds() { return List.of(DOCUMENT_VERSION_ID, AMENDMENT_VERSION_ID); }
        String documentHash() { return documentHash; }
        Map<String, String> fieldIds() { return fieldIds; }
        String manifestSha256() { return hash(entry(archive, "manifest.json")); }

        byte[] withComplete(boolean complete) { return build(1, complete); }
        byte[] withFormatVersion(int version) { return build(version, true); }

        private byte[] build(int formatVersion, boolean complete) {
            try {
                Map<String, byte[]> files = new TreeMap<>();
                files.put("run/run-record.json", json(mapper.createObjectNode().put("id", RUN_ID).put("status", "SUCCEEDED")));
                files.put("run/run-history.json", json(mapper.createArrayNode()));
                files.put("source/snapshots.json", json(mapper.createArrayNode()));
                files.put("source/calls.json", json(mapper.createArrayNode()));
                files.put("source/call-versions.json", json(mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("id", CALL_VERSION_ID).put("call_id", CALL_ID))));
                files.put("source/documents.json", json(mapper.createArrayNode()
                        .add(mapper.createObjectNode().put("id", DOCUMENT_ID).put("call_id", CALL_ID)
                                .put("document_type", "NOTICE"))
                        .add(mapper.createObjectNode().put("id", AMENDMENT_ID).put("call_id", CALL_ID)
                                .put("document_type", "AMENDMENT"))));
                String pdfPath = "source/documents/" + DOCUMENT_VERSION_ID + "/" + documentHash + ".pdf";
                String amendmentPdfPath = "source/documents/" + AMENDMENT_VERSION_ID + "/" + documentHash + ".pdf";
                files.put(pdfPath, pdf);
                files.put(amendmentPdfPath, pdf);
                files.put("source/document-versions.json", json(mapper.createArrayNode()
                        .add(mapper.createObjectNode().put("id", DOCUMENT_VERSION_ID).put("documentId", DOCUMENT_ID)
                                .put("contentHash", documentHash).put("mediaType", "application/pdf")
                                .put("sourceUrl", "https://example.org/call.pdf").put("archivePath", pdfPath))
                        .add(mapper.createObjectNode().put("id", AMENDMENT_VERSION_ID).put("documentId", AMENDMENT_ID)
                                .put("contentHash", documentHash).put("mediaType", "application/pdf")
                                .put("sourceUrl", "https://example.org/amendment.pdf").put("archivePath", amendmentPdfPath))));
                String schemaDefinition = "{\"name\":\"test-schema\",\"strict\":true,"
                        + "\"schema\":{\"type\":\"object\",\"properties\":{},"
                        + "\"additionalProperties\":false,\"required\":[]}}";
                ObjectNode schema = mapper.readTree(schemaDefinition).deepCopy();
                ObjectNode input = mapper.createObjectNode().put("ingestionRunId", RUN_ID)
                        .put("callVersionId", CALL_VERSION_ID).put("provider", "fixture-test")
                        .put("model", "fixture-test").put("schemaVersion", "test-schema")
                        .put("schemaDefinition", schemaDefinition);
                input.set("effectiveParameters", mapper.createObjectNode());
                input.set("excerpts", mapper.createArrayNode().add(mapper.createObjectNode().put("segmentId", "segment-1")
                        .put("documentVersionId", DOCUMENT_VERSION_ID).put("title", "Synthetic test document")
                        .put("sourceUrl", "https://example.org/call.pdf").put("page", 1).put("text", QUOTE)
                        .put("section", "Synthetic test section")));
                input.put("inputDigest", inputDigest(input, schemaDefinition));
                String digest = input.path("inputDigest").asText();
                String resultPath = "extraction/structured-results/" + digest + ".json";
                files.put("extraction/schema.json", json(schema));
                files.put("extraction/provider-input-excerpts.json", json(mapper.createArrayNode().add(input)));
                files.put("extraction/execution-history.json", json(mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("ingestionRunId", RUN_ID).put("callVersionId", CALL_VERSION_ID).put("inputDigest", digest)
                        .put("structuredResultPath", resultPath))));
                files.put(resultPath, json(mapper.createObjectNode().put("ingestionRunId", RUN_ID)
                        .put("inputDigest", digest).put("callVersionId", CALL_VERSION_ID)
                        .set("structuredFields", mapper.createObjectNode())));
                ArrayNode history = mapper.createArrayNode();
                for (Map.Entry<String, String> field : fieldIds.entrySet()) {
                    history.add(mapper.createObjectNode().put("id", field.getValue()).put("callVersionId", CALL_VERSION_ID)
                            .put("fieldName", field.getKey()).put("validationStatus", "MISSING")
                            .put("createdAt", "2026-10-26T12:00:00Z").put("ingestionRunId", RUN_ID).putNull("normalizedValue"));
                }
                files.put("extraction/field-history.json", json(history));
                files.put("extraction/evidence.json", json(mapper.createArrayNode()));
                files.put("review/field-review-matrix-template.json", json(mapper.createArrayNode()));
                List<String> required = new ArrayList<>(files.keySet());
                ArrayNode manifestFiles = mapper.createArrayNode();
                files.forEach((path, bytes) -> manifestFiles.add(mapper.createObjectNode().put("path", path)
                        .put("length", bytes.length).put("sha256", hash(bytes)).put("artifactOrigin", "FIXTURE")));
                ObjectNode manifest = mapper.createObjectNode().put("formatVersion", formatVersion).put("packageId", RUN_ID)
                        .put("ingestionRunId", RUN_ID).put("createdAt", "2026-10-26T12:00:00Z")
                        .put("sourceOrigin", "FIXTURE").put("codeRevision", "0000000000000000000000000000000000000000")
                        .put("complete", complete).set("files", manifestFiles);
                manifest.set("requiredFiles", mapper.valueToTree(required));
                manifest.set("gaps", mapper.createArrayNode());
                manifest.put("retentionYears", 5).put("automaticDeletion", false);
                byte[] manifestBytes = json(manifest);
                StringBuilder sums = new StringBuilder();
                files.forEach((path, bytes) -> sums.append(hash(bytes)).append("  ").append(bytes.length).append("  ")
                        .append(path).append('\n'));
                sums.append(hash(manifestBytes)).append("  ").append(manifestBytes.length).append("  manifest.json\n");
                files.put("manifest.json", manifestBytes);
                files.put("inventory/SHA256SUMS", sums.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(output)) {
                    for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                        zip.putNextEntry(new ZipEntry(entry.getKey()));
                        zip.write(entry.getValue());
                        zip.closeEntry();
                    }
                }
                return output.toByteArray();
            } catch (Exception failure) {
                throw new IllegalStateException("could_not_build_synthetic_test_package", failure);
            }
        }

        private static String inputDigest(ObjectNode input, String schemaDefinition) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode canonical = mapper.createObjectNode().put("provider", input.path("provider").asText())
                    .put("model", input.path("model").asText()).put("schemaVersion", input.path("schemaVersion").asText())
                    .put("schemaDefinition", schemaDefinition);
            canonical.set("effectiveParameters", mapper.createObjectNode());
            ArrayNode excerpts = canonical.putArray("excerpts");
            JsonNode excerpt = input.path("excerpts").get(0);
            excerpts.addObject().put("segmentId", excerpt.path("segmentId").asText())
                    .put("documentVersionId", excerpt.path("documentVersionId").asText())
                    .put("title", excerpt.path("title").asText()).put("sourceUrl", excerpt.path("sourceUrl").asText())
                    .put("page", excerpt.path("page").asInt()).put("text", excerpt.path("text").asText())
                    .put("section", excerpt.path("section").asText());
            return hash(mapper.writeValueAsBytes(canonical));
        }

        private static byte[] entry(byte[] zipBytes, String target) {
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) if (target.equals(entry.getName())) return zip.readAllBytes();
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            throw new IllegalArgumentException("test package entry missing");
        }

        private static byte[] json(JsonNode value) throws Exception { return new ObjectMapper().writeValueAsBytes(value); }

        private static String hash(byte[] value) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
    }
}
