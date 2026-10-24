package com.fomentiq.extraction.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {
    Optional<ExtractedField> findTopByCallVersionIdAndFieldNameOrderByCreatedAtDescIdDesc(UUID callVersionId, String fieldName);
    long countByCallVersionId(UUID callVersionId);
}
