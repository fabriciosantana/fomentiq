package com.fomentiq.ingestion.infrastructure.finep.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.FomentiqApplication;
import com.fomentiq.ingestion.application.FinepIngestionOutcome;
import com.fomentiq.ingestion.application.FinepIngestionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs only behind the private CLI's explicit execution flag and completed preflight. */
final class FinepLiveExecutionRunner {
    private final ObjectMapper mapper;
    private final FinepLiveValidationPolicy policy;

    FinepLiveExecutionRunner(ObjectMapper mapper, FinepLiveValidationPolicy policy) {
        this.mapper = mapper;
        this.policy = policy;
    }

    Result execute(FinepLiveValidationManifest manifest) {
        Instant started = Instant.now();
        FinepIngestionOutcome outcome;
        List<Usage> usage;
        int sourceRequests;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FomentiqApplication.class)
                .web(WebApplicationType.NONE).logStartupInfo(false).run()) {
            var constraints = policy.toConstraints(manifest, started);
            outcome = context.getBean(FinepIngestionService.class).ingest(constraints);
            sourceRequests = constraints.limits().requestCounter().current();
            usage = context.getBean(JdbcTemplate.class).query("""
                    SELECT input_tokens, output_tokens FROM extraction_execution
                    WHERE ingestion_run_id = ? ORDER BY created_at DESC LIMIT 2
                    """, (rs, row) -> new Usage((Long) rs.getObject("input_tokens"),
                    (Long) rs.getObject("output_tokens")), outcome.runId());
        }

        long elapsedMs = Duration.between(started, Instant.now()).toMillis();
        var report = mapper.createObjectNode().put("runId", outcome.runId().toString())
                .put("targetExternalId", manifest.target().externalId())
                .put("targetPortalEntryId", manifest.target().portalEntryId())
                .put("ingestionStatus", outcome.status().name())
                .put("detectedCount", outcome.detectedCount()).put("changedCount", outcome.changedCount())
                .put("documentFailureCount", outcome.documentFailureCount()).put("sourceHttpRequests", sourceRequests)
                .put("durationMs", elapsedMs).put("model", manifest.provider().model())
                .put("schema", manifest.provider().schemaVersion()).put("configuredAttempts", manifest.provider().maxAttempts())
                .put("requiresHumanEvidenceReview", true);

        Usage measured = usage.size() == 1 ? usage.getFirst() : new Usage(null, null);
        if (measured.inputTokens() != null) report.put("inputTokens", measured.inputTokens());
        if (measured.outputTokens() != null) report.put("outputTokens", measured.outputTokens());
        report.put("approvedEstimatedBudgetUsd", manifest.provider().maxSpendUsd().toPlainString())
                .put("pricingSource", manifest.provider().pricingSource().toString())
                .put("pricingDate", manifest.provider().pricingEffectiveDate().toString());
        FinepLiveExecutionAssessment.Assessment assessment = FinepLiveExecutionAssessment.assess(
                outcome.status().name(), usage.size() == 1, measured.inputTokens(), measured.outputTokens(),
                manifest.provider(), policy);
        report.put("status", assessment.status()).put("reason", assessment.reason());
        if (assessment.estimatedCostUsd() != null) {
            report.put("estimatedCostUsd", assessment.estimatedCostUsd().toPlainString());
        }
        return new Result(report, assessment.exitCode());
    }

    record Result(com.fasterxml.jackson.databind.JsonNode report, int exitCode) { }
    private record Usage(Long inputTokens, Long outputTokens) { }
}
