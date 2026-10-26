package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FinepLiveExecutionAssessmentTest {
    private final FinepLiveValidationPolicy policy = new FinepLiveValidationPolicy();
    private final FinepLiveValidationManifest.Provider provider = new FinepLiveValidationPolicyTest()
            .manifest().provider();

    @Test
    void missingUsageEscalatesWithoutReportingZeroCost() {
        var result = FinepLiveExecutionAssessment.assess("FAILED", false, null, null, provider, policy);
        assertThat(result.status()).isEqualTo("ESCALATE");
        assertThat(result.reason()).isEqualTo("provider_usage_unavailable");
        assertThat(result.estimatedCostUsd()).isNull();
    }

    @Test
    void reportedBudgetOverrunEscalatesAndNeverRequestsAnotherAttempt() {
        var result = FinepLiveExecutionAssessment.assess("SUCCEEDED", true, 64_000L, 12_000L, provider, policy);
        assertThat(result.status()).isEqualTo("ESCALATE");
        assertThat(result.reason()).isEqualTo("reported_usage_exceeds_approved_budget");
        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    void failedIngestionFailsAndSuccessfulIngestionStillRequiresEvidenceReview() {
        var failed = FinepLiveExecutionAssessment.assess("FAILED", true, 100L, 100L, provider, policy);
        assertThat(failed.status()).isEqualTo("FAIL");
        var succeeded = FinepLiveExecutionAssessment.assess("SUCCEEDED", true, 100L, 100L, provider, policy);
        assertThat(succeeded.status()).isEqualTo("ESCALATE");
        assertThat(succeeded.reason()).isEqualTo("human_evidence_review_required");
    }
}
