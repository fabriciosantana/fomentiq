package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class FinepLiveValidationRuntimeOverridesTest {
    private static final String[] RUNNER_ARGS = {
            "--fomentiq.finep.live-validation.enabled=true",
            "--fomentiq.finep.live-validation.manifest=manifest.json",
            "--fomentiq.finep.live-validation.approval-reference=approval-01"};

    @Test
    void refusesSpringEnvironmentOverridesBeforeRuntimeTargetChecks() {
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(RUNNER_ARGS,
                Map.of("SPRING_DATASOURCE_URL", "jdbc:unsafe", "DATABASE_URL", "jdbc:approved"),
                new Properties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Private runner refuses Spring runtime configuration overrides");
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(RUNNER_ARGS,
                Map.of("FOMENTIQ_STORAGE_S3_ENDPOINT", "http://unsafe"), new Properties()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesSpringSystemPropertyOverridesBeforeRuntimeTargetChecks() {
        Properties properties = new Properties();
        properties.setProperty("spring.datasource.url", "jdbc:unsafe");
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(RUNNER_ARGS, Map.of(), properties))
                .isInstanceOf(IllegalArgumentException.class);

        for (String placeholder : new String[]{"DATABASE_URL", "S3_ENDPOINT", "OPENAI_MODEL"}) {
            properties.clear();
            properties.setProperty(placeholder, "override");
            assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(RUNNER_ARGS, Map.of(), properties))
                    .as("system property %s must not supersede an environment placeholder", placeholder)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        properties.clear();
        properties.setProperty("fomentiq.storage.s3.endpoint", "http://unsafe");
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(RUNNER_ARGS, Map.of(), properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesCommandLineOverridesEvenWhenRunnerFlagIsExplicit() {
        String[] arguments = {
                "--fomentiq.finep.live-validation.enabled=true",
                "--fomentiq.finep.live-validation.manifest=manifest.json",
                "--fomentiq.finep.live-validation.approval-reference=approval-01",
                "--spring.datasource.url=jdbc:unsafe"};
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(arguments, Map.of(), new Properties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Private runner refuses runtime configuration overrides");
    }
}
