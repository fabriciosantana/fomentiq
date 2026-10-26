package com.fomentiq.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        var format = mapper.createObjectNode().put("type", "json_schema").put("name", request.schemaVersion())
                .put("strict", true);
        var schema = format.putObject("schema").put("type", "object");
        var required = schema.putArray("required");
        var properties = schema.putObject("properties");
        for (String name : com.fomentiq.extraction.application.ExtractionSchemaV1.FIELDS) {
            required.add(name);
            var candidate = properties.putObject(name).put("type", "object");
            candidate.put("additionalProperties", false);
            candidate.putArray("required").add("value").add("rawValue").add("evidence").add("conflicts");
            var fields = candidate.putObject("properties");
            fields.putObject("value");
            fields.putObject("rawValue").putArray("type").add("string").add("null");
            var evidence = fields.putObject("evidence").put("type", "array");
            evidence.set("items", citationSchema());
            var conflicts = fields.putObject("conflicts").put("type", "array");
            var alternative = conflicts.putObject("items").put("type", "object");
            alternative.put("additionalProperties", false);
            alternative.putArray("required").add("value").add("rawValue").add("evidence");
            var alternativeFields = alternative.putObject("properties");
            alternativeFields.putObject("value");
            alternativeFields.putObject("rawValue").putArray("type").add("string").add("null");
            var alternativeEvidence = alternativeFields.putObject("evidence").put("type", "array");
            alternativeEvidence.set("items", citationSchema());
        }
        schema.put("additionalProperties", false);
        try {
            return new ProviderDescriptor("deterministic-fixture", "fixture-v1", request.schemaVersion(),
                    Map.of("source", "frozen-fixture"), mapper.writeValueAsString(format));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Fixture schema could not be serialized");
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode citationSchema() {
        var citation = mapper.createObjectNode().put("type", "object");
        citation.put("additionalProperties", false);
        citation.putArray("required").add("segmentId").add("quote");
        var properties = citation.putObject("properties");
        properties.putObject("segmentId").put("type", "string");
        properties.putObject("quote").put("type", "string");
        return citation;
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
                    if (!quote.isBlank()) {
                        request.excerpts().stream().filter(excerpt -> excerpt.page() != null
                                        && evidence.has("page") && excerpt.page() == evidence.path("page").asInt()
                                        && excerpt.text().contains(quote))
                                .findFirst().ifPresent(excerpt -> citations.add(new Citation(excerpt.segmentId(), quote)));
                    }
                }
                String value = item.path("normalizedValue").isNull() ? null : item.path("normalizedValue").toString();
                fields.put(item.path("fieldName").asText(), new Candidate(value,
                        item.path("rawValue").isNull() ? null : item.path("rawValue").asText(), citations, List.of()));
            }
            return new ProviderResponse("deterministic-fixture", "fixture-v1", request.schemaVersion(),
                    new String(bytes, StandardCharsets.UTF_8), null, false, 0L, 0L, Map.copyOf(fields));
        } catch (IOException exception) { throw new IllegalStateException("Golden extraction response is invalid"); }
    }
}
