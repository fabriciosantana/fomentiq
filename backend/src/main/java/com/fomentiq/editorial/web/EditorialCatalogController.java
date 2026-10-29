package com.fomentiq.editorial.web;

import com.fomentiq.editorial.application.ManualOpportunity;
import com.fomentiq.editorial.application.ManualOpportunityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/editorial/opportunities")
public class EditorialCatalogController {
    private final ManualOpportunityService service;

    public EditorialCatalogController(ManualOpportunityService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PublicOpportunity> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.list(q, true, page, size);
        return new Page<>(result.content().stream().map(PublicOpportunity::from).toList(),
                page, size, result.totalElements(), totalPages(result.totalElements(), size));
    }

    @GetMapping("/{id}")
    public PublicOpportunity detail(@PathVariable UUID id) {
        return PublicOpportunity.from(service.get(id, true));
    }

    private int totalPages(long count, int size) {
        return count == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (count + size - 1) / size);
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
    public record PublicOpportunity(UUID id, String title, String institution, String summary,
            LocalDate deadline, String status, String officialUrl, List<ManualOpportunity.Section> sections) {
        static PublicOpportunity from(ManualOpportunity item) {
            return new PublicOpportunity(item.id(), item.title(), item.institution(), item.summary(),
                    item.deadline(), item.status(), item.officialUrl(), item.sections());
        }
    }
}
