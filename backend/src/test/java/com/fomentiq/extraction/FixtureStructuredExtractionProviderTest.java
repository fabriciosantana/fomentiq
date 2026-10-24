package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionProvider.ExtractionRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureStructuredExtractionProviderTest {
    @Test
    void mapsFrozenReviewedCandidatesWithoutChangingFixtureOrUsingNetwork() {
        var provider = new FixtureStructuredExtractionProvider(new ObjectMapper());
        UUID noticeVersion = UUID.randomUUID();
        var response = provider.extract(new ExtractionRequest(UUID.randomUUID(), ExtractionSchemaV1.VERSION, List.of(
                new StructuredExtractionProvider.SourceExcerpt(noticeVersion + ":15", noticeVersion,
                        "https://finep.gov.br/notice.pdf", "edital", 15, null, "Prazo final 06/06/2025"),
                new StructuredExtractionProvider.SourceExcerpt(noticeVersion + ":12", noticeVersion,
                        "https://finep.gov.br/notice.pdf", "edital", 12, null, "Orçamento R$500.000.000,00"))));

        assertThat(response.fields()).containsOnlyKeys(ExtractionSchemaV1.FIELDS.toArray(String[]::new));
        assertThat(response.fields().get("deadline").value()).contains("2025-06-06T17:00:00-03:00");
        assertThat(response.fields().get("trl").value()).isNull();
        assertThat(response.fields().get("trl").evidence()).isEmpty();
        assertThat(response.fields().get("totalBudget").evidence()).isNotEmpty();
        assertThat(response.provider()).isEqualTo("deterministic-fixture");
    }
}
