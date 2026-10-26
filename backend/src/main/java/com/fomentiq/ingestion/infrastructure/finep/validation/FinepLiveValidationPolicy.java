package com.fomentiq.ingestion.infrastructure.finep.validation;

import com.fomentiq.ingestion.application.IngestionLimits;
import com.fomentiq.ingestion.application.FinepIngestionConstraints;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExecutionLimits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Validates manifest values against immutable safety maxima and current runtime configuration. */
public final class FinepLiveValidationPolicy {
    public static final long MIB = 1024L * 1024L;
    public static final Set<String> FINEP_HOSTS = Set.of("www.finep.gov.br");
    public static final URI REFERENCE_URL = URI.create("https://www.finep.gov.br/e/chamada-publica/222684/751092");
    public static final BigDecimal MAX_INPUT_PRICE = new BigDecimal("100.00");
    public static final BigDecimal MAX_OUTPUT_PRICE = new BigDecimal("500.00");

    public void validate(FinepLiveValidationManifest manifest, String approvalArgument,
                         String configuredModel, String configuredSchema) {
        if (manifest == null || manifest.version() != 1 || !uniqueRunId(manifest.runId())
                || !safeIdentifier(manifest.approvalReference(), 3, 128)
                || !manifest.approvalReference().equals(approvalArgument)) {
            fail();
        }
        var target = manifest.target();
        if (target == null || !"751".equals(target.externalId()) || !"751092".equals(target.portalEntryId())
                || !REFERENCE_URL.equals(target.canonicalUrl()) || !finepUri(target.canonicalUrl())
                || !FINEP_HOSTS.equals(manifest.allowedHosts())) {
            fail();
        }
        validateAcquisition(manifest.acquisition());
        validateProvider(manifest.provider(), configuredModel, configuredSchema);
        validateIsolation(manifest.isolation(), manifest.runId());
        if (!safeReportFile(manifest.reportFile(), manifest.runId())) fail();
    }

    public void requireLiveApprovalReference(FinepLiveValidationManifest manifest, String approvalArgument) {
        String reference = manifest == null ? null : manifest.approvalReference();
        if (reference == null || !reference.equals(approvalArgument)
                || reference.toLowerCase(java.util.Locale.ROOT).contains("pending")
                || reference.toLowerCase(java.util.Locale.ROOT).startsWith("preflight-only")) {
            throw new IllegalArgumentException("A distinct human live-approval reference is required");
        }
    }

