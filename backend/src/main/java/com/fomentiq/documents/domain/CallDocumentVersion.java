package com.fomentiq.documents.domain;

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
@Table(name = "call_document_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_version_hash", columnNames = {"document_id", "content_hash"}))
public class CallDocumentVersion {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CallDocument document;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;
    @Column(name = "media_type", nullable = false, length = 200)
    private String mediaType;
    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;
    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt;
    @Column(name = "extracted_text_key", length = 1024)
    private String extractedTextKey;

    protected CallDocumentVersion() { }

    public CallDocumentVersion(CallDocument document, String contentHash, String storageKey,
                               String mediaType, String sourceUrl, Instant downloadedAt, String extractedTextKey) {
        this.document = document;
        this.contentHash = contentHash;
        this.storageKey = storageKey;
        this.mediaType = mediaType;
        this.sourceUrl = sourceUrl;
        this.downloadedAt = downloadedAt;
        this.extractedTextKey = extractedTextKey;
    }

    public UUID getId() { return id; }
    public String getContentHash() { return contentHash; }
    public String getStorageKey() { return storageKey; }
}
