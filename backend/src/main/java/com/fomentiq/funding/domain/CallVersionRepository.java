package com.fomentiq.funding.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallVersionRepository extends JpaRepository<CallVersion, UUID> { }
