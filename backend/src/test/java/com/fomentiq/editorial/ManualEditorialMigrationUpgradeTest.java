package com.fomentiq.editorial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ManualEditorialMigrationUpgradeTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fomentiq")
            .withUsername("fomentiq")
            .withPassword("fomentiq");

    @Test
    void addsEditorialTablesToExistingFinepDatabaseWithoutChangingItsCall() {
        migrate(MigrationVersion.fromVersion("5"));
        JdbcTemplate jdbc = jdbc();
        UUID agencyId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        jdbc.update("INSERT INTO funding_agency(id,name,acronym) VALUES (?,?,?)",
                agencyId, "Financiadora de Estudos e Projetos", "FINEP-UPGRADE");
        jdbc.update("""
                INSERT INTO call_for_proposals(id,agency_id,external_id,title,status,detected_at,last_checked_at)
                VALUES (?,?,'751','PROINFRA 2024 Expansão','OPEN',?,?)
                """, callId, agencyId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        migrate(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_for_proposals WHERE id=? AND external_id='751'",
                Integer.class, callId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_opportunity", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE version='6' AND success=true", Integer.class)).isEqualTo(6);
    }

    private void migrate(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "fomentiq", "fomentiq")
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "fomentiq", "fomentiq"));
    }
}
