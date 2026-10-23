package com.fomentiq.documents.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallDocumentVersionRepository extends JpaRepository<CallDocumentVersion, UUID> { }
