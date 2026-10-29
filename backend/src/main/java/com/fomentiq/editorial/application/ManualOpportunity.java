package com.fomentiq.editorial.application;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManualOpportunity(UUID id, String title, String institution, String summary, LocalDate deadline,
        String status, String officialUrl, String editorialState, List<Section> sections, long rowVersion,
        String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public ManualOpportunity { sections = List.copyOf(sections); }
    public record Section(String title, int position, List<Field> fields) {
        public Section { fields = List.copyOf(fields); }
    }
    public record Field(String name, String type, Object value, int position) { }
}
