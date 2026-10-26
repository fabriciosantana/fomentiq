package com.fomentiq.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Offline validator for immutable human factual-review records bound to audit-package v1. */
public final class FactualReviewValidator {
    private static final List<String> FIELDS = List.of(
            "deadline", "totalBudget", "eligibility", "counterpart", "trl", "requiresIct");
    private static final Set<String> TECHNICAL_STATES = Set.of("EXTRACTED", "VALIDATED", "CONFLICT", "MISSING");
    private static final Set<String> OUTCOMES = Set.of("APROVADO", "REPROVADO", "INCONCLUSIVO", "AUSÊNCIA CONFIRMADA");
    private static final int MAX_REVIEW_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper mapper;
    private final JsonNode schema;
    private final JsonSchemaValidator schemaValidator = new JsonSchemaValidator();

    public FactualReviewValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream input = FactualReviewValidator.class.getResourceAsStream(
                "/factual-review/factual-review-v1.schema.json")) {
            if (input == null) throw new IllegalStateException("factual_review_schema_missing");
            schema = mapper.readTree(input);
        } catch (IOException failure) {
            throw new IllegalStateException("factual_review_schema_invalid");
        }
    }

    public ValidationResult validate(Path packagePath, Path reviewPath) {
        try {
            if (Files.size(packagePath) > 64L * 1024 * 1024 || Files.size(reviewPath) > MAX_REVIEW_BYTES) {
                return invalid("input_size_limit");
            }
            byte[] packageBytes = Files.readAllBytes(packagePath);
            JsonNode review = mapper.readTree(Files.readAllBytes(reviewPath));
            if (!reviewPath.getFileName().toString().equals(review.path("recordId").asText() + ".json")) {
                return invalid("review_filename_must_match_record_id");
            }
            return validate(packageBytes, review, reviewPath.toAbsolutePath().normalize().getParent());
        } catch (IOException failure) {
            return invalid("input_unreadable_or_invalid");
        }
    }

    public ValidationResult validate(byte[] packageBytes, JsonNode review, Path historyDirectory) {
        List<String> gaps = new ArrayList<>();
        if (packageBytes == null || packageBytes.length > 64L * 1024 * 1024) return invalid("package_size_limit");
        AuditPackageVerifier.VerificationResult packageResult =
                new AuditPackageVerifier(mapper).verify(packageBytes);
        if (!packageResult.integrityValid()) return invalid("audit_package_integrity_invalid");
        if (!packageResult.complete()) return invalid("audit_package_incomplete");

        PackageFiles files;
        try {
            files = readPackage(packageBytes);
        } catch (IOException | RuntimeException failure) {
            return invalid("audit_package_content_invalid");
        }
        JsonNode packageManifest = files.json("manifest.json", mapper);
        if (packageManifest.path("formatVersion").asInt(-1) != 1) return invalid("audit_package_version_unsupported");
        List<String> schemaGaps = schemaValidator.validate(review, schema);
        if (!schemaGaps.isEmpty()) return new ValidationResult(false, null, null, schemaGaps.stream()
                .map(code -> "review_" + code).distinct().toList());
        try {
            AuditPayloadSanitizer.requireSafeText(mapper.writeValueAsString(review));
        } catch (RuntimeException | IOException sensitiveOrInvalid) {
            return invalid("review_sensitive_or_invalid_text");
        }
        try {
            UUID.fromString(review.path("recordId").asText());
            java.time.OffsetDateTime.parse(review.path("createdAt").asText());
        } catch (IllegalArgumentException | java.time.DateTimeException invalidTimeOrId) {
            return invalid("review_identity_or_timestamp_invalid");
        }
        if (!isUtcTimestamp(review.path("createdAt").asText())) return invalid("review_timestamp_not_utc");

        String packageId = packageManifest.path("packageId").asText();
        String packageSha = sha256(packageBytes);
        String manifestSha = sha256(files.bytes.get("manifest.json"));
        JsonNode binding = review.path("package");
        if (!packageId.equals(binding.path("packageId").asText())
                || !packageSha.equals(binding.path("packageSha256").asText())
                || !manifestSha.equals(binding.path("manifestSha256").asText())
                || binding.path("formatVersion").asInt(-1) != 1
                || !packageManifest.path("sourceOrigin").asText().equals(binding.path("sourceOrigin").asText())) {
            gaps.add("review_package_binding_mismatch");
        }
        validateHistory(review, historyDirectory, packageId, packageSha, gaps);

        JsonNode documentVersions = files.json("source/document-versions.json", mapper);
        Map<String, JsonNode> versions = new HashMap<>();
        for (JsonNode version : documentVersions) versions.put(version.path("id").asText(), version);
        Set<String> absenceScope = applicableDocumentVersions(files, mapper, documentVersions);
        JsonNode fieldHistory = files.json("extraction/field-history.json", mapper);
        Map<String, JsonNode> latestFields = latestFields(fieldHistory);
        Set<String> decisions = new HashSet<>();
        for (JsonNode decision : review.path("decisions")) {
            String field = decision.path("fieldName").asText();
            if (!decisions.add(field)) gaps.add("duplicate_field_decision");
            JsonNode technical = latestFields.get(field);
            if (technical == null || !decision.path("extractedFieldId").asText().equals(technical.path("id").asText())
                    || !decision.path("technicalState").asText().equals(technical.path("validationStatus").asText())
                    || !decision.path("extractedValue").equals(technical.path("normalizedValue"))) {
                gaps.add("technical_snapshot_mismatch");
            }
            if (!isUtcTimestamp(decision.path("reviewedAt").asText())) gaps.add("review_timestamp_not_utc");
            validateClaims(decision.path("claims"), versions, files, gaps);
            validateCoverageReferences(decision, gaps);
            validateOutcome(decision, absenceScope, gaps);
        }
        if (!decisions.equals(new HashSet<>(FIELDS))) gaps.add("six_field_matrix_incomplete");
        return new ValidationResult(gaps.isEmpty(), packageId, packageSha, List.copyOf(gaps));
    }

    private void validateOutcome(JsonNode decision, Set<String> applicableDocumentIds, List<String> gaps) {
        String field = decision.path("fieldName").asText();
        String outcome = decision.path("outcome").asText();
        JsonNode claims = decision.path("claims");
        JsonNode coverage = decision.path("coverage");
        if (!TECHNICAL_STATES.contains(decision.path("technicalState").asText())) gaps.add("technical_state_invalid");
        if (!OUTCOMES.contains(outcome)) gaps.add("factual_outcome_invalid");
        if (decision.path("rationale").asText().isBlank()) gaps.add("rationale_missing");

        switch (outcome) {
            case "APROVADO", "REPROVADO" -> {
                if (decision.path("documentaryValue").isNull() || claims.isEmpty()) gaps.add("documentary_decision_evidence_missing");
                if ("APROVADO".equals(outcome) && Set.of("counterpart", "eligibility").contains(field)) {
                    if (!coverage.path("sourceScopeComplete").asBoolean(false)
                            || !coverage.path("branchInventoryComplete").asBoolean(false)
                            || !coverage.path("conditionsAndExceptionsReviewed").asBoolean(false)
                            || decision.path("conditions").isEmpty() || decision.path("exceptions").isEmpty()) {
                        gaps.add("conditional_rule_coverage_incomplete");
                    }
                    if ("counterpart".equals(field) && decision.path("conditions").size() < 2) {
                        gaps.add("counterpart_single_band_is_not_complete");
                    }
                    if ("counterpart".equals(field) && !hasDistinctCounterpartBranches(decision)) {
                        gaps.add("counterpart_branch_coverage_not_distinct");
                    }
                    if ("eligibility".equals(field) && (!coverage.path("exclusionsReviewed").asBoolean(false)
                            || decision.path("exceptions").isEmpty())) gaps.add("eligibility_exclusions_not_reviewed");
                    if (!allCoverageItemsCited(decision.path("conditions"), claims)
                            || !allCoverageItemsCited(decision.path("exceptions"), claims)) {
                        gaps.add("conditional_clause_evidence_missing");
                    }
                }
            }
            case "INCONCLUSIVO" -> {
                if (!decision.path("documentaryValue").isNull() && claims.isEmpty()) {
                    gaps.add("inconclusive_value_without_evidence");
                }
            }
            case "AUSÊNCIA CONFIRMADA" -> {
                if (!decision.path("documentaryValue").isNull() || !claims.isEmpty()) gaps.add("absence_has_documentary_claim");
                JsonNode scope = decision.path("absenceReview");
                Set<String> reviewed = new HashSet<>();
                for (JsonNode id : scope.path("reviewedDocumentVersionIds")) reviewed.add(id.asText());
                if (applicableDocumentIds.isEmpty() || !scope.path("complete").asBoolean(false)
                        || scope.path("searchDescription").asText().isBlank()
                        || !reviewed.equals(applicableDocumentIds)) gaps.add("absence_scope_incomplete");
            }
            default -> gaps.add("factual_outcome_invalid");
        }
    }

    private boolean allCoverageItemsCited(JsonNode items, JsonNode claims) {
        if (!items.isArray()) return false;
        Set<String> claimIds = new HashSet<>();
        for (JsonNode claim : claims) claimIds.add(claim.path("claimId").asText());
        for (JsonNode item : items) {
            if (item.path("statement").asText().isBlank() || item.path("claimIds").isEmpty()) return false;
            for (JsonNode id : item.path("claimIds")) if (!claimIds.contains(id.asText())) return false;
        }
        return true;
    }

    private void validateCoverageReferences(JsonNode decision, List<String> gaps) {
        Set<String> claimIds = new HashSet<>();
        for (JsonNode claim : decision.path("claims")) claimIds.add(claim.path("claimId").asText());
        for (String property : List.of("conditions", "exceptions")) {
            for (JsonNode item : decision.path(property)) {
                for (JsonNode id : item.path("claimIds")) {
                    if (!claimIds.contains(id.asText())) gaps.add("coverage_claim_reference_invalid");
                }
            }
        }
    }

    private boolean hasDistinctCounterpartBranches(JsonNode decision) {
        Set<String> ids = new HashSet<>();
        Set<String> statements = new HashSet<>();
        Set<String> sourceCitations = new HashSet<>();
        Map<String, JsonNode> claims = new HashMap<>();
        for (JsonNode claim : decision.path("claims")) claims.put(claim.path("claimId").asText(), claim);
        for (JsonNode condition : decision.path("conditions")) {
            String id = condition.path("id").asText();
            String statement = condition.path("statement").asText().strip().replaceAll("\\s+", " ")
                    .toLowerCase(java.util.Locale.ROOT);
            if (id.isBlank() || statement.isBlank() || !ids.add(id) || !statements.add(statement)) return false;
            List<String> citations = new ArrayList<>();
            for (JsonNode claimId : condition.path("claimIds")) {
                JsonNode claim = claims.get(claimId.asText());
                if (claim == null) return false;
                citations.add(claim.path("documentVersionId").asText() + ":" + claim.path("page").asInt(-1)
                        + ":" + claim.path("quote").asText());
            }
            if (citations.isEmpty()) return false;
            citations.sort(String::compareTo);
            if (!sourceCitations.add(String.join("|", citations))) return false;
        }
        return ids.size() >= 2;
    }

    private void validateClaims(JsonNode claims, Map<String, JsonNode> versions, PackageFiles files, List<String> gaps) {
        Set<String> ids = new HashSet<>();
        for (JsonNode claim : claims) {
            String id = claim.path("claimId").asText();
            if (id.isBlank() || !ids.add(id)) gaps.add("claim_id_invalid_or_duplicate");
            JsonNode version = versions.get(claim.path("documentVersionId").asText());
            if (version == null || !claim.path("documentSha256").asText().equals(version.path("contentHash").asText())) {
                gaps.add("claim_document_reference_invalid");
                continue;
            }
            int page = claim.path("page").asInt(-1);
            String quote = claim.path("quote").asText();
            JsonNode locator = claim.path("locator");
            byte[] pdf = files.bytes.get(version.path("archivePath").asText());
            if (page < 1 || quote.isBlank() || !locator.isObject() || locator.isEmpty() || pdf == null) {
                gaps.add("claim_locator_missing");
                continue;
            }
            if (locator.path("page").asInt(-1) != page) {
                gaps.add("claim_locator_mismatch");
                continue;
            }
            try (PDDocument document = Loader.loadPDF(pdf)) {
                if (page > document.getNumberOfPages()) {
                    gaps.add("claim_page_out_of_range");
                    continue;
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                if (!stripper.getText(document).contains(quote)) gaps.add("claim_quote_not_in_document");
            } catch (IOException failure) {
                gaps.add("claim_document_unreadable");
            }
        }
    }

    private Map<String, JsonNode> latestFields(JsonNode history) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode field : history) {
            String name = field.path("fieldName").asText();
            JsonNode previous = result.get(name);
            if (previous == null || field.path("createdAt").asText().compareTo(previous.path("createdAt").asText()) > 0) {
                result.put(name, field);
            }
        }
        return result;
    }

    private Set<String> applicableDocumentVersions(PackageFiles files, ObjectMapper objectMapper,
                                                   JsonNode documentVersions) {
        Map<String, String> documentTypes = new HashMap<>();
        for (JsonNode document : files.json("source/documents.json", objectMapper)) {
            documentTypes.put(document.path("id").asText(), document.path("document_type").asText());
        }
        Set<String> applicable = new HashSet<>();
        for (JsonNode version : documentVersions) {
            String type = documentTypes.get(version.path("documentId").asText());
            if (!Set.of("RESULT_PRELIMINARY", "RESULT_FINAL").contains(type)) {
                applicable.add(version.path("id").asText());
            }
        }
        return applicable;
    }

    private boolean isUtcTimestamp(String value) {
        try {
            return java.time.OffsetDateTime.parse(value).getOffset().getTotalSeconds() == 0;
        } catch (java.time.DateTimeException invalid) {
            return false;
        }
    }

    private void validateHistory(JsonNode review, Path historyDirectory, String packageId,
                                 String packageSha, List<String> gaps) {
        JsonNode supersedes = review.path("supersedes");
        if (supersedes.isNull()) return;
        String priorId = supersedes.path("recordId").asText();
        String priorSha = supersedes.path("sha256").asText();
        if (priorId.isBlank() || !priorSha.matches("[0-9a-f]{64}") || historyDirectory == null) {
            gaps.add("superseded_review_reference_invalid");
            return;
        }
        try {
            UUID.fromString(priorId);
            Path priorPath = historyDirectory.resolve(priorId + ".json").normalize();
            if (!priorPath.getParent().equals(historyDirectory.toAbsolutePath().normalize())
                    || Files.isSymbolicLink(priorPath) || !Files.isRegularFile(priorPath)) {
                gaps.add("superseded_review_missing");
                return;
            }
            byte[] priorBytes = Files.readAllBytes(priorPath);
            JsonNode prior = mapper.readTree(priorBytes);
            if (priorId.equals(review.path("recordId").asText()) || !sha256(priorBytes).equals(priorSha)
                    || !schemaValidator.validate(prior, schema).isEmpty()
                    || !priorId.equals(prior.path("recordId").asText())
                    || prior.path("decisions").size() != FIELDS.size()
                    || !packageId.equals(prior.path("package").path("packageId").asText())
                    || !packageSha.equals(prior.path("package").path("packageSha256").asText())) {
                gaps.add("superseded_review_binding_mismatch");
            }
        } catch (IOException | IllegalArgumentException failure) {
            gaps.add("superseded_review_unreadable");
        }
    }

    private PackageFiles readPackage(byte[] bytes) throws IOException {
        Map<String, byte[]> content = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || content.containsKey(entry.getName())) throw new IOException("package_entry_invalid");
                content.put(entry.getName(), zip.readAllBytes());
            }
        }
        return new PackageFiles(content);
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("sha256_unavailable"); }
    }

    private ValidationResult invalid(String code) { return new ValidationResult(false, null, null, List.of(code)); }

    private record PackageFiles(Map<String, byte[]> bytes) {
        private JsonNode json(String path, ObjectMapper mapper) {
            byte[] value = bytes.get(path);
            if (value == null) throw new IllegalArgumentException("package_file_missing");
            try {
                return mapper.readTree(value);
            } catch (IOException failure) {
                throw new IllegalArgumentException("package_json_invalid");
            }
        }
    }

    public record ValidationResult(boolean valid, String packageId, String packageSha256, List<String> gaps) { }
}
