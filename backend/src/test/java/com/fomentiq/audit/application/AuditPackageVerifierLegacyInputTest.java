package com.fomentiq.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditPackageVerifierLegacyInputTest {
    @Test
    void verifiesHistoricalV1InputDigestWithoutCoverageMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var input = mapper.createObjectNode().put("provider", "openai").put("model", "gpt-6-luna")
                .put("schemaVersion", "finep-call-v1").put("schemaDefinition", "{\"name\":\"finep-call-v1\"}")
                .put("inputDigest", "pending");
        input.set("effectiveParameters", mapper.createObjectNode().put("api", "responses"));
        var excerpt = mapper.createObjectNode().put("segmentId", "segment-1")
                .put("documentVersionId", "11111111-1111-4111-8111-111111111111")
                .put("title", "Edital").put("sourceUrl", "https://finep.gov.br/edital.pdf")
                .put("page", 4).put("text", "Prazo original").put("section", "8.1");
        input.putArray("excerpts").add(excerpt);

        var canonical = mapper.createObjectNode().put("provider", input.path("provider").asText())
                .put("model", input.path("model").asText()).put("schemaVersion", input.path("schemaVersion").asText())
                .put("schemaDefinition", input.path("schemaDefinition").asText());
        canonical.set("effectiveParameters", input.path("effectiveParameters"));
        canonical.putArray("excerpts").addObject().put("segmentId", "segment-1")
                .put("documentVersionId", "11111111-1111-4111-8111-111111111111")
                .put("title", "Edital").put("sourceUrl", "https://finep.gov.br/edital.pdf")
                .put("page", 4).put("text", "Prazo original").put("section", "8.1");
        input.put("inputDigest", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(canonical))));

        var gaps = new java.util.ArrayList<String>();
        var verify = AuditPackageVerifier.class.getDeclaredMethod("verifyInputDigest",
                com.fasterxml.jackson.databind.JsonNode.class, List.class);
        verify.setAccessible(true);
        verify.invoke(new AuditPackageVerifier(mapper), input, gaps);

        assertThat(gaps).isEmpty();
    }
}
