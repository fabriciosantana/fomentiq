package com.fomentiq.extraction.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Extracts bounded PDF page excerpts and ranks likely source text for the configured MVP fields. */
public final class DocumentExcerptSelector {
    public static final int MAX_DOCUMENTS = 6;
    public static final int MAX_EXCERPTS = 24;
    public static final int MAX_EXCERPT_CHARS = 1400;
    public static final int MAX_TOTAL_CHARS = 24000;
    private static final List<String> TERMS = List.of("prazo", "submissão", "submissao", "orçamento", "orcamento",
            "elegibilidade", "contrapartida", "trl", "ict", "retificação", "rerratificação", "requisitos");
    private final PdfPageTextExtractor pdfExtractor;

    public DocumentExcerptSelector(PdfPageTextExtractor pdfExtractor) { this.pdfExtractor = pdfExtractor; }

    public List<StructuredExtractionProvider.SourceExcerpt> select(List<DocumentInput> documents) {
        return documents.stream().sorted(Comparator.comparingInt(document -> documentPriority(document.title())))
                .limit(MAX_DOCUMENTS)
                .flatMap(document -> pdfExtractor.pages(document.bytes()).stream()
                .map(page -> new RankedExcerpt(document, page.number(), page.text(), score(page.text()))))
                .filter(candidate -> !candidate.text().isBlank())
                .sorted(Comparator.comparingInt(RankedExcerpt::score).reversed()
                        .thenComparing(candidate -> candidate.document().title()).thenComparingInt(RankedExcerpt::page))
                .limit(MAX_EXCERPTS)
                .collect(() -> new ArrayList<StructuredExtractionProvider.SourceExcerpt>(), (result, candidate) -> {
                    int remaining = MAX_TOTAL_CHARS - result.stream().mapToInt(item -> item.text().length()).sum();
                    if (remaining <= 0) return;
                    int end = Math.min(Math.min(candidate.text().length(), MAX_EXCERPT_CHARS), remaining);
                    String text = candidate.text().substring(0, end);
                    String segmentId = candidate.document().versionId() + ":" + candidate.page();
                    result.add(new StructuredExtractionProvider.SourceExcerpt(segmentId, candidate.document().versionId(),
                            candidate.document().sourceUrl(), candidate.document().title(), candidate.page(), null, text));
                }, ArrayList::addAll);
    }

    private static int score(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return (int) TERMS.stream().filter(value::contains).count();
    }

    private static int documentPriority(String title) {
        String value = title.toLowerCase(Locale.ROOT);
        if (value.contains("rerrat") || value.contains("retifica")) return 0;
        if (value.contains("edital") || value.contains("chamada")) return 1;
        if (value.contains("comunicado") || value.contains("esclarecimento")) return 2;
        if (value.contains("faq") || value.contains("pergunta")) return 3;
        if (value.contains("anexo")) return 4;
        if (value.contains("resultado")) return 5;
        return 6;
    }

    public record DocumentInput(UUID versionId, String title, String sourceUrl, byte[] bytes) {
        public DocumentInput { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    public record PageText(int number, String text) { }
    @FunctionalInterface public interface PdfPageTextExtractor { List<PageText> pages(byte[] bytes); }
    private record RankedExcerpt(DocumentInput document, int page, String text, int score) { }
}
