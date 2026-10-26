package com.fomentiq.ingestion.infrastructure.finep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.ingestion.application.SourceConnector;
import com.fomentiq.ingestion.application.IngestionLimits;
import com.fomentiq.ingestion.application.IngestionLimitExceededException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Finep adapter for the current public portal. The documents API currently requires the OAuth
 * client_credentials flow advertised by the call page; configuration is read at runtime and a
 * changed or unavailable flow fails closed without storing credentials or tokens.
 */
@Component
public class FinepSourceConnector implements SourceConnector {
    private static final int MAX_JSON_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DOCUMENT_BYTES = 50 * 1024 * 1024;
    private static final String ENTRY_ID = "751092";
    private static final String HISTORICAL_ID = "751";
    private static final Pattern CLIENT_CREDENTIALS = Pattern.compile(
            "const\\s+CLIENT_ID\\s*=\\s*([\"\'])([^\"\']{1,256})\\1\\s*;\\s*"
                    + "const\\s+CLIENT_SECRET\\s*=\\s*([\"\'])([^\"\']{1,512})\\3");
    private static final Pattern TOKEN_ENDPOINT = Pattern.compile(
            "getTokenURL\\s*:\\s*function\\s*\\(\\)\\s*\\{\\s*return\\s*([\"\'])([^\"\']{1,1024})\\1");
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FinepUrlPolicy urlPolicy;
    private final BoundedHttpClientFactory boundedClientFactory;
    private final URI canonicalUri;
    private final ThreadLocal<IngestionLimits> activeLimits = new ThreadLocal<>();
    private final ThreadLocal<HttpClient> activeHttpClient = new ThreadLocal<>();

