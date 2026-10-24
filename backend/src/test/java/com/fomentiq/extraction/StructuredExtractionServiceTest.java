package com.fomentiq.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.DeterministicFieldValidator;
import com.fomentiq.extraction.application.DocumentExcerptSelector;
import com.fomentiq.extraction.application.ExtractionPersistence;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import com.fomentiq.extraction.application.StructuredExtractionProvider;
import com.fomentiq.extraction.application.StructuredExtractionService;
import com.fomentiq.extraction.application.StructuredExtractionFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StructuredExtractionServiceTest {
    @Test
    void preservesConflictingVersionValuesAndEvidenceInsteadOfChoosingTheNewerValue() {
        UUID firstVersion = UUID.randomUUID();
        UUID amendmentVersion = UUID.randomUUID();
        Map<String, ExtractionPersistence.FieldRecord> persisted = new HashMap<>();
        var provider = (StructuredExtractionProvider) request -> {
            Map<String, StructuredExtractionProvider.Candidate> candidates = new HashMap<>();
            for (String field : ExtractionSchemaV1.FIELDS) {
                candidates.put(field, new StructuredExtractionProvider.Candidate(null, null, List.of(), List.of()));
            }
            candidates.put("deadline", new StructuredExtractionProvider.Candidate(null, null,
                    List.of(new StructuredExtractionProvider.Citation(firstVersion + ":1", "deadline original")),
                    List.of(new StructuredExtractionProvider.Alternative("2025-04-25T17:00:00-03:00",
                            "25/04/2025", List.of(new StructuredExtractionProvider.Citation(
                                    amendmentVersion + ":1", "deadline amended"))))));
            return new StructuredExtractionProvider.ProviderResponse("deterministic", "test", request.schemaVersion(),
                    "{\"fixture\":true}", null, false, 0L, 0L, candidates);
        };
        ExtractionPersistence persistence = new ExtractionPersistence() {
            @Override public void recordExecution(ExecutionRecord execution) { }
            @Override public UUID recordField(FieldRecord field) {
                persisted.put(field.name(), field);
                return UUID.randomUUID();
            }
        };
        var pageExtractor = (DocumentExcerptSelector.PdfPageTextExtractor) bytes -> List.of(
                new DocumentExcerptSelector.PageText(1, "deadline original deadline amended"));
        var service = new StructuredExtractionService(provider, persistence,
                new DocumentExcerptSelector(pageExtractor), new DeterministicFieldValidator(new ObjectMapper()),
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));

        service.extract(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new DocumentExcerptSelector.DocumentInput(firstVersion, "Edital original", "https://finep.gov.br/original", new byte[]{1}),
                new DocumentExcerptSelector.DocumentInput(amendmentVersion, "Rerratificacao edital",
                        "https://finep.gov.br/amended", new byte[]{2})));

        ExtractionPersistence.FieldRecord deadline = persisted.get("deadline");
        assertThat(deadline.status()).isEqualTo("CONFLICT");
        assertThat(deadline.normalizedValue()).contains("2025-04-25T17:00:00-03:00");
        assertThat(deadline.normalizedValue()).contains("null");
        assertThat(deadline.evidence()).hasSize(2);
        assertThat(persisted.get("trl").status()).isEqualTo("MISSING");
        assertThat(persisted).hasSize(ExtractionSchemaV1.FIELDS.size());
    }

    @Test
    void recordsSafeProviderFailureMetadataWithoutLeakingRequestContent() {
        List<ExtractionPersistence.ExecutionRecord> executions = new java.util.ArrayList<>();
        StructuredExtractionProvider provider = request -> {
            throw new StructuredExtractionFailure("openai", "gpt-6-luna", "OpenAI rate limit exceeded");
        };
        ExtractionPersistence persistence = new ExtractionPersistence() {
            @Override public void recordExecution(ExecutionRecord execution) { executions.add(execution); }
            @Override public UUID recordField(FieldRecord field) { throw new AssertionError("No facts on provider failure"); }
        };
        var service = new StructuredExtractionService(provider, persistence,
                new DocumentExcerptSelector(bytes -> List.of()), new DeterministicFieldValidator(new ObjectMapper()),
                new ObjectMapper(), Clock.systemUTC());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(UUID.randomUUID(), UUID.randomUUID(), List.of()))
                .isInstanceOf(StructuredExtractionService.ExtractionFailure.class);

        assertThat(executions).singleElement().satisfies(execution -> {
            assertThat(execution.status()).isEqualTo("FAILED");
            assertThat(execution.model()).isEqualTo("gpt-6-luna");
            assertThat(execution.rawResponse()).contains("OpenAI rate limit exceeded");
            assertThat(execution.rawResponse()).doesNotContain("Authorization", "Bearer", "document text");
        });
    }
}
