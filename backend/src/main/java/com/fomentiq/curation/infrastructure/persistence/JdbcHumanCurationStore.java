package com.fomentiq.curation.infrastructure.persistence;

import com.fomentiq.curation.application.HumanCurationStore;
import com.fomentiq.curation.application.ReviewQueueItem;
import com.fomentiq.curation.domain.HumanFieldRevision;
import com.fomentiq.curation.domain.ReviewStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHumanCurationStore implements HumanCurationStore {
    private static final RowMapper<HumanFieldRevision> REVISION = (rs, row) -> new HumanFieldRevision(
            rs.getObject("id", UUID.class), rs.getObject("call_id", UUID.class), rs.getString("field_name"),
            rs.getString("value_kind"), rs.getString("proposed_value"), rs.getString("previous_published_value"),
            rs.getObject("previous_revision_id", UUID.class), rs.getString("justification"),
            rs.getObject("document_id", UUID.class), rs.getObject("document_version_id", UUID.class),
            nullableInt(rs, "page"), rs.getString("section"), rs.getString("quote_fragment"),
            rs.getString("locator_json"), ReviewStatus.valueOf(rs.getString("status")), rs.getString("created_by"),
            instant(rs, "created_at"), rs.getString("decided_by"), instant(rs, "decided_at"),
            rs.getString("decision_reason"), rs.getLong("row_version"));
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcHumanCurationStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Page<ReviewQueueItem> reviewQueue(int page, int size) {
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", (long) page * size);
        String from = """
                FROM call_for_proposals c JOIN funding_agency a ON a.id = c.agency_id
                LEFT JOIN funding_program p ON p.id = c.program_id
                """;
        String condition = """
                WHERE EXISTS (SELECT 1 FROM call_version cv JOIN extracted_field ef ON ef.call_version_id=cv.id
                  WHERE cv.call_id=c.id AND cv.version_number=(SELECT max(lv.version_number) FROM call_version lv WHERE lv.call_id=c.id)
                  AND ef.validation_status <> 'VALIDATED')
                """;
        Long total = jdbc.queryForObject("SELECT count(*) " + from + condition, params, Long.class);
        List<ReviewQueueItem> rows = jdbc.query("""
                SELECT c.id, c.external_id, c.portal_entry_id, c.title, a.name agency_name, a.acronym agency_acronym,
                       COALESCE(array_agg(DISTINCT sp.code) FILTER (WHERE sp.code IS NOT NULL), ARRAY[]::text[]) sources,
                       COALESCE(array_agg(DISTINCT fa.acronym) FILTER (WHERE fa.acronym IS NOT NULL), ARRAY[]::text[]) agencies,
                       count(DISTINCT ef.field_name) review_fields
                """ + from + """
                LEFT JOIN call_source_reference csr ON csr.call_id=c.id LEFT JOIN source_publisher sp ON sp.id=csr.publisher_id
                LEFT JOIN call_funding_agency cfa ON cfa.call_id=c.id LEFT JOIN funding_agency fa ON fa.id=cfa.agency_id
                LEFT JOIN LATERAL (SELECT cv.id FROM call_version cv WHERE cv.call_id=c.id
                    ORDER BY cv.version_number DESC LIMIT 1) latest ON true
                LEFT JOIN extracted_field ef ON ef.call_version_id=latest.id AND ef.validation_status <> 'VALIDATED'
                """ + condition + """
                GROUP BY c.id, a.name, a.acronym ORDER BY c.title, c.id LIMIT :limit OFFSET :offset
                """, params, (rs, row) -> new ReviewQueueItem(rs.getObject("id", UUID.class), rs.getString("external_id"),
                rs.getString("portal_entry_id"), rs.getString("title"), rs.getString("agency_name"),
                rs.getString("agency_acronym"), array(rs, "sources"), array(rs, "agencies"),
                rs.getLong("review_fields")));
        return new Page<>(List.copyOf(rows), page, size, total == null ? 0 : total);
    }

    @Override public boolean opportunityExists(UUID callId) {
        return count("SELECT count(*) FROM call_for_proposals WHERE id=:id", callId) > 0;
    }
    @Override public boolean documentVersionBelongsToOpportunity(UUID callId, UUID documentId, UUID versionId) {
        var params = new MapSqlParameterSource().addValue("call", callId).addValue("document", documentId)
                .addValue("version", versionId);
        return jdbc.queryForObject("SELECT count(*) FROM call_document_version v JOIN call_document d ON d.id=v.document_id "
                + "WHERE d.call_id=:call AND d.id=:document AND v.id=:version", params, Integer.class) > 0;
    }
    @Override public void lockOpportunity(UUID callId) {
        jdbc.query("SELECT id FROM call_for_proposals WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource("id", callId), (rs, row) -> rs.getObject(1));
    }
    @Override public Optional<HumanFieldRevision> latestApproved(UUID callId, String field) {
        return jdbc.query("SELECT * FROM curated_field_revision WHERE call_id=:call AND field_name=:field "
                + "AND status='APPROVED' ORDER BY decided_at DESC,id DESC LIMIT 1",
                params(callId, field), REVISION).stream().findFirst();
    }
    @Override public Optional<String> basePublishedValueJson(UUID callId, String field) {
        List<String> values = jdbc.query("""
                SELECT ef.normalized_value FROM call_version cv JOIN extracted_field ef ON ef.call_version_id=cv.id
                WHERE cv.call_id=:call AND cv.version_number=(SELECT max(lv.version_number) FROM call_version lv WHERE lv.call_id=:call)
                  AND ef.field_name=:field ORDER BY ef.created_at DESC,ef.id DESC LIMIT 1
                """, params(callId, field), (rs,row)->rs.getString(1));
        if (!values.isEmpty()) return Optional.ofNullable(values.getFirst());
        String column = switch (field) {
            case "title" -> "title";
            case "program" -> "program_id";
            case "instrumentType" -> "instrument_type";
            case "status" -> "status";
            case "deadline" -> "deadline";
            default -> null;
        };
        if (column == null) return Optional.empty();
        List<String> fallback = jdbc.query("SELECT CAST(" + column + " AS text) "
                + "FROM call_for_proposals WHERE id=:call",
                new MapSqlParameterSource("call", callId), (rs,row)->rs.getString(1));
        return fallback.isEmpty() ? Optional.empty() : Optional.ofNullable(fallback.getFirst());
    }
    @Override public HumanFieldRevision create(PendingRevision r) {
        UUID id = UUID.randomUUID();
        var p = new MapSqlParameterSource().addValue("id", id).addValue("call", r.callId())
                .addValue("field", r.fieldName()).addValue("kind", r.valueKind())
                .addValue("value", r.proposedValueJson()).addValue("previous", r.previousPublishedValueJson())
                .addValue("previousId", r.previousRevisionId()).addValue("justification", r.justification())
                .addValue("document", r.documentId()).addValue("docVersion", r.documentVersionId())
                .addValue("page", r.page()).addValue("section", r.section()).addValue("quote", r.quoteFragment())
                .addValue("locator", r.locatorJson()).addValue("actor", r.createdBy())
                .addValue("created", java.sql.Timestamp.from(r.createdAt()));
        jdbc.update("""
                INSERT INTO curated_field_revision(id, call_id, field_name, value_kind, proposed_value,
                    previous_published_value, previous_revision_id, justification, document_id,
                    document_version_id, page, section, quote_fragment, locator_json, status, created_by, created_at)
                VALUES(:id, :call, :field, :kind, CAST(:value AS jsonb), CAST(:previous AS jsonb),
                    :previousId, :justification, :document, :docVersion, :page, :section, :quote,
                    CAST(:locator AS jsonb), 'PENDING', :actor, :created)
                """,p);
        return findRevision(id).orElseThrow();
    }
    @Override public Optional<HumanFieldRevision> findRevision(UUID id) {
        return jdbc.query("SELECT * FROM curated_field_revision WHERE id=:id",
                new MapSqlParameterSource("id", id), REVISION).stream().findFirst();
    }
    @Override public Page<HumanFieldRevision> revisionHistory(UUID callId, String field, int page, int size) {
        MapSqlParameterSource query = params(callId, field).addValue("limit", size)
                .addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM curated_field_revision WHERE call_id=:call "
                + "AND field_name=:field", query, Long.class);
        List<HumanFieldRevision> rows = jdbc.query("SELECT * FROM curated_field_revision WHERE call_id=:call "
                + "AND field_name=:field ORDER BY created_at, id LIMIT :limit OFFSET :offset", query, REVISION);
        return new Page<>(List.copyOf(rows), page, size, total == null ? 0 : total);
    }
    @Override public boolean transition(UUID id, long expected, ReviewStatus status, String actor, Instant at, String reason) {
        return jdbc.update("UPDATE curated_field_revision SET status=:status, decided_by=:actor, decided_at=:at, "
                + "decision_reason=:reason, row_version=row_version+1 "
                + "WHERE id=:id AND row_version=:version AND status='PENDING'",
                new MapSqlParameterSource().addValue("status", status.name()).addValue("actor", actor)
                        .addValue("at", java.sql.Timestamp.from(at)).addValue("reason", reason).addValue("id", id)
                        .addValue("version", expected)) == 1;
    }
    private int count(String sql, UUID id) {
        Integer value = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Integer.class);
        return value == null ? 0 : value;
    }
    private static MapSqlParameterSource params(UUID call, String field) {
        return new MapSqlParameterSource().addValue("call", call).addValue("field", field);
    }
    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int value = rs.getInt(col);
        return rs.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet rs, String col) throws SQLException {
        var timestamp = rs.getTimestamp(col);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static List<String> array(ResultSet rs, String col) throws SQLException {
        var value = rs.getArray(col);
        return value == null ? List.of() : List.of((String[]) value.getArray());
    }
}
