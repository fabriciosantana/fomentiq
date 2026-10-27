package com.fomentiq.extraction.application;

import com.fomentiq.extraction.application.StructuredExtractionProvider.SourceExcerpt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Field-aware, bounded selector. Coverage is an audit input, never a claim of documentary absence. */
public final class DocumentExcerptSelector {
    public static final int MAX_DOCUMENTS = 6, MAX_EXCERPTS = 24, MAX_EXCERPT_CHARS = 1400,
            MAX_TOTAL_CHARS = 24000;
    public static final List<String> CRITICAL_FIELDS = List.of("deadline", "totalBudget", "eligibility",
            "counterpart", "trl", "requiresIct");
    private static final Map<String, List<String>> PROFILES = Map.of(
            "deadline", List.of("prazo", "encerramento", "submissão", "submissao", "data limite", "até às", "até as"),
            "totalBudget", List.of("orçamento", "orcamento", "recursos disponíveis", "recursos disponiveis", "r$"),
            "eligibility", List.of("elegibilidade", "elegíveis", "elegiveis", "não elegíveis",
                    "nao elegiveis", "vedado", "habilitação", "habilitacao"),
            "counterpart", List.of("contrapartida", "percentual", "contrapartida financeira", "dispensad", "isento"),
            "trl", List.of("trl", "nível de maturidade", "nivel de maturidade", "prontidão tecnológica",
                    "prontidao tecnologica"),
            "requiresIct", List.of("ict", "instituição científica", "instituicao cientifica",
                    "instituição de ciência", "instituicao de ciencia"),
            "program", List.of("programa", "chamada pública", "chamada publica", "seleção pública", "selecao publica"));
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile("(?<=[.;])\\s+(?=[A-ZÁÉÍÓÚÂÊÔÃÕ0-9(])|\\R{2,}");
    private final PdfPageTextExtractor pdfExtractor;

    public DocumentExcerptSelector(PdfPageTextExtractor pdfExtractor) { this.pdfExtractor = pdfExtractor; }

    public List<SourceExcerpt> select(List<DocumentInput> documents) { return selectWithCoverage(documents).excerpts(); }

