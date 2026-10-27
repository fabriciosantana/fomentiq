package com.fomentiq.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic adapter from the frozen fixture's reviewed candidates; never calls external services. */
public final class FixtureStructuredExtractionProvider implements StructuredExtractionProvider {
    private final ObjectMapper mapper;
    public FixtureStructuredExtractionProvider(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public ProviderDescriptor describe(ExtractionRequest request) {
        var openAi = new com.fomentiq.extraction.infrastructure.OpenAiStructuredExtractionProvider(mapper, "unused",
                "fixture", "low", 60, 0, 64_000, 12_000).describe(request);
        return new ProviderDescriptor("deterministic-fixture", "fixture", request.schemaVersion(),
                Map.of("source", "frozen-fixture"), openAi.schemaDefinition());
    }

    @Override
    public ProviderResponse extract(ExtractionRequest request) {
        try (var input = getClass().getResourceAsStream("/fixtures/finep/proinfra-2024-expansao-751/provider-response.json")) {
            if (input == null) throw new IllegalStateException("Golden extraction response is missing");
            byte[] bytes = input.readAllBytes();
            JsonNode root = mapper.readTree(bytes);
            Map<String, Candidate> fields = new HashMap<>();
            for (JsonNode item : root.path("fields")) {
                List<Citation> citations = new ArrayList<>();
                for (JsonNode evidence : item.path("evidence")) {
                    String path = evidence.path("artifactPath").asText();
                    String quote = evidence.path("quoteFragment").asText("");
                    var matchingExcerpt = request.excerpts().stream().filter(excerpt -> excerpt.page() != null
                                    && evidence.has("page") && excerpt.page() == evidence.path("page").asInt()
                                    && !path.isBlank()
                                    && excerpt.sourceUrl().contains(path.substring(path.lastIndexOf('/') + 1)))
                            .findFirst();
                    if (matchingExcerpt.isEmpty() && path.isBlank() && !quote.isBlank()) {
                        matchingExcerpt = request.excerpts().stream().filter(excerpt -> excerpt.page() != null
                                && evidence.has("page") && excerpt.page() == evidence.path("page").asInt()
                                && excerpt.text().contains(quote)).findFirst();
                    }
                    matchingExcerpt.ifPresent(excerpt -> {
                                String literal = !quote.isBlank() && excerpt.text().contains(quote) ? quote : excerpt.text();
                                citations.add(new Citation(excerpt.segmentId(), literal,
                                        item.path("fieldName").asText() + "-evidence-" + citations.size()));
                            });
                }
                String value = item.path("normalizedValue").isNull() ? null : item.path("normalizedValue").toString();
                List<RuleClause> clauses = fixtureClauses(item.path("fieldName").asText(),
                        value == null ? null : mapper.readTree(value), citations);
                fields.put(item.path("fieldName").asText(), new Candidate(value,
                        item.path("rawValue").isNull() ? null : item.path("rawValue").asText(), citations, List.of(),
                        clauses, List.of(), null));
            }
            return new ProviderResponse("deterministic-fixture", "fixture-adapter", request.schemaVersion(),
                    mapper.writeValueAsString(fields), null, false, 0L, 0L, Map.copyOf(fields));
        } catch (IOException exception) { throw new IllegalStateException("Golden extraction response is invalid"); }
    }

    private List<RuleClause> fixtureClauses(String field, JsonNode value, List<Citation> citations) {
        if (!"eligibility".equals(field) && !"counterpart".equals(field)) return List.of();
        List<String> evidenceIds = citations.stream().map(Citation::citationId).toList();
        List<RuleClause> result = new ArrayList<>();
        if (value == null || !value.isObject()) return result;
        if ("eligibility".equals(field)) {
            addStringClauses(result, value.path("eligible"), "INCLUSION", evidenceIds, "eligible");
            addStringClauses(result, value.path("excluded"), "EXCLUSION", evidenceIds, "excluded");
            if (value.path("registration").isTextual()) result.add(clause("registration", "CONDITION",
                    "Registration: " + value.path("registration").asText(), evidenceIds));
            if (value.path("federalExecutorRequiresSupportInstitution").asBoolean(false))
                result.add(clause("federal-support", "CONDITION", "Federal executor requires support institution", evidenceIds));
            if (value.path("privateNonprofitMinimumOperatingYears").isInt())
                result.add(clause("private-min-years", "CONDITION", "Private non-profit minimum operating years: "
                        + value.path("privateNonprofitMinimumOperatingYears").asInt() + " years", evidenceIds));
        } else {
            addStringClauses(result, value.path("exempt"), "EXEMPTION", evidenceIds, "exempt");
            addRangeClauses(result, value.path("municipalPercentRanges"), "municipal", evidenceIds);
            addRangeClauses(result, value.path("stateAndFederalDistrictPercentRanges"), "state", evidenceIds);
        }
        return List.copyOf(result);
    }

    private void addStringClauses(List<RuleClause> target, JsonNode values, String kind, List<String> evidenceIds,
                                  String prefix) {
        int index = 0;
        for (JsonNode value : values) target.add(clause(prefix + "-" + index++, kind, value.asText(), evidenceIds));
    }

    private void addRangeClauses(List<RuleClause> target, JsonNode values, String prefix, List<String> evidenceIds) {
        int index = 0;
        for (JsonNode value : values) target.add(new RuleClause(prefix + "-" + index++, "RANGE",
                value.path("condition").asText(), value.path("condition").asText(), value.path("min").asText(),
                value.path("max").asText(), evidenceIds));
    }

    private RuleClause clause(String id, String kind, String statement, List<String> evidenceIds) {
        return new RuleClause(id, kind, statement, null, null, null, evidenceIds);
    }
}
