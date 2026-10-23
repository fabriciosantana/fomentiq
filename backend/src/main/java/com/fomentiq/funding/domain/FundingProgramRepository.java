package com.fomentiq.funding.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundingProgramRepository extends JpaRepository<FundingProgram, UUID> { }
