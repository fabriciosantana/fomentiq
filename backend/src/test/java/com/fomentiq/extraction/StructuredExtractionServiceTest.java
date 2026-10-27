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
import com.fomentiq.storage.infrastructure.InMemoryPrivateAuditArchive;
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
    void refusesToCallProviderWhenExactInputCannotBeCaptured() {
        InMemoryPrivateAuditArchive auditArchive = new InMemoryPrivateAuditArchive();
        auditArchive.failWrites(true);
        java.util.concurrent.atomic.AtomicBoolean providerCalled = new java.util.concurrent.atomic.AtomicBoolean();
        StructuredExtractionProvider provider = request -> {
            providerCalled.set(true);
            throw new AssertionError("provider must not be called before durable capture");
        };
        ExtractionPersistence persistence = new ExtractionPersistence() {
            @Override public void recordExecution(ExecutionRecord execution) {
                throw new AssertionError("no execution should be recorded");
            }
            @Override public UUID recordField(FieldRecord field) { throw new AssertionError("no field expected"); }
        };
        var mapper = new ObjectMapper();
        var service = new StructuredExtractionService(provider, persistence,
                new DocumentExcerptSelector(bytes -> List.of()), new DeterministicFieldValidator(mapper),
                mapper, Clock.systemUTC(), auditArchive);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new DocumentExcerptSelector.DocumentInput(UUID.randomUUID(), "notice",
                        "https://example.org/notice.pdf", new byte[]{1}))))
                .hasMessage("audit_archive_write_failed");

        assertThat(providerCalled).isFalse();
    }

    @Test
    void preservesConflictingVersionValuesAndEvidenceInsteadOfChoosingTheNewerValue() {
        UUID firstVersion = UUID.randomUUID();
        UUID amendmentVersion = UUID.randomUUID();
        InMemoryPrivateAuditArchive auditArchive = new InMemoryPrivateAuditArchive();
        Map<String, ExtractionPersistence.FieldRecord> persisted = new HashMap<>();
        var provider = (StructuredExtractionProvider) request -> {
            assertThat(auditArchive.artifacts().keySet()).anyMatch(key -> key.startsWith("extraction-input/"));
            String originalSegment = request.excerpts().stream()
                    .filter(item -> firstVersion.equals(item.documentVersionId()))
                    .map(StructuredExtractionProvider.SourceExcerpt::segmentId)
                    .findFirst().orElseThrow();
            String amendmentSegment = request.excerpts().stream()
                    .filter(item -> amendmentVersion.equals(item.documentVersionId()))
                    .map(StructuredExtractionProvider.SourceExcerpt::segmentId)
                    .findFirst().orElseThrow();
            Map<String, StructuredExtractionProvider.Candidate> candidates = new HashMap<>();
            for (String field : ExtractionSchemaV1.FIELDS) {
                candidates.put(field, new StructuredExtractionProvider.Candidate(null, null, List.of(), List.of()));
            }
            candidates.put("deadline", new StructuredExtractionProvider.Candidate(null, null,
                    List.of(new StructuredExtractionProvider.Citation(originalSegment, "prazo original")),
                    List.of(new StructuredExtractionProvider.Alternative("2025-04-25T17:00:00-03:00",
                            "25/04/2025", List.of(new StructuredExtractionProvider.Citation(
                                    amendmentSegment, "prazo retificado"))))));
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
                new DocumentExcerptSelector.PageText(1, "prazo original prazo retificado"));
        var service = new StructuredExtractionService(provider, persistence,
                new DocumentExcerptSelector(pageExtractor), new DeterministicFieldValidator(new ObjectMapper()),
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC), auditArchive);

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
        assertThat(auditArchive.artifacts().keySet()).anyMatch(key -> key.startsWith("extraction-result/"));
    }

    @Test
    void onlyExplicitlyCitedAmendmentLanguageCanResolveAnEarlierClaim() {
        UUID originalVersion = UUID.randomUUID();
        UUID amendedVersion = UUID.randomUUID();
        Map<String, ExtractionPersistence.FieldRecord> persisted = new HashMap<>();
        StructuredExtractionProvider provider = request -> {
            String original = request.excerpts().stream().filter(e -> originalVersion.equals(e.documentVersionId()))
                    .map(StructuredExtractionProvider.SourceExcerpt::segmentId).findFirst().orElseThrow();
            var amended = request.excerpts().stream().filter(e -> amendedVersion.equals(e.documentVersionId()))
                    .findFirst().orElseThrow();
            var oldCitation = new StructuredExtractionProvider.Citation(original, "Prazo final 25/04/2025", "old-cite");
            var newCitation = new StructuredExtractionProvider.Citation(amended.segmentId(),
                    "Onde se lê item 8.1, leia-se item 8.1: Prazo final até 06/06/2025 às 17h00 UTC-03:00",
                    "new-cite");
            Map<String, StructuredExtractionProvider.Candidate> fields = new HashMap<>();
            ExtractionSchemaV1.FIELDS.forEach(field -> fields.put(field,
                    new StructuredExtractionProvider.Candidate(null, null, List.of(), List.of())));
            fields.put("deadline", new StructuredExtractionProvider.Candidate(
                    "\"2025-06-06T17:00:00-03:00\"", "06/06/2025", List.of(newCitation),
                    List.of(new StructuredExtractionProvider.Alternative("\"2025-04-25T17:00:00-03:00\"",
                            "25/04/2025", List.of(oldCitation), "claim-original")), List.of(),
                    List.of(new StructuredExtractionProvider.ReplacementRelation("clause-old", "clause-new",
                            "item 8.1", List.of("new-cite"), "claim-original", "claim-amended")), "claim-amended"));
            return new StructuredExtractionProvider.ProviderResponse("deterministic", "test", request.schemaVersion(),
                    "{}", null, false, 0L, 0L, fields);
        };
        ExtractionPersistence persistence = new ExtractionPersistence() {
            @Override public void recordExecution(ExecutionRecord execution) { }
            @Override public UUID recordField(FieldRecord field) { persisted.put(field.name(), field); return UUID.randomUUID(); }
        };
        var extractor = (DocumentExcerptSelector.PdfPageTextExtractor) bytes -> List.of(
                new DocumentExcerptSelector.PageText(1, new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        var service = new StructuredExtractionService(provider, persistence, new DocumentExcerptSelector(extractor),
                new DeterministicFieldValidator(new ObjectMapper()), new ObjectMapper(), Clock.systemUTC(),
                new InMemoryPrivateAuditArchive());

        byte[] amendmentText = "Onde se lê item 8.1, leia-se item 8.1: Prazo final até 06/06/2025 às 17h00 UTC-03:00"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        service.extract(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new DocumentExcerptSelector.DocumentInput(originalVersion, "Edital original", "https://finep.gov.br/original",
                        "Prazo final 25/04/2025 às 17h00".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new DocumentExcerptSelector.DocumentInput(amendedVersion, "Rerratificação edital", "https://finep.gov.br/amended",
                        amendmentText)));

        assertThat(persisted.get("deadline").status()).isEqualTo("VALIDATED");
        assertThat(persisted.get("deadline").evidence()).hasSize(2);
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
        InMemoryPrivateAuditArchive auditArchive = new InMemoryPrivateAuditArchive();
        var service = new StructuredExtractionService(provider, persistence,
                new DocumentExcerptSelector(bytes -> List.of()), new DeterministicFieldValidator(new ObjectMapper()),
                new ObjectMapper(), Clock.systemUTC(), auditArchive);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(UUID.randomUUID(), UUID.randomUUID(), List.of()))
                .isInstanceOf(StructuredExtractionService.ExtractionFailure.class);

        assertThat(executions).singleElement().satisfies(execution -> {
            assertThat(execution.status()).isEqualTo("FAILED");
            assertThat(execution.model()).isEqualTo("gpt-6-luna");
            assertThat(execution.rawResponse()).contains("OpenAI rate limit exceeded");
            assertThat(execution.rawResponse()).doesNotContain("Authorization", "Bearer", "document text");
        });
        assertThat(auditArchive.artifacts().keySet()).anyMatch(key -> key.startsWith("extraction-input/"));
        assertThat(auditArchive.artifacts().keySet()).noneMatch(key -> key.startsWith("extraction-result/"));
    }
}
