package com.fomentiq.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fomentiq.editorial.application.ManualOpportunity;
import com.fomentiq.editorial.application.ManualOpportunityService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ManualEditorialPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fomentiq")
            .withUsername("fomentiq")
            .withPassword("fomentiq");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ManualOpportunityService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsDraftPublishesSnapshotAndWithdrawsWithoutChangingLegacyRows() {
        int legacyBefore = jdbc.queryForObject("SELECT count(*) FROM call_for_proposals", Integer.class);
        var draft = service.create(opportunity("Primeira versão"), "editor@example.test");

        assertThat(draft.editorialState()).isEqualTo("DRAFT");
        assertThat(service.list("", true, 0, 20).totalElements()).isZero();
        assertThatThrownBy(() -> service.get(draft.id(), true))
                .isInstanceOf(ResponseStatusException.class);
        var published = service.publish(draft.id(), draft.rowVersion(), "editor@example.test");
        assertThat(service.get(draft.id(), true).title()).isEqualTo("Primeira versão");
        var edited = service.save(draft.id(), opportunity("Conteúdo ainda não publicado"),
                published.rowVersion(), "editor@example.test");
        assertThat(service.get(draft.id(), true).title()).isEqualTo("Primeira versão");
        assertThat(edited.title()).isEqualTo("Conteúdo ainda não publicado");
        var republished = service.publish(draft.id(), edited.rowVersion(), "editor@example.test");
        assertThat(service.get(draft.id(), true).title()).isEqualTo("Conteúdo ainda não publicado");
        service.unpublish(draft.id(), republished.rowVersion(), "editor@example.test");
        assertThatThrownBy(() -> service.get(draft.id(), true))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM call_for_proposals", Integer.class))
                .isEqualTo(legacyBefore);
    }

    @Test
    void writesAuditEventsAndRejectsStaleConcurrentUpdates() {
        var draft = service.create(opportunity("Versão inicial"), "editor@example.test");
        var updated = service.save(draft.id(), opportunity("Versão editada"), draft.rowVersion(), "editor@example.test");
        assertThat(updated.rowVersion()).isEqualTo(draft.rowVersion() + 1);
        assertThatThrownBy(() -> service.save(draft.id(), opportunity("Sobrescrita antiga"),
                draft.rowVersion(), "editor@example.test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_opportunity_audit_event WHERE opportunity_id=?",
                Integer.class, draft.id())).isEqualTo(2);
    }

    @Test
    void publicCatalogSearchAndOrderUsePublishedSnapshotAfterDraftEdit() {
        var first = service.create(opportunity("Publicado A"), "editor@example.test");
        service.publish(first.id(), first.rowVersion(), "editor@example.test");
        var editedFirst = service.get(first.id(), false);
        service.save(first.id(), withTitle(editedFirst, "Rascunho Z"), editedFirst.rowVersion(),
                "editor@example.test");

        var second = service.create(opportunity("Publicado B"), "editor@example.test");
        service.publish(second.id(), second.rowVersion(), "editor@example.test");
        var editedSecond = service.get(second.id(), false);
        service.save(second.id(), withTitle(editedSecond, "Rascunho A"), editedSecond.rowVersion(),
                "editor@example.test");

        var publishedTitleSearch = service.list("Publicado A", true, 0, 20);
        assertThat(publishedTitleSearch.content()).extracting(ManualOpportunity::title).containsExactly("Publicado A");
        assertThat(service.list("Rascunho", true, 0, 20).content()).isEmpty();
        assertThat(service.list("Rascunho", false, 0, 20).content()).hasSize(2);
        assertThat(service.list("", true, 0, 20).content())
                .extracting(ManualOpportunity::title).containsExactly("Publicado A", "Publicado B");
    }

    @Test
    void allowsStartingAnIncompletePrivateDraft() {
        var draft = service.create(new ManualOpportunity(null, "", "", "", null,
                "Inscrições abertas", null, "DRAFT", List.of(), 0, null, null, null, null),
                "editor@example.test");
        assertThat(draft.editorialState()).isEqualTo("DRAFT");
        assertThat(draft.title()).isEmpty();
        assertThatThrownBy(() -> service.publish(draft.id(), draft.rowVersion(), "editor@example.test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Preencha título");
    }

    @Test
    void persistsIsoDeadlineAndReloadsUpdatesNullAndCustomSections() {
        var initial = withDeadline(opportunity("Prazo inicial"), LocalDate.of(2027, 4, 30));
        var draft = service.create(initial, "editor@example.test");

        assertThat(service.get(draft.id(), false).deadline()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(jsonContent(draft.id())).contains("\"2027-04-30\"");
        assertThat(deadlineJsonType(draft.id())).isEqualTo("string");
        assertThat(service.get(draft.id(), false).sections()).containsExactlyElementsOf(initial.sections());

        var changed = service.save(draft.id(), withDeadline(draft, LocalDate.of(2027, 5, 15)),
                draft.rowVersion(), "editor@example.test");
        assertThat(service.get(draft.id(), false).deadline()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(jsonContent(draft.id())).contains("\"2027-05-15\"");
        assertThat(deadlineJsonType(draft.id())).isEqualTo("string");

        service.save(draft.id(), withDeadline(changed, null), changed.rowVersion(), "editor@example.test");
        assertThat(service.get(draft.id(), false).deadline()).isNull();
        assertThat(deadlineJsonType(draft.id())).isEqualTo("null");
    }

    @Test
    void validatesTypedValuesAndUnsafeLinks() {
        var invalid = new ManualOpportunity(null, "Edital", "Instituição", "Resumo", null,
                "Inscrições abertas", "https://exemplo.gov.br", "DRAFT",
                List.of(new ManualOpportunity.Section("Dados", 0, List.of(
                        new ManualOpportunity.Field("Valor", "CURRENCY", "-1", 0)))),
                0, null, null, null, null);
        assertThatThrownBy(() -> service.create(invalid, "editor@example.test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> service.create(new ManualOpportunity(null, "Edital", "Instituição", "Resumo",
                null, "Aberta", "javascript:alert(1)", "DRAFT", List.of(), 0, null, null, null, null),
                "editor@example.test"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.create(new ManualOpportunity(null, "Edital", "Instituição", "Resumo",
                null, "Aberta", "https://localhost/private", "DRAFT", List.of(), 0, null, null, null, null),
                "editor@example.test"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void stripsSensitiveQueryParametersFromTypedEditorialLinksBeforePersistence() {
        var content = new ManualOpportunity(null, "Edital", "Instituição", "Resumo", null,
                "Aberta", "https://instituicao.gov.br/chamada", "DRAFT",
                List.of(new ManualOpportunity.Section("Documentos", 0, List.of(
                        new ManualOpportunity.Field("Edital", "LINK",
                                "https://instituicao.gov.br/edital?access_token=secret&view=public", 0)))),
                0, null, null, null, null);
        var created = service.create(content, "editor@example.test");
        assertThat(created.sections().getFirst().fields().getFirst().value())
                .isEqualTo("https://instituicao.gov.br/edital?view=public");
    }

    @Test
    void publicProjectionReturnsOnlyPublishedSnapshotsAndOmitsAdministrationMetadata() {
        var draft = service.create(opportunity("Private draft"), "editor@example.test");
        assertThatThrownBy(() -> service.get(draft.id(), true))
                .isInstanceOf(ResponseStatusException.class);

        var published = service.publish(draft.id(), draft.rowVersion(), "editor@example.test");
        var stored = service.get(draft.id(), true);
        var publicView = new com.fomentiq.editorial.web.EditorialCatalogController.PublicOpportunity(
                stored.id(), stored.title(), stored.institution(), stored.summary(), stored.deadline(),
                stored.status(), stored.officialUrl(), stored.sections());
        assertThat(publicView.title()).isEqualTo("Private draft");
        assertThat(publicView.sections().getFirst().fields().getFirst().value())
                .isEqualTo(List.of("Empresas"));
        assertThat(java.util.Arrays.stream(publicView.getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("createdBy", "rowVersion");
        service.unpublish(draft.id(), published.rowVersion(), "editor@example.test");
        assertThatThrownBy(() -> service.get(draft.id(), true))
                .isInstanceOf(ResponseStatusException.class);
    }

    private ManualOpportunity opportunity(String title) {
        return new ManualOpportunity(null, title, "Instituição de Demonstração", "Resumo editorial",
                null, "Inscrições abertas", "https://instituicao.gov.br/chamada", "DRAFT",
                List.of(new ManualOpportunity.Section("Quem pode participar", 0, List.of(
                        new ManualOpportunity.Field("Público", "LIST", List.of("Empresas"), 0)))),
                0, null, null, null, null);
    }

    private ManualOpportunity withDeadline(ManualOpportunity item, LocalDate deadline) {
        return new ManualOpportunity(item.id(), item.title(), item.institution(), item.summary(), deadline,
                item.status(), item.officialUrl(), item.editorialState(), item.sections(), item.rowVersion(),
                item.createdBy(), item.updatedBy(), item.createdAt(), item.updatedAt());
    }

    private ManualOpportunity withTitle(ManualOpportunity item, String title) {
        return new ManualOpportunity(item.id(), title, item.institution(), item.summary(), item.deadline(),
                item.status(), item.officialUrl(), item.editorialState(), item.sections(), item.rowVersion(),
                item.createdBy(), item.updatedBy(), item.createdAt(), item.updatedAt());
    }

    private String jsonContent(java.util.UUID id) {
        return jdbc.queryForObject("SELECT editable_content::text FROM manual_opportunity WHERE id=?",
                String.class, id);
    }

    private String deadlineJsonType(java.util.UUID id) {
        return jdbc.queryForObject("SELECT jsonb_typeof(editable_content->'deadline') FROM manual_opportunity WHERE id=?",
                String.class, id);
    }
}
