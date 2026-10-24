package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fomentiq.extraction.application.DocumentExcerptSelector;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentExcerptSelectorTest {
    @Test
    void ranksAmendmentsAndBoundsSelectedDocumentsPagesAndCharacters() {
        DocumentExcerptSelector selector = new DocumentExcerptSelector(bytes -> {
            List<DocumentExcerptSelector.PageText> pages = new ArrayList<>();
            for (int page = 1; page <= 5; page++) {
                pages.add(new DocumentExcerptSelector.PageText(page, "prazo orçamento elegibilidade " + "x".repeat(1700)));
            }
            return pages;
        });
        List<DocumentExcerptSelector.DocumentInput> documents = new ArrayList<>();
        for (int i = 0; i < 10; i++) documents.add(new DocumentExcerptSelector.DocumentInput(
                UUID.randomUUID(), "Resultado " + i, "https://finep.gov.br/" + i, new byte[]{1}));
        documents.add(new DocumentExcerptSelector.DocumentInput(UUID.randomUUID(), "2a Rerratificação edital",
                "https://finep.gov.br/amendment", new byte[]{2}));

        List<StructuredExtractionProvider.SourceExcerpt> excerpts = selector.select(documents);
        assertThat(excerpts).hasSizeLessThanOrEqualTo(DocumentExcerptSelector.MAX_EXCERPTS);
        assertThat(excerpts.stream().mapToInt(item -> item.text().length()).sum())
                .isLessThanOrEqualTo(DocumentExcerptSelector.MAX_TOTAL_CHARS);
        assertThat(excerpts).allSatisfy(item -> assertThat(item.text().length())
                .isLessThanOrEqualTo(DocumentExcerptSelector.MAX_EXCERPT_CHARS));
        assertThat(excerpts.getFirst().title()).contains("Rerratificação");
    }
}
