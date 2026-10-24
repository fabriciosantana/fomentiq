package com.fomentiq.extraction.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vendor-neutral extraction port. Inputs are preselected, bounded document excerpts. */
public interface StructuredExtractionProvider {
    ProviderResponse extract(ExtractionRequest request);

    record ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts) { }
    record SourceExcerpt(String segmentId, UUID documentVersionId, String sourceUrl, String title,
                         Integer page, String section, String text) { }
    record ProviderResponse(String provider, String model, String schemaVersion, String rawResponse,
                            String refusal, boolean incomplete, Long inputTokens, Long outputTokens,
                            Map<String, Candidate> fields) { }
    record Candidate(String value, String rawValue, List<Citation> evidence, List<Alternative> conflicts) { }
    record Alternative(String value, String rawValue, List<Citation> evidence) { }
    record Citation(String segmentId, String quote) { }
}
