package com.fomentiq.ingestion.application;

import com.fomentiq.extraction.application.DocumentExcerptSelector;
import com.fomentiq.extraction.application.StructuredExtractionService;
import com.fomentiq.ingestion.domain.SourceSnapshotType;
import com.fomentiq.storage.application.ArtifactStore;
import jakarta.persistence.PersistenceException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;

/** Persists one discovered call in a transaction separate from run audit and orchestration. */
@Service
public class FinepCallIngestionTransaction {
    private static final int MAX_PDF_BYTES = 50 * 1024 * 1024;
    private final ArtifactStore artifactStore;
    private final IngestionPersistence persistence;
    private final StructuredExtractionService extraction;
    private final Clock clock;

    @Autowired
    public FinepCallIngestionTransaction(ArtifactStore artifactStore, IngestionPersistence persistence,
                                         StructuredExtractionService extraction) {
        this(artifactStore, persistence, extraction, Clock.systemUTC());
    }

    FinepCallIngestionTransaction(ArtifactStore artifactStore, IngestionPersistence persistence,
                                  StructuredExtractionService extraction, Clock clock) {
        this.artifactStore = artifactStore;
        this.persistence = persistence;
        this.extraction = extraction;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CallIngestionResult ingest(SourceConnector connector, UUID runId, SourceConnector.SourceCall sourceCall,
                                      Instant detectedAt, FinepIngestionConstraints constraints) {
        if (connector == null) throw new IllegalArgumentException("A source connector is required");
        return ingestBounded(connector, runId, sourceCall, detectedAt, constraints);
    }

    private CallIngestionResult ingestBounded(SourceConnector connector, UUID runId,
                                              SourceConnector.SourceCall sourceCall, Instant detectedAt,
                                              FinepIngestionConstraints constraints) {
        IngestionLimits limits = constraints == null ? null : constraints.limits();
        if (limits != null) limits.requireTimeRemaining();
        IngestionPersistence.CallIdentity identity = persistence.upsertCall(connector.sourceType(), sourceCall, detectedAt);
        UUID callId = identity.callId();
        UUID sourceReferenceId = identity.sourceReferenceId();
        persistSnapshot(runId, callId, sourceReferenceId, SourceSnapshotType.DISCOVERY, sourceCall.discoveryUri(),
                sourceCall.discoverySnapshot(), sourceCall.discoveryMediaType(), limits);
        persistSnapshot(runId, callId, sourceReferenceId, SourceSnapshotType.CALL, sourceCall.callUri(),
                sourceCall.callSnapshot(), sourceCall.callMediaType(), limits);
        int changed = persistence.recordCallVersion(callId, sha256(sourceCall.callSnapshot()), clock.instant()) ? 1 : 0;
        UUID callVersionId = persistence.latestCallVersionId(callId);

        SourceConnector.DocumentCatalog catalog = limits == null
                ? connector.discoverDocuments(sourceCall) : connector.discoverDocuments(sourceCall, limits);
        int maxDocuments = limits == null ? connector.acquisitionBounds().maxDocuments() : limits.maxDocuments();
        long maxAggregateDocumentBytes = limits == null ? connector.acquisitionBounds().maxAggregateDocumentBytes()
                : limits.maxAggregateDocumentBytes();
        long maxBytesPerDocument = limits == null ? connector.acquisitionBounds().maxBytesPerDocument()
                : limits.maxBytesPerDocument();
        if (catalog.documents().size() > maxDocuments) {
            throw new IngestionLimitExceededException("Source document count limit exceeded");
        }
        persistSnapshot(runId, callId, sourceReferenceId, SourceSnapshotType.DOCUMENT_LIST, catalog.sourceUri(),
                catalog.snapshot(), catalog.mediaType(), limits);
        List<DocumentExcerptSelector.DocumentInput> extractionDocuments = new ArrayList<>();
        List<String> documentFailureDetails = new ArrayList<>();
        int documentFailures = 0;
        long downloadedBytes = 0;
        for (SourceConnector.SourceDocument sourceDocument : catalog.documents()) {
            if (limits != null) limits.requireTimeRemaining();
            try {
                long remainingBytes = maxAggregateDocumentBytes - downloadedBytes;
                if (remainingBytes <= 0) throw new IngestionLimitExceededException("Finep aggregate document byte limit exceeded");
                DocumentIngestionResult documentResult = ingestDocument(connector, callId, sourceReferenceId,
                        sourceDocument, extractionDocuments, limits, Math.min(maxBytesPerDocument, remainingBytes));
                changed += documentResult.changedCount();
                downloadedBytes += documentResult.downloadedBytes();
            } catch (DataAccessException | PersistenceException | TransactionException failure) {
                throw failure;
            } catch (IngestionLimitExceededException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                documentFailures++;
                String reason = failure instanceof SourceDocumentAcquisitionException rejected
                        ? rejected.reason().name() : "PRESERVATION_FAILED";
                documentFailureDetails.add(safeFailureReference(sourceDocument.externalId()) + ":" + reason);
            }
        }

        boolean extractionFailed = false;
        try {
            if (limits != null) limits.requireTimeRemaining();
            extraction.extract(runId, callVersionId, extractionDocuments,
                    constraints == null ? null : constraints.extractionLimits());
        } catch (StructuredExtractionService.ExtractionFailure failure) {
            // The explicit FAILED extraction record is part of this still-healthy transaction.
            extractionFailed = true;
        }
        return new CallIngestionResult(changed, documentFailures, extractionFailed, List.copyOf(documentFailureDetails));
    }

    private DocumentIngestionResult ingestDocument(SourceConnector connector, UUID callId, UUID sourceReferenceId,
                               SourceConnector.SourceDocument source,
                               List<DocumentExcerptSelector.DocumentInput> extractionDocuments,
                               IngestionLimits limits, long maxBytes) {
        UUID documentId = persistence.upsertDocument(callId, sourceReferenceId, source);
        SourceConnector.DownloadedDocument downloaded = connector.download(source, maxBytes, limits);
        validateDocument(downloaded, source, maxBytes);
        if (downloaded.content().length > maxBytes) {
            throw new IngestionLimitExceededException("Source document byte limit exceeded");
        }
        String hash = sha256(downloaded.content());
        String extension = "application/pdf".equals(downloaded.mediaType()) ? ".pdf" : ".docx";
        String key = "content/" + hash + extension;
        Optional<String> latestHash = persistence.latestDocumentVersionHash(documentId);
        int changed = 0;
        if (latestHash.isEmpty() || !latestHash.get().equals(hash)) {
            if (limits != null) limits.requireTimeWindow(Duration.ofSeconds(45));
            String storedKey = artifactStore.put(key, downloaded.content(), downloaded.mediaType());
            if (persistence.recordDocumentVersion(documentId, hash, storedKey, downloaded.mediaType(),
                    downloaded.finalUri().toString(), clock.instant())) changed = 1;
        }
        UUID documentVersionId = persistence.latestDocumentVersionId(documentId);
        if ("application/pdf".equals(downloaded.mediaType())) {
            extractionDocuments.add(new DocumentExcerptSelector.DocumentInput(documentVersionId,
                    source.title(), downloaded.finalUri().toString(), downloaded.content(), documentId,
                    documentType(source.category())));
        }
        return new DocumentIngestionResult(changed, downloaded.content().length);
    }

    private void persistSnapshot(UUID runId, UUID callId, UUID sourceReferenceId, SourceSnapshotType type, URI sourceUri,
                                 byte[] bytes, String mediaType, IngestionLimits limits) {
        String hash = sha256(bytes);
        String extension = mediaType != null && mediaType.contains("html") ? ".html" : ".json";
        String key = "content/" + hash + extension;
        if (limits != null) limits.requireTimeWindow(Duration.ofSeconds(45));
        String storedKey = artifactStore.put(key, bytes, mediaType);
        persistence.recordSnapshot(runId, callId, sourceReferenceId, type, sourceUri, hash, storedKey,
                mediaType, clock.instant());
    }

    private static void validateDocument(SourceConnector.DownloadedDocument downloaded,
                                         SourceConnector.SourceDocument source, long maxBytes) {
        if (downloaded == null || downloaded.content() == null || downloaded.content().length == 0
                || downloaded.content().length > maxBytes || downloaded.mediaType() == null
                || source.expectedMediaType() != null && !source.expectedMediaType().equals(downloaded.mediaType())) {
            throw new SourceDocumentAcquisitionException(
                    SourceDocumentAcquisitionException.Reason.UNSUPPORTED_CONTENT_TYPE);
        }
        byte[] bytes = downloaded.content();
        boolean validPdf = "application/pdf".equals(downloaded.mediaType()) && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
        boolean validDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(downloaded.mediaType()) && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && bytes[2] == 3 && bytes[3] == 4;
        if (!validPdf && !validDocx) {
            throw new SourceDocumentAcquisitionException(
                    SourceDocumentAcquisitionException.Reason.INVALID_DOCUMENT_CONTENT);
        }
    }

    private static String safeFailureReference(String externalId) {
        if (externalId == null || externalId.isBlank()) return "document";
        return externalId.replaceAll("[^A-Za-z0-9._-]", "_").substring(0,
                Math.min(80, externalId.replaceAll("[^A-Za-z0-9._-]", "_").length()));
    }


    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static String documentType(String category) {
        String value = category == null ? "" : category.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("edital") || value.equals("chamada") || value.equals("notice")) return "NOTICE";
        if (value.contains("anexo") || value.contains("annex")) return "ANNEX";
        if (value.contains("pergunta") || value.contains("faq")) return "FAQ";
        if (value.contains("rerrat") || value.contains("retifica")) return "AMENDMENT";
        if (value.contains("resultado") && value.contains("preliminar")) return "RESULT_PRELIMINARY";
        if (value.contains("resultado")) return "RESULT_FINAL";
        return "OTHER";
    }

    public record CallIngestionResult(int changedCount, int documentFailureCount, boolean extractionFailed,
                                      List<String> documentFailureDetails) { }
    private record DocumentIngestionResult(int changedCount, int downloadedBytes) { }
}
