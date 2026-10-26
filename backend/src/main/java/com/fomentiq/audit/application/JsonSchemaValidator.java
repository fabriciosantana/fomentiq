package com.fomentiq.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bounded validator for the local JSON Schema subset used by audit and extraction contracts. */
public final class JsonSchemaValidator {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_SCHEMA_NODES = 20_000;
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of("anyOf", "type", "const", "enum", "minLength",
            "maxLength", "pattern", "format", "minimum", "minItems", "maxItems", "items", "required",
            "properties", "additionalProperties");
    private static final String SHA256_PATTERN = "^[a-f0-9]{64}$";
    private static final String CODE_REVISION_PATTERN = "(?i)^(?:[a-f0-9]{40}|[a-f0-9]{64})$";

    public List<String> validate(JsonNode value, JsonNode schema) {
        List<String> errors = new ArrayList<>();
        validateSchemaStructure(schema, 0, new int[] {0}, errors);
        if (!errors.isEmpty()) return List.copyOf(errors);
        validateAt(value, schema, "$", 0, errors);
        return List.copyOf(errors);
    }

    private void validateSchemaStructure(JsonNode schema, int depth, int[] nodes, List<String> errors) {
        if (depth > MAX_DEPTH || ++nodes[0] > MAX_SCHEMA_NODES || schema == null || !schema.isObject()) {
            errors.add("schema_invalid");
            return;
        }
        var keys = schema.fieldNames();
        while (keys.hasNext()) {
            if (!SUPPORTED_KEYWORDS.contains(keys.next())) {
                errors.add("schema_keyword_unsupported");
                return;
            }
        }
        JsonNode type = schema.path("type");
        if (!type.isMissingNode() && !validTypes(type)) errors.add("schema_invalid");
        for (String bound : List.of("minLength", "maxLength", "minItems", "maxItems")) {
            JsonNode value = schema.path(bound);
            if (!value.isMissingNode() && (!value.isIntegralNumber() || value.asLong() < 0)) errors.add("schema_invalid");
        }
        for (String pair : List.of("minLength:maxLength", "minItems:maxItems")) {
            String[] bounds = pair.split(":");
            if (schema.has(bounds[0]) && schema.has(bounds[1])
                    && schema.path(bounds[0]).asLong() > schema.path(bounds[1]).asLong()) errors.add("schema_invalid");
        }
        if (schema.has("minimum") && !schema.path("minimum").isNumber()) errors.add("schema_invalid");
        if (schema.has("enum") && (!schema.path("enum").isArray() || schema.path("enum").isEmpty())) errors.add("schema_invalid");
        if (schema.has("required") && (!schema.path("required").isArray()
                || !allText(schema.path("required")))) errors.add("schema_invalid");
        if (schema.has("format") && (!schema.path("format").isTextual()
                || !Set.of("date", "date-time").contains(schema.path("format").asText()))) errors.add("schema_invalid");
        if (schema.has("pattern")) {
            String pattern = schema.path("pattern").asText();
            if (!schema.path("pattern").isTextual()) errors.add("schema_invalid");
            else if (!Set.of(SHA256_PATTERN, CODE_REVISION_PATTERN).contains(pattern)) errors.add("schema_pattern_unsupported");
        }
        if (schema.has("anyOf")) {
            JsonNode alternatives = schema.path("anyOf");
            if (!alternatives.isArray() || alternatives.isEmpty()) errors.add("schema_invalid");
            else alternatives.forEach(option -> validateSchemaStructure(option, depth + 1, nodes, errors));
        }
        if (schema.has("properties")) {
            JsonNode properties = schema.path("properties");
            if (!properties.isObject()) errors.add("schema_invalid");
            else properties.elements().forEachRemaining(property ->
                    validateSchemaStructure(property, depth + 1, nodes, errors));
        }
        if (schema.has("items")) validateSchemaStructure(schema.path("items"), depth + 1, nodes, errors);
        if (schema.has("additionalProperties")) {
            JsonNode additional = schema.path("additionalProperties");
            if (additional.isObject()) validateSchemaStructure(additional, depth + 1, nodes, errors);
            else if (!additional.isBoolean()) errors.add("schema_invalid");
        }
    }

    private boolean validTypes(JsonNode types) {
        if (types.isTextual()) return isSupportedType(types.asText());
        if (!types.isArray() || types.isEmpty()) return false;
        for (JsonNode type : types) if (!type.isTextual() || !isSupportedType(type.asText())) return false;
        return true;
    }

    private boolean isSupportedType(String type) {
        return Set.of("object", "array", "string", "boolean", "integer", "number", "null").contains(type);
    }

