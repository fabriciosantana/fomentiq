package com.fomentiq.funding.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallForProposalsRepository extends JpaRepository<CallForProposals, UUID> {
    Optional<CallForProposals> findByAgencyAcronymAndExternalId(String acronym, String externalId);
}
