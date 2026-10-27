package com.fomentiq.curation.application;

import com.fomentiq.curation.domain.HumanFieldRevision;
import com.fomentiq.curation.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HumanCurationStore {
    Page<ReviewQueueItem> reviewQueue(int page, int size);
    boolean opportunityExists(UUID callId);
    boolean documentVersionBelongsToOpportunity(UUID callId, UUID documentId, UUID documentVersionId);
    void lockOpportunity(UUID callId);
    Optional<HumanFieldRevision> latestApproved(UUID callId, String fieldName);
    Optional<String> basePublishedValueJson(UUID callId, String fieldName);
    HumanFieldRevision create(PendingRevision revision);
    Optional<HumanFieldRevision> findRevision(UUID revisionId);
    Page<HumanFieldRevision> revisionHistory(UUID callId, String fieldName, int page, int size);
    boolean transition(UUID revisionId, long expectedRowVersion, ReviewStatus nextStatus, String actor,
            Instant decidedAt, String decisionReason);

    record Page<T>(List<T> content, int page, int size, long totalElements) { }
    record PendingRevision(UUID callId, String fieldName, String valueKind, String proposedValueJson,
            String previousPublishedValueJson, UUID previousRevisionId, String justification, UUID documentId,
            UUID documentVersionId, Integer page, String section, String quoteFragment, String locatorJson,
            String createdBy, Instant createdAt) { }
}
