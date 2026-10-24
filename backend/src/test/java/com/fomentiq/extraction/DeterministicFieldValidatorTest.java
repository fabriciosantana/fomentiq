package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.DeterministicFieldValidator;
import com.fomentiq.extraction.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicFieldValidatorTest {
    private final DeterministicFieldValidator validator = new DeterministicFieldValidator(new ObjectMapper());

    @Test
    void validatesDeadlineAndRejectsMalformedDate() {
        assertThat(validator.validate("deadline", "2025-06-06T17:00:00-03:00", true)).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("deadline", "06/06/2025 17h", true)).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void validatesBudgetOnlyWhenCurrencyAndNonNegativeAmountAreDeterministic() {
        assertThat(validator.validate("totalBudget", "{\"currency\":\"BRL\",\"amount\":\"500000000.00\"}", true))
                .isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("totalBudget", "{\"currency\":\"BRL\",\"amount\":\"-10\"}", true))
                .isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void requiresEvidenceForCriticalEligibilityAndCounterpartAndKeepsAbsentTrlMissing() {
        assertThat(validator.validate("eligibility", "{\"eligible\":[\"ICT\"]}", false)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("eligibility", "{\"eligible\":[\"ICT\"]}", true)).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("counterpart", "{\"exempt\":[]}", true)).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("requiresIct", "true", true)).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("requiresIct", "true", false)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("trl", null, false)).isEqualTo(ValidationStatus.MISSING);
    }

    @Test
    void detectsConflictingValuesAcrossDocumentVersionsWithoutChoosingLatest() {
        assertThat(validator.validateAcrossVersions("deadline",
                List.of("2025-04-25T17:00:00-03:00", "2025-06-06T17:00:00-03:00"), true))
                .isEqualTo(ValidationStatus.CONFLICT);
    }
}
