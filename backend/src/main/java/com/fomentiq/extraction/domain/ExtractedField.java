package com.fomentiq.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "extracted_field")
public class ExtractedField {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "call_version_id", nullable = false)
    private UUID callVersionId;
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;
    @Column(name = "normalized_value", columnDefinition = "text")
    private String normalizedValue;
    @Column(name = "raw_value", columnDefinition = "text")
    private String rawValue;
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private ValidationStatus validationStatus;
    @Column(name = "extraction_method", nullable = false, length = 100)
    private String extractionMethod;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "ingestion_run_id", nullable = false)
    private UUID ingestionRunId;
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    protected ExtractedField() { }

    public ExtractedField(UUID callVersionId, String fieldName, String normalizedValue, String rawValue,
                          ValidationStatus validationStatus, String extractionMethod, Instant createdAt,
                          UUID ingestionRunId, BigDecimal confidence) {
        this.callVersionId = callVersionId;
        this.fieldName = fieldName;
        this.normalizedValue = normalizedValue;
        this.rawValue = rawValue;
        this.validationStatus = validationStatus;
        this.extractionMethod = extractionMethod;
        this.createdAt = createdAt;
        this.ingestionRunId = ingestionRunId;
        this.confidence = confidence;
    }

    public UUID getId() { return id; }
    public UUID getCallVersionId() { return callVersionId; }
    public String getFieldName() { return fieldName; }
    public String getNormalizedValue() { return normalizedValue; }
    public String getRawValue() { return rawValue; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public String getExtractionMethod() { return extractionMethod; }
    public BigDecimal getConfidence() { return confidence; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getIngestionRunId() { return ingestionRunId; }
}
