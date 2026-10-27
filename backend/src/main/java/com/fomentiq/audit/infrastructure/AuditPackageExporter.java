package com.fomentiq.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fomentiq.audit.application.AuditPackageVerifier;
import com.fomentiq.audit.application.AuditPayloadSanitizer;
import com.fomentiq.storage.application.ArtifactStore;
import com.fomentiq.storage.application.PrivateAuditArchive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Builds sanitized, immutable audit packages from persisted run and artifact records. */
@Service
public class AuditPackageExporter {
    private static final String PACKAGE_KEY_PREFIX = "packages/";
    private final JdbcTemplate jdbc;
    private final ArtifactStore sourceArtifacts;
    private final PrivateAuditArchive privateArchive;
    private final ObjectMapper mapper;
    private final AuditPackageVerifier verifier;

    public AuditPackageExporter(JdbcTemplate jdbc, ArtifactStore sourceArtifacts,
                                PrivateAuditArchive privateArchive, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.sourceArtifacts = sourceArtifacts;
        this.privateArchive = privateArchive;
        this.mapper = mapper;
        this.verifier = new AuditPackageVerifier(mapper);
    }

    public ExportResult export(UUID ingestionRunId) {
        Map<String, byte[]> payload = new TreeMap<>();
        List<String> gaps = new ArrayList<>();
        List<String> requiredFiles = new ArrayList<>();
        Map<String, String> origins = new TreeMap<>();
        Map<String, Object> run = single("SELECT * FROM ingestion_run WHERE id = ?", ingestionRunId);
        if (run == null || run.get("finished_at") == null || "RUNNING".equals(run.get("status"))) {
            throw new IllegalArgumentException("audit_run_not_terminal");
        }
        String packageKey = PACKAGE_KEY_PREFIX + ingestionRunId + ".zip";
        if (privateArchive.contains(packageKey)) {
            byte[] existing = privateArchive.read(packageKey);
            AuditPackageVerifier.VerificationResult existingVerification = verifier.verify(existing);
            if (!existingVerification.valid()) throw new IllegalStateException("existing_audit_package_invalid");
            return new ExportResult(packageKey, sha256(existing), existingVerification);
        }

        String origin = readRunOrigin(ingestionRunId, gaps);
        String codeRevision = readCodeRevision(ingestionRunId, origin, payload, requiredFiles, origins, gaps);
        addJson(payload, requiredFiles, origins, "run/run-record.json", safeRow(run, gaps), origin, gaps);
        addAuthorization(payload, requiredFiles, origins, ingestionRunId, origin, gaps);

        List<Map<String, Object>> snapshots = rows("SELECT id, call_id, source_reference_id, ingestion_run_id, artifact_type, source_url, "
                + "content_hash, storage_key, media_type, captured_at FROM source_snapshot WHERE ingestion_run_id = ? "
                + "ORDER BY captured_at, id", ingestionRunId);
        Set<UUID> callIds = new java.util.TreeSet<>(Comparator.comparing(UUID::toString));
        ArrayNode snapshotRecords = mapper.createArrayNode();
        for (Map<String, Object> snapshot : snapshots) {
            UUID callId = uuid(snapshot.get("call_id"));
            callIds.add(callId);
            String path = "source/snapshots/" + snapshot.get("id") + "/" + snapshot.get("content_hash") + ".bin";
            requiredFiles.add(path);
            ObjectNode record = mapper.createObjectNode().put("id", text(snapshot.get("id")))
                    .put("callId", callId.toString()).put("ingestionRunId", ingestionRunId.toString())
                    .put("sourceReferenceId", text(snapshot.get("source_reference_id")))
                    .put("artifactType", text(snapshot.get("artifact_type")))
                    .put("sourceUrl", safeUrl(text(snapshot.get("source_url")), gaps))
                    .put("contentHash", text(snapshot.get("content_hash")))
                    .put("mediaType", text(snapshot.get("media_type")))
                    .put("capturedAt", text(snapshot.get("captured_at"))).put("archivePath", path);
            snapshotRecords.add(record);
            copySourceArtifact(text(snapshot.get("storage_key")), path, text(snapshot.get("content_hash")),
                    text(snapshot.get("media_type")), origin, payload, origins, gaps);
        }
        addJson(payload, requiredFiles, origins, "source/snapshots.json", snapshotRecords, origin, gaps);

        List<Map<String, Object>> calls = selectByIds("SELECT c.*, a.name AS agency_name, a.acronym AS agency_acronym, "
                + "a.official_url AS agency_url, p.name AS program_name, p.description AS program_description "
                + "FROM call_for_proposals c JOIN funding_agency a ON a.id = c.agency_id "
                + "LEFT JOIN funding_program p ON p.id = c.program_id WHERE c.id IN (%s) ORDER BY c.id", "id", callIds);
        addJson(payload, requiredFiles, origins, "source/calls.json", mapper.valueToTree(calls.stream()
                .map(row -> safeRow(row, gaps)).toList()), origin, gaps);

        List<Map<String, Object>> sourceReferences = selectByIds("SELECT r.id, r.call_id, p.code AS publisher_code, "
                + "p.name AS publisher_name, p.official_url AS publisher_url, r.external_id, r.portal_entry_id, "
                + "r.canonical_url FROM call_source_reference r JOIN source_publisher p ON p.id = r.publisher_id "
                + "WHERE r.call_id IN (%s) ORDER BY r.call_id, p.code", "call_id", callIds);
        addJson(payload, requiredFiles, origins, "source/source-references.json", mapper.valueToTree(sourceReferences.stream()
                .map(row -> safeRow(row, gaps)).toList()), origin, gaps);
        List<Map<String, Object>> responsibleAgencies = selectByIds("SELECT cfa.call_id, a.id AS agency_id, "
                + "a.name AS agency_name, a.acronym AS agency_acronym, a.official_url AS agency_url, cfa.is_primary "
                + "FROM call_funding_agency cfa JOIN funding_agency a ON a.id = cfa.agency_id "
                + "WHERE cfa.call_id IN (%s) ORDER BY cfa.call_id, cfa.is_primary DESC, a.acronym", "call_id", callIds);
        addJson(payload, requiredFiles, origins, "source/call-funding-agencies.json",
                mapper.valueToTree(responsibleAgencies.stream().map(row -> safeRow(row, gaps)).toList()), origin, gaps);

        List<Map<String, Object>> callVersions = selectByIds("SELECT * FROM call_version WHERE call_id IN (%s) "
                + "ORDER BY call_id, version_number", "call_id", callIds);
        addJson(payload, requiredFiles, origins, "source/call-versions.json", mapper.valueToTree(callVersions.stream()
                .map(row -> safeRow(row, gaps)).toList()), origin, gaps);

        List<Map<String, Object>> documents = selectByIds("SELECT id, call_id, source_reference_id, external_id, document_type, title, "
                + "source_url FROM call_document WHERE call_id IN (%s) ORDER BY call_id, id", "call_id", callIds);
        addJson(payload, requiredFiles, origins, "source/documents.json", mapper.valueToTree(documents.stream()
                .map(row -> safeDocument(row, gaps)).toList()), origin, gaps);
        Set<UUID> documentIds = new java.util.TreeSet<>(Comparator.comparing(UUID::toString));
        documents.forEach(row -> documentIds.add(uuid(row.get("id"))));

        List<Map<String, Object>> documentVersions = selectByIds("SELECT id, document_id, content_hash, storage_key, "
                + "media_type, source_url, downloaded_at, extracted_text_key FROM call_document_version "
                + "WHERE document_id IN (%s) ORDER BY document_id, downloaded_at, id", "document_id", documentIds);
        ArrayNode versionRecords = mapper.createArrayNode();
        for (Map<String, Object> version : documentVersions) {
            UUID versionId = uuid(version.get("id"));
            String contentHash = text(version.get("content_hash"));
            String path = "source/documents/" + versionId + "/" + contentHash
                    + documentExtension(text(version.get("media_type")));
            requiredFiles.add(path);
            ObjectNode record = mapper.createObjectNode().put("id", versionId.toString())
                    .put("documentId", text(version.get("document_id"))).put("contentHash", contentHash)
                    .put("mediaType", text(version.get("media_type")))
                    .put("sourceUrl", safeUrl(text(version.get("source_url")), gaps))
                    .put("downloadedAt", text(version.get("downloaded_at"))).put("archivePath", path);
            versionRecords.add(record);
            copySourceArtifact(text(version.get("storage_key")), path, contentHash,
                    text(version.get("media_type")), origin, payload, origins, gaps);
        }
        addJson(payload, requiredFiles, origins, "source/document-versions.json", versionRecords, origin, gaps);

        List<Map<String, Object>> fields = selectByIds("SELECT ef.id, ef.call_version_id, ef.field_name, "
                + "ef.normalized_value, ef.raw_value, ef.validation_status, ef.extraction_method, ef.created_at, "
                + "ef.ingestion_run_id, ef.confidence FROM extracted_field ef JOIN call_version cv "
                + "ON cv.id = ef.call_version_id WHERE cv.call_id IN (%s) ORDER BY ef.created_at, ef.id",
                "call_id", callIds);
        ArrayNode fieldRecords = mapper.createArrayNode();
        Set<UUID> fieldIds = new java.util.TreeSet<>(Comparator.comparing(UUID::toString));
        for (Map<String, Object> field : fields) {
            fieldIds.add(uuid(field.get("id")));
            fieldRecords.add(fieldRecord(field, gaps));
            if (!ingestionRunId.equals(uuid(field.get("ingestion_run_id")))) {
                // Historical fields are retained, but their own run attribution is preserved explicitly.
                continue;
            }
        }
        addJson(payload, requiredFiles, origins, "extraction/field-history.json", fieldRecords, origin, gaps);
        Set<UUID> fieldRunIds = new java.util.TreeSet<>(Comparator.comparing(UUID::toString));
        fields.forEach(row -> fieldRunIds.add(uuid(row.get("ingestion_run_id"))));
        List<Map<String, Object>> runHistory = selectByIds("SELECT * FROM ingestion_run WHERE id IN (%s) "
                + "ORDER BY started_at, id", "id", fieldRunIds);
        addJson(payload, requiredFiles, origins, "run/run-history.json", mapper.valueToTree(runHistory.stream()
                .map(row -> safeRow(row, gaps)).toList()), origin, gaps);

        List<Map<String, Object>> evidenceRows = selectByIds("SELECT id, extracted_field_id, document_version_id, "
                + "source_url, page, section, quote_fragment, locator_json FROM evidence "
                + "WHERE extracted_field_id IN (%s) ORDER BY extracted_field_id, id", "extracted_field_id", fieldIds);
        ArrayNode evidence = mapper.createArrayNode();
        for (Map<String, Object> item : evidenceRows) {
            ObjectNode record = mapper.createObjectNode().put("id", text(item.get("id")))
                    .put("extractedFieldId", text(item.get("extracted_field_id")))
                    .put("documentVersionId", text(item.get("document_version_id")))
                    .put("sourceUrl", safeUrl(text(item.get("source_url")), gaps))
                    .put("section", text(item.get("section"))).put("quoteFragment", text(item.get("quote_fragment")))
                    .put("locatorJson", jsonText(item.get("locator_json")));
            if (item.get("page") != null) record.put("page", ((Number) item.get("page")).intValue());
            evidence.add(record);
        }
        addJson(payload, requiredFiles, origins, "extraction/evidence.json", evidence, origin, gaps);

        List<Map<String, Object>> executions = rows("SELECT id, ingestion_run_id, call_version_id, provider, model, "
                + "schema_version, input_digest, input_tokens, output_tokens, status, created_at "
                + "FROM extraction_execution WHERE ingestion_run_id = ? ORDER BY created_at, id", ingestionRunId);
        ArrayNode executionRecords = mapper.createArrayNode();
        ArrayNode excerptRecords = mapper.createArrayNode();
        JsonNode exactSchema = null;
        for (Map<String, Object> execution : executions) {
            UUID versionId = uuid(execution.get("call_version_id"));
            String schemaVersion = text(execution.get("schema_version"));
            String digest = text(execution.get("input_digest"));
            String inputKey = com.fomentiq.extraction.application.ExtractionAuditKeys.input(
                    ingestionRunId, versionId, schemaVersion, digest);
            String resultKey = com.fomentiq.extraction.application.ExtractionAuditKeys.result(
                    ingestionRunId, versionId, schemaVersion, digest);
            String inputPath = "extraction/provider-input-excerpts/" + digest + ".json";
            String resultPath = "extraction/structured-results/" + digest + ".json";
            requiredFiles.add(inputPath);
            requiredFiles.add(resultPath);
            try {
                byte[] inputBytes = privateArchive.read(inputKey);
                JsonNode inputNode = mapper.readTree(inputBytes);
                verifyRunBinding(inputNode, ingestionRunId, versionId, digest, gaps);
                addJsonBytes(payload, origins, inputPath, inputBytes, origin, gaps);
                excerptRecords.add(inputNode);
                exactSchema = mapper.readTree(inputNode.path("schemaDefinition").asText("{}"));
            } catch (RuntimeException | IOException missing) {
                gaps.add("provider_input_artifact_missing");
            }
            try {
                byte[] resultBytes = privateArchive.read(resultKey);
                JsonNode resultNode = mapper.readTree(resultBytes);
                verifyRunBinding(resultNode, ingestionRunId, versionId, digest, gaps);
                addJsonBytes(payload, origins, resultPath, resultBytes, origin, gaps);
            } catch (RuntimeException | IOException missing) {
                gaps.add("structured_result_artifact_missing");
            }
            ObjectNode record = mapper.createObjectNode().put("id", text(execution.get("id")))
                    .put("ingestionRunId", ingestionRunId.toString()).put("callVersionId", versionId.toString())
                    .put("provider", text(execution.get("provider"))).put("model", text(execution.get("model")))
                    .put("schemaVersion", schemaVersion).put("inputDigest", digest)
                    .put("status", text(execution.get("status"))).put("createdAt", text(execution.get("created_at")))
                    .put("inputArtifactPath", inputPath).put("structuredResultPath", resultPath);
            if (execution.get("input_tokens") != null) record.put("inputTokens", ((Number) execution.get("input_tokens")).longValue());
            if (execution.get("output_tokens") != null) record.put("outputTokens", ((Number) execution.get("output_tokens")).longValue());
            executionRecords.add(record);
        }
        addJson(payload, requiredFiles, origins, "extraction/execution-history.json", executionRecords, origin, gaps);
        addJson(payload, requiredFiles, origins, "extraction/provider-input-excerpts.json", excerptRecords, origin, gaps);
        if (exactSchema == null) {
            gaps.add("extraction_schema_unavailable");
            exactSchema = mapper.createObjectNode().put("schemaVersion", "unavailable");
        }
        addJson(payload, requiredFiles, origins, "extraction/schema.json", exactSchema, origin, gaps);
        addReviewTemplate(payload, requiredFiles, origins, fieldRecords, evidence, gaps);

        String createdAt = text(run.get("finished_at"));
        byte[] packageBytes = buildArchive(ingestionRunId, createdAt, codeRevision,
                payload, requiredFiles, origins, gaps, origin);
        AuditPackageVerifier.VerificationResult initialVerification = verifier.verify(packageBytes);
        if (!initialVerification.valid()) {
            gaps.addAll(initialVerification.gaps().stream().map(code -> "verifier_" + code).toList());
            packageBytes = buildArchive(ingestionRunId, createdAt, codeRevision,
                    payload, requiredFiles, origins, gaps, origin);
        }
        privateArchive.putImmutable(packageKey, packageBytes);
        byte[] reopened = privateArchive.read(packageKey);
        AuditPackageVerifier.VerificationResult verification = verifier.verify(reopened);
        return new ExportResult(packageKey, sha256(packageBytes), verification);
    }

