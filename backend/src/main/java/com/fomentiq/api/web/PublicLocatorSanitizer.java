package com.fomentiq.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Projects persisted locator JSON onto public source-location fields, excluding arbitrary internal metadata. */
@Component
public class PublicLocatorSanitizer {
    private static final Set<String> PUBLIC_KEYS = Set.of("segmentid", "page", "section", "paragraph",
            "jsonpointer", "jsonpointers", "characterstart", "characterend", "quotehash", "table", "row",
            "column", "bbox");
    private final ObjectMapper mapper;

    public PublicLocatorSanitizer(ObjectMapper mapper) { this.mapper = mapper; }

    public Object sanitize(String value) {
        if (value == null) return Map.of();
        try {
            JsonNode parsed = mapper.readTree(value);
            Object result = project(parsed);
            return result instanceof Map<?, ?> ? result : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Object project(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                if (PUBLIC_KEYS.contains(normalize(entry.getKey()))) result.put(entry.getKey(), project(entry.getValue()));
            });
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            node.forEach(item -> result.add(project(item)));
            return result;
        }
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNumber()) return node.numberValue();
        return node.asText();
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
