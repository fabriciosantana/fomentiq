package com.fomentiq.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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
class MultiSourceUpgradeMigrationIntegrationTest {
    private static final UUID AGENCY = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID CALL = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID RUN = UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT = UUID.fromString("14000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("upgrade_probe").withUsername("fomentiq").withPassword("fomentiq");

    @Test
    void upgradesV3FinepDataAndBackfillsSourceAndAgencyIdentityWithoutChangingTheCall() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Timestamp now = Timestamp.from(java.time.Instant.parse("2026-10-26T12:00:00Z"));
        jdbc.update("INSERT INTO funding_agency (id,name,acronym,official_url) VALUES (?,?,?,?)",
                AGENCY, "Financiadora de Estudos e Projetos", "FINEP", "https://www.finep.gov.br");
        jdbc.update("INSERT INTO call_for_proposals "
                        + "(id,agency_id,external_id,portal_entry_id,title,status,official_url,detected_at,last_checked_at) "
                        + "VALUES (?,?,?,?,?,'CLOSED',?,?,?)", CALL, AGENCY, "751", "751092",
                "PROINFRA 2024 Expansão", "https://www.finep.gov.br/calls/751", now, now);
        jdbc.update("INSERT INTO ingestion_run (id,source_type,started_at,status) VALUES (?,?,?,'SUCCEEDED')",
                RUN, "FINEP", now);
        jdbc.update("INSERT INTO source_snapshot "
                        + "(id,call_id,ingestion_run_id,artifact_type,source_url,content_hash,storage_key,media_type,captured_at) "
                        + "VALUES (?,?,?,'CALL',?,?,?,'application/json',?)", UUID.randomUUID(), CALL, RUN,
                "https://www.finep.gov.br/calls/751", "a".repeat(64), "snapshot/751", now);
        jdbc.update("INSERT INTO call_document (id,call_id,external_id,document_type,title,source_url) "
                        + "VALUES (?,?,?,'NOTICE',?,?)", DOCUMENT, CALL, "notice-751", "Edital",
                "https://www.finep.gov.br/notice.pdf");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_for_proposals WHERE id = ? AND external_id = '751' "
                + "AND portal_entry_id = '751092'", Integer.class, CALL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_source_reference r JOIN source_publisher p "
                + "ON p.id = r.publisher_id WHERE r.call_id = ? AND p.code = 'FINEP' AND r.external_id = '751' "
                + "AND r.portal_entry_id = '751092'", Integer.class, CALL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_snapshot s JOIN call_source_reference r "
                + "ON r.id = s.source_reference_id AND r.call_id = s.call_id WHERE s.call_id = ?", Integer.class, CALL))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_document d JOIN call_source_reference r "
                + "ON r.id = d.source_reference_id AND r.call_id = d.call_id WHERE d.id = ?", Integer.class, DOCUMENT))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT a.acronym FROM call_funding_agency cfa JOIN funding_agency a "
                + "ON a.id = cfa.agency_id WHERE cfa.call_id = ? AND cfa.is_primary", String.class, CALL))
                .isEqualTo("FINEP");

        UUID cnpqAgency = jdbc.queryForObject("SELECT id FROM funding_agency WHERE acronym = 'CNPq'", UUID.class);
        UUID sameExternalIdCall = UUID.fromString("12000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO call_for_proposals (id,agency_id,external_id,title,status,official_url,detected_at,last_checked_at) "
                        + "VALUES (?,?,?,'Synthetic identity isolation check','OPEN',?,?,?)",
                sameExternalIdCall, cnpqAgency, "751", "https://www.gov.br/cnpq/test/751", now, now);
        UUID cnpqPublisher = jdbc.queryForObject("SELECT id FROM source_publisher WHERE code = 'CNPQ'", UUID.class);
        jdbc.update("INSERT INTO call_source_reference (id,call_id,publisher_id,external_id,canonical_url) "
                        + "VALUES (?,?,?,?,?)", UUID.randomUUID(), sameExternalIdCall, cnpqPublisher, "751",
                "https://www.gov.br/cnpq/test/751");
        UUID alternatePublisher = UUID.randomUUID();
        jdbc.update("INSERT INTO source_publisher (id, code, name, official_url) VALUES (?,?,?,?)",
                alternatePublisher, "CNPQ-ALT", "Alternate public catalog", "https://www.gov.br/cnpq");
        UUID sameAgencyCall = UUID.fromString("12000000-0000-0000-0000-000000000003");
        jdbc.update("INSERT INTO call_for_proposals (id,agency_id,external_id,title,status,official_url,detected_at,last_checked_at) "
                        + "VALUES (?,?,?,'Synthetic same-agency source identity','OPEN',?,?,?)",
                sameAgencyCall, cnpqAgency, "751", "https://www.gov.br/cnpq/alternate/751", now, now);
        jdbc.update("INSERT INTO call_source_reference (id,call_id,publisher_id,external_id,canonical_url) "
                        + "VALUES (?,?,?,?,?)", UUID.randomUUID(), sameAgencyCall, alternatePublisher, "751",
                "https://www.gov.br/cnpq/alternate/751");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_for_proposals c JOIN call_source_reference r "
                + "ON r.call_id = c.id WHERE r.external_id = '751'", Integer.class)).isEqualTo(3);
    }
}
