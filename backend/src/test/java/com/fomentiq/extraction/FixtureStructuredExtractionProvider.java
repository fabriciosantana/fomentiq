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
