package com.fomentiq.ingestion.infrastructure.cnpq;

import com.fomentiq.ingestion.application.IngestionLimitExceededException;
import com.fomentiq.ingestion.application.IngestionLimits;
import com.fomentiq.ingestion.application.SourceConnector;
import com.fomentiq.ingestion.application.SourceDocumentAcquisitionException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Fallback;

/** Narrow CNPq adapter for the approved public MAI/DAI call 30/2026. */
@Component
@Fallback
public class CnpqSourceConnector implements SourceConnector {
    static final URI LISTING = URI.create("https://www.gov.br/cnpq/pt-br/chamadas/abertas-para-submissao");
    static final URI CALL = URI.create("https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/"
            + "chamada-no-30-2026/chamada-publica-cnpq-N-30-2026");
    private static final String CALL_PATH = "/chamada-no-30-2026/";
    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b([^>]*?)href\\s*=\\s*['\"]([^'\"]+)['\"]([^>]*)>(.*?)</a>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern DEADLINE = Pattern.compile("(?i)até\\s+o\\s+dia\\s+11\\s+de\\s+dezembro");
    private final CnpqUrlPolicy urls;
    private final HttpClient client;

    public CnpqSourceConnector() {
        this(new CnpqUrlPolicy(), HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    CnpqSourceConnector(CnpqUrlPolicy urls, HttpClient client) {
        this.urls = urls;
        this.client = client;
    }

    @Override public String sourceType() { return "CNPQ"; }
    @Override public AcquisitionBounds acquisitionBounds() {
        return new AcquisitionBounds(18, 5L * 1024 * 1024, 12L * 1024 * 1024,
                2L * 1024 * 1024, 2L * 1024 * 1024, 30, 4, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    @Override public List<SourceCall> discover() { return discoverInternal(null); }
    @Override public List<SourceCall> discover(IngestionLimits limits) { return discoverInternal(limits); }

    private List<SourceCall> discoverInternal(IngestionLimits limits) {
        byte[] listing = get(LISTING, 2L * 1024 * 1024, limits);
        String title = callTitle(listing);
        if (title == null) {
            throw new IllegalStateException("Approved CNPq call is absent from the public listing or its structure changed");
        }
        byte[] page = get(CALL, 2L * 1024 * 1024, limits);
        return List.of(buildCall(listing, page, title));
    }

    SourceCall parseFixtureSnapshots(byte[] listing, byte[] page) {
        String title = callTitle(listing);
        return title == null ? null : buildCall(listing, page, title);
    }

    private String callTitle(byte[] listing) {
        String html = new String(listing, StandardCharsets.UTF_8);
        String title = null;
        Matcher links = ANCHOR.matcher(html);
        while (links.find()) {
            if (!officialHostHref(links.group(2))
                    || !links.group(2).contains("chamada-publica-cnpq-N-30-2026")) continue;
            URI candidate = resolve(LISTING, links.group(2));
            String label = plain(links.group(4));
            if (candidate.equals(CALL) && label.toLowerCase(Locale.ROOT).contains("30/2026")
                    && label.toLowerCase(Locale.ROOT).contains("mai/dai")) {
                if (title != null) throw new IllegalStateException("CNPq listing contains duplicate call identity");
                title = label;
            }
        }
        return title;
    }

    private static SourceCall buildCall(byte[] listing, byte[] page, String title) {
        String summary = articleSummary(new String(page, StandardCharsets.UTF_8));
        LocalDate deadline = DEADLINE.matcher(summary).find() ? LocalDate.of(2026, 12, 11) : null;
        return new SourceCall("30/2026", null, title, summary, "OPEN", null, null, deadline, CALL, LISTING,
                listing, "text/html", CALL, page, "text/html", "CNPq", List.of("CAPES"));
    }

    @Override public DocumentCatalog discoverDocuments(SourceCall call) { return documents(call, null); }
    @Override public DocumentCatalog discoverDocuments(SourceCall call, IngestionLimits limits) { return documents(call, limits); }

    private DocumentCatalog documents(SourceCall call, IngestionLimits limits) {
        if (call == null || !"30/2026".equals(call.externalId()) || !CALL.equals(call.canonicalUri())) {
            throw new IllegalArgumentException("Unsupported CNPq call identity");
        }
        String html = new String(call.callSnapshot(), StandardCharsets.UTF_8);
        List<SourceDocument> documents = new ArrayList<>();
        Matcher links = ANCHOR.matcher(html);
        while (links.find()) {
            String href = links.group(2).toLowerCase(Locale.ROOT);
            if (!officialHostHref(links.group(2)) || !href.contains(CALL_PATH)
                    || !(href.contains(".pdf") || href.contains(".docx"))) continue;
            URI uri = resolve(CALL, links.group(2));
            if (!uri.getPath().contains(CALL_PATH)) continue;
            String path = uri.getPath().toLowerCase(Locale.ROOT);
            String label = plain(links.group(4));
            String mediaType;
            String category;
            if (path.endsWith(".pdf")) {
                mediaType = "application/pdf";
                category = label.toLowerCase(Locale.ROOT).contains("faq") ? "FAQ" : "Chamada";
            } else if (path.endsWith(".docx")) {
                mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                category = "Anexo";
            } else continue;
            String externalId = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
            documents.add(new SourceDocument(externalId, label.isBlank() ? externalId : label,
                    category, uri, mediaType));
        }
        if (documents.isEmpty()) throw new IllegalStateException("CNPq call page has no supported public documents");
        return new DocumentCatalog(CALL, call.callSnapshot(), "text/html", List.copyOf(documents));
    }

    DocumentCatalog parseDocumentSnapshot(SourceCall call) { return documents(call, null); }

    @Override public DownloadedDocument download(SourceDocument document) {
        return download(document, acquisitionBounds().maxBytesPerDocument(), null);
    }
    @Override public DownloadedDocument download(SourceDocument document, long maxBytes, IngestionLimits limits) {
        URI target = urls.requireAllowed(document.sourceUri());
        Response response = fetch(target, maxBytes, limits);
        String type = response.contentType;
        if (type.contains("application/pdf")) type = "application/pdf";
        else if (type.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (document.expectedMediaType() == null || !document.expectedMediaType().equals(type)) {
            throw new SourceDocumentAcquisitionException(
                    SourceDocumentAcquisitionException.Reason.UNSUPPORTED_CONTENT_TYPE);
        }
        return new DownloadedDocument(response.body, type, response.uri);
    }

    private byte[] get(URI uri, long maxBytes, IngestionLimits limits) {
        Response response = fetch(uri, maxBytes, limits);
        if (!response.contentType.contains("text/html")) throw new IllegalStateException("CNPq page was not HTML");
        return response.body;
    }

    private Response fetch(URI initial, long maxBytes, IngestionLimits limits) {
        URI uri = urls.requireAllowed(initial);
        int maxRedirects = limits == null ? acquisitionBounds().maxRedirects() : limits.maxRedirects();
        for (int redirect = 0; redirect <= maxRedirects; redirect++) {
            urls.requirePublicTarget(uri);
            if (limits != null) limits.reserveHttpRequest();
            Duration timeout = limits == null ? Duration.ofSeconds(30) : limits.remainingOperationTimeout();
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "text/html, application/pdf, "
                            + "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .header("User-Agent", "Fomentiq/0.2 (public opportunity catalog)").GET().build();
            try {
                HttpResponse<byte[]> response = client.send(request, info -> new CnpqLimitedBodySubscriber(maxBytes,
                        limits == null ? acquisitionBounds().readTimeout() : limits.remainingOperationTimeout()));
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    uri = urls.validateRedirect(uri, response.headers().firstValue("location").orElse(null));
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("CNPq request failed with HTTP " + response.statusCode());
                }
                long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
                if (declared > maxBytes) {
                    throw new IngestionLimitExceededException("CNPq response byte limit exceeded");
                }
                byte[] body = response.body();
                if (body.length == 0) throw new IllegalStateException("CNPq returned an empty response");
                return new Response(body, response.headers().firstValue("content-type").orElse("")
                        .split(";", 2)[0].trim().toLowerCase(Locale.ROOT), response.uri());
            } catch (IOException exception) {
                throw new IllegalStateException("CNPq response could not be read");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("CNPq request interrupted");
            }
        }
        throw new IllegalStateException("CNPq redirect limit exceeded");
    }

    private URI resolve(URI base, String href) {
        try { return urls.requireAllowed(base.resolve(href.replace("&amp;", "&"))); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("CNPq page contains an unsupported link"); }
    }

    private static boolean officialHostHref(String href) {
        try {
            URI uri = URI.create(href.replace("&amp;", "&"));
            return uri.getScheme() == null && uri.getHost() == null
                    || "www.gov.br".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String plain(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&amp;", "&").replace("&nbsp;", " ")
                .replace("&#8211;", "–").replace("&#8217;", "’").replace("&#x27;", "'")
                .replace("&quot;", "\"").replaceAll("\\s+", " ").trim();
    }

    private static String articleSummary(String html) {
        Matcher match = Pattern.compile("(?is)<div[^>]*id=['\"]parent-fieldname-text['\"][^>]*>(.*?)</div>").matcher(html);
        return match.find() ? plain(match.group(1)) : "";
    }

    private record Response(byte[] body, String contentType, URI uri) { }
}
