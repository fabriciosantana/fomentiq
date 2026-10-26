package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class FinepLiveValidationCliTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesDockerComposeJsonLinesAndArrays() throws Exception {
        byte[] lines = ("{\"Service\":\"postgres\"}\\n{\"Service\":\"object-storage\"}\\n")
                .replace("\\n", "\n").getBytes(StandardCharsets.UTF_8);
        assertThat(FinepLiveValidationCli.parseComposePs(mapper, lines)).hasSize(2);

        byte[] array = "[{\"Service\":\"postgres\"}]".getBytes(StandardCharsets.UTF_8);
        assertThat(FinepLiveValidationCli.parseComposePs(mapper, array)).hasSize(1);
    }

    @Test
    void refusesComposePortsPublishedOutsideLoopback() {
        FinepLiveValidationCli.requireLoopbackHost("127.0.0.1");
        assertThatThrownBy(() -> FinepLiveValidationCli.requireLoopbackHost("0.0.0.0"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsOnlyTheExplicitPrivateExecutionFlagAlongsidePreflightArguments() {
        String[] args = {"--fomentiq.finep.live-validation.enabled=true",
                "--fomentiq.finep.live-validation.manifest=/tmp/run.json",
                "--fomentiq.finep.live-validation.approval-reference=approved-run-01",
                "--fomentiq.finep.live-validation.execute=true"};
        FinepLiveValidationCli.rejectRuntimeOverrides(args, Map.of(), new Properties());
        assertThatThrownBy(() -> FinepLiveValidationCli.rejectRuntimeOverrides(
                new String[]{args[0], args[1], args[2], "--spring.datasource.url=jdbc:unsafe"},
                Map.of(), new Properties())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsPreflightAndLiveReportsDistinctForTheSameApprovedRun() {
        var manifest = new FinepLiveValidationPolicyTest().manifest();
        assertThat(FinepLiveValidationCli.reportPath(manifest, false).getFileName().toString())
                .isEqualTo(manifest.runId() + "-preflight.json");
        assertThat(FinepLiveValidationCli.reportPath(manifest, true).getFileName().toString())
                .isEqualTo(manifest.runId() + "-live.json");
    }
}
