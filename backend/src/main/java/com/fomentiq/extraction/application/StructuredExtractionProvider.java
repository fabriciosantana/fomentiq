package com.fomentiq.extraction.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

/** Vendor-neutral extraction port. Inputs are preselected, bounded document excerpts. */
public interface StructuredExtractionProvider {
    ProviderResponse extract(ExtractionRequest request);

    default ProviderDescriptor describe(ExtractionRequest request) {
        return new ProviderDescriptor("unspecified", "unspecified", request.schemaVersion(), Map.of(), "{}");
    }

    record ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts,
                             ExecutionLimits executionLimits, DocumentExcerptSelector.SelectionCoverage coverage) {
        public ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts,
                                 ExecutionLimits executionLimits) {
            this(ingestionRunId, schemaVersion, excerpts, executionLimits, null);
        }
        public ExtractionRequest(UUID ingestionRunId, String schemaVersion, List<SourceExcerpt> excerpts) {
            this(ingestionRunId, schemaVersion, excerpts, null, null);
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
    record ProviderDescriptor(String provider, String model, String schemaVersion,
                              Map<String, String> effectiveParameters, String schemaDefinition) { }
    record SourceExcerpt(String segmentId, UUID documentVersionId, String sourceUrl, String title,
                         Integer page, String section, String text, UUID documentId, String documentType,
                         int startOffset, int endOffset) {
        public SourceExcerpt(String segmentId, UUID documentVersionId, String sourceUrl, String title,
                             Integer page, String section, String text) {
            this(segmentId, documentVersionId, sourceUrl, title, page, section, text, null, null, 0,
                    text == null ? 0 : text.length());
        }
    }
    record ProviderResponse(String provider, String model, String schemaVersion, String rawResponse,
                            String refusal, boolean incomplete, Long inputTokens, Long outputTokens,
                            Map<String, Candidate> fields, Map<String, String> effectiveParameters) {
        public ProviderResponse(String provider, String model, String schemaVersion, String rawResponse,
                                String refusal, boolean incomplete, Long inputTokens, Long outputTokens,
                                Map<String, Candidate> fields) {
            this(provider, model, schemaVersion, rawResponse, refusal, incomplete, inputTokens, outputTokens,
                    fields, Map.of());
        }
    }
    record Candidate(String value, String rawValue, List<Citation> evidence, List<Alternative> conflicts,
                     List<RuleClause> clauses, List<ReplacementRelation> replacements, String claimId) {
        public Candidate(String value, String rawValue, List<Citation> evidence, List<Alternative> conflicts) {
            this(value, rawValue, evidence, conflicts, List.of(), List.of(), null);
        }
        public Candidate(String value, String rawValue, List<Citation> evidence, List<Alternative> conflicts,
                         List<RuleClause> clauses, List<ReplacementRelation> replacements) {
            this(value, rawValue, evidence, conflicts, clauses, replacements, null);
        }
    }
    record Alternative(String value, String rawValue, List<Citation> evidence, String claimId) {
        public Alternative(String value, String rawValue, List<Citation> evidence) {
            this(value, rawValue, evidence, null);
        }
    }
    record Citation(String segmentId, String quote, String citationId) {
        public Citation(String segmentId, String quote) { this(segmentId, quote, null); }
    }
    record RuleClause(String clauseId, String kind, String statement, String condition,
                      String minimum, String maximum, List<String> evidenceCitationIds) { }
    record ReplacementRelation(String replacedClauseId, String replacementClauseId,
                               String ruleReference, List<String> evidenceCitationIds,
                               String replacedClaimId, String replacementClaimId) {
        public ReplacementRelation(String replacedClauseId, String replacementClauseId,
                                   String ruleReference, List<String> evidenceCitationIds) {
            this(replacedClauseId, replacementClauseId, ruleReference, evidenceCitationIds, null, null);
        }
    }
}
