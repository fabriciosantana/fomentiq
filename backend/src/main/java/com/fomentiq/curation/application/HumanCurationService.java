package com.fomentiq.curation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.curation.application.HumanCurationStore.PendingRevision;
import com.fomentiq.curation.domain.HumanFieldRevision;
import com.fomentiq.curation.domain.ReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HumanCurationService {
    private final HumanCurationStore store;
    private final CuratedFieldValueValidator validator;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public HumanCurationService(HumanCurationStore store, CuratedFieldValueValidator validator,
            ObjectMapper mapper) {
        this(store, validator, mapper, Clock.systemUTC());
    }

    HumanCurationService(HumanCurationStore store, CuratedFieldValueValidator validator,
            ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.validator = validator;
        this.mapper = mapper;
        this.clock = clock;
    }

    public HumanCurationStore.Page<ReviewQueueItem> queue(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw badRequest("Invalid pagination");
        return store.reviewQueue(page, size);
    }

    @Transactional
    public HumanFieldRevision propose(UUID callId, String field, String valueKind, JsonNode value,
            String justification, UUID documentId, UUID documentVersionId, Integer page, String section,
            String quote, JsonNode locator, boolean absenceReviewComplete, String actor) {
        boolean absence = "CONFIRMED_ABSENCE".equals(valueKind);
        if (actor == null || actor.isBlank() || actor.length() > 200) throw badRequest("Authenticated actor is required");
        if (justification == null || justification.isBlank() || justification.length() > 4000) {
            throw badRequest("A justification of at most 4000 characters is required");
        }
        if ((!absence && (quote == null || quote.isBlank())) || (quote != null && quote.length() > 8000)) {
            throw badRequest("A literal documentary quote is required for a value and is limited to 8000 characters");
        }
        if (page != null && page < 1) throw badRequest("Page must be positive");
        if (section != null && section.length() > 500) throw badRequest("Section reference is too long");
        if (locator != null && (!locator.isObject() || locator.toString().length() > 8000)) {
            throw badRequest("Locator must be a bounded object");
        }
        if (!absence && !"VALUE".equals(valueKind)) throw badRequest("Unsupported revision value kind");
        if (absence && !absenceReviewComplete) {
            throw badRequest("Document review must be complete to confirm absence");
        }
        try { validator.validate(field, value, absence); }
        catch (IllegalArgumentException invalid) { throw badRequest(invalid.getMessage()); }
        store.lockOpportunity(callId);
        if (!store.opportunityExists(callId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!store.documentVersionBelongsToOpportunity(callId, documentId, documentVersionId)) {
            throw badRequest("Document version does not belong to this opportunity");
        }
        var previous = store.latestApproved(callId, field);
        String proposed = absence ? null : value.toString();
        String locatorJson = locator == null || locator.isNull() ? "{}" : locator.toString();
        String previousValue = previous.isPresent() ? previous.get().proposedValueJson()
                : store.basePublishedValueJson(callId, field).map(this::asJson).orElse(null);
        return store.create(new PendingRevision(callId, field, valueKind, proposed, previousValue,
                previous.map(HumanFieldRevision::id).orElse(null), justification.trim(), documentId,
                documentVersionId, page, section, quote == null || quote.isBlank() ? null : quote.trim(),
                locatorJson, actor, Instant.now(clock)));
    }

    @Transactional
    public HumanFieldRevision decide(UUID revisionId, long expectedVersion, boolean approve, String reason, String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 200) throw badRequest("Authenticated actor is required");
        if (reason == null || reason.isBlank() || reason.length() > 4000) throw badRequest("A decision reason is required");
        HumanFieldRevision revision = store.findRevision(revisionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (revision.status() != ReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revision is already decided");
        }
        if (approve) {
            store.lockOpportunity(revision.callId());
            UUID current = store.latestApproved(revision.callId(), revision.fieldName()).map(HumanFieldRevision::id).orElse(null);
            if (!java.util.Objects.equals(current, revision.previousRevisionId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Published value changed; create a new revision");
            }
        }
        ReviewStatus next = approve ? ReviewStatus.APPROVED : ReviewStatus.REJECTED;
        if (!store.transition(revisionId, expectedVersion, next, actor, Instant.now(clock), reason.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revision was concurrently changed");
        }
        return store.findRevision(revisionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public HumanCurationStore.Page<HumanFieldRevision> history(UUID callId, String field, int page, int size) {
        if (!store.opportunityExists(callId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (field == null || field.isBlank() || !validator.supports(field)) throw badRequest("Supported field is required");
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw badRequest("Invalid pagination");
        return store.revisionHistory(callId, field, page, size);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private String asJson(String value) {
        try {
            mapper.readTree(value);
            return value;
        } catch (Exception notJson) {
            try { return mapper.writeValueAsString(value); }
            catch (Exception impossible) { throw new IllegalStateException("Could not encode prior value", impossible); }
        }
    }
}
