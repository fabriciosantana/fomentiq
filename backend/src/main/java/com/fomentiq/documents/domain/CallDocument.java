package com.fomentiq.documents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "call_document", uniqueConstraints = @UniqueConstraint(
        name = "uk_call_document_call_source", columnNames = {"call_id", "source_url"}))
public class CallDocument {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "call_id", nullable = false)
    private UUID callId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private CallDocumentType documentType;
    @Column(nullable = false, length = 1000)
    private String title;
    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    protected CallDocument() { }

    public CallDocument(UUID callId, CallDocumentType documentType, String title, String sourceUrl) {
        this.callId = callId;
        this.documentType = documentType;
        this.title = title;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() { return id; }
    public CallDocumentType getDocumentType() { return documentType; }
    public String getSourceUrl() { return sourceUrl; }
}