    private void addAuthorization(Map<String, byte[]> files, List<String> required, Map<String, String> origins,
                                  UUID runId, String origin, List<String> gaps) {
        String target = "authorization/manifest-sanitized.json";
        required.add(target);
        if ("LIVE".equals(origin)) {
            try {
                JsonNode link = mapper.readTree(privateArchive.read("run-manifest-link/" + runId + ".json"));
                String manifestRunId = link.path("manifestRunId").asText();
                if (!runId.toString().equals(link.path("ingestionRunId").asText())) {
                    gaps.add("live_run_manifest_link_invalid");
                }
                JsonNode approval = mapper.readTree(privateArchive.read("authorizations/" + manifestRunId + ".json"));
                addJson(files, required, origins, target, approval, origin, gaps);
                addJson(files, required, origins, "authorization/run-manifest-link.json", link, origin, gaps);
            } catch (RuntimeException | IOException failure) {
                gaps.add("live_authorization_manifest_missing");
            }
        } else if ("FIXTURE".equals(origin)) {
            ObjectNode fixture = mapper.createObjectNode().put("executionMode", "OFFLINE_FIXTURE")
                    .put("humanLiveApproval", "not_applicable").put("artifactOrigin", "FIXTURE");
            addJson(files, required, origins, target, fixture, "FIXTURE", gaps);
        } else {
            gaps.add("run_origin_unsupported");
        }
    }

