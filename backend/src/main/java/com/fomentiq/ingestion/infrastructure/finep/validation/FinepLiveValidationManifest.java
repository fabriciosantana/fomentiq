package com.fomentiq.ingestion.infrastructure.finep.validation;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;

/** Typed, non-secret execution contract for one explicitly approved Finep validation run. */
public record FinepLiveValidationManifest(
        int version,
        String runId,
        String approvalReference,
        Target target,
        Set<String> allowedHosts,
        Acquisition acquisition,
        Provider provider,
        Isolation isolation,
        String reportFile) {

    public record Target(String externalId, String portalEntryId, URI canonicalUrl) { }

    public record Acquisition(
            int maxDocuments,
            long maxBytesPerDocument,
            long maxAggregateDocumentBytes,
            long maxDiscoveryResponseBytes,
            long maxMetadataResponseBytes,
            long maxOAuthPageBytes,
            long maxTokenResponseBytes,
            int maxHttpRequests,
            int maxRedirects,
            int connectTimeoutSeconds,
            int readTimeoutSeconds,
            int operationTimeoutSeconds,
            int globalTimeoutSeconds) { }

    public record Provider(
            String model,
            String schemaVersion,
            String reasoningEffort,
            int maxAttempts,
            int maxInputTokens,
            int maxOutputTokens,
            int timeoutSeconds,
            BigDecimal inputUsdPerMillionTokens,
            BigDecimal outputUsdPerMillionTokens,
            URI pricingSource,
            LocalDate pricingEffectiveDate,
            BigDecimal maxSpendUsd) { }

    public record Isolation(
            String jdbcUrl,
            String database,
            String username,
            URI storageEndpoint,
            String storageBucket,
            String composeProject,
            String postgresVolume,
            String storageVolume) { }
}
