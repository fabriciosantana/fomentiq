package com.fomentiq.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extracted_field_id", nullable = false)
    private ExtractedField extractedField;
    @Column(name = "document_version_id")
    private UUID documentVersionId;
    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;
    private Integer page;
    @Column(length = 500)
    private String section;
    @Column(name = "quote_fragment", columnDefinition = "text")
    private String quoteFragment;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "locator_json", nullable = false, columnDefinition = "jsonb")
    private String locatorJson;

    protected Evidence() { }

    public Evidence(ExtractedField extractedField, UUID documentVersionId, String sourceUrl,
                    Integer page, String section, String quoteFragment, String locatorJson) {
        this.extractedField = extractedField;
        this.documentVersionId = documentVersionId;
        this.sourceUrl = sourceUrl;
        this.page = page;
        this.section = section;
        this.quoteFragment = quoteFragment;
        this.locatorJson = locatorJson;
    }

    public UUID getId() { return id; }
    public String getSourceUrl() { return sourceUrl; }
}
