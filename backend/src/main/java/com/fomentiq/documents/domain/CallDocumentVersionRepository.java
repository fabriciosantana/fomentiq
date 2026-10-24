package com.fomentiq.documents.domain;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallDocumentVersionRepository extends JpaRepository<CallDocumentVersion, UUID> {
    Optional<CallDocumentVersion> findTopByDocument_IdOrderByDownloadedAtDesc(UUID documentId);
    long countByDocument_Id(UUID documentId);
}
