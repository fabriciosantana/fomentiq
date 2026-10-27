package com.fomentiq.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Offline-only integrity and reference verifier. It has no Spring, DB, S3, Finep or provider dependencies. */
public final class AuditPackageVerifier {
    private static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1_000;
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    private final ObjectMapper mapper;
    private final JsonNode manifestSchema;
    private final JsonSchemaValidator schemaValidator = new JsonSchemaValidator();

    public AuditPackageVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream schema = AuditPackageVerifier.class.getResourceAsStream("/audit-package/manifest-v1.schema.json")) {
            if (schema == null) throw new IllegalStateException("audit_manifest_schema_missing");
            this.manifestSchema = mapper.readTree(schema);
        } catch (IOException failure) {
            throw new IllegalStateException("audit_manifest_schema_invalid");
        }
    }

    public VerificationResult verify(Path archivePath) {
        try {
            long size = Files.size(archivePath);
            if (size > MAX_ARCHIVE_BYTES) return invalid("archive_size_limit");
            return verify(Files.readAllBytes(archivePath));
        } catch (IOException failure) {
            return invalid("archive_unreadable");
        }
    }

    public VerificationResult verify(byte[] archiveBytes) {
        if (archiveBytes == null || archiveBytes.length > MAX_ARCHIVE_BYTES) return invalid("archive_size_limit");
        Map<String, byte[]> files;
        try {
            files = readArchive(archiveBytes);
        } catch (IOException | IllegalArgumentException failure) {
            return invalid("archive_format_invalid");
        }
        try {
            byte[] manifestBytes = files.get("manifest.json");
            byte[] sumsBytes = files.get("inventory/SHA256SUMS");
            if (manifestBytes == null || sumsBytes == null) return invalid("inventory_required_file_missing");
            JsonNode manifest = mapper.readTree(manifestBytes);
            if (manifest.path("formatVersion").asInt(-1) != 1) return invalid("manifest_version_unsupported");
            if (!schemaValidator.validate(manifest, manifestSchema).isEmpty()) return invalid("manifest_schema_invalid");
            List<String> gaps = new ArrayList<>();
            verifyManifestHeader(manifest, gaps);
            int checksums = verifyChecksums(files, sumsBytes, gaps);
            if (!gaps.isEmpty()) return new VerificationResult(false, false, checksums, List.copyOf(gaps));
            verifyManifestInventory(manifest, files, gaps);
            verifyReferences(manifest, files, gaps);
            boolean manifestComplete = manifest.path("complete").asBoolean(false);
            if (!manifestComplete) gaps.add("package_marked_incomplete");
            boolean complete = manifestComplete && gaps.isEmpty();
            return new VerificationResult(true, complete, checksums, List.copyOf(gaps));
        } catch (IOException | RuntimeException failure) {
            return invalid("package_content_invalid");
        }
    }

    private void verifyManifestHeader(JsonNode manifest, List<String> gaps) {
        String runId = manifest.path("ingestionRunId").asText();
        String sourceOrigin = manifest.path("sourceOrigin").asText();
        if (!runId.equals(manifest.path("packageId").asText())) gaps.add("package_run_identity_mismatch");
        if (!Set.of("LIVE", "FIXTURE", "RECONSTRUCTED").contains(sourceOrigin)) gaps.add("source_origin_invalid");
        if (manifest.path("createdAt").asText().isBlank()) gaps.add("package_creation_time_missing");
        if (!manifest.path("codeRevision").asText().matches("(?i)[a-f0-9]{40}|[a-f0-9]{64}")) {
            gaps.add("run_code_revision_missing_or_invalid");
        }
        if (manifest.path("retentionYears").asInt(-1) != 5
                || !manifest.path("automaticDeletion").isBoolean()
                || manifest.path("automaticDeletion").asBoolean()) gaps.add("retention_policy_invalid");
        if (!manifest.path("complete").isBoolean() || !manifest.path("requiredFiles").isArray()
                || !manifest.path("files").isArray() || !manifest.path("gaps").isArray()) {
            gaps.add("manifest_schema_invalid");
        }
        if (manifest.path("complete").asBoolean(false) && !manifest.path("gaps").isEmpty()) {
            gaps.add("manifest_claims_complete_with_gaps");
        }
    }

    private Map<String, byte[]> readArchive(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new java.util.TreeMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validatePath(name);
                if (entry.isDirectory() || files.containsKey(name) || files.size() >= MAX_ENTRIES) {
                    throw new IllegalArgumentException("archive entry invalid");
                }
                byte[] content = readBounded(zip, MAX_ENTRY_BYTES);
                total += content.length;
                if (total > MAX_ARCHIVE_BYTES) throw new IllegalArgumentException("archive size invalid");
                verifySanitizedContent(name, content);
                files.put(name, content);
            }
        }
        return files;
    }

    private void verifySanitizedContent(String path, byte[] content) throws IOException {
        if (path.endsWith(".pdf") && content.length >= 4
                && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F') return;
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        AuditPayloadSanitizer.requireSafeText(text);
        if (path.endsWith(".json")) validateUrls(mapper.readTree(content));
    }

    private void validateUrls(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getKey().toLowerCase(java.util.Locale.ROOT).endsWith("url")
                        && field.getValue().isTextual()) {
                    AuditPayloadSanitizer.requireSafeUrl(field.getValue().asText());
                }
                validateUrls(field.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(this::validateUrls);
        }
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IllegalArgumentException("entry size invalid");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void validatePath(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException("unsafe archive path");
        for (String part : name.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("unsafe archive path");
            }
        }
    }

    private int verifyChecksums(Map<String, byte[]> files, byte[] sums, List<String> gaps) {
        Set<String> listed = new HashSet<>();
        int verified = 0;
        for (String line : new String(sums, java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 3);
            if (parts.length != 3 || !SHA256.matcher(parts[0]).matches()) {
                gaps.add("checksum_inventory_malformed");
                continue;
            }
            String path = parts[2];
            validatePath(path);
            if (path.equals("inventory/SHA256SUMS") || !listed.add(path)) {
                gaps.add("checksum_inventory_duplicate");
                continue;
            }
            byte[] content = files.get(path);
            if (content == null) {
                gaps.add("checksum_target_missing");
            } else if (!parts[0].equals(sha256(content)) || !parts[1].equals(Integer.toString(content.length))) {
                gaps.add("checksum_mismatch");
            } else {
                verified++;
            }
        }
        for (String path : files.keySet()) {
            if (!path.equals("inventory/SHA256SUMS") && !listed.contains(path)) gaps.add("file_not_in_inventory");
        }
        return verified;
    }

    private void verifyManifestInventory(JsonNode manifest, Map<String, byte[]> files, List<String> gaps) {
        Set<String> declared = new HashSet<>();
        Set<String> inventoryPaths = new HashSet<>();
        for (JsonNode required : manifest.path("requiredFiles")) {
            String path = required.asText();
            declared.add(path);
            if (!files.containsKey(path)) gaps.add("required_file_missing");
        }
        for (JsonNode item : manifest.path("files")) {
            String path = item.path("path").asText();
            if (!inventoryPaths.add(path)) gaps.add("manifest_file_duplicate");
            byte[] content = files.get(path);
            if (content == null) {
                gaps.add("manifest_file_missing");
                continue;
            }
            if (!item.path("sha256").asText().equals(sha256(content))
                    || item.path("length").asLong(-1) != content.length) gaps.add("manifest_file_mismatch");
            if (!declared.contains(path)) gaps.add("manifest_file_not_required");
            String origin = item.path("artifactOrigin").asText();
            if (!Set.of("LIVE", "FIXTURE", "RECONSTRUCTED", "GENERATED").contains(origin)) {
                gaps.add("artifact_origin_invalid");
            }
        }
        for (String path : declared) {
            if (!inventoryPaths.contains(path)) gaps.add("required_file_not_in_manifest_inventory");
        }
        for (String path : files.keySet()) {
            if (!Set.of("manifest.json", "inventory/SHA256SUMS").contains(path)
                    && !inventoryPaths.contains(path)) gaps.add("archive_file_not_in_manifest_inventory");
        }
        if (!manifest.path("ingestionRunId").isTextual() || manifest.path("ingestionRunId").asText().isBlank()) {
            gaps.add("run_attribution_missing");
        }
    }

    private void verifyReferences(JsonNode manifest, Map<String, byte[]> files, List<String> gaps) throws IOException {
        JsonNode fields = json(files, "extraction/field-history.json");
        JsonNode evidence = json(files, "extraction/evidence.json");
        JsonNode snapshots = json(files, "source/snapshots.json");
        JsonNode versions = json(files, "source/document-versions.json");
        JsonNode excerpts = json(files, "extraction/provider-input-excerpts.json");
        JsonNode extractionSchema = json(files, "extraction/schema.json");
        JsonNode outputSchema = extractionSchema.path("schema");
        if (!outputSchema.isObject() || !extractionSchema.path("strict").asBoolean(false)
                || extractionSchema.path("name").asText().isBlank()) {
            gaps.add("extraction_schema_invalid");
        }
        String runId = manifest.path("ingestionRunId").asText();
        JsonNode executions = json(files, "extraction/execution-history.json");
        if ("LIVE".equals(manifest.path("sourceOrigin").asText())) {
            JsonNode approval = json(files, "authorization/manifest-sanitized.json");
            JsonNode link = json(files, "authorization/run-manifest-link.json");
            JsonNode approvedManifest = approval.path("approvedManifest");
            if (!runId.equals(link.path("ingestionRunId").asText())
                    || !link.path("manifestRunId").asText().equals(approval.path("manifestRunId").asText())
                    || !link.path("manifestRunId").asText().equals(approvedManifest.path("runId").asText())
                    || approvedManifest.path("isolation").path("composeProject").asText().isBlank()) {
                gaps.add("live_authorization_attribution_invalid");
            }
        }
        Map<String, JsonNode> inputsByDigest = new HashMap<>();
        JsonNode inputList = excerpts.isArray() ? excerpts : mapper.createArrayNode().add(excerpts);
        for (JsonNode input : inputList) {
            if (!runId.equals(input.path("ingestionRunId").asText())) gaps.add("provider_input_run_attribution_invalid");
            inputsByDigest.put(input.path("inputDigest").asText(), input);
        }
        if (executions.isEmpty()) gaps.add("extraction_execution_missing");
        for (JsonNode snapshot : snapshots) {
            byte[] snapshotBytes = files.get(snapshot.path("archivePath").asText());
            if (snapshotBytes == null || !sha256(snapshotBytes).equals(snapshot.path("contentHash").asText())) {
                gaps.add("source_snapshot_hash_mismatch");
            }
        }
        for (JsonNode execution : executions) {
            if (!runId.equals(execution.path("ingestionRunId").asText())) gaps.add("execution_run_attribution_invalid");
            String digest = execution.path("inputDigest").asText();
            JsonNode input = inputsByDigest.get(digest);
            if (input == null || !execution.path("callVersionId").asText().equals(input.path("callVersionId").asText())) {
                gaps.add("execution_input_reference_invalid");
            } else {
                try {
                    JsonNode capturedSchema = mapper.readTree(input.path("schemaDefinition").asText());
                    if (!capturedSchema.equals(extractionSchema)
                            || !input.path("schemaVersion").asText().equals(extractionSchema.path("name").asText())) {
                        gaps.add("extraction_schema_reference_invalid");
                    }
                } catch (IOException invalidSchema) {
                    gaps.add("extraction_schema_reference_invalid");
                }
            }
            JsonNode result = json(files, execution.path("structuredResultPath").asText());
            if (!runId.equals(result.path("ingestionRunId").asText())
                    || !digest.equals(result.path("inputDigest").asText())
                    || !execution.path("callVersionId").asText().equals(result.path("callVersionId").asText())) {
                gaps.add("execution_result_reference_invalid");
            }
            List<String> resultSchemaErrors = schemaValidator.validate(result.path("structuredFields"), outputSchema);
            if (!resultSchemaErrors.isEmpty()) {
                gaps.add("structured_result_schema_invalid");
            }
        }
        Set<String> fieldIds = new HashSet<>();
        Set<String> runIds = new HashSet<>();
        JsonNode runHistory = json(files, "run/run-history.json");
        runIds.add(runId);
        for (JsonNode run : runHistory) runIds.add(run.path("id").asText());
        Map<String, String> callByVersionId = new HashMap<>();
        for (JsonNode version : json(files, "source/call-versions.json")) {
            callByVersionId.put(version.path("id").asText(), version.path("call_id").asText());
        }
        Map<String, String> callByDocumentId = new HashMap<>();
        for (JsonNode document : json(files, "source/documents.json")) {
            callByDocumentId.put(document.path("id").asText(), document.path("call_id").asText());
        }
        for (JsonNode field : fields) {
            fieldIds.add(field.path("id").asText());
            if (!runIds.contains(field.path("ingestionRunId").asText())) gaps.add("field_run_attribution_invalid");
            if (!callByVersionId.containsKey(field.path("callVersionId").asText())) {
                gaps.add("field_call_version_reference_invalid");
            }
        }
        Map<String, JsonNode> docs = new HashMap<>();
        for (JsonNode version : versions) {
            docs.put(version.path("id").asText(), version);
            String documentId = version.path("documentId").asText();
            if (!callByDocumentId.containsKey(documentId)) gaps.add("document_owner_reference_invalid");
            byte[] documentBytes = files.get(version.path("archivePath").asText());
            if (documentBytes == null || !sha256(documentBytes).equals(version.path("contentHash").asText())) {
                gaps.add("source_document_hash_mismatch");
            }
        }
        Map<String, JsonNode> segments = new HashMap<>();
        if (excerpts.isArray()) {
            for (JsonNode input : excerpts) {
                verifyInputDigest(input, gaps);
                for (JsonNode excerpt : input.path("excerpts")) segments.put(excerpt.path("segmentId").asText(), excerpt);
            }
        } else {
            verifyInputDigest(excerpts, gaps);
            for (JsonNode excerpt : excerpts.path("excerpts")) segments.put(excerpt.path("segmentId").asText(), excerpt);
        }
        for (JsonNode item : evidence) {
            String fieldId = item.path("extractedFieldId").asText();
            if (!fieldIds.contains(fieldId)) gaps.add("evidence_field_reference_invalid");
            String versionId = item.path("documentVersionId").asText("");
            JsonNode version = docs.get(versionId);
            if (version == null) {
                gaps.add("evidence_document_reference_invalid");
                continue;
            }
            JsonNode field = fields.findParents("id").stream()
                    .filter(candidate -> fieldId.equals(candidate.path("id").asText())).findFirst().orElse(null);
            String fieldCallId = field == null ? null : callByVersionId.get(field.path("callVersionId").asText());
            String documentCallId = callByDocumentId.get(version.path("documentId").asText());
            if (fieldCallId == null || !fieldCallId.equals(documentCallId)) {
                gaps.add("evidence_opportunity_reference_invalid");
            }
            JsonNode locator = mapper.readTree(item.path("locatorJson").asText("{}"));
            String segmentId = locator.path("segmentId").asText();
            JsonNode excerpt = segments.get(segmentId);
            String quote = item.path("quoteFragment").asText("");
            if (excerpt == null || !excerpt.path("documentVersionId").asText().equals(versionId)
                    || quote.isBlank() || !excerpt.path("text").asText().contains(quote)) {
                gaps.add("evidence_excerpt_locator_invalid");
                continue;
            }
            verifyPdfQuote(version, item.path("page").asInt(-1), quote, files, gaps);
        }
    }

    private void verifyInputDigest(JsonNode input, List<String> gaps) {
        try {
            if (input.has("coverage")) verifyCoverage(input, gaps);
            JsonNode parameters = input.path("effectiveParameters");
            var canonical = mapper.createObjectNode().put("provider", input.path("provider").asText())
                    .put("model", input.path("model").asText()).put("schemaVersion", input.path("schemaVersion").asText())
                    .put("schemaDefinition", input.path("schemaDefinition").asText());
            var sortedParameters = mapper.createObjectNode();
            java.util.TreeMap<String, JsonNode> sorted = new java.util.TreeMap<>();
            parameters.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach(sortedParameters::set);
            canonical.set("effectiveParameters", sortedParameters);
            var excerptArray = canonical.putArray("excerpts");
            for (JsonNode excerpt : input.path("excerpts")) {
                var item = excerptArray.addObject().put("segmentId", excerpt.path("segmentId").asText())
                        .put("documentVersionId", excerpt.path("documentVersionId").asText())
                        .put("title", excerpt.path("title").asText()).put("sourceUrl", excerpt.path("sourceUrl").asText());
                if (excerpt.path("page").isInt()) item.put("page", excerpt.path("page").asInt());
                else item.putNull("page");
                item.put("text", excerpt.path("text").asText());
                if (excerpt.path("section").isTextual()) item.put("section", excerpt.path("section").asText());
                if (input.has("coverage")) {
                    if (excerpt.path("documentId").isTextual()) item.put("documentId", excerpt.path("documentId").asText());
                    if (excerpt.path("documentType").isTextual()) item.put("documentType", excerpt.path("documentType").asText());
                    item.put("startOffset", excerpt.path("startOffset").asInt(0));
                    item.put("endOffset", excerpt.path("endOffset").asInt(excerpt.path("text").asText().length()));
                }
            }
            if (input.has("coverage")) canonical.set("coverage", input.path("coverage"));
            if (!input.path("inputDigest").asText().equals(sha256(mapper.writeValueAsBytes(canonical)))) {
                gaps.add("provider_input_digest_mismatch");
            }
        } catch (IOException | RuntimeException failure) {
            gaps.add("provider_input_digest_invalid");
        }
    }

    private void verifyCoverage(JsonNode input, List<String> gaps) {
        JsonNode coverage = input.path("coverage");
        if (coverage.path("version").asInt(-1) != 1
                || coverage.path("maxDocuments").asInt(-1) != 6
                || coverage.path("maxExcerpts").asInt(-1) != 24
                || coverage.path("maxExcerptChars").asInt(-1) != 1400
                || coverage.path("maxTotalChars").asInt(-1) != 24000
                || !coverage.path("fields").isObject() || !coverage.path("sourceScans").isArray()
                || !coverage.path("gaps").isArray()) {
            gaps.add("selection_coverage_invalid");
            return;
        }
        JsonNode recordedSummary = input.path("coverageSummary");
        var expectedSummary = mapper.createObjectNode().put("complete", coverage.path("complete").asBoolean(false));
        expectedSummary.set("gaps", coverage.path("gaps"));
        var summaryFields = expectedSummary.putObject("fields");
        var sourceFields = coverage.path("fields").fields();
        while (sourceFields.hasNext()) {
            var field = sourceFields.next();
            long selectedCount = 0;
            for (JsonNode entry : field.getValue()) if (entry.path("selected").asBoolean(false)) selectedCount++;
            summaryFields.putObject(field.getKey()).put("candidatePages", field.getValue().size())
                    .put("selectedPages", Math.toIntExact(selectedCount)).put("complete", !field.getValue().isEmpty()
                            && selectedCount == field.getValue().size());
        }
        if (!recordedSummary.equals(expectedSummary)) gaps.add("selection_coverage_summary_mismatch");
        int chars = 0;
        Map<String, JsonNode> excerpts = new HashMap<>();
        for (JsonNode excerpt : input.path("excerpts")) {
            chars += excerpt.path("text").asText().length();
            excerpts.put(excerpt.path("documentVersionId").asText() + ":" + excerpt.path("page").asInt(-1), excerpt);
        }
        if (input.path("excerpts").size() > 24 || chars != coverage.path("selectedChars").asInt(-1)
                || chars > 24000) gaps.add("selection_coverage_limits_invalid");
        int scannedSources = 0;
        for (JsonNode source : coverage.path("sourceScans")) {
            if (source.path("documentVersionId").asText().isBlank() || source.path("documentType").asText().isBlank()
                    || source.path("pageCount").asInt(-1) < 0) {
                gaps.add("selection_source_scan_invalid");
                continue;
            }
            if (source.path("scanned").asBoolean(false)) scannedSources++;
            else if (!Set.of("SOURCE_UNSCANNED", "RESULT_NOT_REQUIREMENT_SOURCE")
                    .contains(source.path("skipReason").asText())) gaps.add("selection_source_skip_reason_invalid");
        }
        if (scannedSources > 6 || coverage.path("complete").asBoolean(false)
                && coverage.path("sourceScans").findValuesAsText("skipReason").contains("SOURCE_UNSCANNED")) {
            gaps.add("selection_source_scan_limit_or_completeness_invalid");
        }
        var fields = coverage.path("fields").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getValue().isArray()) { gaps.add("selection_coverage_field_invalid"); continue; }
            for (JsonNode entry : field.getValue()) {
                if (!entry.path("candidate").asBoolean(false)
                        && !"SOURCE_UNSCANNED".equals(entry.path("gapReason").asText())) {
                    gaps.add("selection_coverage_candidate_missing");
                }
                if (entry.path("selected").asBoolean(false)) {
                    String key = entry.path("documentVersionId").asText() + ":" + entry.path("page").asInt(-1);
                    JsonNode excerpt = excerpts.get(key);
                    if (excerpt == null || !entry.path("documentType").asText().equals(excerpt.path("documentType").asText())
                            || entry.path("documentId").isTextual()
                            && !entry.path("documentId").asText().equals(excerpt.path("documentId").asText())) {
                        gaps.add("selection_coverage_reference_invalid");
                    }
                } else if (entry.path("gapReason").asText().isBlank()) {
                    gaps.add("selection_coverage_gap_reason_missing");
                }
            }
        }
        if (coverage.path("complete").asBoolean(false) && !coverage.path("gaps").isEmpty()) {
            gaps.add("selection_coverage_claims_complete_with_gaps");
        }
    }

    private void verifyPdfQuote(JsonNode version, int page, String quote, Map<String, byte[]> files, List<String> gaps) {
        String path = version.path("archivePath").asText();
        byte[] pdf = files.get(path);
        if (pdf == null || page < 1) {
            gaps.add("evidence_source_locator_missing");
            return;
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (page > document.getNumberOfPages()) {
                gaps.add("evidence_page_out_of_range");
                return;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            if (!stripper.getText(document).contains(quote)) gaps.add("evidence_quote_not_in_source");
        } catch (IOException failure) {
            gaps.add("source_document_unreadable");
        }
    }

    private JsonNode json(Map<String, byte[]> files, String path) throws IOException {
        byte[] content = files.get(path);
        if (content == null) throw new IllegalArgumentException("required package JSON missing");
        return mapper.readTree(content);
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private VerificationResult invalid(String code) { return new VerificationResult(false, false, 0, List.of(code)); }

    public record VerificationResult(boolean integrityValid, boolean complete, int filesVerified, List<String> gaps) {
        public boolean valid() { return integrityValid && complete; }
    }
}
