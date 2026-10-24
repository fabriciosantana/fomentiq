package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshotType;
import com.fomentiq.storage.application.ArtifactStore;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinepIngestionService {
    private static final int MAX_PDF_BYTES = 50 * 1024 * 1024;
    private final SourceConnector connector;
    private final ArtifactStore artifactStore;
    private final IngestionPersistence persistence;
    private final Clock clock;

    @Autowired
    public FinepIngestionService(SourceConnector connector, ArtifactStore artifactStore,
                                 IngestionPersistence persistence) {
        this(connector, artifactStore, persistence, Clock.systemUTC());
    }

    FinepIngestionService(SourceConnector connector, ArtifactStore artifactStore,
                          IngestionPersistence persistence, Clock clock) {
        this.connector = connector;
        this.artifactStore = artifactStore;
        this.persistence = persistence;
        this.clock = clock;
    }

    @Transactional
    public FinepIngestionOutcome ingest() {
        Instant started = clock.instant();
        UUID runId = persistence.startRun(connector.sourceType(), started);
        int detected = 0;
        int changed = 0;
        int documentFailures = 0;
        IngestionRunStatus finalStatus = IngestionRunStatus.SUCCEEDED;
        String errorSummary = null;
        try {
            List<SourceConnector.SourceCall> discovered = connector.discover();
            detected = discovered.size();
            for (SourceConnector.SourceCall sourceCall : discovered) {
                UUID callId = persistence.upsertCall(sourceCall, started);
                persistSnapshot(runId, callId, SourceSnapshotType.DISCOVERY, sourceCall.discoveryUri(),
                        sourceCall.discoverySnapshot(), sourceCall.discoveryMediaType());
                persistSnapshot(runId, callId, SourceSnapshotType.CALL, sourceCall.callUri(),
                        sourceCall.callSnapshot(), sourceCall.callMediaType());
                if (persistence.recordCallVersion(callId, sha256(sourceCall.callSnapshot()), clock.instant())) changed++;

                SourceConnector.DocumentCatalog catalog = connector.discoverDocuments(sourceCall);
                persistSnapshot(runId, callId, SourceSnapshotType.DOCUMENT_LIST, catalog.sourceUri(),
                        catalog.snapshot(), catalog.mediaType());
                for (SourceConnector.SourceDocument sourceDocument : catalog.documents()) {
                    try { changed += ingestDocument(callId, sourceDocument); }
                    catch (RuntimeException failure) { documentFailures++; }
                }
            }
            if (documentFailures > 0) {
                finalStatus = IngestionRunStatus.PARTIAL;
                errorSummary = documentFailures + " call document(s) could not be preserved";
            }
        } catch (RuntimeException failure) {
            finalStatus = IngestionRunStatus.FAILED;
            errorSummary = "Finep source discovery or preservation failed (" + failure.getClass().getSimpleName() + ")";
        }
        persistence.finishRun(runId, clock.instant(), finalStatus, detected, changed, errorSummary);
        return new FinepIngestionOutcome(runId, finalStatus, detected, changed, documentFailures);
    }

    private int ingestDocument(UUID callId, SourceConnector.SourceDocument source) {
        UUID documentId = persistence.upsertDocument(callId, source);
        SourceConnector.DownloadedDocument downloaded = connector.download(source);
        validatePdf(downloaded, source);
        String hash = sha256(downloaded.content());
        String key = "calls/" + callId + "/documents/" + source.externalId() + "/" + hash + ".pdf";
        Optional<String> latestHash = persistence.latestDocumentVersionHash(documentId);
        if (latestHash.isPresent() && latestHash.get().equals(hash)) return 0;
        String storedKey = artifactStore.put(key, downloaded.content(), "application/pdf");
        return persistence.recordDocumentVersion(documentId, hash, storedKey, "application/pdf",
                downloaded.finalUri().toString(), clock.instant()) ? 1 : 0;
    }

    private void persistSnapshot(UUID runId, UUID callId, SourceSnapshotType type, URI sourceUri,
                                 byte[] bytes, String mediaType) {
        String hash = sha256(bytes);
        String extension = mediaType != null && mediaType.contains("html") ? ".html" : ".json";
        String key = "calls/" + callId + "/snapshots/" + type.name().toLowerCase() + "/" + hash + extension;
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
}
