package com.fomentiq.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void validatesRequiredTypesAndRejectsExtraProperties() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","required":["field"],"properties":{"field":{"type":"string"}},
                 "additionalProperties":false}
                """);

        assertThat(validator.validate(mapper.readTree("{\"field\":\"ok\"}"), schema)).isEmpty();
        assertThat(validator.validate(mapper.readTree("{\"field\":7,\"other\":true}"), schema))
                .contains("schema_additional_property_present", "schema_type_mismatch");
    }

    @Test
    void validatesNullableSchemaAlternativesAndEnums() throws Exception {
        var schema = mapper.readTree("""
                {"anyOf":[{"type":"string","enum":["OPEN","CLOSED"]},{"type":"null"}]}
                """);

        assertThat(validator.validate(mapper.readTree("\"OPEN\""), schema)).isEmpty();
        assertThat(validator.validate(mapper.readTree("null"), schema)).isEmpty();
        assertThat(validator.validate(mapper.readTree("\"OTHER\""), schema)).contains("schema_any_of_mismatch");
    }

    @Test
    void reportsInvalidManifestAgainstVersionedLocalSchema() throws Exception {
        try (var input = getClass().getResourceAsStream("/audit-package/manifest-v1.schema.json")) {
            var schema = mapper.readTree(input);
            var invalid = mapper.readTree("{\"formatVersion\":2,\"unexpected\":true}");
            assertThat(validator.validate(invalid, schema)).contains("schema_required_property_missing",
                    "schema_const_mismatch", "schema_additional_property_present");
        }
    }

    @Test
    void enforcesDepthAcrossNestedAnyOfAlternatives() {
        var schema = mapper.createObjectNode();
        var current = schema;
        for (int depth = 0; depth < 70; depth++) {
            current = current.putArray("anyOf").addObject();
        }
        current.put("type", "null");

        assertThat(validator.validate(mapper.nullNode(), schema)).contains("schema_invalid");
    }

    @Test
    void rejectsUnknownKeywordsAndArbitraryRegularExpressions() throws Exception {
        var unknownKeyword = mapper.readTree("{\"type\":\"string\",\"patternProperties\":{}}");
        var unsafePattern = mapper.readTree("{\"type\":\"string\",\"pattern\":\"^(a+)+$\"}");

        assertThat(validator.validate(mapper.readTree("\"value\""), unknownKeyword))
                .contains("schema_keyword_unsupported");
        assertThat(validator.validate(mapper.readTree("\"aaaaaaaaaaaaaaaa!\""), unsafePattern))
                .contains("schema_pattern_unsupported");
    }

    @Test
    void rejectsInvalidAlternativeEvenWhenAnotherAnyOfBranchAcceptsTheValue() throws Exception {
        var schema = mapper.readTree("""
                {"anyOf":[{"type":"string","unknownKeyword":true},{"type":"string"}]}
                """);

        assertThat(validator.validate(mapper.readTree("\"accepted\""), schema))
                .contains("schema_keyword_unsupported");
    }

    @Test
    void rejectsMalformedSupportedKeywordBeforeEvaluatingValue() throws Exception {
        var schema = mapper.readTree("{\"type\":\"object\",\"properties\":[]}");

        assertThat(validator.validate(mapper.readTree("{}"), schema)).contains("schema_invalid");
    }
}
