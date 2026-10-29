package com.fomentiq.editorial.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fomentiq.editorial.application.ManualOpportunity;
import com.fomentiq.editorial.application.ManualOpportunityStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcManualOpportunityStore implements ManualOpportunityStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcManualOpportunityStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Page<ManualOpportunity> list(String query, boolean publishedOnly, int page, int size) {
        var params = new MapSqlParameterSource()
                .addValue("query", "%" + query.toLowerCase() + "%")
                .addValue("published", publishedOnly)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String predicate = "(:published=false OR editorial_state='PUBLISHED') "
                + "AND ((:published=true AND (lower(published_content->>'title') LIKE :query "
                + "OR lower(published_content->>'institution') LIKE :query)) "
                + "OR (:published=false AND (lower(title) LIKE :query OR lower(institution) LIKE :query)))";
        Long count = jdbc.queryForObject("SELECT count(*) FROM manual_opportunity WHERE " + predicate,
                params, Long.class);
        List<UUID> ids = jdbc.query("SELECT id FROM manual_opportunity WHERE " + predicate
                + " ORDER BY CASE WHEN :published THEN lower(published_content->>'title') "
                + "ELSE lower(title) END,id LIMIT :limit OFFSET :offset", params,
                (rs, row) -> rs.getObject(1, UUID.class));
        List<ManualOpportunity> items = ids.stream()
                .map(id -> find(id, publishedOnly).orElseThrow())
                .toList();
        return new Page<>(items, page, size, count == null ? 0 : count);
    }

    @Override
    public Optional<ManualOpportunity> find(UUID id, boolean publishedOnly) {
        String sql = "SELECT *, CASE WHEN :published THEN published_content "
                + "ELSE editable_content END AS response_content "
                + "FROM manual_opportunity WHERE id=:id "
                + "AND (:published=false OR editorial_state='PUBLISHED')";
        var params = new MapSqlParameterSource().addValue("id", id).addValue("published", publishedOnly);
        return jdbc.query(sql, params, this::map).stream().findFirst();
    }

    @Override
    public ManualOpportunity create(ManualOpportunity item, String actor) {
        String sql = """
                INSERT INTO manual_opportunity(id,title,institution,summary,deadline,status,official_url,
                    editorial_state,editable_content,created_by,updated_by,created_at,updated_at,row_version)
                VALUES(:id,:title,:institution,:summary,:deadline,:status,:url,'DRAFT',CAST(:content AS jsonb),
                    :actor,:actor,:now,:now,0)
                """;
        Instant now = Instant.now();
        jdbc.update(sql, itemParameters(item, actor, json(item))
                .addValue("now", java.sql.Timestamp.from(now)));
        audit(item.id(), "CREATED", 0, actor, now);
        return find(item.id(), false).orElseThrow();
    }

    @Override
    public ManualOpportunity save(ManualOpportunity item, long expectedVersion, String actor) {
        String sql = """
                UPDATE manual_opportunity SET title=:title,institution=:institution,summary=:summary,
                    deadline=:deadline,status=:status,official_url=:url,
                    editable_content=CAST(:content AS jsonb),updated_by=:actor,updated_at=:now,
                    row_version=row_version+1 WHERE id=:id AND row_version=:expected
                """;
        Instant now = Instant.now();
        var params = itemParameters(item, actor, json(item))
                .addValue("expected", expectedVersion).addValue("now", java.sql.Timestamp.from(now));
        ensureOneChanged(jdbc.update(sql, params), item.id());
        audit(item.id(), "SAVED", expectedVersion + 1, actor, now);
        return find(item.id(), false).orElseThrow();
    }

    @Override
    public ManualOpportunity publish(UUID id, long expectedVersion, String actor) {
        ManualOpportunity current = find(id, false)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sql = """
                UPDATE manual_opportunity SET editorial_state='PUBLISHED',
                    published_content=CAST(:content AS jsonb),updated_by=:actor,updated_at=:now,
                    row_version=row_version+1 WHERE id=:id AND row_version=:expected
                """;
        Instant now = Instant.now();
        var params = new MapSqlParameterSource().addValue("content", json(current))
                .addValue("actor", actor).addValue("now", java.sql.Timestamp.from(now)).addValue("id", id)
                .addValue("expected", expectedVersion);
        ensureOneChanged(jdbc.update(sql, params), id);
        audit(id, "PUBLISHED", expectedVersion + 1, actor, now);
        return find(id, false).orElseThrow();
    }

    @Override
    public ManualOpportunity unpublish(UUID id, long expectedVersion, String actor) {
        String sql = """
                UPDATE manual_opportunity SET editorial_state='DRAFT',published_content=NULL,
                    updated_by=:actor,updated_at=:now,row_version=row_version+1
                WHERE id=:id AND row_version=:expected
                """;
        Instant now = Instant.now();
        var params = new MapSqlParameterSource().addValue("actor", actor)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("id", id).addValue("expected", expectedVersion);
        ensureOneChanged(jdbc.update(sql, params), id);
        audit(id, "UNPUBLISHED", expectedVersion + 1, actor, now);
        return find(id, false).orElseThrow();
    }

    private ManualOpportunity map(ResultSet rs, int row) throws SQLException {
        try {
            var data = mapper.readTree(rs.getString("response_content"));
            List<ManualOpportunity.Section> sections = mapper.convertValue(
                    data.path("sections"), new TypeReference<>() { });
            String deadlineValue = data.path("deadline").isNull()
                    ? null : data.path("deadline").asText();
            java.time.LocalDate deadline = deadlineValue == null
                    ? null : java.time.LocalDate.parse(deadlineValue);
            return new ManualOpportunity(rs.getObject("id", UUID.class), data.path("title").asText(),
                    data.path("institution").asText(), data.path("summary").asText(), deadline,
                    data.path("status").asText(), data.path("officialUrl").isNull()
                            ? null : data.path("officialUrl").asText(), rs.getString("editorial_state"), sections,
                    rs.getLong("row_version"), rs.getString("created_by"), rs.getString("updated_by"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored editorial content is invalid", exception);
        }
    }

    private String json(ManualOpportunity item) {
        try {
            return mapper.writeValueAsString(new Content(item.title(), item.institution(), item.summary(),
                    item.deadline(), item.status(), item.officialUrl(), item.sections()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize editorial content", exception);
        }
    }

    private MapSqlParameterSource itemParameters(ManualOpportunity item, String actor, String content) {
        return new MapSqlParameterSource().addValue("id", item.id()).addValue("title", item.title())
                .addValue("institution", item.institution()).addValue("summary", item.summary())
                .addValue("deadline", item.deadline()).addValue("status", item.status())
                .addValue("url", item.officialUrl()).addValue("content", content).addValue("actor", actor);
    }

    private void audit(UUID id, String action, long version, String actor, Instant at) {
        String sql = "INSERT INTO manual_opportunity_audit_event"
                + "(id,opportunity_id,action,aggregate_version,actor,occurred_at)"
                + " VALUES(:event,:id,:action,:version,:actor,:at)";
        var params = new MapSqlParameterSource().addValue("event", UUID.randomUUID()).addValue("id", id)
                .addValue("action", action).addValue("version", version).addValue("actor", actor)
                .addValue("at", java.sql.Timestamp.from(at));
        jdbc.update(sql, params);
    }

    private void ensureOneChanged(int changed, UUID id) {
        if (changed == 1) {
            return;
        }
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM manual_opportunity WHERE id=:id",
                new MapSqlParameterSource("id", id), Integer.class);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A oportunidade foi alterada em outra sessão. Recarregue e tente novamente.");
    }

    private record Content(String title, String institution, String summary,
            java.time.LocalDate deadline, String status, String officialUrl,
            List<ManualOpportunity.Section> sections) { }
}
