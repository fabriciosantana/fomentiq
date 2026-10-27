package com.fomentiq.extraction.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.domain.ValidationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic scalar checks. Compound legal rules remain extracted for factual review. */
public final class DeterministicFieldValidator {
    private static final Pattern MONEY = Pattern.compile("(?:r\\$|brl)\\s*([0-9](?:[0-9]|[.,](?=[0-9]))*)");
    private static final Pattern GLOBAL_BUDGET = Pattern.compile("(?:orcamento global|global budget|"
            + "orcamento total|total budget)");

    private final ObjectMapper mapper;

    public DeterministicFieldValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ValidationStatus validate(String field, String value, boolean hasEvidence) {
        if (value == null || value.isBlank()) return ValidationStatus.MISSING;
        if (isConflict(value)) return ValidationStatus.CONFLICT;
        if (!ExtractionSchemaV1.CRITICAL.contains(field) || !hasEvidence) return ValidationStatus.EXTRACTED;
        // Scalar budget/ICT facts require source text, not just a well-formed value and any citation.
        if (field.equals("totalBudget") || field.equals("requiresIct")
                || field.equals("eligibility") || field.equals("counterpart")) return ValidationStatus.EXTRACTED;
        try {
            return valid(field, value) ? ValidationStatus.VALIDATED : ValidationStatus.EXTRACTED;
        } catch (Exception exception) {
            return ValidationStatus.EXTRACTED;
        }
    }

    public ValidationStatus validateAcrossVersions(String field, List<String> values, boolean hasEvidence) {
        List<String> nonBlank = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (nonBlank.size() > 1) return ValidationStatus.CONFLICT;
        return validate(field, nonBlank.stream().findFirst().orElse(null), hasEvidence);
    }

    /** Scalar critical fields need value-bearing source text, not merely a well-formed value and any citation. */
    public ValidationStatus validateWithEvidence(String field, String value, boolean hasEvidence,
            List<String> quotes) {
        if (value == null || value.isBlank()) return ValidationStatus.MISSING;
        if (isConflict(value)) return ValidationStatus.CONFLICT;
        if (!ExtractionSchemaV1.CRITICAL.contains(field) || !hasEvidence) return ValidationStatus.EXTRACTED;
        try {
            if (!valid(field, value) || quotes == null || quotes.isEmpty()) return ValidationStatus.EXTRACTED;
            if (field.equals("deadline")) {
                return dateSupported(value, quotes) ? ValidationStatus.VALIDATED : ValidationStatus.EXTRACTED;
            }
            JsonNode parsed = mapper.readTree(value);
            boolean supported = switch (field) {
                case "totalBudget" -> budgetSupported(parsed, quotes);
                case "requiresIct" -> requiresIctSupported(parsed.asBoolean(), quotes);
                case "trl" -> trlSupported(parsed, quotes);
                default -> false;
            };
            return supported ? ValidationStatus.VALIDATED : ValidationStatus.EXTRACTED;
        } catch (Exception invalid) {
            return ValidationStatus.EXTRACTED;
        }
    }

    /**
     * Preserve composite values and their literal citations without asserting legal completeness or relation.
     * A complete-looking clause set deliberately remains EXTRACTED until factual review.
     */
    public ValidationStatus validateComposite(String field, String value,
            StructuredExtractionProvider.Candidate candidate, boolean hasEvidence, boolean coverageComplete,
            Map<String, StructuredExtractionProvider.SourceExcerpt> selected) {
        if (value == null || value.isBlank()) return ValidationStatus.MISSING;
        if (isConflict(value)) return ValidationStatus.CONFLICT;
        // Candidate clauses, coverage and literal co-occurrence cannot prove legal completeness or relation.
        // ExtractionService independently retains only verified literal evidence; factual approval is separate.
        return ValidationStatus.EXTRACTED;
    }

    private boolean valid(String field, String value) throws Exception {
        return switch (field) {
            case "deadline" -> validDate(value);
            case "totalBudget" -> validBudget(value);
            case "eligibility", "counterpart" -> false;
            case "trl" -> validTrl(value);
            case "requiresIct" -> "true".equals(value) || "false".equals(value);
            default -> false;
        };
    }

    private static boolean dateSupported(String value, List<String> quotes) {
        OffsetDateTime dateTime;
        try {
            dateTime = OffsetDateTime.parse(value);
        } catch (DateTimeParseException invalid) {
            return false;
        }
        String date = dateTime.toLocalDate().toString();
        String brDate = String.format(java.util.Locale.ROOT, "%02d/%02d/%04d", dateTime.getDayOfMonth(),
                dateTime.getMonthValue(), dateTime.getYear());
        String time = String.format(java.util.Locale.ROOT, "%02d:%02d", dateTime.getHour(), dateTime.getMinute());
        String offset = dateTime.getOffset().getId();
        String compactOffset = offset.replace(":", "");
        return quotes.stream().anyMatch(quote -> quote.contains(date) && hasTimeOffset(quote, time, offset,
                compactOffset)) || quotes.stream().anyMatch(quote -> quote.contains(brDate)
                        && hasTimeOffset(quote, time, offset, compactOffset));
    }

    private static boolean hasTimeOffset(String quote, String time, String offset, String compactOffset) {
        return (quote.contains(time) || quote.contains(time.replace(":", "h"))
                || quote.contains(time.substring(0, 2) + "h"))
                && (quote.contains(offset) || quote.contains(compactOffset)
                        || offset.equals("Z") && normalize(quote).contains("utc"));
    }

