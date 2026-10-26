package com.fomentiq.extraction.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

/** Vendor-neutral extraction port. Inputs are preselected, bounded document excerpts. */
public interface StructuredExtractionProvider {
    ProviderResponse extract(ExtractionRequest request);

    record ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts,
                             ExecutionLimits executionLimits) {
        public ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts) {
            this(ingestionRunId, schemaVersion, excerpts, null);
        }
    }
    record ExecutionLimits(int maxAttempts, int maxInputTokens, int maxOutputTokens,
                           Duration timeout, Instant deadline) {
        public ExecutionLimits {
            if (maxAttempts < 1 || maxInputTokens < 1 || maxOutputTokens < 1 || timeout == null
                    || timeout.isNegative() || timeout.isZero() || deadline == null) {
                throw new IllegalArgumentException("Invalid extraction execution limits");
            }
        }
    }
    record SourceExcerpt(String segmentId, UUID documentVersionId, String sourceUrl, String title,
                         Integer page, String section, String text) { }
    record ProviderResponse(String provider, String model, String schemaVersion, String rawResponse,
                            String refusal, boolean incomplete, Long inputTokens, Long outputTokens,
                            Map<String, Candidate> fields) { }
    record Candidate(String value, String rawValue, List<Citation> evidence, List<Alternative> conflicts) { }
    record Alternative(String value, String rawValue, List<Citation> evidence) { }
    record Citation(String segmentId, String quote) { }
}
