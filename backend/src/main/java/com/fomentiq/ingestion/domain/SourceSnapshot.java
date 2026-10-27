package com.fomentiq.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "source_snapshot")
public class SourceSnapshot {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "call_id", nullable = false)
    private UUID callId;
    @Column(name = "source_reference_id", nullable = false)
    private UUID sourceReferenceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingestion_run_id", nullable = false)
    private IngestionRun ingestionRun;
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 30)
    private SourceSnapshotType artifactType;
    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;
    @Column(name = "media_type", nullable = false, length = 200)
    private String mediaType;
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected SourceSnapshot() { }

    public SourceSnapshot(UUID callId, UUID sourceReferenceId, IngestionRun ingestionRun, SourceSnapshotType artifactType,
                          String sourceUrl, String contentHash, String storageKey, String mediaType, Instant capturedAt) {
        this.callId = callId;
        this.sourceReferenceId = sourceReferenceId;
        this.ingestionRun = ingestionRun;
        this.artifactType = artifactType;
        this.sourceUrl = sourceUrl;
        this.contentHash = contentHash;
        this.storageKey = storageKey;
        this.mediaType = mediaType;
        this.capturedAt = capturedAt;
    }

    public UUID getId() { return id; }
    public UUID getCallId() { return callId; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public SourceSnapshotType getArtifactType() { return artifactType; }
    public String getContentHash() { return contentHash; }
    public String getStorageKey() { return storageKey; }
}
