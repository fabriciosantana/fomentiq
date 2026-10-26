package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinepLiveValidationIsolationGuardTest {
    private final FinepLiveValidationIsolationGuard guard = new FinepLiveValidationIsolationGuard();
    private final FinepLiveValidationManifest manifest = new FinepLiveValidationPolicyTest().manifest();

    @Test
    void rejectsSharedDemoProjectAndMismatchedEffectiveEndpoint() {
        var runtime = new FinepLiveValidationIsolationGuard.RuntimeTargets(manifest.isolation().jdbcUrl(),
                manifest.isolation().database(), manifest.isolation().username(),
                manifest.isolation().storageEndpoint(), manifest.isolation().storageBucket());
        var postgres = new FinepLiveValidationIsolationGuard.Service(manifest.isolation().composeProject(), "postgres",
                Set.of(manifest.isolation().postgresVolume()), Set.of(55432));
        var storage = new FinepLiveValidationIsolationGuard.Service(manifest.isolation().composeProject(), "object-storage",
                Set.of(manifest.isolation().storageVolume()), Set.of(59000));
        var shared = new FinepLiveValidationIsolationGuard.ComposeProject("fomentiq-manual-evaluation",
                Map.of("postgres", postgres, "object-storage", storage));
        assertThatThrownBy(() -> guard.verify(manifest, runtime, shared)).isInstanceOf(IllegalStateException.class);

        var wrongRuntime = new FinepLiveValidationIsolationGuard.RuntimeTargets(runtime.jdbcUrl(), runtime.database(),
                runtime.username(), URI.create("http://127.0.0.1:9000"), runtime.storageBucket());
        var isolated = new FinepLiveValidationIsolationGuard.ComposeProject(manifest.isolation().composeProject(),
                Map.of("postgres", postgres, "object-storage", storage));
        assertThatThrownBy(() -> guard.verify(manifest, wrongRuntime, isolated)).isInstanceOf(IllegalStateException.class);
    }
}
