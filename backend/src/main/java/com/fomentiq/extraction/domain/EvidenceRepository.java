package com.fomentiq.extraction.domain;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByExtractedFieldId(UUID extractedFieldId);
}