    @Autowired
    public FinepSourceConnector(ObjectMapper objectMapper,
                                @Value("${fomentiq.finep.canonical-url:https://www.finep.gov.br/e/chamada-publica/222684/751092}")
                                URI canonicalUri) {
        this(objectMapper, canonicalUri, new FinepUrlPolicy(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                timeout -> HttpClient.newBuilder().connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    FinepSourceConnector(ObjectMapper objectMapper, URI canonicalUri, FinepUrlPolicy urlPolicy,
                         HttpClient httpClient, BoundedHttpClientFactory boundedClientFactory) {
        this.objectMapper = objectMapper;
        this.urlPolicy = urlPolicy;
        this.canonicalUri = urlPolicy.requireAllowed(canonicalUri);
        this.httpClient = httpClient;
        this.boundedClientFactory = boundedClientFactory;
    }

    @Override
    public String sourceType() { return "FINEP"; }

    @Override
    public List<SourceCall> discover() {
        URI page = urlPolicy.requireAllowed(canonicalUri);
        URI discoveryUri = URI.create("https://www.finep.gov.br/o/c/chamadapublicas?filter=databaseId%20eq%20"
                + HISTORICAL_ID + "&pageSize=100");
        byte[] discovery = getAuthenticatedJson(discoveryUri);
        JsonNode root = readJson(discovery);
        List<SourceCall> result = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            if (!HISTORICAL_ID.equals(item.path("databaseId").asText())) continue;
            String portalId = item.path("id").asText();
            if (!ENTRY_ID.equals(portalId)) throw new IllegalStateException("Unexpected Finep portal entry for historical call");
            URI callUri = urlPolicy.resolve("/o/c/chamadapublicas/" + portalId);
            byte[] call = getAuthenticatedJson(callUri);
            JsonNode callNode = readJson(call);
            if (!HISTORICAL_ID.equals(callNode.path("databaseId").asText())
                    || !portalId.equals(callNode.path("id").asText())) {
                throw new IllegalStateException("Finep call identity does not match configured entry");
            }
            result.add(new SourceCall(HISTORICAL_ID, portalId, text(callNode, "titulo"),
                    text(callNode, "descricaoRawText"), callStatus(callNode), date(callNode, "dataDePublicacao"),
                    null, null, canonicalUri, discoveryUri, discovery, "application/json",
                    callUri, call, "application/json"));
        }
        return List.copyOf(result);
    }

    @Override
    public List<SourceCall> discover(IngestionLimits limits) {
        return withLimits(limits, this::discover);
    }

    @Override
    public DocumentCatalog discoverDocuments(SourceCall call) {
        requireKnownCall(call);
        URI uri = urlPolicy.resolve("/o/c/chamadapublicas/" + ENTRY_ID + "/documentos?pageSize=500");
        byte[] bytes = getAuthenticatedJson(uri);
        JsonNode root = readJson(bytes);
        List<SourceDocument> documents = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            JsonNode owner = item.path("documentoProprietario");
            String id = item.path("id").asText();
            String title = owner.path("name").asText("");
            String href = owner.path("link").path("href").asText("");
            if (id.isBlank() || title.isBlank() || href.isBlank()) continue;
            documents.add(new SourceDocument(id, title,
                    item.path("legenda").asText("OTHER"), urlPolicy.resolve(href)));
        }
        return new DocumentCatalog(uri, bytes, "application/json", List.copyOf(documents));
    }

    @Override
    public DocumentCatalog discoverDocuments(SourceCall call, IngestionLimits limits) {
        return withLimits(limits, () -> discoverDocuments(call));
    }

    @Override
    public DownloadedDocument download(SourceDocument document) {
        URI source = urlPolicy.requireAllowed(document.sourceUri());
        HttpResponse<byte[]> response = send(source, null, MAX_DOCUMENT_BYTES);
        byte[] bytes = response.body();
        String type = response.headers().firstValue("content-type").orElse("application/octet-stream")
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return new DownloadedDocument(bytes, type, response.uri());
    }

    @Override
    public DownloadedDocument download(SourceDocument document, long maxBytes, IngestionLimits limits) {
        return withLimits(limits, () -> {
            URI source = urlPolicy.requireAllowed(document.sourceUri());
            HttpResponse<byte[]> response = send(source, null, maxBytes);
            String type = response.headers().firstValue("content-type").orElse("application/octet-stream")
                    .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            return new DownloadedDocument(response.body(), type, response.uri());
        });
    }

    private byte[] getAuthenticatedJson(URI uri) {
        String token = acquirePageToken(canonicalUri);
        return getJson(uri, token);
    }

    private String acquirePageToken(URI page) {
        HttpResponse<byte[]> response = send(page, null, oauthPageLimit());
        try {
            String html = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            Matcher credentials = CLIENT_CREDENTIALS.matcher(html);
            Matcher endpoint = TOKEN_ENDPOINT.matcher(html);
            if (!credentials.find() || !endpoint.find()) {
                throw new IllegalStateException("Finep public page did not provide its OAuth client configuration");
            }
            URI tokenUri = urlPolicy.resolve(endpoint.group(2));
            String basicCredentials = Base64.getEncoder().encodeToString(
                    (credentials.group(2) + ":" + credentials.group(4))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return requestAccessToken(tokenUri, basicCredentials);
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalStateException || ex instanceof IngestionLimitExceededException) throw ex;
            throw new IllegalStateException("Unable to read Finep public page");
        }
    }

    private String requestAccessToken(URI endpoint, String basicCredentials) {
        IngestionLimits limits = activeLimits.get();
        if (limits != null) limits.reserveHttpRequest();
        Duration timeout = limits == null ? Duration.ofSeconds(30) : limits.remainingOperationTimeout();
        HttpRequest request = HttpRequest.newBuilder(urlPolicy.requirePublicTarget(endpoint)).timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basicCredentials)
                .POST(BodyPublishers.ofString("grant_type=client_credentials")).build();
        try {
            HttpResponse<byte[]> response = boundedSend(request, tokenResponseLimit(), limits);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Finep token request failed with HTTP " + response.statusCode());
            }
            JsonNode tokenResponse = parseTokenResponse(response.body());
            String token = tokenResponse.path("access_token").asText("");
            if (token.isBlank() || token.length() > 8192) {
                throw new IllegalStateException("Finep token response was invalid");
            }
            return token;
        } catch (IOException exception) {
            throw translateHttpFailure(exception, limits);
        } catch (RuntimeException exception) {
            if (exception instanceof IngestionLimitExceededException) throw exception;
            throw new IllegalStateException("Finep token request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Finep token request interrupted");
        }
    }

    private byte[] getJson(URI uri, String token) {
        HttpResponse<byte[]> response = send(uri, token, jsonLimit(uri));
        String type = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (!type.contains("json")) throw new IllegalStateException("Finep API returned a non-JSON response");
        return response.body();
    }

    private HttpResponse<byte[]> send(URI initial, String token, long maxBytes) {
        URI uri = urlPolicy.requireAllowed(initial);
        IngestionLimits limits = activeLimits.get();
        int maxRedirects = limits == null ? 4 : limits.maxRedirects();
        for (int redirects = 0; redirects <= maxRedirects; redirects++) {
            uri = urlPolicy.requirePublicTarget(uri);
            if (limits != null) limits.reserveHttpRequest();
            Duration timeout = limits == null ? Duration.ofSeconds(30) : limits.remainingOperationTimeout();
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/json, text/html, application/pdf")
                    .header("User-Agent", "Fomentiq/0.1 (public funding call ingestion)").GET();
            if (token != null) builder.header("Authorization", "Bearer " + token);
            try {
                HttpResponse<byte[]> response = boundedSend(builder.build(), maxBytes, limits);
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new IllegalStateException("Finep redirect has no location"));
                    uri = urlPolicy.validateRedirect(uri, location);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Finep request failed with HTTP " + response.statusCode());
                }
                return response;
            } catch (IOException ex) {
                throw translateHttpFailure(ex, limits);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Finep request interrupted");
            }
        }
        throw new IllegalStateException("Finep redirect limit exceeded");
    }

    private static RuntimeException translateHttpFailure(IOException failure) {
        return translateHttpFailure(failure, null);
    }

    private static RuntimeException translateHttpFailure(IOException failure, IngestionLimits limits) {
        if (limits != null && limits.requestCounter().violation() != null) {
            return limits.requestCounter().violation();
        }
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof IngestionLimitExceededException limit) return limit;
            cause = cause.getCause();
        }
        return new IllegalStateException("Finep request failed");
    }

    private HttpResponse<byte[]> boundedSend(HttpRequest request, long maxBytes, IngestionLimits limits)
            throws IOException, InterruptedException {
        Duration readTimeout = limits == null ? Duration.ofSeconds(30) : limits.readTimeout();
        HttpClient client = limits == null ? httpClient : activeHttpClient.get();
        if (client == null) throw new IllegalStateException("Bounded Finep HTTP client is unavailable");
        try {
            return client.send(request, info -> {
                long length = info.headers().firstValueAsLong("content-length").orElse(-1);
                if (length > maxBytes) throw new IngestionLimitExceededException("Finep response byte limit exceeded");
                return new LimitedByteArrayBodySubscriber(maxBytes, readTimeout,
                        limits == null ? ignored -> { } : limits.requestCounter()::recordViolation);
            });
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Finep response stream failed", cause);
        }
    }

    private long jsonLimit(URI uri) {
        IngestionLimits limits = activeLimits.get();
        if (limits == null) return MAX_JSON_BYTES;
        return uri.toString().contains("filter=databaseId")
                ? limits.maxDiscoveryResponseBytes() : limits.maxMetadataResponseBytes();
    }

    private long oauthPageLimit() {
        IngestionLimits limits = activeLimits.get();
        return limits == null ? 2L * 1024 * 1024 : limits.maxOAuthPageBytes();
    }

    private long tokenResponseLimit() {
        IngestionLimits limits = activeLimits.get();
        return limits == null ? 64L * 1024 : limits.maxTokenResponseBytes();
    }

    private <T> T withLimits(IngestionLimits limits, java.util.concurrent.Callable<T> action) {
        if (limits == null) throw new IllegalArgumentException("Bounded Finep limits are required");
        if (activeLimits.get() != null) throw new IllegalStateException("Nested bounded Finep request context");
        activeLimits.set(limits);
        HttpClient boundedClient = null;
        try {
            boundedClient = boundedClientFactory.create(limits.connectTimeout());
            activeHttpClient.set(boundedClient);
            limits.requireTimeRemaining();
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Finep bounded operation failed");
        } finally {
            activeHttpClient.remove();
            activeLimits.remove();
            if (boundedClient != null && boundedClient != httpClient) boundedClient.close();
        }
    }

    @FunctionalInterface
    interface BoundedHttpClientFactory { HttpClient create(Duration connectTimeout); }

    private JsonNode parseTokenResponse(byte[] bytes) {
        try { return objectMapper.readTree(bytes); }
        catch (IOException exception) { throw new IllegalStateException("Finep token response was invalid"); }
    }

    private JsonNode readJson(byte[] bytes) {
        try { return objectMapper.readTree(bytes); }
        catch (IOException exception) { throw new IllegalStateException("Finep API returned invalid JSON"); }
    }

    private static String text(JsonNode node, String name) {
        return node.path(name).asText("").trim();
    }

    private static LocalDate date(JsonNode node, String name) {
        String value = text(node, name);
        return value.isBlank() ? null : OffsetDateTime.parse(value).toLocalDate();
    }

    private static String callStatus(JsonNode node) {
        String value = node.path("situacao").path("name").asText("UNKNOWN").toUpperCase(Locale.ROOT);
        if (value.contains("ENCERR") || value.contains("FECH")) return "CLOSED";
        if (value.contains("ABERT")) return "OPEN";
        if (value.contains("SUSPENS")) return "SUSPENDED";
        if (value.contains("PREVIST") || value.contains("EM BREVE")) return "UPCOMING";
        return "UNKNOWN";
    }

    private static void requireKnownCall(SourceCall call) {
        if (!HISTORICAL_ID.equals(call.externalId()) || !ENTRY_ID.equals(call.portalEntryId())) {
            throw new IllegalArgumentException("Unsupported Finep call identity");
        }
    }
}
