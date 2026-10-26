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
    private final SourceConnector connector;
    private final ArtifactStore artifactStore;
    private final IngestionPersistence persistence;
    private final StructuredExtractionService extraction;
    private final Clock clock;

    @Autowired
    public FinepCallIngestionTransaction(SourceConnector connector, ArtifactStore artifactStore,
                                         IngestionPersistence persistence, StructuredExtractionService extraction) {
        this(connector, artifactStore, persistence, extraction, Clock.systemUTC());
    }

    FinepCallIngestionTransaction(SourceConnector connector, ArtifactStore artifactStore,
                                  IngestionPersistence persistence, StructuredExtractionService extraction, Clock clock) {
        this.connector = connector;
        this.artifactStore = artifactStore;
        this.persistence = persistence;
        this.extraction = extraction;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CallIngestionResult ingest(UUID runId, SourceConnector.SourceCall sourceCall, Instant detectedAt) {
        return ingestBounded(runId, sourceCall, detectedAt, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CallIngestionResult ingest(UUID runId, SourceConnector.SourceCall sourceCall, Instant detectedAt,
                                     FinepIngestionConstraints constraints) {
        if (constraints == null) throw new IllegalArgumentException("Finep ingestion constraints are required");
        return ingestBounded(runId, sourceCall, detectedAt, constraints);
    }

    private CallIngestionResult ingestBounded(UUID runId, SourceConnector.SourceCall sourceCall, Instant detectedAt,
                                              FinepIngestionConstraints constraints) {
        IngestionLimits limits = constraints == null ? null : constraints.limits();
        if (limits != null) limits.requireTimeRemaining();
        UUID callId = persistence.upsertCall(sourceCall, detectedAt);
        persistSnapshot(runId, callId, SourceSnapshotType.DISCOVERY, sourceCall.discoveryUri(),
                sourceCall.discoverySnapshot(), sourceCall.discoveryMediaType(), limits);
        persistSnapshot(runId, callId, SourceSnapshotType.CALL, sourceCall.callUri(),
                sourceCall.callSnapshot(), sourceCall.callMediaType(), limits);
        int changed = persistence.recordCallVersion(callId, sha256(sourceCall.callSnapshot()), clock.instant()) ? 1 : 0;
        UUID callVersionId = persistence.latestCallVersionId(callId);

        SourceConnector.DocumentCatalog catalog = limits == null
                ? connector.discoverDocuments(sourceCall) : connector.discoverDocuments(sourceCall, limits);
        if (limits != null && catalog.documents().size() > limits.maxDocuments()) {
            throw new IngestionLimitExceededException("Finep document count limit exceeded");
        }
        persistSnapshot(runId, callId, SourceSnapshotType.DOCUMENT_LIST, catalog.sourceUri(),
                catalog.snapshot(), catalog.mediaType(), limits);
        List<DocumentExcerptSelector.DocumentInput> extractionDocuments = new ArrayList<>();
        int documentFailures = 0;
        long downloadedBytes = 0;
        for (SourceConnector.SourceDocument sourceDocument : catalog.documents()) {
            if (limits != null) limits.requireTimeRemaining();
            try {
                long remainingBytes = limits == null ? MAX_PDF_BYTES
                        : limits.maxAggregateDocumentBytes() - downloadedBytes;
                if (remainingBytes <= 0) throw new IngestionLimitExceededException("Finep aggregate document byte limit exceeded");
                DocumentIngestionResult documentResult = ingestDocument(callId, sourceDocument,
                        extractionDocuments, limits, remainingBytes);
                changed += documentResult.changedCount();
                downloadedBytes += documentResult.downloadedBytes();
            } catch (DataAccessException | PersistenceException | TransactionException failure) {
                throw failure;
            } catch (IngestionLimitExceededException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                documentFailures++;
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
        return new CallIngestionResult(changed, documentFailures, extractionFailed);
    }

    private DocumentIngestionResult ingestDocument(UUID callId, SourceConnector.SourceDocument source,
                               List<DocumentExcerptSelector.DocumentInput> extractionDocuments,
                               IngestionLimits limits, long remainingAggregateBytes) {
        UUID documentId = persistence.upsertDocument(callId, source);
        SourceConnector.DownloadedDocument downloaded = limits == null ? connector.download(source)
                : connector.download(source, Math.min(limits.maxBytesPerDocument(), remainingAggregateBytes), limits);
        validatePdf(downloaded, source);
        if (limits != null && downloaded.content().length > limits.maxBytesPerDocument()) {
            throw new IngestionLimitExceededException("Finep per-document byte limit exceeded");
        }
        if (limits != null && downloaded.content().length > remainingAggregateBytes) {
            throw new IngestionLimitExceededException("Finep aggregate document byte limit exceeded");
        }
        String hash = sha256(downloaded.content());
        String key = "content/" + hash + ".pdf";
        Optional<String> latestHash = persistence.latestDocumentVersionHash(documentId);
        int changed = 0;
        if (latestHash.isEmpty() || !latestHash.get().equals(hash)) {
            if (limits != null) limits.requireTimeWindow(Duration.ofSeconds(45));
            String storedKey = artifactStore.put(key, downloaded.content(), "application/pdf");
            if (persistence.recordDocumentVersion(documentId, hash, storedKey, "application/pdf",
                    downloaded.finalUri().toString(), clock.instant())) changed = 1;
        }
        UUID documentVersionId = persistence.latestDocumentVersionId(documentId);
        extractionDocuments.add(new DocumentExcerptSelector.DocumentInput(documentVersionId,
                source.title(), downloaded.finalUri().toString(), downloaded.content()));
        return new DocumentIngestionResult(changed, downloaded.content().length);
    }

    private void persistSnapshot(UUID runId, UUID callId, SourceSnapshotType type, URI sourceUri,
                                 byte[] bytes, String mediaType, IngestionLimits limits) {
        String hash = sha256(bytes);
        String extension = mediaType != null && mediaType.contains("html") ? ".html" : ".json";
        String key = "content/" + hash + extension;
        if (limits != null) limits.requireTimeWindow(Duration.ofSeconds(45));
        String storedKey = artifactStore.put(key, bytes, mediaType);
        persistence.recordSnapshot(runId, callId, type, sourceUri, hash, storedKey, mediaType, clock.instant());
    }

    private static void validatePdf(SourceConnector.DownloadedDocument downloaded,
                                    SourceConnector.SourceDocument source) {
        if (downloaded == null || downloaded.content() == null || downloaded.content().length == 0
                || downloaded.content().length > MAX_PDF_BYTES || !"application/pdf".equalsIgnoreCase(downloaded.mediaType())
                || !isOfficialFinepUri(source.sourceUri()) || !isOfficialFinepUri(downloaded.finalUri())
                || downloaded.content().length < 5
                || downloaded.content()[0] != '%' || downloaded.content()[1] != 'P'
                || downloaded.content()[2] != 'D' || downloaded.content()[3] != 'F' || downloaded.content()[4] != '-') {
            throw new IllegalArgumentException("Finep document failed PDF validation");
        }
    }

    private static boolean isOfficialFinepUri(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "www.finep.gov.br".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && uri.getFragment() == null && uri.toString().length() <= 2048;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    public record CallIngestionResult(int changedCount, int documentFailureCount, boolean extractionFailed) { }
    private record DocumentIngestionResult(int changedCount, int downloadedBytes) { }
}
