package com.fomentiq.editorial.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualOpportunityStore {
    record Page<T>(List<T> content, int page, int size, long totalElements) { }
    Page<ManualOpportunity> list(String query, boolean publishedOnly, int page, int size);
    Optional<ManualOpportunity> find(UUID id, boolean publishedOnly);
    ManualOpportunity create(ManualOpportunity draft, String actor);
    ManualOpportunity save(ManualOpportunity content, long expectedVersion, String actor);
    ManualOpportunity publish(UUID id, long expectedVersion, String actor);
    ManualOpportunity unpublish(UUID id, long expectedVersion, String actor);
}
