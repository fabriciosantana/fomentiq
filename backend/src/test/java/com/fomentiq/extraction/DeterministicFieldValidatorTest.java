package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.DeterministicFieldValidator;
import com.fomentiq.extraction.domain.ValidationStatus;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicFieldValidatorTest {
    private final DeterministicFieldValidator validator = new DeterministicFieldValidator(new ObjectMapper());

    @Test
    void validatesDeadlineAndRejectsMalformedDate() {
        assertThat(validator.validate("deadline", "2025-06-06T17:00:00-03:00", true)).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("deadline", "06/06/2025 17h", true)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("deadline", "2025-06-06T17:00:00-03:00", true,
                List.of("The call closes on 06/06/2025 at 17h00 UTC-03:00."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("deadline", "2025-06-06T17:00:00-03:00", true,
                List.of("The call closes on 06/06/2025 at 17h00 horário de Brasília.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void validatesBudgetOnlyWhenCurrencyAndNonNegativeAmountAreDeterministic() {
        assertThat(validator.validate("totalBudget", "{\"currency\":\"BRL\",\"amount\":\"500000000.00\"}", true))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("totalBudget", "{\"currency\":\"BRL\",\"amount\":\"-10\"}", true))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("totalBudget",
                "{\"currency\":\"BRL\",\"amount\":\"500000000.00\",\"qualifier\":\"UP_TO\"}", true,
                List.of("O orçamento global é de até R$ 500.000.000,00."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("totalBudget",
                "{\"currency\":\"BRL\",\"amount\":\"500000000.00\",\"qualifier\":\"UP_TO\"}", true,
                List.of("R$ 500.000.000,00."))).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("totalBudget",
                "{\"currency\":\"BRL\",\"amount\":\"500000000.00\",\"qualifier\":\"UP_TO\"}", true,
                List.of())).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void bindsBudgetAmountQualifierAndGlobalScopeWithinOneMonetaryExpression() {
        String upTo = "{\"currency\":\"BRL\",\"amount\":\"500000000.00\",\"qualifier\":\"UP_TO\"}";
        String exact = "{\"currency\":\"BRL\",\"amount\":\"500000000.00\",\"qualifier\":\"EXACT\"}";
        assertThat(validator.validateWithEvidence("totalBudget", upTo, true,
                List.of("O orçamento global é de até R$ 500.000.000,00."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("totalBudget", exact, true,
                List.of("O orçamento global é exatamente R$ 500.000.000,00."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("totalBudget", upTo, true, List.of(
                "O orçamento global é exatamente R$ 500.000.000,00. Projetos podem receber até R$ 800.000.000,00.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("totalBudget", upTo, true,
                List.of("O orçamento global é de R$ 500.000.000,00; projetos podem receber até R$ 800.000.000,00.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("totalBudget", upTo, true,
                List.of("O orçamento global é de até R$ 500.000.000,00 ou até R$ 800.000.000,00.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("totalBudget", upTo, true,
                List.of("O orçamento global será administrado; projetos podem receber até R$ 500.000.000,00.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void requiresIctOnlyWhenEligibleApplicantIsTheExplicitObligationSubject() {
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("Somente proponentes devem ser ICTs."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("Somente ICTs podem apresentar propostas."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("Eligible applicants must be an ICT."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("A ICT deverá apresentar certidão de regularidade. Proponentes podem ser universidades, institutos ou empresas.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("Proponentes podem ser ICTs. O proponente deverá apresentar documentos.")))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true,
                List.of("Não é necessário que o proponente seja ICT."))).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("requiresIct", "false", true,
                List.of("Não é necessário que o proponente seja ICT."))).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validator.validate("requiresIct", "true", true)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateWithEvidence("requiresIct", "true", true, List.of()))
                .isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void requiresEvidenceForCriticalEligibilityAndCounterpartAndKeepsAbsentTrlMissing() {
        assertThat(validator.validate("eligibility", "{\"eligible\":[\"ICT\"]}", false)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("eligibility", "{\"eligible\":[\"ICT\"]}", true))
                .isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("counterpart", "{\"exempt\":[]}", true)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("requiresIct", "true", true)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("requiresIct", "true", false)).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validate("trl", null, false)).isEqualTo(ValidationStatus.MISSING);
    }

    @Test
    void detectsConflictingValuesAcrossDocumentVersionsWithoutChoosingLatest() {
        assertThat(validator.validateAcrossVersions("deadline",
                List.of("2025-04-25T17:00:00-03:00", "2025-06-06T17:00:00-03:00"), true))
                .isEqualTo(ValidationStatus.CONFLICT);
    }

    @Test
    void keepsEvenACompleteSingleRangeCounterpartExtractedAndRejectsMissingClauseEvidence() {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1",
                "Municipal rule: 0.1% to 4% for up to 50,000 residents; no exemptions apply.");
        var citation = new StructuredExtractionProvider.Citation("segment-1", excerpt.text(), "cite-1");
        var clauses = List.of(
                new StructuredExtractionProvider.RuleClause("range-1", "RANGE", "municipal rule", "up to 50,000 residents",
                        "0.1", "4", List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("no-exemptions", "NO_EXEMPTIONS", "no exemptions apply",
                        null, null, null, List.of("cite-1")));
        String value = "{\"exempt\":[],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50,000 residents\",\"min\":\"0.1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), clauses,
                List.of(), null);

        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
        var incompleteClause = new StructuredExtractionProvider.RuleClause("range-1", "RANGE", "municipal rule",
                "up to 50,000 residents", "0.1", "4", List.of());
        var incomplete = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(),
                List.of(incompleteClause, clauses.get(1)), List.of(), null);
        assertThat(validator.validateComposite("counterpart", value, incomplete, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);

        var unsupported = new StructuredExtractionProvider.RuleClause("exception-1", "EXCEPTION", "Banco X",
                null, null, null, List.of("cite-1"));
        String unsupportedValue = "{\"exempt\":[\"Banco X\"],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50,000 residents\",\"min\":\"0.1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var unsupportedCandidate = new StructuredExtractionProvider.Candidate(unsupportedValue, null,
                List.of(citation), List.of(), List.of(clauses.get(0), unsupported), List.of(), null);
        assertThat(validator.validateComposite("counterpart", unsupportedValue, unsupportedCandidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void preservesCounterpartEvidenceWithoutAutomaticallyValidatingLegalRelations() {
        assertCounterpartStatus("Municipal rule: 0.1% to 4% for up to 50,000 residents; no exemptions apply.",
                "municipalPercentRanges", "municipal rule", "up to 50,000 residents", "0.1", "4",
                ValidationStatus.EXTRACTED);
        assertCounterpartStatus("State and federal districts: 0.2% to 8% for priority areas; no exemptions apply.",
                "stateAndFederalDistrictPercentRanges", "state and federal district rule", "priority areas", "0.2",
                "8", ValidationStatus.EXTRACTED);
        assertCounterpartStatus("Municipalities up to 50,000 people: counterpart rate 0.2% to 4%. "
                        + "State and federal districts: counterpart rate 0.1%. No exemptions apply.",
                "municipalPercentRanges", "municipal rule", "up to 50,000 people", "0.1", "4",
                ValidationStatus.EXTRACTED);
        assertCounterpartStatus("Municipal rule: 0.1% to 4%. This applies for up to 50,000 residents.",
                "municipalPercentRanges", "municipal rule", "up to 50,000 residents", "0.1", "4",
                ValidationStatus.EXTRACTED);
        assertCounterpartStatus("Municipal rule: 0.1% to 4% for up to 50,000 residents; "
                        + "state and federal districts: 0.2% to 8% for other cases; no exemptions apply.",
                "municipalPercentRanges", "municipal rule", "up to 50,000 residents", "0.1", "4",
                ValidationStatus.EXTRACTED);
    }

    @Test
    void aLiteralButIncompleteCounterpartCitationCannotProveTheRuleRelation() {
        String fullText = "Municipal rule: 0.1% to 4% for up to 50,000 residents; no exemptions apply.";
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1", fullText);
        var rangeCitation = new StructuredExtractionProvider.Citation("segment-1",
                "Municipal rule: 0.1% to 4%", "range-citation");
        var noExceptionCitation = new StructuredExtractionProvider.Citation("segment-1", fullText,
                "exception-citation");
        String value = "{\"exempt\":[],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50,000 residents\",\"min\":\"0.1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null,
                List.of(rangeCitation, noExceptionCitation), List.of(), List.of(
                        new StructuredExtractionProvider.RuleClause("range", "RANGE", "municipal rule",
                                "up to 50,000 residents", "0.1", "4", List.of("range-citation")),
                        new StructuredExtractionProvider.RuleClause("no-exemptions", "NO_EXEMPTIONS",
                                "no exemptions apply", null, null, null, List.of("exception-citation"))),
                List.of(), null);

        assertThat(fullText).contains(rangeCitation.quote());
        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    private void assertCounterpartStatus(String quote, String group, String statement, String condition,
            String minimum, String maximum, ValidationStatus expected) {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1", quote);
        var citation = new StructuredExtractionProvider.Citation("segment-1", quote, "cite-1");
        String ranges = group.equals("municipalPercentRanges")
                ? "\"municipalPercentRanges\":[{\"condition\":\"" + condition + "\",\"min\":\""
                        + minimum + "\",\"max\":\"" + maximum + "\"}],\"stateAndFederalDistrictPercentRanges\":[]"
                : "\"municipalPercentRanges\":[],\"stateAndFederalDistrictPercentRanges\":[{\"condition\":\""
                        + condition + "\",\"min\":\"" + minimum + "\",\"max\":\"" + maximum + "\"}]";
        String value = "{\"exempt\":[]," + ranges + "}";
        var clauses = List.of(new StructuredExtractionProvider.RuleClause("range", "RANGE", statement,
                condition, minimum, maximum, List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("no-exemptions", "NO_EXEMPTIONS",
                        "No exemptions apply", null, null, null, List.of("cite-1")));
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(),
                clauses, List.of(), null);
        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(expected);
    }

    @Test
    void retainsRepresentedConditionsAndExceptionsAsExtractedClaims() {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1",
                "Municipal counterpart: 1% to 4% for up to 50 thousand inhabitants; ICT federal is exempt from counterpart.");
        var citation = new StructuredExtractionProvider.Citation("segment-1", excerpt.text(), "cite-1");
        String value = "{\"exempt\":[\"ICT federal\"],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50 thousand inhabitants\",\"min\":\"1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var clauses = List.of(
                new StructuredExtractionProvider.RuleClause("range-1", "RANGE", "municipal rate",
                        "up to 50 thousand inhabitants", "1", "4", List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("condition-1", "CONDITION",
                        "up to 50 thousand inhabitants", "up to 50 thousand inhabitants", null, null,
                        List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("exception-1", "EXCEPTION", "ICT federal",
                        null, null, null, List.of("cite-1")));
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), clauses,
                List.of(), null);

        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);

        var unsupportedException = new StructuredExtractionProvider.RuleClause("exception-2", "EXCEPTION",
                "all applicants exempt", null, null, null, List.of("cite-1"));
        var incomplete = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(),
                java.util.stream.Stream.concat(clauses.stream(), java.util.stream.Stream.of(unsupportedException)).toList(),
                List.of(), null);
        assertThat(validator.validateComposite("counterpart", value, incomplete, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void incidentalDocumentDutyDoesNotValidateCounterpartExemption() {
        String quote = "Municipal rule: 0.1% to 4% for up to 50,000 residents; "
                + "ICT federal applicants submit documents.";
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1", quote);
        var citation = new StructuredExtractionProvider.Citation("segment-1", quote, "cite-1");
        String value = "{\"exempt\":[\"ICT federal\"],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50,000 residents\",\"min\":\"0.1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), List.of(
                new StructuredExtractionProvider.RuleClause("range", "RANGE", "municipal rule",
                        "up to 50,000 residents", "0.1", "4", List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("exemption", "EXEMPTION", "ICT federal",
                        null, null, null, List.of("cite-1"))), List.of(), null);

        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void paperworkWaiverIsNotInterpretedAsWaiverOfFinancialCounterpart() {
        String quote = "Municipal rule: 0.1% to 4% for up to 50,000 residents. "
                + "Não se exige, para a contrapartida, declaração de ICT federal.";
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 7, "7.1", quote);
        var citation = new StructuredExtractionProvider.Citation("segment-1", quote, "cite-1");
        String value = "{\"exempt\":[\"ICT federal\"],\"municipalPercentRanges\":["
                + "{\"condition\":\"up to 50,000 residents\",\"min\":\"0.1\",\"max\":\"4\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var clauses = List.of(
                new StructuredExtractionProvider.RuleClause("range", "RANGE", "municipal rule",
                        "up to 50,000 residents", "0.1", "4", List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("exemption", "EXEMPTION", "ICT federal",
                        null, null, null, List.of("cite-1")));
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), clauses,
                List.of(), null);

        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void preservesMissingAndConflictStatesForCompositeFields() {
        assertThat(validator.validateComposite("counterpart", null, null, false, false, java.util.Map.of()))
                .isEqualTo(ValidationStatus.MISSING);
        assertThat(validator.validateComposite("eligibility", "{\"conflictValues\":[\"A\",\"B\"]}", null,
                false, false, java.util.Map.of())).isEqualTo(ValidationStatus.CONFLICT);
    }

    @Test
    void incompleteCoverageAndEligibilityWithoutExclusionGroupCannotValidate() {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 3, "3.1", "ICT pública elegível");
        var citation = new StructuredExtractionProvider.Citation("segment-1", excerpt.text(), "cite-1");
        var value = "{\"eligible\":[\"ICT pública\"],\"excluded\":[],\"registration\":null}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(),
                List.of(new StructuredExtractionProvider.RuleClause("eligible-1", "INCLUSION", "ICT pública", null,
                        null, null, List.of("cite-1"))), List.of(), null);

        assertThat(validator.validateComposite("eligibility", value, candidate, true, false,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
        assertThat(validator.validateComposite("eligibility", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void eligibilityInclusionCannotBeValidatedAgainstAnExplicitlyNegatedCitation() {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 3, "3.1", "ICT pública não é elegível.");
        var citation = new StructuredExtractionProvider.Citation("segment-1", excerpt.text(), "cite-1");
        String value = "{\"eligible\":[\"ICT pública\"],\"excluded\":[\"unidades vinculadas ao MCTI\"],"
                + "\"registration\":null,\"federalExecutorRequiresSupportInstitution\":null,"
                + "\"privateNonprofitMinimumOperatingYears\":null}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), List.of(
                new StructuredExtractionProvider.RuleClause("include", "INCLUSION", "ICT pública", null,
                        null, null, List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("exclude", "EXCLUSION", "unidades vinculadas ao MCTI",
                        null, null, null, List.of("cite-1"))), List.of(), null);

        assertThat(validator.validateComposite("eligibility", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);

        var unrelatedNegation = new StructuredExtractionProvider.SourceExcerpt("segment-2",
                java.util.UUID.randomUUID(), "https://finep.gov.br/notice.pdf", "notice", 3, "3.1",
                "ICT pública é elegível; empresas estrangeiras não são elegíveis.");
        var unrelatedQuote = new StructuredExtractionProvider.Citation("segment-2", unrelatedNegation.text(), "cite-2");
        String contradictoryRule = "{\"eligible\":[\"ICT pública\"],\"excluded\":[\"ICT pública\"],"
                + "\"registration\":null,\"federalExecutorRequiresSupportInstitution\":null,"
                + "\"privateNonprofitMinimumOperatingYears\":null}";
        var unrelatedCandidate = new StructuredExtractionProvider.Candidate(contradictoryRule, null,
                List.of(unrelatedQuote), List.of(), List.of(
                        new StructuredExtractionProvider.RuleClause("include", "INCLUSION", "ICT pública", null,
                                null, null, List.of("cite-2")),
                        new StructuredExtractionProvider.RuleClause("exclude", "EXCLUSION", "ICT pública", null,
                                null, null, List.of("cite-2"))), List.of(), null);
        assertThat(validator.validateComposite("eligibility", contradictoryRule, unrelatedCandidate, true, true,
                java.util.Map.of("segment-2", unrelatedNegation))).isEqualTo(ValidationStatus.EXTRACTED);
    }

    @Test
    void aSingleCitedBandCannotValidateAValueWithSeveralConditionalBands() {
        var excerpt = new StructuredExtractionProvider.SourceExcerpt("segment-1", java.util.UUID.randomUUID(),
                "https://finep.gov.br/notice.pdf", "notice", 14, "7.1", "Faixa A 1% a 4%; faixa B 2% a 8%; isenções");
        var citation = new StructuredExtractionProvider.Citation("segment-1", excerpt.text(), "cite-1");
        String value = "{\"exempt\":[\"ICT federal\"],\"municipalPercentRanges\":["
                + "{\"condition\":\"até 50 mil habitantes\",\"min\":\"1\",\"max\":\"4\"},"
                + "{\"condition\":\"demais municípios\",\"min\":\"2\",\"max\":\"8\"}],"
                + "\"stateAndFederalDistrictPercentRanges\":[]}";
        var candidate = new StructuredExtractionProvider.Candidate(value, null, List.of(citation), List.of(), List.of(
                new StructuredExtractionProvider.RuleClause("range-1", "RANGE", "first band",
                        "até 50 mil habitantes", "1", "4", List.of("cite-1")),
                new StructuredExtractionProvider.RuleClause("exemption-1", "EXEMPTION", "ICT federal",
                        null, null, null, List.of("cite-1"))), List.of(), null);

        assertThat(validator.validateComposite("counterpart", value, candidate, true, true,
                java.util.Map.of("segment-1", excerpt))).isEqualTo(ValidationStatus.EXTRACTED);
    }
}
