package com.fomentiq.documents.infrastructure.persistence;

import com.fomentiq.documents.application.PreservedDocumentVersionQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Resolves storage metadata only after proving the full opportunity/document/version relationship. */
@Repository
public class JdbcPreservedDocumentVersionQuery implements PreservedDocumentVersionQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPreservedDocumentVersionQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VersionObject> find(UUID callId, UUID documentId, UUID versionId) {
        List<VersionObject> rows = jdbc.query("""
                SELECT v.storage_key, v.content_hash, v.media_type
                FROM call_document_version v
                JOIN call_document d ON d.id = v.document_id
                WHERE d.call_id = :callId AND d.id = :documentId AND v.id = :versionId
                """, new MapSqlParameterSource().addValue("callId", callId)
                .addValue("documentId", documentId).addValue("versionId", versionId),
                (rs, row) -> new VersionObject(rs.getString("storage_key"), rs.getString("content_hash"),
                        rs.getString("media_type")));
        return rows.stream().findFirst();
    }
}
