package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshotType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Application boundary for the persistence work owned by the ingestion use case. */
public interface IngestionPersistence {
    UUID startRun(String sourceType, Instant startedAt);
    UUID upsertCall(SourceConnector.SourceCall source, Instant detectedAt);
    boolean recordCallVersion(UUID callId, String contentHash, Instant detectedAt);
    void recordSnapshot(UUID runId, UUID callId, SourceSnapshotType type, URI sourceUri,
                        String contentHash, String storageKey, String mediaType, Instant capturedAt);
    UUID upsertDocument(UUID callId, SourceConnector.SourceDocument source);
    java.util.Optional<String> latestDocumentVersionHash(UUID documentId);
    boolean recordDocumentVersion(UUID documentId, String contentHash, String storageKey,
                                  String mediaType, String sourceUrl, Instant downloadedAt);
    void finishRun(UUID runId, Instant finishedAt, IngestionRunStatus status, int detectedCount,
                   int changedCount, String errorSummary);
}