    private static boolean budgetSupported(JsonNode budget, List<String> quotes) {
        if (!"BRL".equals(budget.path("currency").asText()) || !budget.path("amount").isTextual()) return false;
        String qualifier = budget.path("qualifier").asText();
        BigDecimal expected;
        try {
            expected = new BigDecimal(budget.path("amount").asText());
        } catch (NumberFormatException invalid) {
            return false;
        }
        return quotes.stream().anyMatch(quote -> budgetExpressions(quote).stream().anyMatch(expression ->
                expression.amount().compareTo(expected) == 0 && expression.qualifier().equals(qualifier)));
    }

    private static boolean requiresIctSupported(boolean required, List<String> quotes) {
        return quotes.stream().flatMap(quote -> java.util.Arrays.stream(normalize(quote)
                        .split("[;.!?\\r\\n]+")))
                .map(String::trim).anyMatch(clause -> required ? REQUIRED_ICT.matcher(clause).matches()
                        : NOT_REQUIRED_ICT.matcher(clause).matches());
    }

    private static final Pattern REQUIRED_ICT = Pattern.compile(
            "(?:(?:somente|apenas|only)\\s+icts?\\s+(?:podem|may|can)\\s+"
                    + "(?:apresentar|submeter|submit|present)\\s+(?:as?\\s+)?(?:propostas?|proposals?)|"
                    + "(?:(?:somente|apenas|only)\\s+)?(?:proponentes?|proponents?|applicants?|"
                    + "eligible applicants?|submitters?|submitting entities|executores?|executors?)\\s+"
                    + "(?:devem|deverao|must|shall)\\s+(?:ser|be)\\s+(?:an?\\s+)?icts?)");
    private static final Pattern NOT_REQUIRED_ICT = Pattern.compile(
            "(?:nao\\s+e\\s+necessario\\s+que\\s+(?:o\\s+)?proponente\\s+seja\\s+icts?|"
                    + "(?:the\\s+)?(?:eligible\\s+)?(?:applicant|proponent)\\s+is\\s+not\\s+required\\s+to\\s+be\\s+an?\\s+ict)");

    private record BudgetExpression(BigDecimal amount, String qualifier) { }

    private static List<BudgetExpression> budgetExpressions(String quote) {
        List<BudgetExpression> expressions = new java.util.ArrayList<>();
        String normalized = normalize(quote);
        for (String clause : normalized.split("(?<=[.!?])\\s+|[;\\r\\n]+")) {
            Matcher money = MONEY.matcher(clause);
            int count = 0;
            while (money.find()) count++;
            Matcher globalBudget = GLOBAL_BUDGET.matcher(clause);
            int anchorCount = 0;
            while (globalBudget.find()) anchorCount++;
            if (count != 1 || anchorCount != 1) continue;
            Matcher amountMatch = MONEY.matcher(clause);
            if (!amountMatch.find()) continue;
            BigDecimal amount = parseLocalizedNumber(amountMatch.group(1));
            if (amount == null) continue;
            globalBudget.reset();
            if (!globalBudget.find() || globalBudget.start() > amountMatch.start()) continue;
            String relation = clause.substring(globalBudget.end(), amountMatch.start()).trim();
            String qualifier;
            if (relation.matches("(?:(?:e|is|sera|:|de|of)\\s*)*(?:ate|up to|limite maximo|maximo)"
                    + "\\s*(?:de|of)?")) qualifier = "UP_TO";
            else if (relation.matches("(?:(?:e|is|sera|:|de|of)\\s*)*(?:exatamente|exactly|totaliza|"
                    + "corresponde a|igual a)")) qualifier = "EXACT";
            else continue;
            expressions.add(new BudgetExpression(amount, qualifier));
        }
        return expressions;
    }

    private static BigDecimal parseLocalizedNumber(String token) {
        try {
            int comma = token.lastIndexOf(',');
            int dot = token.lastIndexOf('.');
            String canonical;
            if (comma >= 0 && dot >= 0) {
                char decimalSeparator = comma > dot ? ',' : '.';
                char groupingSeparator = decimalSeparator == ',' ? '.' : ',';
                canonical = token.replace(String.valueOf(groupingSeparator), "");
                if (decimalSeparator == ',') canonical = canonical.replace(',', '.');
            } else if (comma >= 0) {
                canonical = token.length() - comma - 1 == 3 ? token.replace(",", "") : token.replace(',', '.');
            } else if (dot >= 0) {
                canonical = token.length() - dot - 1 == 3 ? token.replace(".", "") : token;
            } else canonical = token;
            return new BigDecimal(canonical);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static boolean trlSupported(JsonNode trl, List<String> quotes) {
        if (!trl.isObject() || trl.path("min").isNull() || trl.path("max").isNull()) return false;
        String min = trl.path("min").asText(), max = trl.path("max").asText();
        return quotes.stream().anyMatch(quote -> {
            String normalized = normalize(quote);
            return normalized.contains("trl") && numberAppears(min, quote) && numberAppears(max, quote);
        });
    }

    private static boolean numberAppears(String expected, String quote) {
        if (expected == null) return false;
        BigDecimal target;
        try {
            target = new BigDecimal(expected);
        } catch (NumberFormatException invalid) {
            return false;
        }
        Matcher matcher = Pattern.compile("(?<!\\d)\\d[\\d.,]*(?!\\d)").matcher(quote);
        while (matcher.find()) {
            String token = matcher.group();
            String canonical = token;
            if (token.contains(",")) canonical = token.replace(".", "").replace(',', '.');
            else if (token.chars().filter(ch -> ch == '.').count() > 1) canonical = token.replace(".", "");
            try {
                if (target.compareTo(new BigDecimal(canonical)) == 0) return true;
            } catch (NumberFormatException ignored) {
                // Try the next visible number.
            }
        }
        return false;
    }

    private boolean validDate(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            LocalDate.parse(value);
            return true;
        }
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
        try {
            return mapper.readTree(value).path("conflictValues").isArray();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
