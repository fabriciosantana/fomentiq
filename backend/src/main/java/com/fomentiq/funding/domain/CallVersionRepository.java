package com.fomentiq.funding.domain;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallVersionRepository extends JpaRepository<CallVersion, UUID> {
    Optional<CallVersion> findTopByCall_IdOrderByVersionNumberDesc(UUID callId);
    long countByCall_Id(UUID callId);
}