    public BigDecimal estimatedMaximumCost(FinepLiveValidationManifest.Provider provider) {
        BigDecimal input = BigDecimal.valueOf(provider.maxInputTokens())
                .multiply(provider.inputUsdPerMillionTokens());
        BigDecimal output = BigDecimal.valueOf(provider.maxOutputTokens())
                .multiply(provider.outputUsdPerMillionTokens());
        return input.add(output).multiply(BigDecimal.valueOf(provider.maxAttempts()))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);
    }

    public BigDecimal estimatedReportedCost(FinepLiveValidationManifest.Provider provider,
                                             long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Invalid reported token usage");
        return BigDecimal.valueOf(inputTokens).multiply(provider.inputUsdPerMillionTokens())
                .add(BigDecimal.valueOf(outputTokens).multiply(provider.outputUsdPerMillionTokens()))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);
    }

    public IngestionLimits toIngestionLimits(FinepLiveValidationManifest manifest, Instant startedAt) {
        var limits = manifest.acquisition();
        return new IngestionLimits(limits.maxDocuments(), limits.maxBytesPerDocument(),
                limits.maxAggregateDocumentBytes(), limits.maxDiscoveryResponseBytes(),
                limits.maxMetadataResponseBytes(), limits.maxOAuthPageBytes(), limits.maxTokenResponseBytes(),
                limits.maxHttpRequests(), limits.maxRedirects(), Duration.ofSeconds(limits.connectTimeoutSeconds()),
                Duration.ofSeconds(limits.readTimeoutSeconds()), Duration.ofSeconds(limits.operationTimeoutSeconds()),
                startedAt.plusSeconds(limits.globalTimeoutSeconds()), new IngestionLimits.RequestCounter());
    }

    public ExecutionLimits toExtractionLimits(FinepLiveValidationManifest manifest, Instant startedAt) {
        var provider = manifest.provider();
        return new ExecutionLimits(provider.maxAttempts(), provider.maxInputTokens(), provider.maxOutputTokens(),
                Duration.ofSeconds(provider.timeoutSeconds()), startedAt.plusSeconds(manifest.acquisition().globalTimeoutSeconds()));
    }

    public FinepIngestionConstraints toConstraints(FinepLiveValidationManifest manifest, Instant startedAt) {
        return new FinepIngestionConstraints(manifest.target().externalId(), manifest.target().portalEntryId(),
                manifest.target().canonicalUrl(), toIngestionLimits(manifest, startedAt),
                toExtractionLimits(manifest, startedAt));
    }

    private static void validateAcquisition(FinepLiveValidationManifest.Acquisition a) {
        if (a == null || a.maxDocuments() < 1 || a.maxDocuments() > 18
                || a.maxBytesPerDocument() < 1 || a.maxBytesPerDocument() > 10 * MIB
                || a.maxAggregateDocumentBytes() < a.maxBytesPerDocument()
                || a.maxAggregateDocumentBytes() > 25 * MIB
                || a.maxDiscoveryResponseBytes() < 1 || a.maxDiscoveryResponseBytes() > MIB
                || a.maxMetadataResponseBytes() < 1 || a.maxMetadataResponseBytes() > 2 * MIB
                || a.maxOAuthPageBytes() < 1 || a.maxOAuthPageBytes() > 2 * MIB
                || a.maxTokenResponseBytes() < 1 || a.maxTokenResponseBytes() > 64 * 1024
                || a.maxHttpRequests() < 1 || a.maxHttpRequests() > 150
                || a.maxRedirects() < 0 || a.maxRedirects() > 4
                || a.connectTimeoutSeconds() < 1 || a.connectTimeoutSeconds() > 10
                || a.readTimeoutSeconds() < 1 || a.readTimeoutSeconds() > 30
                || a.operationTimeoutSeconds() < 1 || a.operationTimeoutSeconds() > 45
                || a.globalTimeoutSeconds() < 1 || a.globalTimeoutSeconds() > 900) fail();
    }

    private static void validateProvider(FinepLiveValidationManifest.Provider p, String configuredModel,
                                         String configuredSchema) {
        if (p == null || !safeIdentifier(p.model(), 1, 64) || !p.model().equals(configuredModel)
                || !"finep-call-v1".equals(p.schemaVersion()) || !p.schemaVersion().equals(configuredSchema)
                || !"low".equals(p.reasoningEffort())
                || p.maxAttempts() != 1
                || p.maxInputTokens() < 1 || p.maxInputTokens() > 64_000
                || p.maxOutputTokens() < 1 || p.maxOutputTokens() > 12_000
                || p.timeoutSeconds() < 1 || p.timeoutSeconds() > 60
                || !validPrice(p.inputUsdPerMillionTokens(), MAX_INPUT_PRICE)
                || !validPrice(p.outputUsdPerMillionTokens(), MAX_OUTPUT_PRICE)
                || !safePricingSource(p.pricingSource()) || p.pricingEffectiveDate() == null
                || p.maxSpendUsd() == null || p.maxSpendUsd().signum() <= 0) fail();
    }

    private static void validateIsolation(FinepLiveValidationManifest.Isolation i, String runId) {
        if (i == null || i.jdbcUrl() == null || !i.jdbcUrl().matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/[a-z][a-z0-9_]{2,62}")
                || i.database() == null || !safeIdentifier(i.database(), 3, 63)
                || i.username() == null || !safeIdentifier(i.username(), 3, 63)
                || i.storageEndpoint() == null || !"http".equalsIgnoreCase(i.storageEndpoint().getScheme())
                || i.storageEndpoint().getHost() == null || !Set.of("127.0.0.1", "localhost").contains(i.storageEndpoint().getHost())
                || i.storageEndpoint().getUserInfo() != null || i.storageEndpoint().getQuery() != null
                || i.storageEndpoint().getFragment() != null
                || i.storageEndpoint().getPath() != null && !i.storageEndpoint().getPath().isEmpty()
                && !"/".equals(i.storageEndpoint().getPath())
                || i.storageBucket() == null || !i.storageBucket().matches("fomentiq-live-[a-z0-9-]{3,64}")
                || !i.storageBucket().equals("fomentiq-live-" + runId)
                || i.composeProject() == null || !i.composeProject().equals("fomentiq-finep-live-" + runId)
                || !safeVolume(i.postgresVolume(), i.composeProject(), "postgres_data")
                || !safeVolume(i.storageVolume(), i.composeProject(), "object_storage_data")) fail();
    }

    private static boolean safeReportFile(String reportFile, String runId) {
        return reportFile != null && reportFile.equals("docs/engineering/verification/finep-live-validation/" + runId + ".json");
    }

    private static boolean safeVolume(String value, String project, String suffix) {
        return (project + "_" + suffix).equals(value);
    }

    private static boolean validPrice(BigDecimal value, BigDecimal max) {
        return value != null && value.signum() > 0 && value.compareTo(max) <= 0;
    }

    private static boolean safePricingSource(URI source) {
        return source != null && "https".equalsIgnoreCase(source.getScheme())
                && source.getUserInfo() == null && source.getQuery() == null && source.getFragment() == null
                && source.getPort() == -1
                && Set.of("https://openai.com/api/pricing/", "https://platform.openai.com/docs/pricing",
                "https://developers.openai.com/api/docs/pricing")
                .contains(source.toString());
    }

    private static boolean finepUri(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "www.finep.gov.br".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1
                && uri.getUserInfo() == null && uri.getFragment() == null;
    }

    private static boolean safeIdentifier(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max
                && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private static boolean uniqueRunId(String value) {
        if (value == null) return false;
        try { return java.util.UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static void fail() {
        throw new IllegalArgumentException("Finep live validation manifest failed safety validation");
    }
}
