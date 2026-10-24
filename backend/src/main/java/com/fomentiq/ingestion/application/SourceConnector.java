package com.fomentiq.ingestion.application;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Application port for discovering one source's calls and preserving their source artifacts. */
public interface SourceConnector {
    String sourceType();
    List<SourceCall> discover();
    DocumentCatalog discoverDocuments(SourceCall call);
    DownloadedDocument download(SourceDocument document);

    record SourceCall(String externalId, String portalEntryId, String title, String summary, String status,
                      LocalDate publicationDate, LocalDate openingDate, LocalDate deadline, URI canonicalUri,
                      URI discoveryUri, byte[] discoverySnapshot, String discoveryMediaType,
                      URI callUri, byte[] callSnapshot, String callMediaType) { }

    record DocumentCatalog(URI sourceUri, byte[] snapshot, String mediaType, List<SourceDocument> documents) { }

    record SourceDocument(String externalId, String title, String category, URI sourceUri) { }

    record DownloadedDocument(byte[] content, String mediaType, URI finalUri) { }
}
