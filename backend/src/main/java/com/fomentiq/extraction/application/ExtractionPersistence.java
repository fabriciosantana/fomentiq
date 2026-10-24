package com.fomentiq.extraction.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence port for auditable provider responses, facts and source evidence. */
public interface ExtractionPersistence {
    void recordExecution(ExecutionRecord execution);
    UUID recordField(FieldRecord field);

    record ExecutionRecord(UUID runId, UUID callVersionId, String provider, String model, String schemaVersion,
                           String inputDigest, String rawResponse, Long inputTokens, Long outputTokens,
                           String status, Instant createdAt) { }
    record FieldRecord(UUID callVersionId, String name, String normalizedValue, String rawValue, String status,
                       String method, Instant createdAt, UUID runId, BigDecimal confidence,
                       List<EvidenceRecord> evidence) { }
    record EvidenceRecord(UUID documentVersionId, String sourceUrl, Integer page, String section,
                          String quote, String locatorJson) { }
}
