package com.fomentiq.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.api.web.ApiDtos.Agency;
import com.fomentiq.api.web.ApiDtos.CallVersion;
import com.fomentiq.api.web.ApiDtos.Document;
import com.fomentiq.api.web.ApiDtos.DocumentVersion;
import com.fomentiq.api.web.ApiDtos.Evidence;
import com.fomentiq.api.web.ApiDtos.ExtractedField;
import com.fomentiq.api.web.ApiDtos.OpportunityDetail;
import com.fomentiq.api.web.ApiDtos.OpportunitySummary;
import com.fomentiq.api.web.ApiDtos.Page;
import com.fomentiq.search.application.OpportunityReadPort;
import com.fomentiq.search.application.OpportunityReadPort.FieldView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only HTTP API for the opportunity catalog. */
@RestController
@RequestMapping("/api/opportunities")
@Validated
public class OpportunityApiController {
    private final OpportunityReadPort reads;
    private final ObjectMapper mapper;
    private final PublicUrlSanitizer urls;
    private final PublicLocatorSanitizer locators;

    public OpportunityApiController(OpportunityReadPort reads, ObjectMapper mapper, PublicUrlSanitizer urls,
                                    PublicLocatorSanitizer locators) {
        this.reads = reads;
        this.mapper = mapper;
        this.urls = urls;
        this.locators = locators;
    }

    @GetMapping
    public Page<OpportunitySummary> list(@RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Pattern(regexp = "OPEN|UPCOMING|CLOSED|SUSPENDED|UNKNOWN") String status,
            @RequestParam(required = false) LocalDate deadlineFrom,
            @RequestParam(required = false) LocalDate deadlineTo,
            @RequestParam(required = false) @Size(max = 100) String instrument,
            @RequestParam(required = false) @Size(max = 120) String region,
            @RequestParam(required = false) @Size(max = 120) String theme,
            @RequestParam(required = false) @Pattern(regexp = "FINEP|CNPQ") String source,
            @RequestParam(required = false) @Pattern(regexp = "FINEP|CNPq|CAPES") String agency,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (deadlineFrom != null && deadlineTo != null && deadlineFrom.isAfter(deadlineTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deadlineFrom must not be after deadlineTo");
        }
        var result = reads.search(new OpportunityReadPort.Search(q, status, deadlineFrom, deadlineTo,
                instrument, region, theme, source, agency), page, size);
        return page(result, result.content().stream().map(this::summary).toList());
    }

    @GetMapping("/{callId}")
    public OpportunityDetail detail(@PathVariable UUID callId) {
        var detail = reads.findOpportunity(callId).orElseThrow(() -> notFound("Opportunity not found"));
        return new OpportunityDetail(summary(detail.opportunity()), detail.summary(), urls.sanitize(detail.officialUrl()),
                detail.latestCallVersion(), detail.fields().stream().map(this::field).toList());
    }

    @GetMapping("/{callId}/versions")
    public Page<CallVersion> callVersions(@PathVariable UUID callId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        requireCall(callId);
        var result = reads.callVersions(callId, page, size);
        return page(result, result.content().stream().map(v -> new CallVersion(v.id(), v.versionNumber(),
                v.contentHash(), v.detectedAt(), v.supersedesVersionId())).toList());
    }

    @GetMapping("/{callId}/documents")
    public Page<Document> documents(@PathVariable UUID callId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        requireCall(callId);
        var result = reads.documents(callId, page, size);
        return page(result, result.content().stream().map(d -> new Document(d.id(), d.externalId(), d.type(),
                d.title(), urls.sanitize(d.sourceUrl()), d.versionCount(), d.latestDownloadedAt())).toList());
    }

    @GetMapping("/{callId}/documents/{documentId}/versions")
    public Page<DocumentVersion> documentVersions(@PathVariable UUID callId, @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = reads.documentVersions(callId, documentId, page, size)
                .orElseThrow(() -> notFound("Document not found"));
        return page(result, result.content().stream().map(v -> new DocumentVersion(v.id(), v.contentHash(),
                v.mediaType(), urls.sanitize(v.sourceUrl()), v.downloadedAt())).toList());
    }

    @GetMapping("/{callId}/evidence")
    public Page<Evidence> evidence(@PathVariable UUID callId,
            @RequestParam(required = false) @Size(max = 100) String field,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = reads.evidence(callId, field, page, size).orElseThrow(() -> notFound("Opportunity not found"));
        return page(result, result.content().stream().map(this::evidence).toList());
    }

    private OpportunitySummary summary(OpportunityReadPort.OpportunitySummary item) {
        return new OpportunitySummary(item.id(), item.externalId(), item.portalEntryId(), item.title(),
                new Agency(item.agencyName(), item.agencyAcronym()), item.program(), item.status(),
                item.deadline(), item.instrumentType(),
                item.sources().stream().map(source -> new com.fomentiq.api.web.ApiDtos.SourcePublisher(
                        source.code(), source.name())).toList(),
                item.responsibleAgencies().stream().map(agency -> new com.fomentiq.api.web.ApiDtos.ResponsibleAgency(
                        agency.name(), agency.acronym(), agency.primary())).toList());
    }

    private ExtractedField field(FieldView field) {
        return new ExtractedField(field.name(), jsonValue(field.value()), field.rawValue(), field.status(),
                field.createdAt(), field.evidence().stream().map(this::evidence).toList());
    }

    private Evidence evidence(OpportunityReadPort.EvidenceView evidence) {
        return new Evidence(evidence.id(), evidence.extractedFieldId(), evidence.fieldName(),
                evidence.documentVersionId(), urls.sanitize(evidence.sourceUrl()), evidence.page(),
                evidence.section(), evidence.quote(), locators.sanitize(evidence.locatorJson()));
    }

    private Object jsonValue(String value) {
        if (value == null) return null;
        try { return mapper.convertValue(mapper.readTree(value), Object.class); }
        catch (Exception ignored) { return value; }
    }

    private void requireCall(UUID callId) {
        if (reads.findOpportunity(callId).isEmpty()) throw notFound("Opportunity not found");
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static <A, B> Page<B> page(OpportunityReadPort.Page<A> source, List<B> content) {
        int totalPages = source.totalElements() == 0 ? 0
                : (int) Math.min(Integer.MAX_VALUE, (source.totalElements() + source.size() - 1) / source.size());
        return new Page<>(content, source.page(), source.size(), source.totalElements(), totalPages);
    }
}