    private String readRunOrigin(UUID runId, List<String> gaps) {
        try {
            JsonNode origin = mapper.readTree(privateArchive.read("run-origin/" + runId + ".json"));
            String value = origin.path("artifactOrigin").asText();
            if (!Set.of("LIVE", "FIXTURE", "RECONSTRUCTED").contains(value)
                    || !runId.toString().equals(origin.path("ingestionRunId").asText())) {
                gaps.add("run_origin_invalid");
                return "RECONSTRUCTED";
            }
            return value;
        } catch (RuntimeException | IOException failure) {
            gaps.add("run_origin_missing");
            return "RECONSTRUCTED";
        }
    }

    private String readCodeRevision(UUID runId, String origin, Map<String, byte[]> payload,
                                    List<String> required, Map<String, String> origins, List<String> gaps) {
        String path = "run/origin.json";
        required.add(path);
        try {
            JsonNode record = mapper.readTree(privateArchive.read("run-origin/" + runId + ".json"));
            String revision = record.path("codeRevision").asText();
            if (!revision.matches("(?i)[a-f0-9]{40}|[a-f0-9]{64}")) gaps.add("run_code_revision_missing_or_invalid");
            addJson(payload, required, origins, path, record, origin, gaps);
            return revision;
        } catch (RuntimeException | IOException failure) {
            gaps.add("run_origin_missing");
            return "";
        }
    }

