package com.fomentiq.extraction.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExecutionLimits;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenAI Responses API adapter; credentials and response content are never logged. */
@Component
public class OpenAiStructuredExtractionProvider implements StructuredExtractionProvider {
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final String effort;
    private final Duration timeout;
    private final int maxRetries;
    private final int maxInputTokens;
    private final int maxOutputTokens;
    private final URI endpoint;

    @Autowired
    public OpenAiStructuredExtractionProvider(ObjectMapper mapper,
            @Value("${fomentiq.extraction.openai.api-key:}") String apiKey,
            @Value("${fomentiq.extraction.openai.model}") String model,
            @Value("${fomentiq.extraction.reasoning-effort:low}") String effort,
            @Value("${fomentiq.extraction.timeout-seconds:60}") int timeoutSeconds,
            @Value("${fomentiq.extraction.max-retries:2}") int maxRetries,
            @Value("${fomentiq.extraction.max-input-tokens:64000}") int maxInputTokens,
            @Value("${fomentiq.extraction.max-output-tokens:12000}") int maxOutputTokens) {
        this(mapper, apiKey, model, effort, timeoutSeconds, maxRetries,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), ENDPOINT,
                maxInputTokens, maxOutputTokens);
    }

    OpenAiStructuredExtractionProvider(ObjectMapper mapper, String apiKey, String model, String effort,
                                       int timeoutSeconds, int maxRetries, HttpClient client, URI endpoint) {
        this(mapper, apiKey, model, effort, timeoutSeconds, maxRetries, client, endpoint, 64_000, 12_000);
    }

    OpenAiStructuredExtractionProvider(ObjectMapper mapper, String apiKey, String model, String effort,
                                       int timeoutSeconds, int maxRetries, HttpClient client, URI endpoint,
                                       int maxInputTokens, int maxOutputTokens) {
        if (timeoutSeconds < 1 || timeoutSeconds > 300 || maxRetries < 0 || maxRetries > 5
                || maxInputTokens < 1 || maxInputTokens > 64_000 || maxOutputTokens < 1 || maxOutputTokens > 12_000
                || !Set.of("low", "medium", "high").contains(effort)) {
            throw new IllegalArgumentException("Invalid extraction provider settings");
        }
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.effort = effort;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.client = client;
        this.endpoint = endpoint;
    }

    @Override
    public ProviderDescriptor describe(ExtractionRequest request) {
        ExecutionLimits limits = request.executionLimits();
        int allowedInput = limits == null ? maxInputTokens : Math.min(maxInputTokens, limits.maxInputTokens());
        int allowedOutput = limits == null ? maxOutputTokens : Math.min(maxOutputTokens, limits.maxOutputTokens());
        int allowedAttempts = limits == null ? maxRetries + 1 : Math.min(maxRetries + 1, limits.maxAttempts());
        Duration effectiveTimeout = limits == null || timeout.compareTo(limits.timeout()) <= 0
                ? timeout : limits.timeout();
        try {
            return new ProviderDescriptor("openai", model, request.schemaVersion(), Map.of(
                    "api", "responses", "structuredOutputs", "true", "reasoningEffort", effort,
                    "maxAttempts", Integer.toString(allowedAttempts), "maxInputTokens", Integer.toString(allowedInput),
                    "maxOutputTokens", Integer.toString(allowedOutput),
                    "timeoutMillis", Long.toString(effectiveTimeout.toMillis())),
                    mapper.writeValueAsString(schemaFormat()));
        } catch (IOException exception) {
            throw new ExtractionProviderException("Unable to describe extraction request", model);
        }
    }

    @Override
    public ProviderResponse extract(ExtractionRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExtractionProviderException("OpenAI extraction is not configured", model);
        }
        if (!ExtractionSchemaV1.VERSION.equals(request.schemaVersion())) {
            throw new ExtractionProviderException("Unsupported extraction schema version", model);
        }
        byte[] payload;
        ExecutionLimits executionLimits = request.executionLimits();
        int allowedInput = executionLimits == null ? maxInputTokens : Math.min(maxInputTokens, executionLimits.maxInputTokens());
        int allowedOutput = executionLimits == null ? maxOutputTokens : Math.min(maxOutputTokens, executionLimits.maxOutputTokens());
        int allowedAttempts = executionLimits == null ? maxRetries + 1 : Math.min(maxRetries + 1, executionLimits.maxAttempts());
        Duration configuredTimeout = timeout;
        if (executionLimits != null) {
            if (executionLimits.timeout().compareTo(configuredTimeout) < 0) {
                configuredTimeout = executionLimits.timeout();
            }
        }
        try { payload = mapper.writeValueAsBytes(buildRequest(request, allowedOutput)); }
        catch (IOException exception) {
            throw new ExtractionProviderException("Unable to prepare extraction request", model);
        }
        // UTF-8 bytes are used as a deliberately conservative upper bound for input tokens.
        if (payload.length > allowedInput) {
            throw new ExtractionProviderException("OpenAI input exceeded configured token ceiling", model);
        }
        for (int attempt = 0; attempt < allowedAttempts; attempt++) {
            try {
                Duration requestTimeout = configuredTimeout;
                if (executionLimits != null) {
                    Duration remaining = Duration.between(java.time.Instant.now(), executionLimits.deadline());
                    if (remaining.isNegative() || remaining.isZero()) {
                        throw new ExtractionProviderException("OpenAI execution deadline exceeded", model);
                    }
                    if (remaining.compareTo(requestTimeout) < 0) requestTimeout = remaining;
                }
                Duration responseTimeout = requestTimeout;
                HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                        .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                HttpResponse<byte[]> response = client.send(httpRequest, info -> {
                    if (info.headers().firstValueAsLong("content-length").orElse(-1) > MAX_RESPONSE_BYTES) {
                        throw new ExtractionProviderException("OpenAI response exceeded size limit", model);
                    }
                    return new LimitedExtractionResponseBodySubscriber(MAX_RESPONSE_BYTES, responseTimeout);
                });
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt + 1 < allowedAttempts) { Thread.sleep(Math.min(250L * (1L << attempt), 2000L)); continue; }
                    String message = response.statusCode() == 429
                            ? "OpenAI rate limit exceeded" : "OpenAI service unavailable";
                    throw new ExtractionProviderException(message, model);
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ExtractionProviderException("OpenAI request failed with HTTP " + response.statusCode(), model);
                }
                ProviderResponse parsed = parseResponse(response.body());
                Map<String, String> effectiveParameters = describe(request).effectiveParameters();
                return new ProviderResponse(parsed.provider(), parsed.model(), parsed.schemaVersion(),
                        parsed.rawResponse(), parsed.refusal(), parsed.incomplete(), parsed.inputTokens(),
                        parsed.outputTokens(), parsed.fields(), effectiveParameters);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExtractionProviderException("OpenAI request interrupted", model);
            } catch (IOException exception) {
                if (attempt + 1 < allowedAttempts) continue;
                if (exception instanceof HttpTimeoutException) {
                    throw new ExtractionProviderException("OpenAI request timed out", model);
                }
                throw new ExtractionProviderException("OpenAI request failed", model);
            }
        }
        throw new ExtractionProviderException("OpenAI request failed", model);
    }

    private ObjectNode buildRequest(ExtractionRequest request, int outputTokenLimit) {
        ObjectNode root = mapper.createObjectNode().put("model", model);
        root.put("max_output_tokens", outputTokenLimit);
        root.putObject("reasoning").put("effort", effort);
        root.putObject("text").set("format", schemaFormat());
        ArrayNode input = root.putArray("input");
        input.addObject().put("role", "system").putArray("content").addObject().put("type", "input_text")
                .put("text", "Extract only facts explicitly supported by supplied excerpts. Never infer absent facts. "
                        + "Cite segmentId and exact quote for each fact. Preserve conflicting source values in conflicts; "
                        + "never choose a value only because its document is newer. Return every field; use null when absent.");
        ObjectNode user = input.addObject().put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "input_text").put("text", "run=" + request.ingestionRunId()
                + " schema=" + request.schemaVersion() + "\n" + serializeExcerpts(request.excerpts()));
        return root;
    }

    private ObjectNode schemaFormat() {
        ObjectNode format = mapper.createObjectNode().put("type", "json_schema").put("name", ExtractionSchemaV1.VERSION)
                .put("strict", true);
        ObjectNode schema = format.putObject("schema").put("type", "object");
        ArrayNode required = schema.putArray("required");
        ObjectNode properties = schema.putObject("properties");
        for (String field : ExtractionSchemaV1.FIELDS) {
            required.add(field);
            ObjectNode candidate = properties.putObject(field).put("type", "object");
            candidate.putArray("required").add("value").add("rawValue").add("evidence").add("conflicts");
            ObjectNode candidateProps = candidate.putObject("properties");
            candidateProps.set("value", valueSchema(field));
            nullableString(candidateProps.putObject("rawValue"));
            ObjectNode evidence = candidateProps.putObject("evidence").put("type", "array");
            evidence.set("items", citationSchema());
            ObjectNode conflicts = candidateProps.putObject("conflicts").put("type", "array");
            conflicts.set("items", alternativeSchema(field));
            candidate.put("additionalProperties", false);
        }
        schema.put("additionalProperties", false);
        return format;
    }

    private ObjectNode citationSchema() {
        return object(property("segmentId", typed("string")), property("quote", typed("string")));
    }

    private ObjectNode alternativeSchema(String field) {
        ObjectNode evidence = typed("array");
        evidence.set("items", citationSchema());
        return object(property("value", valueSchema(field)), property("rawValue", nullable(typed("string"))),
                property("evidence", evidence));
    }

    private ObjectNode valueSchema(String field) {
        return switch (field) {
            case "publicationDate", "openingDate" -> nullable(typed("string").put("format", "date"));
            case "deadline" -> nullable(typed("string").put("format", "date-time"));
            case "status" -> nullable(enumeration("OPEN", "UPCOMING", "CLOSED", "SUSPENDED", "UNKNOWN"));
            case "totalBudget" -> nullable(object(
                    property("currency", enumeration("BRL", "USD", "EUR")),
                    property("amount", typed("string")),
                    property("qualifier", enumeration("UP_TO", "RANGE", "EXACT", "AT_LEAST", "UNKNOWN"))));
            case "requiresIct" -> nullable(typed("boolean"));
            case "trl" -> nullable(object(property("min", nullable(typed("integer"))),
                    property("max", nullable(typed("integer")))));
            case "technologyThemes", "eligibleExpenses", "officialLinks" -> nullable(stringArray());
            case "evaluationCriteria" -> nullable(criteriaArray());
            case "projectFunding" -> nullable(projectFundingSchema());
            case "eligibility" -> nullable(eligibilitySchema());
            case "regions" -> nullable(regionsSchema());
            case "counterpart" -> nullable(counterpartSchema());
            case "requiredDocuments" -> nullable(object(
                    property("general", stringArray()), property("conditionalAnnexes", stringArray())));
            default -> nullable(typed("string"));
        };
    }

    private ObjectNode projectFundingSchema() {
        ObjectNode tiers = typed("array");
        tiers.set("items", object(property("min", typed("integer")), property("max", nullable(typed("integer"))),
                property("amount", typed("string"))));
        return object(property("currency", enumeration("BRL", "USD", "EUR")),
                property("proposalMaximumByEmployedDoctorCount", tiers),
                property("subprojectMinimum", object(property("basicOrExecutiveDesign", nullable(typed("string"))),
                        property("other", nullable(typed("string"))))));
    }

    private ObjectNode eligibilitySchema() {
        return object(property("eligible", stringArray()), property("registration", nullable(typed("string"))),
                property("excluded", stringArray()), property("federalExecutorRequiresSupportInstitution", nullable(typed("boolean"))),
                property("privateNonprofitMinimumOperatingYears", nullable(typed("integer"))));
    }

    private ObjectNode regionsSchema() {
        return object(property("scope", nullable(typed("string"))),
                property("minimumBudgetShare", object(property("regions", stringArray()),
                        property("percentage", nullable(typed("string"))), property("condition", nullable(typed("string"))))));
    }

    private ObjectNode counterpartSchema() {
        ObjectNode ranges = typed("array");
        ranges.set("items", object(property("condition", typed("string")), property("min", typed("string")),
                property("max", typed("string"))));
        return object(property("exempt", stringArray()), property("municipalPercentRanges", ranges),
                property("stateAndFederalDistrictPercentRanges", ranges.deepCopy()));
    }

    private ObjectNode criteriaArray() {
        ObjectNode result = typed("array");
        result.set("items", object(property("criterion", typed("string")), property("weight", typed("number"))));
        return result;
    }

    private ObjectNode stringArray() {
        ObjectNode result = typed("array");
        result.set("items", typed("string"));
        return result;
    }

    private ObjectNode object(ObjectNode... properties) {
        ObjectNode result = typed("object");
        ObjectNode props = result.putObject("properties");
        ArrayNode required = result.putArray("required");
        for (ObjectNode entry : properties) {
            entry.fields().forEachRemaining(field -> {
                required.add(field.getKey());
                props.set(field.getKey(), field.getValue());
            });
        }
        result.put("additionalProperties", false);
        return result;
    }

    private ObjectNode property(String name, ObjectNode schema) {
        ObjectNode result = mapper.createObjectNode();
        result.set(name, schema);
        return result;
    }

    private ObjectNode typed(String type) { return mapper.createObjectNode().put("type", type); }

    private ObjectNode nullable(ObjectNode schema) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode options = result.putArray("anyOf");
        options.add(schema);
        options.addObject().put("type", "null");
        return result;
    }

    private ObjectNode enumeration(String... values) {
        ObjectNode result = typed("string");
        ArrayNode options = result.putArray("enum");
        for (String value : values) options.add(value);
        return result;
    }

    private static void nullableString(ObjectNode node) {
        ArrayNode types = node.putArray("type");
        types.add("string").add("null");
    }

    private String serializeExcerpts(List<SourceExcerpt> excerpts) {
        ArrayNode array = mapper.createArrayNode();
        for (SourceExcerpt excerpt : excerpts) {
            ObjectNode item = array.addObject().put("segmentId", excerpt.segmentId()).put("title", excerpt.title())
                    .put("sourceUrl", excerpt.sourceUrl()).put("page", excerpt.page()).put("text", excerpt.text());
            if (excerpt.section() != null) item.put("section", excerpt.section());
        }
        return array.toString();
    }

    private ProviderResponse parseResponse(byte[] bytes) {
        try {
            JsonNode root = mapper.readTree(bytes);
            String raw = root.toString();
            boolean incomplete = "incomplete".equals(root.path("status").asText())
                    || root.path("incomplete_details").isObject();
            String refusal = null;
            String outputText = null;
            for (JsonNode output : root.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("refusal".equals(content.path("type").asText())) refusal = content.path("refusal").asText("refused");
                    if ("output_text".equals(content.path("type").asText())) outputText = content.path("text").asText();
                }
            }
            Map<String, Candidate> fields = new HashMap<>();
            if (!incomplete && refusal == null && outputText != null) {
                JsonNode parsed = mapper.readTree(outputText);
                for (String name : ExtractionSchemaV1.FIELDS) {
                    JsonNode candidate = parsed.path(name);
                    if (candidate.isMissingNode() || !candidate.path("evidence").isArray()
                            || !candidate.path("conflicts").isArray()) {
                        throw new ExtractionProviderException("OpenAI response did not match the extraction schema", model);
                    }
                    List<Citation> evidence = citations(candidate.path("evidence"));
                    List<Alternative> conflicts = new ArrayList<>();
                    for (JsonNode alternative : candidate.path("conflicts")) {
                        conflicts.add(new Alternative(candidateValue(alternative.path("value")),
                                nullableText(alternative.path("rawValue")), citations(alternative.path("evidence"))));
                    }
                    fields.put(name, new Candidate(candidateValue(candidate.path("value")),
                            nullableText(candidate.path("rawValue")), evidence, conflicts));
                }
            }
            JsonNode usage = root.path("usage");
            return new ProviderResponse("openai", model, ExtractionSchemaV1.VERSION, raw, refusal, incomplete,
                    usage.has("input_tokens") ? usage.path("input_tokens").asLong() : null,
                    usage.has("output_tokens") ? usage.path("output_tokens").asLong() : null, Map.copyOf(fields));
        } catch (IOException exception) {
            return new ProviderResponse("openai", model, ExtractionSchemaV1.VERSION,
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8), null, false, null, null, Map.of());
        }
    }

    private static String nullableText(JsonNode value) { return value == null || value.isNull() ? null : value.asText(); }

    private static String candidateValue(JsonNode value) {
        return value == null || value.isNull() ? null : value.isTextual() ? value.asText() : value.toString();
    }

    private static List<Citation> citations(JsonNode evidence) {
        List<Citation> result = new ArrayList<>();
        evidence.forEach(item -> result.add(new Citation(item.path("segmentId").asText(), item.path("quote").asText())));
        return List.copyOf(result);
    }
}
