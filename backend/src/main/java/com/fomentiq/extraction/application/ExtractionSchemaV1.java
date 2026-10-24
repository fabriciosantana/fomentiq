package com.fomentiq.extraction.application;

import java.util.List;
import java.util.Set;
import java.util.Map;

/** Stable field contract shared by extraction providers and deterministic validation. */
public final class ExtractionSchemaV1 {
    public static final String VERSION = "finep-call-v1";
    public static final List<String> FIELDS = List.of("agency", "program", "instrumentType", "title", "summary",
            "status", "publicationDate", "openingDate", "deadline", "totalBudget", "projectFunding",
            "eligibility", "regions", "trl", "requiresIct", "counterpart", "technologyThemes",
            "eligibleExpenses", "evaluationCriteria", "requiredDocuments", "officialLinks");
    public static final Set<String> CRITICAL = Set.of("deadline", "totalBudget", "eligibility", "counterpart",
            "trl", "requiresIct");
    public static final Map<String, ValueType> TYPES = Map.ofEntries(
            Map.entry("agency", ValueType.TEXT), Map.entry("program", ValueType.TEXT),
            Map.entry("instrumentType", ValueType.TEXT), Map.entry("title", ValueType.TEXT),
            Map.entry("summary", ValueType.TEXT), Map.entry("status", ValueType.TEXT),
            Map.entry("publicationDate", ValueType.DATE), Map.entry("openingDate", ValueType.DATE),
            Map.entry("deadline", ValueType.DATE_TIME), Map.entry("totalBudget", ValueType.MONEY),
            Map.entry("projectFunding", ValueType.STRUCTURED_OBJECT), Map.entry("eligibility", ValueType.STRUCTURED_OBJECT),
            Map.entry("regions", ValueType.STRUCTURED_OBJECT), Map.entry("trl", ValueType.TRL_RANGE),
            Map.entry("requiresIct", ValueType.BOOLEAN), Map.entry("counterpart", ValueType.STRUCTURED_OBJECT),
            Map.entry("technologyThemes", ValueType.TEXT_LIST), Map.entry("eligibleExpenses", ValueType.TEXT_LIST),
            Map.entry("evaluationCriteria", ValueType.STRUCTURED_LIST), Map.entry("requiredDocuments", ValueType.STRUCTURED_OBJECT),
            Map.entry("officialLinks", ValueType.URL_LIST));

    static {
        if (!TYPES.keySet().equals(Set.copyOf(FIELDS))) {
            throw new IllegalStateException("Extraction schema type map does not match configured fields");
        }
    }

    private ExtractionSchemaV1() { }

    public enum ValueType { TEXT, DATE, DATE_TIME, MONEY, STRUCTURED_OBJECT, TRL_RANGE, BOOLEAN, TEXT_LIST,
        STRUCTURED_LIST, URL_LIST }
}
