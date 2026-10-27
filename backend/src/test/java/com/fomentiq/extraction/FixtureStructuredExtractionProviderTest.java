package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.application.ExtractionSchemaV2;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExtractionRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureStructuredExtractionProviderTest {
    private static final String EDITAL_URL = "https://finep.gov.br/07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf";
    @Test
    void mapsFrozenReviewedCandidatesWithoutChangingFixtureOrUsingNetwork() {
        var provider = new FixtureStructuredExtractionProvider(new ObjectMapper());
        UUID noticeVersion = UUID.randomUUID();
        var response = provider.extract(new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV2.VERSION, List.of(
                new StructuredExtractionProvider.SourceExcerpt(noticeVersion + ":15", noticeVersion,
                        EDITAL_URL, "edital", 15, null, "Prazo final 06/06/2025"),
                new StructuredExtractionProvider.SourceExcerpt(noticeVersion + ":12", noticeVersion,
                        EDITAL_URL, "edital", 12, null, "Orçamento R$500.000.000,00"))));

        assertThat(response.fields()).containsOnlyKeys(ExtractionSchemaV1.FIELDS.toArray(String[]::new));
        assertThat(response.fields().get("deadline").value()).contains("2025-06-06T17:00:00-03:00");
        assertThat(response.fields().get("deadline").evidence()).isNotEmpty();
        assertThat(response.fields().get("trl").value()).isNull();
        assertThat(response.fields().get("trl").evidence()).isEmpty();
        assertThat(response.fields().get("totalBudget").evidence()).isNotEmpty();
        assertThat(response.fields().get("counterpart").clauses()).hasSizeGreaterThan(5);
        assertThat(response.fields().get("eligibility").clauses()).hasSizeGreaterThan(3);
        assertThat(provider.describe(new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV2.VERSION, List.of()))
                .schemaDefinition()).contains("finep-call-v2");
        assertThat(response.provider()).isEqualTo("deterministic-fixture");
    }
}