    public SelectionResult selectWithCoverage(List<DocumentInput> documents) {
        List<DocumentInput> ordered = documents.stream().sorted(Comparator.comparingInt(DocumentExcerptSelector::documentPriority)
                .thenComparing(DocumentInput::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        List<DocumentInput> requirementSources = ordered.stream().filter(document -> !isResult(document)).toList();
        List<DocumentInput> scanned = requirementSources.stream().limit(MAX_DOCUMENTS).toList();
        Map<String, List<CandidatePage>> byField = new LinkedHashMap<>();
        CRITICAL_FIELDS.forEach(field -> byField.put(field, new ArrayList<>()));
        List<SourceScan> sourceScans = new ArrayList<>();
        for (DocumentInput result : ordered.stream().filter(DocumentExcerptSelector::isResult).toList()) {
            sourceScans.add(new SourceScan(result.documentId(), result.versionId(), result.documentType(), 0, false,
                    "RESULT_NOT_REQUIREMENT_SOURCE"));
        }
        for (DocumentInput document : scanned) {
            List<PageText> pages = pdfExtractor.pages(document.bytes());
            sourceScans.add(new SourceScan(document.documentId(), document.versionId(), document.documentType(),
                    pages.size(), true, null));
            for (PageText page : pages) {
                if (page.text() == null || page.text().isBlank()) continue;
                List<Clause> clauses = clauses(page.text());
                for (String field : CRITICAL_FIELDS) {
                    byField.get(field).addAll(candidateWindows(document, page, clauses, field));
                }
            }
        }

        LinkedHashMap<String, Selected> selected = new LinkedHashMap<>();
        Map<String, Set<String>> selectedFor = new HashMap<>();
        Map<String, List<CoverageEntry>> entries = new LinkedHashMap<>();
        List<String> gaps = new ArrayList<>();
        int chars = 0;
        // Reserve at least the best applicable passage per critical field and document version before spending budget by score.
        for (String field : CRITICAL_FIELDS) {
            List<CandidatePage> candidates = byField.get(field).stream()
                    .sorted(Comparator.comparingInt(CandidatePage::score).reversed()
                            .thenComparingInt(c -> documentPriority(c.document())).thenComparingInt(CandidatePage::page)).toList();
            LinkedHashMap<UUID, CandidatePage> perVersion = new LinkedHashMap<>();
            for (CandidatePage candidate : candidates) perVersion.putIfAbsent(candidate.document().versionId(), candidate);
            for (CandidatePage candidate : perVersion.values()) {
                AddResult add = add(selected, candidate, chars);
                if (add.added()) {
                    chars += add.chars();
                    selectedFor.computeIfAbsent(key(candidate), ignored -> new HashSet<>()).add(field);
                }
                else gaps.add(gap(field, candidate, add.reason()));
            }
        }
        // Keep one title-page passage from the primary notice for identifying the call and program.
        // Critical-field reservations take precedence; this supplemental excerpt uses only remaining budget.
        DocumentInput primaryNotice = scanned.stream()
                .filter(document -> "NOTICE".equalsIgnoreCase(document.documentType())).findFirst().orElse(null);
        if (primaryNotice != null) {
            PageText firstPage = pdfExtractor.pages(primaryNotice.bytes()).stream()
                    .filter(page -> page.number() == 1).findFirst().orElse(null);
            if (firstPage != null && firstPage.text() != null && !firstPage.text().isBlank()) {
                List<Clause> pageClauses = clauses(firstPage.text());
                CandidatePage titlePassage = candidateWindows(primaryNotice, firstPage, pageClauses, "program").stream()
                        .sorted(Comparator.comparingInt(CandidatePage::score).reversed()).findFirst().orElse(null);
                if (titlePassage != null) {
                    AddResult add = add(selected, titlePassage, chars);
                    if (add.added()) chars += add.chars();
                }
            }
        }
        // Allocate remaining candidate passages with diversity already reserved.
        List<CandidatePage> rest = byField.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparingInt(CandidatePage::score).reversed()
                        .thenComparingInt(c -> documentPriority(c.document())).thenComparingInt(CandidatePage::page)).toList();
        for (CandidatePage candidate : rest) {
            AddResult add = add(selected, candidate, chars);
            if (add.added()) chars += add.chars();
            else if (add.reason() != null) {
                for (String field : candidateFields(candidate, byField)) gaps.add(gap(field, candidate, add.reason()));
            }
            for (String field : candidateFields(candidate, byField)) {
                if (selected.containsKey(key(candidate))) {
                    selectedFor.computeIfAbsent(key(candidate), ignored -> new HashSet<>()).add(field);
                }
            }
        }

        List<SourceExcerpt> excerpts = new ArrayList<>();
        for (Selected item : selected.values()) {
            CandidatePage candidate = item.candidate();
            String stableSuffix = UUID.nameUUIDFromBytes(key(candidate).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            String id = candidate.document().versionId() + ":" + candidate.page() + ":" + stableSuffix;
            excerpts.add(new SourceExcerpt(id, candidate.document().versionId(), candidate.document().sourceUrl(),
                    candidate.document().title(), candidate.page(), candidate.section(), candidate.text(),
                    candidate.document().documentId(), candidate.document().documentType(), candidate.startOffset(),
                    candidate.startOffset() + candidate.text().length()));
        }
        for (DocumentInput skipped : requirementSources.stream().skip(MAX_DOCUMENTS).toList()) {
            sourceScans.add(new SourceScan(skipped.documentId(), skipped.versionId(), skipped.documentType(),
                    0, false, "SOURCE_UNSCANNED"));
        }
        for (String field : CRITICAL_FIELDS) {
            if (byField.get(field).isEmpty()) gaps.add(field + ":NO_CANDIDATE_PASSAGE");
            for (CandidatePage candidate : byField.get(field)) {
                boolean chosen = selectedFor.getOrDefault(key(candidate), Set.of()).contains(field);
                String prefix = field + ":" + candidate.document().versionId() + ":page=" + candidate.page() + ":";
                String reason = candidate.tooLong() ? "PASSAGE_TOO_LONG" : chosen ? null : gaps.stream()
                        .filter(gap -> gap.startsWith(prefix)).map(gap -> gap.substring(prefix.length())).findFirst()
                        .orElse("EXCERPT_LIMIT");
                entries.computeIfAbsent(field, ignored -> new ArrayList<>()).add(new CoverageEntry(candidate.document().documentId(),
                        candidate.document().versionId(), candidate.document().documentType(), candidate.page(), true, chosen,
                        !candidate.tooLong(), reason));
                if (candidate.tooLong() && !chosen) gaps.add(gap(field, candidate, "PASSAGE_TOO_LONG"));
            }
            if (requirementSources.size() > MAX_DOCUMENTS) {
                gaps.add(field + ":SOURCE_UNSCANNED:document_limit=" + MAX_DOCUMENTS);
                for (DocumentInput skipped : requirementSources.stream().skip(MAX_DOCUMENTS).toList()) {
                    entries.computeIfAbsent(field, ignored -> new ArrayList<>()).add(new CoverageEntry(
                            skipped.documentId(), skipped.versionId(), skipped.documentType(), 0, false, false,
                            false, "SOURCE_UNSCANNED"));
                }
            }
        }
        SelectionCoverage coverage = new SelectionCoverage(1, MAX_DOCUMENTS, MAX_EXCERPTS, MAX_EXCERPT_CHARS,
                MAX_TOTAL_CHARS, chars, entries, List.copyOf(sourceScans), List.copyOf(new LinkedHashSet<>(gaps)),
                requirementSources.size() <= MAX_DOCUMENTS && gaps.isEmpty());
        return new SelectionResult(List.copyOf(excerpts), coverage);
    }

    private AddResult add(LinkedHashMap<String, Selected> selected, CandidatePage candidate, int chars) {
        String key = key(candidate);
        if (selected.containsKey(key)) return new AddResult(true, 0, null);
        if (candidate.tooLong()) return new AddResult(false, 0, "PASSAGE_TOO_LONG");
        if (candidate.text().length() > MAX_EXCERPT_CHARS) return new AddResult(false, 0, "PASSAGE_TOO_LONG");
        if (selected.size() >= MAX_EXCERPTS) return new AddResult(false, 0, "EXCERPT_LIMIT");
        if (chars + candidate.text().length() > MAX_TOTAL_CHARS) return new AddResult(false, 0, "CHARACTER_LIMIT");
        selected.put(key, new Selected(candidate));
        return new AddResult(true, candidate.text().length(), null);
    }

    private static List<String> candidateFields(CandidatePage page, Map<String, List<CandidatePage>> byField) {
        return byField.entrySet().stream().filter(e -> e.getValue().contains(page)).map(Map.Entry::getKey).toList();
    }
    private static String key(CandidatePage c) {
        return c.document().versionId() + ":" + c.page() + ":" + c.startOffset() + ":" + c.text();
    }
    private static String gap(String field, CandidatePage c, String reason) {
        return field + ":" + c.document().versionId() + ":page=" + c.page() + ":" + reason;
    }
    private static int score(String field, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        int score = PROFILES.get(field).stream().mapToInt(term -> occurrences(normalized, term)).sum();
        return switch (field) {
            case "deadline" -> score + (containsTimeOfDay(normalized) ? 8 : 0)
                    + (normalized.matches(".*\\bitem\\s+\\d+(?:\\.\\d+)*\\b.*") ? 4 : 0);
            case "totalBudget" -> score + occurrences(normalized, "r$") * 2
                    + (normalized.contains("até o limite de") && normalized.contains("r$") ? 10 : 0)
                    + (normalized.contains("recursos financeiros a serem concedidos") ? 8 : 0)
                    + (normalized.contains("recursos não reembolsáveis")
                            || normalized.contains("recursos nao reembolsaveis") ? 5 : 0);
            case "eligibility" -> score + occurrences(normalized, "não poderão")
                    + occurrences(normalized, "nao poderao") + occurrences(normalized, "proponente")
                    + occurrences(normalized, "elegív") * 4 + occurrences(normalized, "elegiv") * 4
                    + occurrences(normalized, "vedad") * 3 + occurrences(normalized, "credenci") * 2
                    + occurrences(normalized, "funcionamento regular") * 2 + occurrences(normalized, "poderes legais");
            case "counterpart" -> score + occurrences(normalized, "habitantes") * 3
                    + occurrences(normalized, "%") * 2 + occurrences(normalized, "demais")
                    + occurrences(normalized, "isenta");
            case "requiresIct" -> score + occurrences(normalized, "ict") * 2;
            default -> score;
        };
    }

    private static int occurrences(String text, String term) {
        int count = 0, offset = 0;
        while ((offset = text.indexOf(term, offset)) >= 0) { count++; offset += term.length(); }
        return count;
    }
    private static boolean containsTimeOfDay(String text) {
        return text.matches(".*\\b(?:[01]?\\d|2[0-3])(?::[0-5]\\d|h(?:[0-5]\\d)?)\\b.*");
    }
    private static boolean continuation(String field, String clause, String page) {
        String normalized = clause.toLowerCase(Locale.ROOT);
        String pageText = page.toLowerCase(Locale.ROOT);
        return switch (field) {
            case "deadline" -> (pageText.contains("submiss") || pageText.contains("prazo")
                    || pageText.contains("encerr") || pageText.matches(".*\\bitem\\s+\\d+(?:\\.\\d+)*\\b.*"))
                    && (normalized.matches(".*\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}\\b.*")
                    || normalized.matches(".*\\b\\d{1,2}\\s+de\\s+(janeiro|fevereiro|março|marco|abril|maio|"
                    + "junho|julho|agosto|setembro|outubro|novembro|dezembro)\\b.*")
                    || containsTimeOfDay(normalized));
            case "counterpart" -> (pageText.contains("contrapartida") || normalized.contains("habitante")
                    || normalized.contains("município") || normalized.contains("municipio")
                    || normalized.contains("demais municípios") || normalized.contains("demais municipios"))
                    && (normalized.contains("%") || normalized.contains("habitante")
                    || normalized.contains("município") || normalized.contains("municipio")
                    || normalized.contains("estado") || normalized.contains("isenta") || normalized.contains("dispens"));
            case "eligibility" -> pageText.contains("elegibilidade")
                    && (normalized.contains("ict") || normalized.contains("proponente")
                    || normalized.contains("institui") || normalized.contains("organiza")
                    || normalized.contains("vedad") || normalized.contains("não serão")
                    || normalized.contains("nao serao") || normalized.contains("exclu"));
            default -> false;
        };
    }
    private static List<CandidatePage> candidateWindows(DocumentInput document, PageText page,
                                                         List<Clause> clauses, String field) {
        Set<Integer> relevant = new java.util.TreeSet<>();
        for (int index = 0; index < clauses.size(); index++) {
            String text = clauses.get(index).text();
            if (score(field, text) > 0 || continuation(field, text, page.text())) {
                if (index > 0) relevant.add(index - 1);
                relevant.add(index);
                if (index + 1 < clauses.size()) relevant.add(index + 1);
            }
        }
        if (relevant.isEmpty()) return List.of();
        List<CandidatePage> result = new ArrayList<>();
        List<Clause> current = new ArrayList<>();
        int priorIndex = -2;
        for (int index : relevant) {
            Clause clause = clauses.get(index);
            boolean separate = !current.isEmpty() && (index != priorIndex + 1
                    || clause.endOffset() - current.getFirst().offset() > MAX_EXCERPT_CHARS);
            if (separate) {
                result.add(candidate(document, page.number(), field, current, page.text()));
                current.clear();
            }
            current.add(clause);
            priorIndex = index;
        }
        if (!current.isEmpty()) result.add(candidate(document, page.number(), field, current, page.text()));
        return List.copyOf(result);
    }

    private static CandidatePage candidate(DocumentInput document, int page, String field, List<Clause> clauses,
                                           String sourceText) {
        int start = clauses.getFirst().offset();
        int end = clauses.getLast().endOffset();
        String text = sourceText.substring(start, end);
        boolean tooLong = text.length() > MAX_EXCERPT_CHARS;
        return new CandidatePage(document, page, text, Math.max(1, score(field, text)), List.copyOf(clauses),
                start, end, tooLong);
    }
    private static List<Clause> clauses(String text) {
        List<Clause> result = new ArrayList<>();
        int offset = 0;
        for (String value : CLAUSE_BOUNDARY.split(text)) {
            String trimmed = value.trim();
            int position = text.indexOf(value, offset);
            if (!trimmed.isBlank()) result.add(new Clause(trimmed, Math.max(0, position),
                    Math.max(0, position) + value.length()));
            offset = Math.max(offset, position + value.length());
        }
        if (result.isEmpty() && !text.isBlank()) result.add(new Clause(text.trim(), 0, text.length()));
        return result;
    }
    private static int documentPriority(DocumentInput d) {
        if (d.documentType() != null) return switch (d.documentType().toUpperCase(Locale.ROOT)) {
            case "NOTICE" -> 0; case "AMENDMENT" -> 1; case "FAQ" -> 2; case "ANNEX" -> 3;
            case "RESULT_PRELIMINARY", "RESULT_FINAL" -> 9; default -> 5;
        };
        String value = Objects.toString(d.title(), "").toLowerCase(Locale.ROOT);
        if (value.contains("rerrat") || value.contains("retifica")) return 1;
        if (value.contains("edital") || value.contains("chamada")) return 0;
        if (value.contains("comunicado") || value.contains("esclarecimento")) return 2;
        if (value.contains("faq") || value.contains("pergunta")) return 2;
        if (value.contains("anexo")) return 3;
        if (value.contains("resultado")) return 9;
        return 5;
    }
    private static boolean isResult(DocumentInput d) { return documentPriority(d) >= 9; }

    public record DocumentInput(UUID versionId, String title, String sourceUrl, byte[] bytes,
                                UUID documentId, String documentType) {
        public DocumentInput(UUID versionId, String title, String sourceUrl, byte[] bytes) {
            this(versionId, title, sourceUrl, bytes, null, null);
        }
        public DocumentInput { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    public record PageText(int number, String text) { }
    @FunctionalInterface public interface PdfPageTextExtractor { List<PageText> pages(byte[] bytes); }
    public record SelectionResult(List<SourceExcerpt> excerpts, SelectionCoverage coverage) { }
    public record SelectionCoverage(int version, int maxDocuments, int maxExcerpts, int maxExcerptChars,
                                    int maxTotalChars, int selectedChars,
                                    Map<String, List<CoverageEntry>> fields, List<SourceScan> sourceScans,
                                    List<String> gaps, boolean complete) { }
    public record SourceScan(UUID documentId, UUID documentVersionId, String documentType,
                             int pageCount, boolean scanned, String skipReason) { }
    public record CoverageEntry(UUID documentId, UUID documentVersionId, String documentType, int page,
                                boolean candidate, boolean selected, boolean passageComplete, String gapReason) { }
    private record Clause(String text, int offset, int endOffset) { }
    private record CandidatePage(DocumentInput document, int page, String text, int score, List<Clause> clauses,
                                 int startOffset, int endOffset, boolean tooLong) {
        String section() {
            return clauses.isEmpty() ? null
                    : clauses.get(0).text().substring(0, Math.min(120, clauses.get(0).text().length()));
        }
    }
    private record Selected(CandidatePage candidate) { }
    private record AddResult(boolean added, int chars, String reason) { }
}
