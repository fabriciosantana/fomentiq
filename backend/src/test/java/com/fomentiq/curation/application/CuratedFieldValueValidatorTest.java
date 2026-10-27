package com.fomentiq.curation.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CuratedFieldValueValidatorTest {
    private final CuratedFieldValueValidator validator = new CuratedFieldValueValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsTypedDateAndStructuredMoneyValues() throws Exception {
        assertThatCode(() -> validator.validate("deadline", mapper.readTree("\"2026-12-31T12:00:00-03:00\""), false))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("totalBudget",
                mapper.readTree("{\"currency\":\"BRL\",\"amount\":\"500.00\",\"qualifier\":\"UP_TO\"}"), false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedValuesAndArbitraryJson() throws Exception {
        assertThatThrownBy(() -> validator.validate("deadline", mapper.readTree("\"31/12/2026 12:00\""), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("totalBudget", mapper.readTree("{\"anything\":true}"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("agency", mapper.readTree("\"Finep\""), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitAbsenceRequiresNullableFieldAndNullValue() throws Exception {
        assertThatCode(() -> validator.validate("deadline", mapper.getNodeFactory().nullNode(), true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("deadline", mapper.readTree("\"2026-12-31\""), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("title", mapper.getNodeFactory().nullNode(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eligibilityMustMatchTheRecognizedStructuredShape() throws Exception {
        assertThatThrownBy(() -> validator.validate("eligibility", mapper.readTree("{\"eligible\":[]}"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validate("eligibility", mapper.readTree("""
                {"eligible":["ICT pública"],"registration":"SISGON","excluded":[],
                 "federalExecutorRequiresSupportInstitution":true,"privateNonprofitMinimumOperatingYears":3}
                """), false)).doesNotThrowAnyException();
    }

    @Test
    void humanPublishedLinksMustBeHttpsPublicDnsWithoutCredentialParameters() throws Exception {
        assertThatCode(() -> validator.validate("officialLinks",
                mapper.readTree("[\"https://www.gov.br/finep\"]"), false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("officialLinks",
                mapper.readTree("[\"http://127.0.0.1/admin\"]"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("officialLinks",
                mapper.readTree("[\"https://example.org/call?access_key=private\"]"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
