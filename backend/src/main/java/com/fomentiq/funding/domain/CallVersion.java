package com.fomentiq.funding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "call_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_call_version_number", columnNames = {"call_id", "version_number"}))
public class CallVersion {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_id", nullable = false)
    private CallForProposals call;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_version_id")
    private CallVersion supersedesVersion;

    protected CallVersion() { }

    public CallVersion(CallForProposals call, int versionNumber, String contentHash,
                       Instant detectedAt, CallVersion supersedesVersion) {
        this.call = call;
        this.versionNumber = versionNumber;
        this.contentHash = contentHash;
        this.detectedAt = detectedAt;
        this.supersedesVersion = supersedesVersion;
    }

    public UUID getId() { return id; }
    public int getVersionNumber() { return versionNumber; }
    public String getContentHash() { return contentHash; }
    public CallForProposals getCall() { return call; }
}
