package com.fomentiq.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.ingestion.application.SourceConnector;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

final class FixtureSourceConnector implements SourceConnector {
    private static final String ROOT = "fixtures/finep/proinfra-2024-expansao-751/";
    private static final URI PAGE = URI.create("https://www.finep.gov.br/e/chamada-publica/222684/751092");
    private final ObjectMapper mapper;
    private final FixtureControl control;

    FixtureSourceConnector(ObjectMapper mapper, FixtureControl control) {
        this.mapper = mapper;
        this.control = control;
    }

    @Override
    public String sourceType() { return "FINEP"; }

    @Override
    public ArtifactOrigin artifactOrigin() { return ArtifactOrigin.FIXTURE; }

    @Override
    public List<SourceCall> discover() {
        byte[] discovery = bytes(ROOT + "discovery.json");
        byte[] call = bytes(ROOT + (control.synthetic ? "synthetic/call-changed.json" : "call.json"));
        JsonNode root = json(call);
        JsonNode discoveryRoot = json(discovery);
        boolean identityFound = false;
        for (JsonNode item : discoveryRoot.path("items")) {
            if ("751".equals(item.path("databaseId").asText())
                    && "751092".equals(item.path("id").asText())) identityFound = true;
        }
        if (!identityFound) return List.of();
        return List.of(new SourceCall("751", "751092", root.path("titulo").asText().trim(),
                root.path("descricaoRawText").asText(), "CLOSED", date(root, "dataDePublicacao"),
                null, null, PAGE, URI.create("https://www.finep.gov.br/o/c/chamadapublicas?filter=databaseId%20eq%20751"),
                discovery, "application/json", URI.create("https://www.finep.gov.br/o/c/chamadapublicas/751092"),
                call, "application/json"));
    }

    @Override
    public DocumentCatalog discoverDocuments(SourceCall call) {
        JsonNode response = json(bytes(ROOT + "documents.json"));
        JsonNode manifest = json(bytes(ROOT + "manifest.json"));
        Map<String, String> paths = new HashMap<>();
        for (JsonNode link : manifest.path("documentLinks")) {
            if ("captured".equals(link.path("disposition").asText())
                    && "documentoProprietario".equals(link.path("format").asText())) {
                paths.put(link.path("documentEntryId").asText(), link.path("path").asText());
            }
        }
        List<SourceDocument> documents = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            JsonNode owner = item.path("documentoProprietario");
            String externalId = item.path("id").asText();
            String href = owner.path("link").path("href").asText();
            URI source = href.startsWith("http") ? URI.create(href) : URI.create("https://www.finep.gov.br" + href);
            documents.add(new SourceDocument(externalId, owner.path("name").asText(),
                    item.path("legenda").asText(), source));
        }
        control.documentPaths = paths;
        return new DocumentCatalog(URI.create("https://www.finep.gov.br/o/c/chamadapublicas/751092/documentos"),
                bytes(ROOT + "documents.json"), "application/json", List.copyOf(documents));
    }

    @Override
    public DownloadedDocument download(SourceDocument document) {
        if (control.failedDocumentId.equals(document.externalId())) {
            throw new IllegalStateException("controlled fixture document failure");
        }
        String path = control.documentPaths.get(document.externalId());
        if (path == null) throw new IllegalStateException("fixture document is not captured");
        return new DownloadedDocument(bytes(ROOT + path), "application/pdf", document.sourceUri());
    }

    private JsonNode json(byte[] bytes) {
        try { return mapper.readTree(bytes); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static LocalDate date(JsonNode node, String key) {
        String value = node.path(key).asText("");
        return value.isBlank() ? null : OffsetDateTime.parse(value).toLocalDate();
    }

    private static byte[] bytes(String path) {
        try { return new ClassPathResource(path).getContentAsByteArray(); }
        catch (IOException exception) { throw new IllegalStateException("Fixture resource unavailable", exception); }
    }

    static final class FixtureControl {
        volatile boolean synthetic;
        volatile String failedDocumentId = "";
        volatile Map<String, String> documentPaths = Map.of();
    }
}
