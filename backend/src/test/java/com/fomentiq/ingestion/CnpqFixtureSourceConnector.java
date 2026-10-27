package com.fomentiq.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.ingestion.application.SourceConnector;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/** Offline replay of the reviewed public CNPq fixture. */
final class CnpqFixtureSourceConnector implements SourceConnector {
    private static final String ROOT = "fixtures/cnpq/chamada-30-2026/";
    private final ObjectMapper mapper;

    CnpqFixtureSourceConnector(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public String sourceType() { return "CNPQ"; }
    @Override public ArtifactOrigin artifactOrigin() { return ArtifactOrigin.FIXTURE; }

    @Override public List<SourceCall> discover() {
        JsonNode manifest = json(bytes("manifest.json"));
        JsonNode identity = manifest.path("sourceIdentity");
        JsonNode call = manifest.path("opportunity");
        URI canonical = URI.create(identity.path("canonicalUrl").asText());
        byte[] discovery = bytes("discovery.html");
        byte[] page = bytes("call.html");
        SourceCall result = new SourceCall(identity.path("externalId").asText(), null, call.path("title").asText(),
                call.path("eligibilitySummary").asText(), "OPEN", null, null,
                LocalDate.parse(call.path("submissionDeadline").asText()), canonical,
                URI.create(manifest.path("capture").path("artifacts").get(0).path("sourceUrl").asText()),
                discovery, "text/html", canonical, page, "text/html", "CNPq", List.of("CAPES"));
        return List.of(result);
    }

    @Override public DocumentCatalog discoverDocuments(SourceCall call) {
        List<SourceDocument> docs = new ArrayList<>();
        JsonNode artifacts = json(bytes("manifest.json")).path("capture").path("artifacts");
        for (JsonNode artifact : artifacts) {
            String kind = artifact.path("kind").asText();
            if (!List.of("NOTICE", "ANNEX", "FAQ").contains(kind)) continue;
            String path = artifact.path("path").asText();
            String mediaType = path.endsWith(".pdf") ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            docs.add(new SourceDocument(path, artifact.path("title").asText(kind), kind,
                    URI.create(artifact.path("sourceUrl").asText()), mediaType));
        }
        byte[] snapshot = bytes("call.html");
        return new DocumentCatalog(call.callUri(), snapshot, "text/html", List.copyOf(docs));
    }

    @Override public DownloadedDocument download(SourceDocument document) {
        String path = document.externalId();
        byte[] body = bytes(path);
        return new DownloadedDocument(body, document.expectedMediaType(), document.sourceUri());
    }

    private JsonNode json(byte[] bytes) {
        try { return mapper.readTree(bytes); }
        catch (IOException exception) { throw new IllegalStateException("CNPq fixture manifest is invalid"); }
    }
    private static byte[] bytes(String path) {
        try { return new ClassPathResource(ROOT + path).getContentAsByteArray(); }
        catch (IOException exception) { throw new IllegalStateException("CNPq fixture artifact is missing"); }
    }
}
