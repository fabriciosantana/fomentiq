package com.fomentiq.ingestion.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSnapshotRepository extends JpaRepository<SourceSnapshot, UUID> {
    List<SourceSnapshot> findByCallIdOrderByCapturedAtAsc(UUID callId);
    List<SourceSnapshot> findByIngestionRun_Id(UUID runId);
}
