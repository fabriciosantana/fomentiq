package com.fomentiq.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ingestion_run")
public class IngestionRun {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "source_type", nullable = false, length = 80)
    private String sourceType;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngestionRunStatus status;
    @Column(name = "detected_count", nullable = false)
    private int detectedCount;
    @Column(name = "changed_count", nullable = false)
    private int changedCount;
    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    protected IngestionRun() { }

    public IngestionRun(String sourceType, Instant startedAt, IngestionRunStatus status) {
        this.sourceType = sourceType;
        this.startedAt = startedAt;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public Instant getStartedAt() { return startedAt; }
    public IngestionRunStatus getStatus() { return status; }
}
