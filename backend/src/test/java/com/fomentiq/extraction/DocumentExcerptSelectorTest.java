package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fomentiq.extraction.application.DocumentExcerptSelector;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentExcerptSelectorTest {
    @Test
    void selectsByFieldAndVersionAndExcludesResultDocuments() {
        UUID originalId = UUID.randomUUID();
        UUID amendmentId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        var selector = new DocumentExcerptSelector(bytes -> List.of(new DocumentExcerptSelector.PageText(3,
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8))));

        var result = selector.selectWithCoverage(List.of(
                document(UUID.randomUUID(), resultId, "RESULT_FINAL", "resultado", "Prazo encerrado; orçamento"),
                document(UUID.randomUUID(), originalId, "NOTICE", "Edital", "Prazo original para submissão até 25/04/2025"),
                document(UUID.randomUUID(), amendmentId, "AMENDMENT", "Rerratificação", "Prazo alterado para 06/06/2025")));

        assertThat(result.excerpts()).hasSize(2);
        assertThat(result.excerpts()).extracting("documentId").containsExactly(originalId, amendmentId);
        assertThat(result.excerpts()).allSatisfy(excerpt -> {
            assertThat(excerpt.documentVersionId()).isNotNull();
            assertThat(excerpt.documentType()).isIn("NOTICE", "AMENDMENT");
            assertThat(excerpt.startOffset()).isZero();
            assertThat(excerpt.endOffset()).isEqualTo(excerpt.text().length());
        });
        assertThat(result.coverage().fields()).containsKey("deadline");
        assertThat(result.coverage().fields().get("deadline")).allSatisfy(entry -> assertThat(entry.selected()).isTrue());
        assertThat(result.coverage().maxExcerpts()).isEqualTo(24);
        assertThat(result.coverage().maxExcerptChars()).isEqualTo(1400);
        assertThat(result.coverage().maxTotalChars()).isEqualTo(24000);
    }

    @Test
    void recordsLongClausesAndUnscannedVersionsWithoutTruncatingThem() {
        String longClause = "Contrapartida " + "condição documental ".repeat(100);
        var docs = new java.util.ArrayList<DocumentExcerptSelector.DocumentInput>();
        for (int index = 0; index < 7; index++) {
            String type = index == 0 ? "NOTICE" : "ANNEX";
            docs.add(document(UUID.randomUUID(), UUID.randomUUID(), type, "Doc " + index,
                    index == 0 ? longClause : "Anexo sem regra crítica"));
        }
        var selector = new DocumentExcerptSelector(bytes -> List.of(new DocumentExcerptSelector.PageText(14,
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8))));

        var result = selector.selectWithCoverage(docs);

        assertThat(result.excerpts()).noneMatch(excerpt -> excerpt.text().length() > 1400);
        assertThat(result.coverage().gaps()).anyMatch(gap -> gap.contains("PASSAGE_TOO_LONG"));
        assertThat(result.coverage().gaps()).anyMatch(gap -> gap.contains("SOURCE_UNSCANNED"));
        assertThat(result.coverage().fields().get("counterpart")).anyMatch(entry ->
                "SOURCE_UNSCANNED".equals(entry.gapReason()) && !entry.selected());
    }

    @Test
    void passageAndOffsetsAreExactSlicesOfTheExtractedSourceText() {
        String source = "Introdução sem requisito.\n\nContexto geral.\n\nPrazo final para submissão até 25/04/2025.\n\nRodapé.";
        var selector = new DocumentExcerptSelector(bytes -> List.of(new DocumentExcerptSelector.PageText(2,
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8))));
        var excerpt = selector.selectWithCoverage(List.of(document(UUID.randomUUID(), UUID.randomUUID(), "NOTICE",
                "Edital", source))).excerpts().stream().filter(item -> item.text().contains("25/04/2025"))
                .findFirst().orElseThrow();

        assertThat(excerpt.startOffset()).isGreaterThan(0);
        assertThat(source.substring(excerpt.startOffset(), excerpt.endOffset())).isEqualTo(excerpt.text());
    }

    @Test
    void noMatchingPassageIsRecordedAsCoverageGapRatherThanAbsence() {
        var selector = new DocumentExcerptSelector(bytes -> List.of(new DocumentExcerptSelector.PageText(1,
                "General description of a public program.")));

        var result = selector.selectWithCoverage(List.of(document(UUID.randomUUID(), UUID.randomUUID(), "NOTICE",
                "Edital", "General description of a public program.")));

        assertThat(result.coverage().gaps()).contains("deadline:NO_CANDIDATE_PASSAGE", "totalBudget:NO_CANDIDATE_PASSAGE",
                "eligibility:NO_CANDIDATE_PASSAGE", "counterpart:NO_CANDIDATE_PASSAGE", "trl:NO_CANDIDATE_PASSAGE",
                "requiresIct:NO_CANDIDATE_PASSAGE");
        assertThat(result.coverage().complete()).isFalse();
    }

    private DocumentExcerptSelector.DocumentInput document(UUID versionId, UUID documentId, String type,
                                                            String title, String page) {
        return new DocumentExcerptSelector.DocumentInput(versionId, title, "https://finep.gov.br/doc.pdf",
                page.getBytes(java.nio.charset.StandardCharsets.UTF_8), documentId, type);
    }
}