    private void copySourceArtifact(String key, String archivePath, String expectedHash, String mediaType,
                                    String origin, Map<String, byte[]> files, Map<String, String> origins,
                                    List<String> gaps) {
        try {
            byte[] content = sourceArtifacts.get(key);
            if (!sha256(content).equals(expectedHash)) {
                gaps.add("source_artifact_hash_mismatch");
                return;
            }
            if (mediaType.startsWith("application/json")) {
                AuditPayloadSanitizer.requireSafeText(new String(content, StandardCharsets.UTF_8));
            }
            files.put(archivePath, content);
            origins.put(archivePath, origin);
        } catch (RuntimeException failure) {
            gaps.add("source_artifact_unavailable_or_sensitive");
        }
    }

    private void addReviewTemplate(Map<String, byte[]> files, List<String> required, Map<String, String> origins,
                                   ArrayNode fieldRecords, ArrayNode evidence, List<String> gaps) {
        ArrayNode matrix = mapper.createArrayNode();
        for (String field : List.of("deadline", "totalBudget", "eligibility", "counterpart", "trl", "requiresIct")) {
            ObjectNode row = matrix.addObject().put("fieldName", field).put("reviewOutcome", "PENDING")
                    .put("factualApproval", false);
            fieldRecords.findParents("fieldName").stream()
                    .filter(candidate -> field.equals(candidate.path("fieldName").asText()))
                    .max(Comparator.comparing(candidate -> candidate.path("createdAt").asText()))
                    .ifPresent(candidate -> {
                        row.set("extractedValue", candidate.path("normalizedValue").deepCopy());
                        row.put("technicalState", candidate.path("validationStatus").asText());
                        row.put("ingestionRunId", candidate.path("ingestionRunId").asText());
                        row.put("callVersionId", candidate.path("callVersionId").asText());
                        ArrayNode references = row.putArray("evidenceReferences");
                        evidence.elements().forEachRemaining(item -> {
                            if (candidate.path("id").asText().equals(item.path("extractedFieldId").asText())) {
                                ObjectNode reference = references.addObject()
                                        .put("evidenceId", item.path("id").asText())
                                        .put("documentVersionId", item.path("documentVersionId").asText())
                                        .put("sourceUrl", item.path("sourceUrl").asText())
                                        .put("section", item.path("section").asText())
                                        .put("quoteFragment", item.path("quoteFragment").asText())
                                        .put("locatorJson", item.path("locatorJson").asText());
                                if (item.has("page")) reference.put("page", item.path("page").asInt());
                            }
                        });
                    });
        }
        addJson(files, required, origins, "review/field-review-matrix-template.json", matrix, "GENERATED", gaps);
    }

