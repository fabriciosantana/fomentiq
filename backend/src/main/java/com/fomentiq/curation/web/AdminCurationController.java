package com.fomentiq.curation.web;

import com.fomentiq.curation.application.HumanCurationService;
import com.fomentiq.curation.application.HumanCurationStore;
import com.fomentiq.curation.application.ReviewQueueItem;
import com.fomentiq.curation.domain.HumanFieldRevision;
import com.fomentiq.api.web.ApiDtos;
import com.fomentiq.api.web.PublicLocatorSanitizer;
import com.fomentiq.api.web.PublicUrlSanitizer;
import com.fomentiq.search.application.OpportunityReadPort;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DTO-only administrative API. Its bean and matching security chain exist only in manual-demo. */
@RestController
@RequestMapping("/api/admin")
@Profile("manual-demo")
@ConditionalOnProperty(name = "fomentiq.admin.demo.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCurationController {
    private final HumanCurationService curation;
    private final OpportunityReadPort reads;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final PublicUrlSanitizer urls;
    private final PublicLocatorSanitizer locators;
    public AdminCurationController(HumanCurationService curation, OpportunityReadPort reads,
            com.fasterxml.jackson.databind.ObjectMapper mapper, PublicUrlSanitizer urls,
            PublicLocatorSanitizer locators) {
        this.curation = curation;
        this.reads = reads;
        this.mapper = mapper;
        this.urls = urls;
        this.locators = locators;
    }

    @GetMapping("/opportunities")
    public HumanCurationStore.Page<ReviewQueueItem> queue(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return curation.queue(page, size); }

    @GetMapping("/opportunities/{callId}")
    public ApiDtos.OpportunityDetail opportunityReview(@PathVariable UUID callId) {
        var detail = reads.findOpportunity(callId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var item = detail.opportunity();
        var summary = new ApiDtos.OpportunitySummary(item.id(), item.externalId(), item.portalEntryId(), item.title(),
                new ApiDtos.Agency(item.agencyName(), item.agencyAcronym()), item.program(), item.status(), item.deadline(),
                item.instrumentType(), item.sources().stream().map(source -> new ApiDtos.SourcePublisher(
                        source.code(), source.name())).toList(),
                item.responsibleAgencies().stream().map(agency -> new ApiDtos.ResponsibleAgency(
                        agency.name(), agency.acronym(), agency.primary())).toList());
        var fields = detail.fields().stream().map(field -> new ApiDtos.ExtractedField(field.name(), json(field.value()),
                field.rawValue(), field.status(), field.createdAt(), field.evidence().stream().map(evidence ->
                new ApiDtos.Evidence(evidence.id(), evidence.extractedFieldId(), evidence.fieldName(),
                        evidence.documentVersionId(), urls.sanitize(evidence.sourceUrl()), evidence.page(),
                        evidence.section(), evidence.quote(), locators.sanitize(evidence.locatorJson()))).toList(),
                field.publicationSource(), field.publishedRevisionId(), json(field.originalValue()),
                field.confirmedHumanAbsence(), field.publicationReference() == null ? null
                        : new ApiDtos.PublicationReference(field.publicationReference().documentId(),
                                field.publicationReference().documentVersionId(),
                                urls.sanitize(field.publicationReference().sourceUrl()),
                                field.publicationReference().page(), field.publicationReference().section(),
                                field.publicationReference().quote()))).toList();
        return new ApiDtos.OpportunityDetail(summary, detail.summary(), urls.sanitize(detail.officialUrl()),
                detail.latestCallVersion(), fields);
    }

    @GetMapping("/opportunities/{callId}/revisions")
    public HumanCurationStore.Page<RevisionDto> history(@PathVariable UUID callId, @RequestParam String field,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = curation.history(callId, field, page, size);
        return new HumanCurationStore.Page<>(result.content().stream().map(RevisionDto::from).toList(),
                result.page(), result.size(), result.totalElements());
    }

    @PostMapping("/opportunities/{callId}/revisions")
    public RevisionDto propose(@PathVariable UUID callId, @RequestBody CreateRevision request,
            Authentication authentication) {
        return RevisionDto.from(curation.propose(callId, request.fieldName(), request.valueKind(),
                mapper.valueToTree(request.value()),
                request.justification(), request.documentId(), request.documentVersionId(), request.page(),
                request.section(), request.quote(), request.locator() == null ? null : mapper.valueToTree(request.locator()),
                request.absenceReviewComplete(),
                authentication.getName()));
    }

    @PostMapping("/revisions/{revisionId}/approve")
    public RevisionDto approve(@PathVariable UUID revisionId, @RequestBody Decision request,
            Authentication authentication) {
        return RevisionDto.from(curation.decide(revisionId, request.rowVersion(), true, request.reason(), authentication.getName()));
    }

    @PostMapping("/revisions/{revisionId}/reject")
    public RevisionDto reject(@PathVariable UUID revisionId, @RequestBody Decision request,
            Authentication authentication) {
        return RevisionDto.from(curation.decide(revisionId, request.rowVersion(), false, request.reason(), authentication.getName()));
    }

    public record CreateRevision(String fieldName, String valueKind, Object value, String justification,
            UUID documentId, UUID documentVersionId, Integer page, String section, String quote, Object locator,
            boolean absenceReviewComplete) { }
    public record Decision(long rowVersion, String reason) { }
    public record RevisionDto(UUID id, UUID callId, String fieldName, String valueKind, Object proposedValue,
            Object previousPublishedValue, UUID previousRevisionId, String justification, UUID documentId,
            UUID documentVersionId, Integer page, String section, String quote, Object locator, String status,
            String createdBy, java.time.Instant createdAt, String decidedBy, java.time.Instant decidedAt,
            String decisionReason, long rowVersion) {
        static RevisionDto from(HumanFieldRevision revision) {
            return new RevisionDto(revision.id(), revision.callId(), revision.fieldName(), revision.valueKind(),
                    read(revision.proposedValueJson()), read(revision.previousPublishedValueJson()),
                    revision.previousRevisionId(), revision.justification(), revision.documentId(),
                    revision.documentVersionId(), revision.page(), revision.section(), revision.quoteFragment(),
                    read(revision.locatorJson()), revision.status().name(), revision.createdBy(), revision.createdAt(),
                    revision.decidedBy(), revision.decidedAt(), revision.decisionReason(), revision.rowVersion());
        }
        private static Object read(String json) {
            if (json == null) return null;
            try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class); }
            catch (java.io.IOException invalid) { throw new IllegalStateException("Stored revision JSON is invalid", invalid); }
        }
    }

    private Object json(String value) {
        if (value == null) return null;
        try { return mapper.convertValue(mapper.readTree(value), Object.class); }
        catch (Exception invalid) { return value; }
    }
}
