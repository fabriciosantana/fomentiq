package com.fomentiq.editorial.web;

import com.fomentiq.editorial.application.ManualOpportunity;
import com.fomentiq.editorial.application.ManualOpportunityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("manual-demo")
@ConditionalOnProperty(name = "fomentiq.admin.demo.enabled", havingValue = "true")
@RequestMapping("/api/admin/manual-opportunities")
@PreAuthorize("hasRole('ADMIN')")
public class ManualOpportunityController {
    private final ManualOpportunityService service;

    public ManualOpportunityController(ManualOpportunityService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminOpportunity> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.list(q, false, page, size);
        return new Page<>(result.content().stream().map(AdminOpportunity::from).toList(),
                page, size, result.totalElements(), totalPages(result.totalElements(), size));
    }

    @PostMapping
    public AdminOpportunity create(@RequestBody ContentRequest request, Authentication authentication) {
        return AdminOpportunity.from(service.create(request.toModel(), authentication.getName()));
    }

    @GetMapping("/{id}")
    public AdminOpportunity get(@PathVariable UUID id) {
        return AdminOpportunity.from(service.get(id, false));
    }

    @PutMapping("/{id}")
    public AdminOpportunity save(@PathVariable UUID id, @RequestBody SaveRequest request,
            Authentication authentication) {
        return AdminOpportunity.from(service.save(id, request.content().toModel(),
                request.expectedVersion(), authentication.getName()));
    }

    @GetMapping("/{id}/preview")
    public AdminOpportunity preview(@PathVariable UUID id) {
        return AdminOpportunity.from(service.get(id, false));
    }

    @PostMapping("/{id}/publish")
    public AdminOpportunity publish(@PathVariable UUID id, @RequestBody VersionRequest request,
            Authentication authentication) {
        return AdminOpportunity.from(service.publish(id, request.expectedVersion(), authentication.getName()));
    }

    @PostMapping("/{id}/unpublish")
    public AdminOpportunity unpublish(@PathVariable UUID id, @RequestBody VersionRequest request,
            Authentication authentication) {
        return AdminOpportunity.from(service.unpublish(id, request.expectedVersion(), authentication.getName()));
    }

    public record VersionRequest(long expectedVersion) { }
    public record SaveRequest(long expectedVersion, ContentRequest content) { }
    public record ContentRequest(String title, String institution, String summary, LocalDate deadline,
            String status, String officialUrl, List<SectionRequest> sections) {
        ManualOpportunity toModel() {
            var contentSections = sections == null ? List.<ManualOpportunity.Section>of()
                    : sections.stream().map(SectionRequest::toModel).toList();
            return new ManualOpportunity(null, title, institution, summary, deadline, status,
                    officialUrl, "DRAFT", contentSections, 0, null, null, null, null);
        }
    }
    public record SectionRequest(String title, int position, List<FieldRequest> fields) {
        ManualOpportunity.Section toModel() {
            var contentFields = fields == null ? List.<ManualOpportunity.Field>of()
                    : fields.stream().map(FieldRequest::toModel).toList();
            return new ManualOpportunity.Section(title, position, contentFields);
        }
    }
    public record FieldRequest(String name, String type, Object value, int position) {
        ManualOpportunity.Field toModel() {
            return new ManualOpportunity.Field(name, type, value, position);
        }
    }
    public record AdminOpportunity(UUID id, String title, String institution, String summary,
            LocalDate deadline, String status, String officialUrl, String editorialState,
            List<ManualOpportunity.Section> sections, long rowVersion, String createdBy,
            String updatedBy, java.time.Instant createdAt, java.time.Instant updatedAt) {
        static AdminOpportunity from(ManualOpportunity item) {
            return new AdminOpportunity(item.id(), item.title(), item.institution(), item.summary(),
                    item.deadline(), item.status(), item.officialUrl(), item.editorialState(),
                    item.sections(), item.rowVersion(), item.createdBy(), item.updatedBy(),
                    item.createdAt(), item.updatedAt());
        }
    }
    private int totalPages(long count, int size) {
        return count == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (count + size - 1) / size);
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
}
