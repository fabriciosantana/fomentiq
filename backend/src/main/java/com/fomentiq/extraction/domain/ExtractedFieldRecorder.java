package com.fomentiq.extraction.domain;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtractedFieldRecorder {
    private final EntityManager entityManager;
    private final ExtractedFieldRepository repository;

    public ExtractedFieldRecorder(EntityManager entityManager, ExtractedFieldRepository repository) {
        this.entityManager = entityManager;
        this.repository = repository;
    }

    @Transactional
    public ExtractedField record(UUID callVersionId, String fieldName, String normalizedValue, String rawValue,
                                 ValidationStatus status, String extractionMethod, Instant createdAt,
                                 UUID ingestionRunId, BigDecimal confidence) {
        return recordWithResult(callVersionId, fieldName, normalizedValue, rawValue, status, extractionMethod,
                createdAt, ingestionRunId, confidence).field();
    }

    public RecordResult recordWithResult(UUID callVersionId, String fieldName, String normalizedValue, String rawValue,
                                 ValidationStatus status, String extractionMethod, Instant createdAt,
                                 UUID ingestionRunId, BigDecimal confidence) {
        entityManager.createNativeQuery("select id from call_version where id = :id for update")
                .setParameter("id", callVersionId).getSingleResult();
        ExtractedField latest = repository.findTopByCallVersionIdAndFieldNameOrderByCreatedAtDescIdDesc(
                callVersionId, fieldName).orElse(null);
        if (latest != null && sameFact(latest, normalizedValue, rawValue, status, extractionMethod, confidence)) {
            return new RecordResult(latest, false);
        }
        return new RecordResult(repository.save(new ExtractedField(callVersionId, fieldName, normalizedValue, rawValue,
                status, extractionMethod, createdAt, ingestionRunId, confidence)), true);
    }

    private boolean sameFact(ExtractedField previous, String normalized, String raw,
                             ValidationStatus status, String method, BigDecimal confidence) {
        return Objects.equals(previous.getNormalizedValue(), normalized)
                && Objects.equals(previous.getRawValue(), raw)
                && previous.getValidationStatus() == status
                && Objects.equals(previous.getConfidence(), confidence)
                && Objects.equals(previous.getExtractionMethod(), method);
    }

    public record RecordResult(ExtractedField field, boolean created) { }
}
