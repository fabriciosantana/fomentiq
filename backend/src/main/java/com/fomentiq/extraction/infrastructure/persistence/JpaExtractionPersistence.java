package com.fomentiq.extraction.infrastructure.persistence;

import com.fomentiq.extraction.application.ExtractionPersistence;
import com.fomentiq.extraction.domain.Evidence;
import com.fomentiq.extraction.domain.ExtractedField;
import com.fomentiq.extraction.domain.ExtractedFieldRecorder;
import com.fomentiq.extraction.domain.ValidationStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaExtractionPersistence implements ExtractionPersistence {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ExtractedFieldRecorder fields;

    public JpaExtractionPersistence(JdbcTemplate jdbc, EntityManager entityManager, ExtractedFieldRecorder fields) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.fields = fields;
    }

    @Override
    @Transactional
    public void recordExecution(ExecutionRecord record) {
        jdbc.update("""
                INSERT INTO extraction_execution (id, ingestion_run_id, call_version_id, provider, model, schema_version,
                    input_digest, response_body, input_tokens, output_tokens, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (ingestion_run_id, call_version_id, schema_version, input_digest) DO NOTHING
                """, UUID.randomUUID(), record.runId(), record.callVersionId(), record.provider(), record.model(),
                record.schemaVersion(), record.inputDigest(), record.rawResponse(),
                record.inputTokens(), record.outputTokens(), record.status(), Timestamp.from(record.createdAt()));
    }

    @Override
    @Transactional
    public UUID recordField(FieldRecord record) {
        ValidationStatus status = ValidationStatus.valueOf(record.status());
        ExtractedFieldRecorder.RecordResult result = fields.recordWithResult(record.callVersionId(), record.name(),
                record.normalizedValue(),
                record.rawValue(), status, record.method(), record.createdAt(), record.runId(), record.confidence());
        entityManager.flush();
        ExtractedField field = result.field();
        if (result.created()) {
            for (EvidenceRecord evidence : record.evidence()) {
                entityManager.persist(new Evidence(field, evidence.documentVersionId(), evidence.sourceUrl(),
                        evidence.page(), evidence.section(), evidence.quote(), evidence.locatorJson()));
            }
        }
        return field.getId();
    }
}
