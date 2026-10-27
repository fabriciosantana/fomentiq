package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinepLiveValidationManifestLoaderTest {
    @TempDir Path directory;

    @Test
    void rejectsMissingOversizedAndUnknownPropertyManifests() throws Exception {
        var loader = new FinepLiveValidationManifestLoader(new ObjectMapper());
        assertThatThrownBy(() -> loader.load(directory.resolve("missing.json")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Finep validation manifest is invalid");
        Path malformed = Files.writeString(directory.resolve("unknown.json"), "{\"unexpected\":true}");
        assertThatThrownBy(() -> loader.load(malformed)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Finep validation manifest is invalid");
        Path oversized = Files.writeString(directory.resolve("large.json"), "x".repeat(65 * 1024));
        assertThatThrownBy(() -> loader.load(oversized)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Finep validation manifest is invalid");
    }

    @Test
    void loadsCompleteTypedManifestIncludingPricingDateAndUris() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var manifest = new FinepLiveValidationPolicyTest().manifest();
        Path file = directory.resolve("valid.json");
        Files.writeString(file, mapper.writeValueAsString(manifest));

        assertThat(new FinepLiveValidationManifestLoader(new ObjectMapper()).load(file)).isEqualTo(manifest);
    }

    @Test
    void historicalV1CandidateCannotAuthorizeNewV2Extraction() {
        Path candidate = Path.of("docs/engineering/verification/"
                + "finep-live-integration-validation-manifest-candidate-2026-10-26.json");
        if (!Files.isRegularFile(candidate)) candidate = Path.of("../docs/engineering/verification/"
                + "finep-live-integration-validation-manifest-candidate-2026-10-26.json");
        var manifest = new FinepLiveValidationManifestLoader(new ObjectMapper()).load(candidate);
        assertThatThrownBy(() -> new FinepLiveValidationPolicy().validate(manifest, manifest.approvalReference(),
                "gpt-6-luna", "finep-call-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Finep live validation manifest failed safety validation");
    }

    @Test
    void explicitRunnerRefusesMissingManifestBeforeStartingSpringOrAccessingSources() {
        String[] args = {"--fomentiq.finep.live-validation.enabled=true",
                "--fomentiq.finep.live-validation.manifest=" + directory.resolve("absent.json"),
                "--fomentiq.finep.live-validation.approval-reference=approval-01"};
        assertThat(FinepLiveValidationCli.requested(args)).isTrue();
        assertThat(new FinepLiveValidationCli().execute(args, Map.of())).isEqualTo(2);
    }
}
