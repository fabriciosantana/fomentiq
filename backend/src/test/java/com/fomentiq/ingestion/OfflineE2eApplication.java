package com.fomentiq.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.FomentiqApplication;
import com.fomentiq.extraction.FixtureStructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.ingestion.application.FinepIngestionOutcome;
import com.fomentiq.ingestion.application.FinepIngestionService;
import com.fomentiq.ingestion.application.SourceConnector;
import com.fomentiq.storage.application.PrivateAuditArchive;
import com.fomentiq.storage.infrastructure.InMemoryPrivateAuditArchive;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Test-classpath launcher for a reproducible fixture-only UI/API demonstration. */
@Import(OfflineE2eApplication.Configuration.class)
public class OfflineE2eApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(FomentiqApplication.class, OfflineE2eApplication.Configuration.class)
                .web(WebApplicationType.SERVLET).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        FixtureSourceConnector.FixtureControl fixtureControl() {
            return new FixtureSourceConnector.FixtureControl();
        }

        @Bean
        @Primary
        SourceConnector fixtureSourceConnector(ObjectMapper mapper, FixtureSourceConnector.FixtureControl control) {
            return new FixtureSourceConnector(mapper, control);
        }

        @Bean
        CnpqFixtureSourceConnector cnpqFixtureSourceConnector(ObjectMapper mapper) {
            return new CnpqFixtureSourceConnector(mapper);
        }

        @Bean
        @Primary
        StructuredExtractionProvider deterministicProvider(ObjectMapper mapper) {
            return new FixtureStructuredExtractionProvider(mapper);
        }

        @Bean
        @Primary
        PrivateAuditArchive auditArchive() {
            return new InMemoryPrivateAuditArchive();
        }

        @Bean
        ApplicationRunner ingestFixtureOnStartup(FinepIngestionService ingestion,
                                                 CnpqFixtureSourceConnector cnpqFixture) {
            return args -> {
                FinepIngestionOutcome finep = ingestion.ingest();
                FinepIngestionOutcome cnpq = ingestion.ingest(cnpqFixture);
                if (finep.status() != IngestionRunStatus.SUCCEEDED || cnpq.status() != IngestionRunStatus.SUCCEEDED) {
                    throw new IllegalStateException("Offline multi-source fixture ingestion did not succeed");
                }
                System.out.printf("OFFLINE_FIXTURE_INGEST source=FINEP status=%s detected=%d changed=%d runId=%s%n",
                        finep.status(), finep.detectedCount(), finep.changedCount(), finep.runId());
                System.out.printf("OFFLINE_FIXTURE_INGEST source=CNPQ status=%s detected=%d changed=%d runId=%s%n",
                        cnpq.status(), cnpq.detectedCount(), cnpq.changedCount(), cnpq.runId());
            };
        }
    }
}
