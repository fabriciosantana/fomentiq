package com.fomentiq.search.application;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-side application port for the opportunity catalog; it exposes no persistence entities. */
public interface OpportunityReadPort {
    Page<OpportunitySummary> search(Search search, int page, int size);
    Optional<OpportunityDetail> findOpportunity(UUID callId);
    Page<CallVersionView> callVersions(UUID callId, int page, int size);
    Page<DocumentView> documents(UUID callId, int page, int size);
    Optional<Page<DocumentVersionView>> documentVersions(UUID callId, UUID documentId, int page, int size);
    Optional<Page<EvidenceView>> evidence(UUID callId, String fieldName, int page, int size);

    record Search(String query, String status, LocalDate deadlineFrom, LocalDate deadlineTo,
                  String instrumentType, String region, String theme, String sourcePublisher, String agency) {
        public Search(String query, String status, LocalDate deadlineFrom, LocalDate deadlineTo,
                      String instrumentType, String region, String theme) {
            this(query, status, deadlineFrom, deadlineTo, instrumentType, region, theme, null, null);
        }
    }
    record Page<T>(List<T> content, int page, int size, long totalElements) { }
    record OpportunitySummary(UUID id, String externalId, String portalEntryId, String title,
                              String agencyName, String agencyAcronym, String program,
                              String status, LocalDate deadline, String instrumentType,
                              List<SourceView> sources, List<AgencyView> responsibleAgencies) {
        public OpportunitySummary(UUID id, String externalId, String portalEntryId, String title,
                                  String agencyName, String agencyAcronym, String program,
                                  String status, LocalDate deadline, String instrumentType) {
            this(id, externalId, portalEntryId, title, agencyName, agencyAcronym, program, status,
                    deadline, instrumentType, List.of(), List.of());
        }
    }
    record SourceView(String code, String name) { }
    record AgencyView(String name, String acronym, boolean primary) { }
    record OpportunityDetail(OpportunitySummary opportunity, String summary, String officialUrl,
                             Integer latestCallVersion, List<FieldView> fields) { }
    record FieldView(String name, String value, String rawValue, String status, Instant createdAt,
                     List<EvidenceView> evidence) { }
    record CallVersionView(UUID id, int versionNumber, String contentHash, Instant detectedAt,
                           UUID supersedesVersionId) { }
    record DocumentView(UUID id, String externalId, String type, String title, String sourceUrl,
                        long versionCount, Instant latestDownloadedAt) { }
    record DocumentVersionView(UUID id, String contentHash, String mediaType, String sourceUrl,
                               Instant downloadedAt) { }
    record EvidenceView(UUID id, UUID extractedFieldId, String fieldName, UUID documentVersionId,
                        String sourceUrl, Integer page, String section, String quote, String locatorJson) { }
}
