package com.fomentiq.funding.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundingAgencyRepository extends JpaRepository<FundingAgency, UUID> {
    Optional<FundingAgency> findByAcronym(String acronym);
}
