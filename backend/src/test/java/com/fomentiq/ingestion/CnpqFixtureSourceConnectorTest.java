package com.fomentiq.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CnpqFixtureSourceConnectorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void frozenPublicFixtureHasApprovedIdentityDocumentsAndMatchingHashes() throws Exception {
        JsonNode manifest;
        try (InputStream input = new ClassPathResource("fixtures/cnpq/chamada-30-2026/manifest.json").getInputStream()) {
            manifest = mapper.readTree(input);
        }
        CnpqFixtureSourceConnector connector = new CnpqFixtureSourceConnector(mapper);
        var call = connector.discover().getFirst();
        var catalog = connector.discoverDocuments(call);

        assertThat(connector.sourceType()).isEqualTo("CNPQ");
        assertThat(call.externalId()).isEqualTo("30/2026");
        assertThat(call.canonicalUri().toString()).contains("chamada-publica-cnpq-N-30-2026");
        assertThat(call.primaryAgencyAcronym()).isEqualTo("CNPq");
        assertThat(call.additionalFundingAgencyAcronyms()).containsExactly("CAPES");
        assertThat(catalog.documents()).hasSize(5);
        for (JsonNode artifact : manifest.path("capture").path("artifacts")) {
            String expectedHash = artifact.path("sha256").asText(artifact.path("storedSha256").asText());
            if (expectedHash.isBlank()) continue;
            String path = artifact.path("path").asText();
            byte[] bytes = new ClassPathResource("fixtures/cnpq/chamada-30-2026/" + path)
                    .getContentAsByteArray();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(actual).as("SHA-256 of %s", path).isEqualTo(expectedHash);
        }
        assertThat(catalog.documents()).allSatisfy(document -> {
            assertThat(document.sourceUri().getHost()).isEqualTo("www.gov.br");
            assertThat(document.expectedMediaType()).isNotBlank();
        });
    }
}
