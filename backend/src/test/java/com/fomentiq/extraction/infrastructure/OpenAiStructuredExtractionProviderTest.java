package com.fomentiq.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.application.ExtractionSchemaV2;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExtractionRequest;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExecutionLimits;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenAiStructuredExtractionProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesResponsesStrictSchemaConfiguredModelAndLowReasoningSetting() throws Exception {
        withServer(200, response(validOutput(), "completed"), (endpoint, state) -> {
            var result = provider(endpoint, 0).extract(request());
            assertThat(result.model()).isEqualTo("gpt-6-luna");
            assertThat(result.inputTokens()).isEqualTo(21);
            assertThat(result.fields()).containsOnlyKeys(ExtractionSchemaV1.FIELDS.toArray(String[]::new));
            assertThat(result.fields().get("totalBudget").value()).contains("BRL");
            assertThat(state.authorization.get()).isEqualTo("Bearer test-only-key");
            var body = mapper.readTree(state.requestBody.get());
            assertThat(body.path("model").asText()).isEqualTo("gpt-6-luna");
            assertThat(body.path("reasoning").path("effort").asText()).isEqualTo("low");
            assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
            assertThat(body.path("text").path("format").path("schema").path("required").size())
                    .isEqualTo(ExtractionSchemaV1.FIELDS.size());
            assertThat(body.path("text").path("format").path("schema").path("properties")
                    .path("totalBudget").path("properties").path("value").path("anyOf").get(0)
                    .path("properties").has("amount")).isTrue();
        });
    }

    @Test
    void parsesVersionTwoClausesAndUsesStrictClauseEvidenceSchema() throws Exception {
        withServer(200, response(validOutputV2(), "completed"), (endpoint, state) -> {
            var request = new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV2.VERSION, List.of());
            var provider = provider(endpoint, 0);
            var result = provider.extract(request);
            assertThat(result.schemaVersion()).isEqualTo(ExtractionSchemaV2.VERSION);
            assertThat(result.fields().get("counterpart").clauses()).hasSize(1);
            assertThat(result.fields().get("counterpart").evidence().getFirst().citationId()).isEqualTo("citation-1");
            var body = mapper.readTree(state.requestBody.get());
            var candidateSchema = body.path("text").path("format").path("schema")
                    .path("properties").path("counterpart");
            assertThat(candidateSchema.path("required").toString()).contains("clauses", "replacements", "claimId");
            assertThat(candidateSchema.path("properties").path("clauses").path("items")
                    .path("additionalProperties").asBoolean()).isFalse();
        });
    }

    @Test
    void reportsRefusalIncompleteSchemaAndRateLimitExplicitly() throws Exception {
        String refusal = "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"not allowed\"}]}]}";
        withServer(200, refusal, (endpoint, ignored) -> assertThat(provider(endpoint, 0)
                .extract(request()).refusal()).isEqualTo("not allowed"));
        withServer(200, "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"length\"}}",
                (endpoint, ignored) -> assertThat(provider(endpoint, 0).extract(request()).incomplete()).isTrue());
        withServer(200, response("{}", "completed"), (endpoint, ignored) -> assertThatThrownBy(
                () -> provider(endpoint, 0).extract(request())).isInstanceOf(ExtractionProviderException.class)
                .hasMessageContaining("schema"));
        withServer(429, "{}", (endpoint, ignored) -> assertThatThrownBy(() -> provider(endpoint, 0)
                .extract(request())).isInstanceOf(ExtractionProviderException.class)
                .hasMessage("OpenAI rate limit exceeded"));
    }

    @Test
    void automaticRetriesDoNotExceedConfiguredAttemptCount() throws Exception {
        withServer(429, "{}", (endpoint, state) -> {
            assertThatThrownBy(() -> provider(endpoint, 2).extract(request()))
                    .isInstanceOf(ExtractionProviderException.class);
            assertThat(state.requestCount.get()).isEqualTo(3);
        });
    }

    @Test
    void rejectsOversizedInputBeforeCallingTheProvider() throws Exception {
        withServer(200, response("{}", "completed"), (endpoint, state) -> {
            var bounded = new OpenAiStructuredExtractionProvider(mapper, "test-only-key", "gpt-6-luna", "low", 5,
                    0, HttpClient.newHttpClient(), endpoint, 1, 12_000);
            assertThatThrownBy(() -> bounded.extract(request())).isInstanceOf(ExtractionProviderException.class)
                    .hasMessage("OpenAI input exceeded configured token ceiling");
            assertThat(state.requestCount.get()).isZero();
        });
    }

    @Test
    void manifestAttemptAndDeadlineLimitsOverrideProviderRetryConfiguration() throws Exception {
        withServer(429, "{}", (endpoint, state) -> {
            ExecutionLimits oneAttempt = new ExecutionLimits(1, 64_000, 12_000,
                    Duration.ofSeconds(5), Instant.now().plusSeconds(10));
            assertThatThrownBy(() -> provider(endpoint, 5).extract(request(oneAttempt)))
                    .isInstanceOf(ExtractionProviderException.class);
            assertThat(state.requestCount.get()).isEqualTo(1);
        });
        withServer(200, response(validOutput(), "completed"), (endpoint, state) -> {
            ExecutionLimits expired = new ExecutionLimits(1, 64_000, 12_000,
                    Duration.ofSeconds(5), Instant.now().minusSeconds(1));
            assertThatThrownBy(() -> provider(endpoint, 0).extract(request(expired)))
                    .isInstanceOf(ExtractionProviderException.class)
                    .hasMessage("OpenAI execution deadline exceeded");
            assertThat(state.requestCount.get()).isZero();
        });
    }

    private OpenAiStructuredExtractionProvider provider(URI endpoint, int retries) {
        return new OpenAiStructuredExtractionProvider(mapper, "test-only-key", "gpt-6-luna", "low", 5,
                retries, HttpClient.newHttpClient(), endpoint);
    }

    private ExtractionRequest request() {
        return new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV1.VERSION, List.of());
    }

    private ExtractionRequest request(ExecutionLimits limits) {
        return new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV1.VERSION, List.of(), limits);
    }

    private String response(String output, String status) {
        var root = mapper.createObjectNode().put("status", status);
        var item = root.putArray("output").addObject().put("type", "message");
        item.putArray("content").addObject().put("type", "output_text").put("text", output);
        root.putObject("usage").put("input_tokens", 21).put("output_tokens", 8);
        return root.toString();
    }

    private String validOutput() {
        var root = mapper.createObjectNode();
        for (String name : ExtractionSchemaV1.FIELDS) {
            var field = root.putObject(name);
            field.putNull("value");
            field.putNull("rawValue");
            field.putArray("evidence");
            field.putArray("conflicts");
        }
        root.with("deadline").put("value", "2025-06-06T17:00:00-03:00");
        root.with("totalBudget").set("value", mapper.createObjectNode().put("currency", "BRL")
                .put("amount", "500000000.00").put("qualifier", "UP_TO"));
        root.with("requiresIct").put("value", true);
        return root.toString();
    }

    private String validOutputV2() throws Exception {
        com.fasterxml.jackson.databind.JsonNode root;
        try { root = mapper.readTree(validOutput()); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        root.fields().forEachRemaining(entry -> {
            var field = (com.fasterxml.jackson.databind.node.ObjectNode) entry.getValue();
            field.putArray("clauses");
            field.putArray("replacements");
            field.putNull("claimId");
        });
        var counterpart = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("counterpart");
        counterpart.putArray("evidence").addObject().put("segmentId", "segment-1")
                .put("quote", "complete source range").put("citationId", "citation-1");
        var clause = counterpart.putArray("clauses").addObject().put("clauseId", "range-1")
                .put("kind", "RANGE").put("statement", "one complete range").putNull("condition")
                .put("minimum", "1").put("maximum", "2");
        clause.putArray("evidenceCitationIds").add("citation-1");
        return root.toString();
    }

    private void withServer(int status, String body, ServerTest test) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            State state = new State();
            server.createContext("/responses", exchange -> {
                state.requestCount.incrementAndGet();
                state.authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                state.requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            test.run(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"), state);
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface private interface ServerTest { void run(URI endpoint, State state) throws Exception; }
    private static final class State {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();
    }
}
