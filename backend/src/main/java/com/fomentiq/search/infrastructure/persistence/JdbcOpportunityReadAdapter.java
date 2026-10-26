package com.fomentiq.search.infrastructure.persistence;

import com.fomentiq.search.application.OpportunityReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL read model adapter. Queries return bounded projections and never serialize JPA entities. */
@Repository
public class JdbcOpportunityReadAdapter implements OpportunityReadPort {
    private static final String BASE = """
            FROM call_for_proposals c
            JOIN funding_agency a ON a.id = c.agency_id
            LEFT JOIN funding_program p ON p.id = c.program_id
            LEFT JOIN LATERAL (SELECT cv.id, cv.version_number FROM call_version cv
                WHERE cv.call_id = c.id ORDER BY cv.version_number DESC LIMIT 1) current_version ON TRUE
            LEFT JOIN LATERAL (SELECT ef.normalized_value FROM extracted_field ef
                WHERE ef.call_version_id = current_version.id AND ef.field_name = 'deadline'
                ORDER BY ef.created_at DESC, ef.id DESC LIMIT 1) deadline_field ON TRUE
            """;
    private static final RowMapper<OpportunitySummary> SUMMARY = (rs, row) -> new OpportunitySummary(
            rs.getObject("id", UUID.class), rs.getString("external_id"), rs.getString("portal_entry_id"),
            rs.getString("title"), rs.getString("agency_name"), rs.getString("agency_acronym"),
            rs.getString("program_name"), rs.getString("status"), localDate(rs, "deadline"),
            rs.getString("instrument_type"));
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOpportunityReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Page<OpportunitySummary> search(Search search, int page, int size) {
        MapSqlParameterSource params = parameters(search).addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = where(search);
        Long total = jdbc.queryForObject("SELECT count(*) " + BASE + where, params, Long.class);
        List<OpportunitySummary> content = jdbc.query("""
                SELECT c.id, c.external_id, c.portal_entry_id, c.title, a.name AS agency_name,
                       a.acronym AS agency_acronym, p.name AS program_name, c.status,
                       COALESCE(c.deadline, CASE WHEN pg_input_is_valid(substring(deadline_field.normalized_value, 1, 10), 'date')
                           THEN substring(deadline_field.normalized_value, 1, 10)::date END)
                           AS deadline, c.instrument_type
                """ + BASE + where + " ORDER BY c.title, c.id LIMIT :limit OFFSET :offset", params, SUMMARY);
        return new Page<>(List.copyOf(content), page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<OpportunityDetail> findOpportunity(UUID callId) {
        List<OpportunityDetail> results = jdbc.query("""
                SELECT c.id, c.external_id, c.portal_entry_id, c.title, a.name AS agency_name,
                       a.acronym AS agency_acronym, p.name AS program_name, c.status,
                       COALESCE(c.deadline, CASE WHEN pg_input_is_valid(substring(deadline_field.normalized_value, 1, 10), 'date')
                           THEN substring(deadline_field.normalized_value, 1, 10)::date END)
                           AS deadline, c.instrument_type, c.summary, c.official_url, current_version.version_number
                """ + BASE + " WHERE c.id = :callId", new MapSqlParameterSource("callId", callId),
                (rs, row) -> new OpportunityDetail(summary(rs), rs.getString("summary"),
                        rs.getString("official_url"), nullableInt(rs, "version_number"), fields(callId)));
        return results.stream().findFirst();
    }

    private List<FieldView> fields(UUID callId) {
        return jdbc.query("""
                SELECT ef.id, ef.field_name, ef.normalized_value, ef.raw_value, ef.validation_status, ef.created_at
                FROM call_version cv JOIN extracted_field ef ON ef.call_version_id = cv.id
                WHERE cv.call_id = :callId AND cv.version_number =
                    (SELECT max(latest.version_number) FROM call_version latest WHERE latest.call_id = cv.call_id)
                  AND ef.id = (SELECT current_field.id FROM extracted_field current_field
                      WHERE current_field.call_version_id = ef.call_version_id
                        AND current_field.field_name = ef.field_name
                      ORDER BY current_field.created_at DESC, current_field.id DESC LIMIT 1)
                ORDER BY ef.field_name
                """, new MapSqlParameterSource("callId", callId), (rs, row) -> {
            UUID fieldId = rs.getObject("id", UUID.class);
            return new FieldView(rs.getString("field_name"), rs.getString("normalized_value"),
                    rs.getString("raw_value"), rs.getString("validation_status"), instant(rs, "created_at"),
                    evidenceForField(callId, fieldId));
        });
    }

    private List<EvidenceView> evidenceForField(UUID callId, UUID fieldId) {
        return jdbc.query("""
                SELECT e.id, e.extracted_field_id, f.field_name, e.document_version_id, e.source_url,
                       e.page, e.section, e.quote_fragment, e.locator_json::text AS locator_json
                FROM evidence e JOIN extracted_field f ON f.id = e.extracted_field_id
                LEFT JOIN call_document_version dv ON dv.id = e.document_version_id
                LEFT JOIN call_document d ON d.id = dv.document_id
                WHERE e.extracted_field_id = :fieldId
                  AND (e.document_version_id IS NULL OR d.call_id = :callId)
                ORDER BY e.page NULLS LAST, e.id
                """, new MapSqlParameterSource().addValue("fieldId", fieldId).addValue("callId", callId), EVIDENCE);
    }

    @Override
    public Page<CallVersionView> callVersions(UUID callId, int page, int size) {
        return pageQuery(callId, page, size, "call_version", "call_id", "version_number DESC",
                "SELECT id, version_number, content_hash, detected_at, supersedes_version_id FROM call_version",
                (rs, row) -> new CallVersionView(rs.getObject("id", UUID.class), rs.getInt("version_number"),
                        rs.getString("content_hash"), instant(rs, "detected_at"),
                        rs.getObject("supersedes_version_id", UUID.class)));
    }

    @Override
    public Page<DocumentView> documents(UUID callId, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("callId", callId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM call_document WHERE call_id = :callId", params, Long.class);
        List<DocumentView> rows = jdbc.query("""
                SELECT d.id, d.external_id, d.document_type, d.title, d.source_url,
                       count(v.id) AS version_count, max(v.downloaded_at) AS latest_downloaded_at
                FROM call_document d LEFT JOIN call_document_version v ON v.document_id = d.id
                WHERE d.call_id = :callId GROUP BY d.id ORDER BY d.title, d.id LIMIT :limit OFFSET :offset
                """, params, (rs, row) -> new DocumentView(rs.getObject("id", UUID.class),
                rs.getString("external_id"), rs.getString("document_type"), rs.getString("title"),
                rs.getString("source_url"), rs.getLong("version_count"), instant(rs, "latest_downloaded_at")));
        return new Page<>(List.copyOf(rows), page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<Page<DocumentVersionView>> documentVersions(UUID callId, UUID documentId, int page, int size) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM call_document WHERE id = :documentId AND call_id = :callId",
                new MapSqlParameterSource().addValue("callId", callId).addValue("documentId", documentId), Integer.class);
        if (exists == null || exists == 0) return Optional.empty();
        return Optional.of(pageQuery(documentId, page, size, "call_document_version", "document_id", "downloaded_at DESC, id",
                "SELECT id, content_hash, media_type, source_url, downloaded_at FROM call_document_version",
                (rs, row) -> new DocumentVersionView(rs.getObject("id", UUID.class), rs.getString("content_hash"),
                        rs.getString("media_type"), rs.getString("source_url"), instant(rs, "downloaded_at"))));
    }

    @Override
    public Optional<Page<EvidenceView>> evidence(UUID callId, String fieldName, int page, int size) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM call_for_proposals WHERE id = :callId",
                new MapSqlParameterSource("callId", callId), Integer.class);
        if (exists == null || exists == 0) return Optional.empty();
        MapSqlParameterSource params = new MapSqlParameterSource("callId", callId).addValue("fieldName", fieldName)
                .addValue("limit", size).addValue("offset", (long) page * size);
        String filter = " AND (CAST(:fieldName AS varchar) IS NULL OR f.field_name = :fieldName)"
                + " AND (e.document_version_id IS NULL OR EXISTS (SELECT 1 FROM call_document_version dv"
                + " JOIN call_document d ON d.id = dv.document_id WHERE dv.id = e.document_version_id"
                + " AND d.call_id = :callId))";
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM evidence e JOIN extracted_field f ON f.id = e.extracted_field_id
                JOIN call_version cv ON cv.id = f.call_version_id WHERE cv.call_id = :callId
                """ + filter, params, Long.class);
        List<EvidenceView> rows = jdbc.query("""
                SELECT e.id, e.extracted_field_id, f.field_name, e.document_version_id, e.source_url,
                       e.page, e.section, e.quote_fragment, e.locator_json::text AS locator_json
                FROM evidence e JOIN extracted_field f ON f.id = e.extracted_field_id
                JOIN call_version cv ON cv.id = f.call_version_id WHERE cv.call_id = :callId
                """ + filter + " ORDER BY f.field_name, e.page NULLS LAST, e.id LIMIT :limit OFFSET :offset",
                params, EVIDENCE);
        return Optional.of(new Page<>(List.copyOf(rows), page, size, total == null ? 0 : total));
    }

    private <T> Page<T> pageQuery(UUID parent, int page, int size, String table, String parentColumn,
                                  String order, String select, RowMapper<T> mapper) {
        MapSqlParameterSource params = new MapSqlParameterSource("parent", parent)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + parentColumn + " = :parent",
                params, Long.class);
        List<T> content = jdbc.query(select + " WHERE " + parentColumn + " = :parent ORDER BY " + order
                + " LIMIT :limit OFFSET :offset", params, mapper);
        return new Page<>(List.copyOf(content), page, size, total == null ? 0 : total);
    }

    private MapSqlParameterSource parameters(Search search) {
        return new MapSqlParameterSource().addValue("query", like(search.query()))
                .addValue("status", search.status()).addValue("deadlineFrom", search.deadlineFrom())
                .addValue("deadlineTo", search.deadlineTo()).addValue("instrument", like(search.instrumentType()))
                .addValue("region", like(search.region())).addValue("theme", like(search.theme()));
    }

    private String where(Search search) {
        return """
                WHERE (CAST(:status AS varchar) IS NULL OR c.status = :status)
                  AND (CAST(:deadlineFrom AS date) IS NULL OR COALESCE(c.deadline,
                       CASE WHEN pg_input_is_valid(substring(deadline_field.normalized_value, 1, 10), 'date')
                       THEN substring(deadline_field.normalized_value, 1, 10)::date END) >= :deadlineFrom)
                  AND (CAST(:deadlineTo AS date) IS NULL OR COALESCE(c.deadline,
                       CASE WHEN pg_input_is_valid(substring(deadline_field.normalized_value, 1, 10), 'date')
                       THEN substring(deadline_field.normalized_value, 1, 10)::date END) <= :deadlineTo)
                  AND (CAST(:instrument AS varchar) IS NULL OR c.instrument_type ILIKE :instrument)
                  AND (CAST(:region AS varchar) IS NULL OR EXISTS (SELECT 1 FROM call_version fcv JOIN extracted_field rf
                       ON rf.call_version_id = fcv.id WHERE fcv.call_id = c.id
                       AND fcv.version_number = (SELECT max(lcv.version_number) FROM call_version lcv WHERE lcv.call_id = c.id)
                       AND rf.field_name = 'regions'
                       AND rf.normalized_value ILIKE :region))
                  AND (CAST(:theme AS varchar) IS NULL OR EXISTS (SELECT 1 FROM call_version fcv JOIN extracted_field tf
                       ON tf.call_version_id = fcv.id WHERE fcv.call_id = c.id
                       AND fcv.version_number = (SELECT max(lcv.version_number) FROM call_version lcv WHERE lcv.call_id = c.id)
                       AND tf.field_name = 'technologyThemes'
                       AND tf.normalized_value ILIKE :theme))
                  AND (CAST(:query AS varchar) IS NULL OR c.title ILIKE :query OR c.summary ILIKE :query OR a.name ILIKE :query
                       OR a.acronym ILIKE :query OR p.name ILIKE :query OR EXISTS (SELECT 1 FROM call_version qcv
                           JOIN extracted_field qf ON qf.call_version_id = qcv.id
                           WHERE qcv.call_id = c.id
                             AND qcv.version_number = (SELECT max(lcv.version_number) FROM call_version lcv WHERE lcv.call_id = c.id)
                             AND qf.normalized_value ILIKE :query))
                """;
    }

    private OpportunitySummary summary(ResultSet rs) throws SQLException {
        return new OpportunitySummary(rs.getObject("id", UUID.class), rs.getString("external_id"),
                rs.getString("portal_entry_id"), rs.getString("title"), rs.getString("agency_name"),
                rs.getString("agency_acronym"), rs.getString("program_name"), rs.getString("status"),
                localDate(rs, "deadline"), rs.getString("instrument_type"));
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim().replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static final RowMapper<EvidenceView> EVIDENCE = (rs, row) -> new EvidenceView(
            rs.getObject("id", UUID.class), rs.getObject("extracted_field_id", UUID.class), rs.getString("field_name"),
            rs.getObject("document_version_id", UUID.class), rs.getString("source_url"), nullableInt(rs, "page"),
            rs.getString("section"), rs.getString("quote_fragment"), rs.getString("locator_json"));
}
