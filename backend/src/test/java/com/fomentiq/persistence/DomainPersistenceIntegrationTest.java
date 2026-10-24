package com.fomentiq.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fomentiq.documents.domain.CallDocument;
import com.fomentiq.documents.domain.CallDocumentRepository;
import com.fomentiq.documents.domain.CallDocumentType;
import com.fomentiq.documents.domain.CallDocumentVersion;
import com.fomentiq.documents.domain.CallDocumentVersionRepository;
import com.fomentiq.extraction.domain.Evidence;
import com.fomentiq.extraction.domain.EvidenceRepository;
import com.fomentiq.extraction.domain.ExtractedField;
import com.fomentiq.extraction.domain.ExtractedFieldRecorder;
import com.fomentiq.extraction.domain.ExtractedFieldRepository;
import com.fomentiq.extraction.domain.ValidationStatus;
import com.fomentiq.funding.domain.CallForProposals;
import com.fomentiq.funding.domain.CallForProposalsRepository;
import com.fomentiq.funding.domain.CallStatus;
import com.fomentiq.funding.domain.CallVersion;
import com.fomentiq.funding.domain.CallVersionRepository;
import com.fomentiq.funding.domain.FundingAgency;
import com.fomentiq.funding.domain.FundingAgencyRepository;
import com.fomentiq.funding.domain.FundingProgram;
import com.fomentiq.funding.domain.FundingProgramRepository;
import com.fomentiq.ingestion.domain.IngestionRun;
import com.fomentiq.ingestion.domain.IngestionRunRepository;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class DomainPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fomentiq")
            .withUsername("fomentiq")
            .withPassword("fomentiq");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired FundingAgencyRepository agencies;
    @Autowired FundingProgramRepository programs;
    @Autowired CallForProposalsRepository calls;
    @Autowired CallVersionRepository callVersions;
    @Autowired CallDocumentRepository documents;
    @Autowired CallDocumentVersionRepository documentVersions;
    @Autowired IngestionRunRepository runs;
    @Autowired ExtractedFieldRepository fields;
    @Autowired EvidenceRepository evidence;
    @Autowired ExtractedFieldRecorder recorder;

    @Test
    void migratesCleanDatabaseAndPersistsVersionedDomainWithRunAttribution() {
        Instant timestamp = Instant.parse("2026-10-23T12:30:00Z");
        FundingAgency agency = agencies.saveAndFlush(new FundingAgency(
                "Financiadora de Estudos e Projetos", "FINEP", "https://finep.gov.br"));
        FundingProgram program = programs.saveAndFlush(new FundingProgram(
                agency, "PROINFRA", "Infraestrutura de pesquisa", new String[]{"SUBVENTION"}));
        CallForProposals call = calls.saveAndFlush(new CallForProposals(agency, program, "751", "PROINFRA 2024 Expansão",
                "Resumo", "SUBVENTION", CallStatus.CLOSED, LocalDate.of(2024, 12, 12), null,
                LocalDate.of(2025, 3, 31), "https://finep.gov.br/call/751", timestamp, timestamp));
        CallVersion version = callVersions.saveAndFlush(new CallVersion(call, 1, "a".repeat(64), timestamp, null));
        CallDocument document = documents.saveAndFlush(new CallDocument(call.getId(), "legacy-notice", CallDocumentType.NOTICE,
                "Edital", "https://finep.gov.br/call/751/notice.pdf"));
        CallDocumentVersion documentVersion = documentVersions.saveAndFlush(new CallDocumentVersion(document,
                "b".repeat(64), "calls/751/notice.pdf", "application/pdf", document.getSourceUrl(), timestamp, null));
        IngestionRun firstRun = runs.saveAndFlush(new IngestionRun("FINEP", timestamp, IngestionRunStatus.SUCCEEDED));

        ExtractedField first = recorder.record(version.getId(), "deadline", "2025-03-31", "31/03/2025",
                ValidationStatus.EXTRACTED, "fixture", timestamp, firstRun.getId(), new BigDecimal("0.9000"));
        Evidence source = evidence.saveAndFlush(new Evidence(first, documentVersion.getId(), document.getSourceUrl(),
                4, "Cronograma", "31/03/2025", "{\"paragraph\": 12}"));
        runs.flush();

        IngestionRun repeatedRun = runs.saveAndFlush(new IngestionRun("FINEP", timestamp.plusSeconds(10), IngestionRunStatus.SUCCEEDED));
        ExtractedField repeated = recorder.record(version.getId(), "deadline", "2025-03-31", "31/03/2025",
                ValidationStatus.EXTRACTED, "fixture", timestamp.plusSeconds(10), repeatedRun.getId(), new BigDecimal("0.9000"));
        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(repeated.getCreatedAt()).isEqualTo(timestamp);
        assertThat(repeated.getIngestionRunId()).isEqualTo(firstRun.getId());

        IngestionRun changedRun = runs.saveAndFlush(new IngestionRun("FINEP", timestamp.plusSeconds(20), IngestionRunStatus.SUCCEEDED));
        ExtractedField changed = recorder.record(version.getId(), "deadline", "2025-04-07", "07/04/2025",
                ValidationStatus.VALIDATED, "fixture", timestamp.plusSeconds(20), changedRun.getId(), new BigDecimal("0.9000"));
        assertThat(changed.getId()).isNotEqualTo(first.getId());
        assertThat(fields.count()).isEqualTo(2);
        assertThat(evidence.findById(source.getId())).isPresent();
        assertThat(agencies.findByAcronym("FINEP")).isPresent();
        assertThat(calls.findByAgencyAcronymAndExternalId("FINEP", "751")).isPresent();
        assertThat(changed.getIngestionRunId()).isEqualTo(changedRun.getId());
        assertThat(evidence.findByExtractedFieldId(first.getId()))
                .extracting(Evidence::getId).containsExactly(source.getId());

        ExtractedField missing = recorder.record(version.getId(), "trl", null, null,
                ValidationStatus.MISSING, "fixture", timestamp.plusSeconds(30), changedRun.getId(), null);
        assertThat(missing.getCreatedAt()).isEqualTo(timestamp.plusSeconds(30));
        assertThat(missing.getIngestionRunId()).isEqualTo(changedRun.getId());
        assertThat(fields.count()).isEqualTo(3);
    }
}
