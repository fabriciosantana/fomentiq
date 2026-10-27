package com.fomentiq.ingestion.application;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** Application port for discovering one source's calls and preserving their source artifacts. */
public interface SourceConnector {
    String sourceType();
    default ArtifactOrigin artifactOrigin() { return ArtifactOrigin.LIVE; }
    default AcquisitionBounds acquisitionBounds() { return AcquisitionBounds.DEFAULT; }
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
                      URI callUri, byte[] callSnapshot, String callMediaType, String primaryAgencyAcronym,
                      List<String> additionalFundingAgencyAcronyms) {
        public SourceCall(String externalId, String portalEntryId, String title, String summary, String status,
                          LocalDate publicationDate, LocalDate openingDate, LocalDate deadline, URI canonicalUri,
                          URI discoveryUri, byte[] discoverySnapshot, String discoveryMediaType,
                          URI callUri, byte[] callSnapshot, String callMediaType) {
            this(externalId, portalEntryId, title, summary, status, publicationDate, openingDate, deadline,
                    canonicalUri, discoveryUri, discoverySnapshot, discoveryMediaType, callUri, callSnapshot,
                    callMediaType, "FINEP", List.of());
        }
        public SourceCall {
            additionalFundingAgencyAcronyms = additionalFundingAgencyAcronyms == null
                    ? List.of() : List.copyOf(additionalFundingAgencyAcronyms);
        }
    }

    record DocumentCatalog(URI sourceUri, byte[] snapshot, String mediaType, List<SourceDocument> documents) { }

    record SourceDocument(String externalId, String title, String category, URI sourceUri, String expectedMediaType) {
        public SourceDocument(String externalId, String title, String category, URI sourceUri) {
            this(externalId, title, category, sourceUri, null);
        }
    }

    record DownloadedDocument(byte[] content, String mediaType, URI finalUri) { }

    record AcquisitionBounds(int maxDocuments, long maxBytesPerDocument, long maxAggregateDocumentBytes,
                             long maxDiscoveryResponseBytes, long maxMetadataResponseBytes, int maxHttpRequests,
                             int maxRedirects, Duration connectTimeout, Duration readTimeout) {
        public static final AcquisitionBounds DEFAULT = new AcquisitionBounds(18, 50L * 1024 * 1024,
                250L * 1024 * 1024, 5L * 1024 * 1024, 5L * 1024 * 1024, 100, 4,
                Duration.ofSeconds(10), Duration.ofSeconds(30));

        public AcquisitionBounds {
            if (maxDocuments < 1 || maxBytesPerDocument < 1 || maxAggregateDocumentBytes < maxBytesPerDocument
                    || maxDiscoveryResponseBytes < 1 || maxMetadataResponseBytes < 1 || maxHttpRequests < 1
                    || maxRedirects < 0 || connectTimeout == null || readTimeout == null
                    || connectTimeout.isNegative() || connectTimeout.isZero()
                    || readTimeout.isNegative() || readTimeout.isZero()) {
                throw new IllegalArgumentException("Invalid source acquisition bounds");
            }
        }
    }
}