    private byte[] buildArchive(UUID runId, String createdAt, String codeRevision,
                                Map<String, byte[]> payload, List<String> requiredFiles,
                                Map<String, String> origins, List<String> gaps, String origin) {
        payload.remove("manifest.json");
        payload.remove("inventory/SHA256SUMS");
        ArrayNode inventory = mapper.createArrayNode();
        payload.forEach((path, bytes) -> inventory.add(mapper.createObjectNode().put("path", path)
                .put("length", bytes.length).put("sha256", sha256(bytes)).put("artifactOrigin", origins.get(path))));
        ObjectNode manifest = mapper.createObjectNode().put("formatVersion", 1).put("packageId", runId.toString())
                .put("ingestionRunId", runId.toString()).put("createdAt", createdAt)
                .put("sourceOrigin", origin).put("complete", gaps.isEmpty());
        if (codeRevision == null || codeRevision.isBlank()) manifest.putNull("codeRevision");
        else manifest.put("codeRevision", codeRevision);
        manifest.set("files", inventory);
        manifest.set("requiredFiles", mapper.valueToTree(requiredFiles.stream().distinct().sorted().toList()));
        manifest.set("gaps", mapper.valueToTree(gaps.stream().distinct().sorted().toList()));
        manifest.put("retentionYears", 5).put("automaticDeletion", false);
        payload.put("manifest.json", safeBytes(manifest));
        StringBuilder sums = new StringBuilder();
        payload.forEach((path, bytes) -> sums.append(sha256(bytes)).append("  ")
                .append(bytes.length).append("  ").append(path).append('\n'));
        payload.put("inventory/SHA256SUMS", sums.toString().getBytes(StandardCharsets.UTF_8));
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> entry : payload.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(0L);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("audit_package_archive_failed");
        }
    }

    private void addJson(Map<String, byte[]> files, List<String> required, Map<String, String> origins,
                         String path, JsonNode value, String origin, List<String> gaps) {
        required.add(path);
        try {
            addJsonBytes(files, origins, path, safeBytes(value), origin, gaps);
        } catch (RuntimeException failure) {
            gaps.add("sensitive_or_invalid_text_excluded");
        }
    }

    private void addJsonBytes(Map<String, byte[]> files, Map<String, String> origins, String path,
                              byte[] bytes, String origin, List<String> gaps) {
        try {
            AuditPayloadSanitizer.requireSafeText(new String(bytes, StandardCharsets.UTF_8));
            files.put(path, bytes);
            origins.put(path, origin);
        } catch (RuntimeException failure) {
            gaps.add("sensitive_or_invalid_text_excluded");
        }
    }

    private byte[] safeBytes(JsonNode node) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(node);
            AuditPayloadSanitizer.requireSafeText(new String(bytes, StandardCharsets.UTF_8));
            return bytes;
        } catch (IOException failure) {
            throw new IllegalStateException("audit_payload_serialization_failed");
        }
    }

    private ObjectNode safeDocument(Map<String, Object> row, List<String> gaps) {
        ObjectNode result = mapper.valueToTree(safeRow(row, gaps));
        result.put("source_url", safeUrl(text(row.get("source_url")), gaps));
        return result;
    }

    private static String documentExtension(String mediaType) {
        return switch (mediaType) {
            case "application/pdf" -> ".pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> ".bin";
        };
    }

    private ObjectNode safeRow(Map<String, Object> row, List<String> gaps) {
        ObjectNode result = mapper.createObjectNode();
        new TreeMap<>(row).forEach((key, value) -> {
            if (key.equals("storage_key") || key.equals("extracted_text_key") || key.equals("response_body")) return;
            if (key.endsWith("url") || key.equals("official_url")) {
                result.put(key, safeUrl(text(value), gaps));
            } else {
                result.set(key, mapper.valueToTree(value));
            }
        });
        return result;
    }

    private ObjectNode fieldRecord(Map<String, Object> row, List<String> gaps) {
        ObjectNode field = mapper.createObjectNode().put("id", text(row.get("id")))
                .put("callVersionId", text(row.get("call_version_id")))
                .put("fieldName", text(row.get("field_name")))
                .put("validationStatus", text(row.get("validation_status")))
                .put("extractionMethod", text(row.get("extraction_method")))
                .put("createdAt", text(row.get("created_at")))
                .put("ingestionRunId", text(row.get("ingestion_run_id")));
        field.set("normalizedValue", safeValue(row.get("normalized_value"), gaps));
        field.set("rawValue", safeValue(row.get("raw_value"), gaps));
        if (row.get("confidence") != null) field.put("confidence", ((Number) row.get("confidence")).doubleValue());
        return field;
    }

    private JsonNode safeValue(Object value, List<String> gaps) {
        if (value == null) return mapper.nullNode();
        String string = value.toString();
        try {
            AuditPayloadSanitizer.requireSafeText(string);
            JsonNode parsed = mapper.readTree(string);
            return parsed;
        } catch (IOException invalidJson) {
            return mapper.getNodeFactory().textNode(string);
        } catch (RuntimeException unsafe) {
            gaps.add("sensitive_extracted_value_excluded");
            return mapper.nullNode();
        }
    }

    private String safeUrl(String value, List<String> gaps) {
        if (value == null) return null;
        try {
            AuditPayloadSanitizer.requireSafeUrl(value);
            return value;
        } catch (RuntimeException unsafe) {
            gaps.add("sensitive_url_redacted");
            return AuditPayloadSanitizer.redactSensitiveUrl(value);
        }
    }

    private Map<String, Object> single(String query, UUID id) {
        List<Map<String, Object>> result = jdbc.queryForList(query, id);
        return result.isEmpty() ? null : result.getFirst();
    }

    private List<Map<String, Object>> rows(String query, Object... args) { return jdbc.queryForList(query, args); }

    private List<Map<String, Object>> selectByIds(String sqlTemplate, String idColumn, Set<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.queryForList(sqlTemplate.formatted(placeholders), ids.toArray());
    }

    private void verifyRunBinding(JsonNode artifact, UUID runId, UUID callVersionId, String digest,
                                  List<String> gaps) {
        if (!runId.toString().equals(artifact.path("ingestionRunId").asText())
                || !callVersionId.toString().equals(artifact.path("callVersionId").asText())
                || !digest.equals(artifact.path("inputDigest").asText())) gaps.add("extraction_artifact_attribution_mismatch");
    }

    private String jsonText(Object value) {
        if (value == null) return "{}";
        if (value instanceof String string) return string;
        if (!(value instanceof Map<?, ?>) && !(value instanceof JsonNode)) return value.toString();
        try { return mapper.writeValueAsString(value); }
        catch (IOException failure) { throw new IllegalStateException("audit_json_serialization_failed"); }
    }

    private String text(Object value) {
        if (value == null) return "";
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        return value.toString();
    }

    private UUID uuid(Object value) { return value instanceof UUID id ? id : UUID.fromString(text(value)); }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public record ExportResult(String archiveKey, String archiveSha256,
                               AuditPackageVerifier.VerificationResult verification) { }
}
