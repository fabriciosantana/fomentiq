package com.fomentiq.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.FomentiqApplication;
import com.fomentiq.extraction.FixtureStructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.ingestion.application.FinepIngestionOutcome;
import com.fomentiq.ingestion.application.FinepIngestionService;
import com.fomentiq.ingestion.application.SourceConnector;
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
        @Primary
        StructuredExtractionProvider deterministicProvider(ObjectMapper mapper) {
            return new FixtureStructuredExtractionProvider(mapper);
        }

        @Bean
        ApplicationRunner ingestFixtureOnStartup(FinepIngestionService ingestion) {
            return args -> {
                FinepIngestionOutcome outcome = ingestion.ingest();
                if (outcome.status() != IngestionRunStatus.SUCCEEDED) {
                    throw new IllegalStateException("Offline fixture ingestion did not succeed: " + outcome.status());
                }
                System.out.printf("OFFLINE_FIXTURE_INGEST status=%s detected=%d changed=%d runId=%s%n",
                        outcome.status(), outcome.detectedCount(), outcome.changedCount(), outcome.runId());
            };
        }
    }
}
