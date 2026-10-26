package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinepLiveValidationPolicyTest {
    private final FinepLiveValidationPolicy policy = new FinepLiveValidationPolicy();

    @Test
    void acceptsExactAuthorizedIdentityAndBounds() {
        policy.validate(manifest(), "approval-01", "gpt-6-luna", "finep-call-v1");
        policy.requireLiveApprovalReference(manifest(), "approval-01");
    }

    @Test
    void refusesPendingOrPreflightOnlyApprovalReferencesForLiveExecution() {
        var value = manifest();
        for (String marker : Set.of("human-live-approval-pending", "preflight-only-2026-10-26")) {
            var pending = new FinepLiveValidationManifest(value.version(), value.runId(), marker, value.target(),
                    value.allowedHosts(), value.acquisition(), value.provider(), value.isolation(), value.reportFile());
            assertThatThrownBy(() -> policy.requireLiveApprovalReference(pending, marker))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesChangedOpportunityAndOverLimitDocumentsBeforeExecution() {
        var changedTarget = new FinepLiveValidationManifest.Target("752", "751092",
                FinepLiveValidationPolicy.REFERENCE_URL);
        assertThatThrownBy(() -> policy.validate(withTarget(changedTarget), "approval-01", "gpt-6-luna", "finep-call-v1"))
                .isInstanceOf(IllegalArgumentException.class);
        var original = manifest();
        var tooMany = new FinepLiveValidationManifest.Acquisition(19, original.acquisition().maxBytesPerDocument(),
                original.acquisition().maxAggregateDocumentBytes(), original.acquisition().maxDiscoveryResponseBytes(),
                original.acquisition().maxMetadataResponseBytes(), original.acquisition().maxOAuthPageBytes(),
                original.acquisition().maxTokenResponseBytes(), original.acquisition().maxHttpRequests(),
                original.acquisition().maxRedirects(), original.acquisition().connectTimeoutSeconds(),
                original.acquisition().readTimeoutSeconds(), original.acquisition().operationTimeoutSeconds(),
                original.acquisition().globalTimeoutSeconds());
        assertThatThrownBy(() -> policy.validate(withAcquisition(tooMany), "approval-01", "gpt-6-luna", "finep-call-v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesProviderAttemptsAboveTheManifestSafetyCeiling() {
        var value = manifest();
        var provider = new FinepLiveValidationManifest.Provider(value.provider().model(),
                value.provider().schemaVersion(), value.provider().reasoningEffort(), 4, value.provider().maxInputTokens(),
                value.provider().maxOutputTokens(), value.provider().timeoutSeconds(),
                value.provider().inputUsdPerMillionTokens(), value.provider().outputUsdPerMillionTokens(),
                value.provider().pricingSource(), value.provider().pricingEffectiveDate(), value.provider().maxSpendUsd());
        var overBudget = new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(),
                value.target(), value.allowedHosts(), value.acquisition(), provider, value.isolation(), value.reportFile());
        assertThatThrownBy(() -> policy.validate(overBudget, "approval-01", "gpt-6-luna", "finep-call-v1"))
                .isInstanceOf(IllegalArgumentException.class);
        var twoAttempts = new FinepLiveValidationManifest.Provider(value.provider().model(),
                value.provider().schemaVersion(), value.provider().reasoningEffort(), 2,
                value.provider().maxInputTokens(), value.provider().maxOutputTokens(),
                value.provider().timeoutSeconds(), value.provider().inputUsdPerMillionTokens(),
                value.provider().outputUsdPerMillionTokens(), value.provider().pricingSource(),
                value.provider().pricingEffectiveDate(), value.provider().maxSpendUsd());
        var unsafeRetry = new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(),
                value.target(), value.allowedHosts(), value.acquisition(), twoAttempts, value.isolation(), value.reportFile());
        assertThatThrownBy(() -> policy.validate(unsafeRetry, "approval-01", "gpt-6-luna", "finep-call-v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesPricingSourceWithUserInfoOrQueryBeforeItCanReachTheReport() {
        var value = manifest();
        var provider = new FinepLiveValidationManifest.Provider(value.provider().model(), value.provider().schemaVersion(),
                value.provider().reasoningEffort(),
                value.provider().maxAttempts(), value.provider().maxInputTokens(), value.provider().maxOutputTokens(),
                value.provider().timeoutSeconds(), value.provider().inputUsdPerMillionTokens(),
                value.provider().outputUsdPerMillionTokens(),
                URI.create("https://user:marker@openai.com/api/pricing/?credential=marker"),
                value.provider().pricingEffectiveDate(), value.provider().maxSpendUsd());
        var unsafe = new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(),
                value.target(), value.allowedHosts(), value.acquisition(), provider, value.isolation(), value.reportFile());
        assertThatThrownBy(() -> policy.validate(unsafe, "approval-01", "gpt-6-luna", "finep-call-v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesStorageEndpointWithUserInfoQueryOrFragment() {
        for (URI endpoint : Set.of(URI.create("http://user:secret@127.0.0.1:59000"),
                URI.create("http://127.0.0.1:59000/?token=secret"),
                URI.create("http://127.0.0.1:59000/#secret"))) {
            assertThatThrownBy(() -> policy.validate(withStorageEndpoint(endpoint), "approval-01", "gpt-6-luna",
                    "finep-call-v1")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void calculatesConservativeCostEstimateAcrossEveryAllowedAttempt() {
        assertThat(policy.estimatedMaximumCost(manifest().provider())).isEqualByComparingTo("12.40000000");
    }

    @Test
    void acceptsOfficialOpenAiPricingReferenceAndMonitoredBudgetWithinEstimate() {
        var value = manifest();
        var provider = new FinepLiveValidationManifest.Provider(value.provider().model(), value.provider().schemaVersion(),
                value.provider().reasoningEffort(),
                1, value.provider().maxInputTokens(), value.provider().maxOutputTokens(), value.provider().timeoutSeconds(),
                new BigDecimal("0.10"), new BigDecimal("0.50"),
                URI.create("https://developers.openai.com/api/docs/pricing"), LocalDate.of(2026, 10, 26),
                new BigDecimal("1.00"));
        var monitored = new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(),
                value.target(), value.allowedHosts(), value.acquisition(), provider, value.isolation(), value.reportFile());
        policy.validate(monitored, "approval-01", "gpt-6-luna", "finep-call-v1");
        assertThat(policy.estimatedMaximumCost(provider)).isEqualByComparingTo("0.01240000");
        assertThat(policy.estimatedReportedCost(provider, 20_000, 2_000)).isEqualByComparingTo("0.00300000");
    }

    @Test
    void propagatesManifestCapsToAcquisitionAndExtractionPorts() {
        var constraints = policy.toConstraints(manifest(), Instant.now());
        assertThat(constraints.externalId()).isEqualTo("751");
        assertThat(constraints.limits().maxDocuments()).isEqualTo(18);
        assertThat(constraints.extractionLimits().maxAttempts()).isEqualTo(1);
        assertThat(constraints.extractionLimits().maxInputTokens()).isEqualTo(64_000);
    }

    FinepLiveValidationManifest manifest() {
        return new FinepLiveValidationManifest(1, "00000000-0000-4000-8000-000000000001", "approval-01",
                new FinepLiveValidationManifest.Target("751", "751092", FinepLiveValidationPolicy.REFERENCE_URL),
                Set.of("www.finep.gov.br"),
                new FinepLiveValidationManifest.Acquisition(18, 10 * FinepLiveValidationPolicy.MIB,
                        25 * FinepLiveValidationPolicy.MIB, FinepLiveValidationPolicy.MIB,
                        2 * FinepLiveValidationPolicy.MIB, 2 * FinepLiveValidationPolicy.MIB, 64 * 1024,
                        123, 4, 10, 30, 45, 900),
                new FinepLiveValidationManifest.Provider("gpt-6-luna", "finep-call-v1", "low", 1, 64_000, 12_000,
                        60, new BigDecimal("100"), new BigDecimal("500"), URI.create("https://openai.com/api/pricing/"),
                        LocalDate.of(2026, 10, 26), new BigDecimal("10")),
                new FinepLiveValidationManifest.Isolation("jdbc:postgresql://127.0.0.1:55432/fomentiq_live",
                        "fomentiq_live", "fomentiq_live_user", URI.create("http://127.0.0.1:59000"),
                        "fomentiq-live-00000000-0000-4000-8000-000000000001", "fomentiq-finep-live-00000000-0000-4000-8000-000000000001",
                        "fomentiq-finep-live-00000000-0000-4000-8000-000000000001_postgres_data",
                        "fomentiq-finep-live-00000000-0000-4000-8000-000000000001_object_storage_data"),
                "docs/engineering/verification/finep-live-validation/00000000-0000-4000-8000-000000000001.json");
    }

    private FinepLiveValidationManifest withTarget(FinepLiveValidationManifest.Target target) {
        FinepLiveValidationManifest value = manifest();
        return new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(), target,
                value.allowedHosts(), value.acquisition(), value.provider(), value.isolation(), value.reportFile());
    }

    private FinepLiveValidationManifest withAcquisition(FinepLiveValidationManifest.Acquisition acquisition) {
        FinepLiveValidationManifest value = manifest();
        return new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(), value.target(),
                value.allowedHosts(), acquisition, value.provider(), value.isolation(), value.reportFile());
    }

    private FinepLiveValidationManifest withStorageEndpoint(URI endpoint) {
        FinepLiveValidationManifest value = manifest();
        var original = value.isolation();
        var isolation = new FinepLiveValidationManifest.Isolation(original.jdbcUrl(), original.database(),
                original.username(), endpoint, original.storageBucket(), original.composeProject(),
                original.postgresVolume(), original.storageVolume());
        return new FinepLiveValidationManifest(value.version(), value.runId(), value.approvalReference(),
                value.target(), value.allowedHosts(), value.acquisition(), value.provider(), isolation,
                value.reportFile());
    }
}
