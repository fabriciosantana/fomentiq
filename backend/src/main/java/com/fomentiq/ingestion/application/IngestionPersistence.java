package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshotType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Application boundary for the persistence work owned by the ingestion use case. */
public interface IngestionPersistence {
    UUID startRun(String sourceType, Instant startedAt);
    CallIdentity upsertCall(String publisherCode, SourceConnector.SourceCall source, Instant detectedAt);
    boolean recordCallVersion(UUID callId, String contentHash, Instant detectedAt);
    UUID latestCallVersionId(UUID callId);
    void recordSnapshot(UUID runId, UUID callId, UUID sourceReferenceId, SourceSnapshotType type, URI sourceUri,
                        String contentHash, String storageKey, String mediaType, Instant capturedAt);
    UUID upsertDocument(UUID callId, UUID sourceReferenceId, SourceConnector.SourceDocument source);
    java.util.Optional<String> latestDocumentVersionHash(UUID documentId);
    boolean recordDocumentVersion(UUID documentId, String contentHash, String storageKey,
                                  String mediaType, String sourceUrl, Instant downloadedAt);
    UUID latestDocumentVersionId(UUID documentId);
    void finishRun(UUID runId, Instant finishedAt, IngestionRunStatus status, int detectedCount,
                   int changedCount, String errorSummary);

    record CallIdentity(UUID callId, UUID sourceReferenceId) { }
}
