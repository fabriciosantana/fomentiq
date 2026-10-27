package com.fomentiq.curation.domain;

import java.time.Instant;
import java.util.UUID;

public record HumanFieldRevision(UUID id, UUID callId, String fieldName, String valueKind,
        String proposedValueJson, String previousPublishedValueJson, UUID previousRevisionId, String justification,
        UUID documentId, UUID documentVersionId, Integer page, String section, String quoteFragment, String locatorJson,
        ReviewStatus status, String createdBy, Instant createdAt, String decidedBy, Instant decidedAt,
        String decisionReason, long rowVersion) { }
