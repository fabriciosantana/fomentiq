package com.fomentiq.documents.domain;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallDocumentRepository extends JpaRepository<CallDocument, UUID> {
    Optional<CallDocument> findBySourceReferenceIdAndExternalId(UUID sourceReferenceId, String externalId);
    long countByCallId(UUID callId);
}
