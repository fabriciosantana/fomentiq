package com.fomentiq.ingestion.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> { }
