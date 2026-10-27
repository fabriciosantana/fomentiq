package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fomentiq.extraction.application.DocumentExcerptSelector;
import com.fomentiq.extraction.infrastructure.PdfBoxPageTextExtractor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GoldenFixtureSelectionTest {
    private static final String ROOT = "fixtures/finep/proinfra-2024-expansao-751/";

    @Test
    void includesKnownCriticalPagesAcrossOriginalAndAmendmentsWithoutExceedingBounds() throws Exception {
        var documents = List.of(
                input("12_12-2024_EDIP2024_EditaL.pdf", "Edital original", "NOTICE"),
                input("23_12_2024_Exp_2024_1a_Rerratificacao.pdf", "1a Rerratificação", "AMENDMENT"),
                input("07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf", "2a Rerratificação", "AMENDMENT"),
                input("07_04_2025_Edital_PROINFRA2024-Expansao_comunicado.pdf", "Comunicado", "OTHER"));
        var result = new DocumentExcerptSelector(new PdfBoxPageTextExtractor()).selectWithCoverage(documents);
        var pages = result.excerpts().stream().map(excerpt -> excerpt.title() + ":p" + excerpt.page()).toList();

        assertThat(result.excerpts()).isNotEmpty().allSatisfy(excerpt -> {
            assertThat(excerpt.text().length()).isLessThanOrEqualTo(1400);
            assertThat(excerpt.documentType()).isNotEqualTo("RESULT_FINAL");
        });
        assertThat(result.coverage().selectedChars()).isLessThanOrEqualTo(24000);
        assertThat(result.excerpts()).hasSizeLessThanOrEqualTo(24);
        assertThat(pages).as("selected fixture pages").anyMatch(page -> page.contains("2a Rerratificação:p15"));
        assertThat(pages).as("selected fixture pages").anyMatch(page -> page.contains("2a Rerratificação:p14"));
        assertThat(pages).as("eligibility evidence page; selected=%s", pages)
                .anyMatch(page -> page.contains("2a Rerratificação:p4"));
        assertThat(result.excerpts().stream().filter(excerpt -> excerpt.title().equals("2a Rerratificação")
                && excerpt.page() == 12)).as("global budget evidence page; selected=%s", pages).isNotEmpty();
        assertThat(result.excerpts().stream().filter(excerpt -> excerpt.title().equals("2a Rerratificação")
                && excerpt.page() == 15)).isNotEmpty().allSatisfy(excerpt ->
                assertThat(excerpt.text()).contains("06/06/2025"));
        assertThat(result.coverage().fields().keySet()).contains("deadline", "totalBudget", "eligibility",
                "counterpart", "requiresIct");
    }

    private DocumentExcerptSelector.DocumentInput input(String file, String title, String type) throws Exception {
        byte[] pdf = new ClassPathResource(ROOT + "pdf/" + file).getContentAsByteArray();
        UUID documentId = UUID.nameUUIDFromBytes(file.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new DocumentExcerptSelector.DocumentInput(UUID.nameUUIDFromBytes((file + "-v1")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), title,
                "https://www.finep.gov.br/documents/" + file, pdf, documentId, type);
    }
}
