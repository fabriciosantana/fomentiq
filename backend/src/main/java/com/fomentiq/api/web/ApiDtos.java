package com.fomentiq.api.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Explicit public response contracts; persistence entities and extraction execution records never leave the API layer. */
public final class ApiDtos {
    private ApiDtos() { }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
    public record OpportunitySummary(UUID id, String externalId, String portalEntryId, String title,
                                    Agency agency, String program, String status, LocalDate deadline,
                                    String instrumentType, List<SourcePublisher> sources,
                                    List<ResponsibleAgency> responsibleAgencies) { }
    public record Agency(String name, String acronym) { }
    public record SourcePublisher(String code, String name) { }
    public record ResponsibleAgency(String name, String acronym, boolean primary) { }
    public record OpportunityDetail(OpportunitySummary opportunity, String summary, String officialUrl,
                                   Integer currentCallVersion, List<ExtractedField> fields) { }
    public record ExtractedField(String name, Object value, String rawValue, String validationStatus,
                                 Instant createdAt, List<Evidence> evidence, String publicationSource,
                                 UUID publishedRevisionId, Object originalValue, boolean confirmedHumanAbsence,
                                 PublicationReference publicationReference) { }
    public record PublicationReference(UUID documentId, UUID documentVersionId, String sourceUrl,
                                       Integer page, String section, String quote) { }
    public record Evidence(UUID id, UUID extractedFieldId, String fieldName, UUID documentVersionId,
                           String sourceUrl, Integer page, String section, String quote, Object locator) { }
    public record CallVersion(UUID id, int versionNumber, String contentHash, Instant detectedAt,
                              UUID supersedesVersionId) { }
    public record Document(UUID id, String externalId, String type, String title, String sourceUrl,
                           long versionCount, Instant latestDownloadedAt) { }
    public record DocumentVersion(UUID id, String contentHash, String mediaType, String sourceUrl,
                                  Instant downloadedAt) { }
}
