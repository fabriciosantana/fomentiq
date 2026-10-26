package com.fomentiq.ingestion.application;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Application port for discovering one source's calls and preserving their source artifacts. */
public interface SourceConnector {
    String sourceType();
    default ArtifactOrigin artifactOrigin() { return ArtifactOrigin.LIVE; }
    List<SourceCall> discover();
    DocumentCatalog discoverDocuments(SourceCall call);
    DownloadedDocument download(SourceDocument document);

    default List<SourceCall> discover(IngestionLimits limits) { return discover(); }
    default DocumentCatalog discoverDocuments(SourceCall call, IngestionLimits limits) {
        return discoverDocuments(call);
    }
    default DownloadedDocument download(SourceDocument document, long remainingAggregateBytes,
                                        IngestionLimits limits) {
        return download(document);
    }

    enum ArtifactOrigin { LIVE, FIXTURE, RECONSTRUCTED }

    record SourceCall(String externalId, String portalEntryId, String title, String summary, String status,
                      LocalDate publicationDate, LocalDate openingDate, LocalDate deadline, URI canonicalUri,
                      URI discoveryUri, byte[] discoverySnapshot, String discoveryMediaType,
                      URI callUri, byte[] callSnapshot, String callMediaType) { }

    record DocumentCatalog(URI sourceUri, byte[] snapshot, String mediaType, List<SourceDocument> documents) { }

    record SourceDocument(String externalId, String title, String category, URI sourceUri) { }

    record DownloadedDocument(byte[] content, String mediaType, URI finalUri) { }
}
