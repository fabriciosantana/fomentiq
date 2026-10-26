package com.fomentiq.ingestion.infrastructure.finep.validation;

import java.math.BigDecimal;

/** Pure post-run policy: PASS is reserved until the evidence review is complete. */
final class FinepLiveExecutionAssessment {
    private FinepLiveExecutionAssessment() { }

    static Assessment assess(String ingestionStatus, boolean usageCountIsOne, Long inputTokens, Long outputTokens,
                             FinepLiveValidationManifest.Provider provider, FinepLiveValidationPolicy policy) {
        if (!usageCountIsOne || inputTokens == null || outputTokens == null) {
            return new Assessment("ESCALATE", "provider_usage_unavailable", null, 3);
        }
        BigDecimal cost = policy.estimatedReportedCost(provider, inputTokens, outputTokens);
        if (cost.compareTo(provider.maxSpendUsd()) > 0) {
            return new Assessment("ESCALATE", "reported_usage_exceeds_approved_budget", cost, 3);
        }
        if (!"SUCCEEDED".equals(ingestionStatus)) {
            return new Assessment("FAIL", "ingestion_not_succeeded", cost, 3);
        }
        return new Assessment("ESCALATE", "human_evidence_review_required", cost, 3);
    }

    record Assessment(String status, String reason, BigDecimal estimatedCostUsd, int exitCode) { }
}
