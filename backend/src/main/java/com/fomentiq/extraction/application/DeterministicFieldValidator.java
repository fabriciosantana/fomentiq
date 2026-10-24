package com.fomentiq.extraction.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.domain.ValidationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic rule checks. The model never decides its own validation state. */
public final class DeterministicFieldValidator {
    private final ObjectMapper mapper;
    public DeterministicFieldValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ValidationStatus validate(String field, String value, boolean hasEvidence) {
        if (value == null || value.isBlank()) return ValidationStatus.MISSING;
        if (isConflict(value)) return ValidationStatus.CONFLICT;
        if (!ExtractionSchemaV1.CRITICAL.contains(field) || !hasEvidence) return ValidationStatus.EXTRACTED;
        try {
            return valid(field, value) ? ValidationStatus.VALIDATED : ValidationStatus.EXTRACTED;
        } catch (Exception exception) { return ValidationStatus.EXTRACTED; }
    }

    public ValidationStatus validateAcrossVersions(String field, List<String> values, boolean hasEvidence) {
        Set<String> distinct = new HashSet<>(values.stream().filter(value -> value != null && !value.isBlank()).toList());
        if (distinct.size() > 1) return ValidationStatus.CONFLICT;
        return validate(field, distinct.stream().findFirst().orElse(null), hasEvidence);
    }

    private boolean valid(String field, String value) throws Exception {
        return switch (field) {
            case "deadline" -> validDate(value);
            case "totalBudget" -> validBudget(value);
            case "eligibility", "counterpart" -> mapper.readTree(value).isObject()
                    && mapper.readTree(value).size() > 0;
            case "trl" -> validTrl(value);
            case "requiresIct" -> "true".equals(value) || "false".equals(value);
            default -> false;
        };
    }

    private boolean validDate(String value) {
        try { OffsetDateTime.parse(value); return true; }
        catch (DateTimeParseException ignored) { LocalDate.parse(value); return true; }
    }

    private boolean validBudget(String value) throws Exception {
        JsonNode budget = mapper.readTree(value);
        if (!"BRL".equals(budget.path("currency").asText())) return false;
        BigDecimal amount = new BigDecimal(budget.path("amount").asText());
        return amount.signum() >= 0;
    }

    private boolean validTrl(String value) throws Exception {
        JsonNode trl = mapper.readTree(value);
        if (trl.isIntegralNumber()) return trl.intValue() >= 1 && trl.intValue() <= 9;
        int min = trl.path("min").asInt(0), max = trl.path("max").asInt(0);
        return min >= 1 && max <= 9 && min <= max;
    }

    private boolean isConflict(String value) {
        try { return mapper.readTree(value).path("conflictValues").isArray(); }
        catch (Exception ignored) { return false; }
    }
}