    private boolean allText(JsonNode values) {
        for (JsonNode value : values) if (!value.isTextual()) return false;
        return true;
    }

    private void validateAt(JsonNode value, JsonNode schema, String path, int depth, List<String> errors) {
        if (depth > MAX_DEPTH || schema == null || !schema.isObject()) {
            errors.add("schema_invalid");
            return;
        }
        var schemaKeys = schema.fieldNames();
        while (schemaKeys.hasNext()) {
            if (!SUPPORTED_KEYWORDS.contains(schemaKeys.next())) {
                errors.add("schema_keyword_unsupported");
                return;
            }
        }
        if (schema.has("anyOf")) {
            for (JsonNode option : schema.path("anyOf")) {
                List<String> optionErrors = new ArrayList<>();
                validateAt(value, option, path, depth + 1, optionErrors);
                if (optionErrors.isEmpty()) return;
            }
            errors.add("schema_any_of_mismatch");
            return;
        }
        JsonNode allowedTypes = schema.path("type");
        if (!allowedTypes.isMissingNode()) {
            boolean matches = allowedTypes.isArray()
                    ? iterable(allowedTypes).stream().anyMatch(type -> matchesType(value, type.asText()))
                    : matchesType(value, allowedTypes.asText());
            if (!matches) {
                errors.add("schema_type_mismatch");
                return;
            }
        }
        if (schema.has("const") && !schema.path("const").equals(value)) errors.add("schema_const_mismatch");
        if (schema.path("enum").isArray() && iterable(schema.path("enum")).stream().noneMatch(value::equals)) {
            errors.add("schema_enum_mismatch");
        }
        if (value.isTextual()) validateString(value.asText(), schema, errors);
        if (value.isNumber()) {
            if (schema.path("type").asText().equals("integer") && !value.isIntegralNumber()) {
                errors.add("schema_integer_mismatch");
            }
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) {
                errors.add("schema_minimum_mismatch");
            }
        }
        if (value.isArray()) {
            if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) errors.add("schema_array_too_short");
            if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) errors.add("schema_array_too_long");
            if (schema.has("items")) {
                for (int index = 0; index < value.size(); index++) {
                    validateAt(value.get(index), schema.path("items"), path + "[" + index + "]", depth + 1, errors);
                }
            }
        }
        if (value.isObject()) {
            JsonNode properties = schema.path("properties");
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.asText())) errors.add("schema_required_property_missing");
            }
            var entries = value.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                JsonNode property = properties.path(entry.getKey());
                if (property.isMissingNode()) {
                    if (schema.path("additionalProperties").isBoolean()
                            && !schema.path("additionalProperties").asBoolean()) {
                        errors.add("schema_additional_property_present");
                    } else if (schema.path("additionalProperties").isObject()) {
                        validateAt(entry.getValue(), schema.path("additionalProperties"),
                                path + "." + entry.getKey(), depth + 1, errors);
                    }
                } else {
                    validateAt(entry.getValue(), property, path + "." + entry.getKey(), depth + 1, errors);
                }
            }
        }
    }

    private void validateString(String value, JsonNode schema, List<String> errors) {
        if (schema.has("minLength") && value.length() < schema.path("minLength").asInt()) errors.add("schema_string_too_short");
        if (schema.has("maxLength") && value.length() > schema.path("maxLength").asInt()) errors.add("schema_string_too_long");
        if (schema.has("pattern")) {
            String pattern = schema.path("pattern").asText();
            if (!SHA256_PATTERN.equals(pattern) && !CODE_REVISION_PATTERN.equals(pattern)) {
                errors.add("schema_pattern_unsupported");
            } else if (!matchesSupportedPattern(value, pattern)) {
                errors.add("schema_string_pattern_mismatch");
            }
        }
        String format = schema.path("format").asText();
        try {
            if ("date".equals(format)) LocalDate.parse(value);
            if ("date-time".equals(format)) java.time.OffsetDateTime.parse(value);
        } catch (java.time.DateTimeException invalid) {
            errors.add("schema_string_format_mismatch");
        }
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private boolean matchesSupportedPattern(String value, String pattern) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean hex = character >= '0' && character <= '9'
                    || character >= 'a' && character <= 'f'
                    || CODE_REVISION_PATTERN.equals(pattern) && character >= 'A' && character <= 'F';
            if (!hex) return false;
        }
        if (SHA256_PATTERN.equals(pattern)) return value.length() == 64 && value.equals(value.toLowerCase(java.util.Locale.ROOT));
        return value.length() == 40 || value.length() == 64;
    }

    private List<JsonNode> iterable(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }
}
