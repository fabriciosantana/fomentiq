package com.fomentiq.editorial.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fomentiq.api.web.PublicUrlSanitizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManualOpportunityService {
    private static final Set<String> TYPES = Set.of(
            "SHORT_TEXT", "LONG_TEXT", "DATE", "CURRENCY", "LINK", "LIST");
    private final ManualOpportunityStore store;
    private final PublicUrlSanitizer urls;

    public ManualOpportunityService(ManualOpportunityStore store, PublicUrlSanitizer urls) {
        this.store = store;
        this.urls = urls;
    }

    public ManualOpportunityStore.Page<ManualOpportunity> list(
            String query, boolean published, int page, int size) {
        bounds(page, size);
        String safeQuery = query == null ? "" : query;
        if (safeQuery.length() > 200) {
            bad("A busca pode ter até 200 caracteres.");
        }
        return store.list(safeQuery, published, page, size);
    }

    public ManualOpportunity get(UUID id, boolean publishedOnly) {
        return store.find(id, publishedOnly)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ManualOpportunity create(ManualOpportunity input, String actor) {
        validate(input, false);
        return store.create(new ManualOpportunity(UUID.randomUUID(), input.title().trim(),
                input.institution().trim(), input.summary(), input.deadline(), input.status().trim(),
                cleanUrl(input.officialUrl()), "DRAFT", safeSections(input.sections()), 0,
                actor, actor, null, null), actor);
    }

    @Transactional
    public ManualOpportunity save(UUID id, ManualOpportunity input, long expected, String actor) {
        validate(input, false);
        return store.save(new ManualOpportunity(id, input.title().trim(), input.institution().trim(),
                input.summary(), input.deadline(), input.status().trim(), cleanUrl(input.officialUrl()),
                "DRAFT", safeSections(input.sections()), expected, actor, actor, null, null), expected, actor);
    }

    @Transactional
    public ManualOpportunity publish(UUID id, long expected, String actor) {
        ManualOpportunity current = get(id, false);
        validate(current, true);
        return store.publish(id, expected, actor);
    }

    @Transactional
    public ManualOpportunity unpublish(UUID id, long expected, String actor) {
        return store.unpublish(id, expected, actor);
    }

    private void validate(ManualOpportunity item, boolean publication) {
        if (item == null || item.title() == null || item.title().length() > 240
                || item.institution() == null || item.institution().length() > 240
                || item.summary() == null || item.summary().length() > 2000
                || item.status() == null || item.status().length() > 40) {
            bad("Revise o tamanho dos dados principais.");
        }
        if (publication && (blank(item.title()) || blank(item.institution()) || blank(item.status()))) {
            bad("Preencha título, instituição responsável e situação antes de publicar.");
        }
        cleanUrl(item.officialUrl());
        if (item.sections().size() > 40) {
            bad("A oportunidade pode ter no máximo 40 seções.");
        }
        int fieldCount = 0;
        Set<String> sectionNames = new HashSet<>();
        for (int sectionIndex = 0; sectionIndex < item.sections().size(); sectionIndex++) {
            var section = item.sections().get(sectionIndex);
            if (blank(section.title()) || section.title().length() > 160
                    || section.position() != sectionIndex
                    || !sectionNames.add(section.title().trim().toLowerCase())) {
                bad("Confira os títulos e a ordem das seções.");
            }
            for (int fieldIndex = 0; fieldIndex < section.fields().size(); fieldIndex++) {
                var field = section.fields().get(fieldIndex);
                fieldCount++;
                if (blank(field.name()) || field.name().length() > 120
                        || field.position() != fieldIndex || !TYPES.contains(field.type())) {
                    bad("Confira o nome, o tipo e a ordem das informações.");
                }
                validateValue(field.type(), field.value());
            }
        }
        if (fieldCount > 200) {
            bad("A oportunidade pode ter no máximo 200 informações.");
        }
        if (publication && item.officialUrl() == null) {
            bad("Adicione o link oficial antes de publicar.");
        }
    }

    private void validateValue(String type, Object value) {
        if (value == null) {
            return;
        }
        try {
            switch (type) {
                case "SHORT_TEXT" -> validateText(value, 500, "Texto curto");
                case "LONG_TEXT" -> validateText(value, 10000, "Texto longo");
                case "DATE" -> {
                    if (!(value instanceof String text)) {
                        bad("Informe uma data válida.");
                    } else if (!text.isBlank()) {
                        LocalDate.parse(text);
                    }
                }
                case "CURRENCY" -> validateCurrency(value);
                case "LINK" -> {
                    if (!(value instanceof String text)) {
                        bad("Informe um link válido.");
                    } else if (!text.isBlank()) {
                        cleanUrl(text);
                    }
                }
                case "LIST" -> validateList(value);
                default -> bad("Tipo de informação não suportado.");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException) {
                throw exception;
            }
            bad("O valor não corresponde ao tipo selecionado.");
        }
    }

    private List<ManualOpportunity.Section> safeSections(List<ManualOpportunity.Section> sections) {
        return sections.stream().map(section -> new ManualOpportunity.Section(section.title(),
                section.position(), section.fields().stream().map(field -> {
                    Object value = field.value();
                    if ("LINK".equals(field.type()) && value instanceof String link && !link.isBlank()) {
                        value = cleanUrl(link);
                    }
                    return new ManualOpportunity.Field(field.name(), field.type(), value, field.position());
                }).toList())).toList();
    }

    private void validateText(Object value, int maxLength, String label) {
        if (!(value instanceof String text) || text.length() > maxLength) {
            bad(label + " excede o limite permitido.");
        }
    }

    private void validateCurrency(Object value) {
        if (!(value instanceof String text)) {
            bad("Informe um valor em reais válido.");
        }
        var amount = new BigDecimal((String) value);
        if (amount.signum() < 0 || amount.scale() > 2) {
            bad("Informe um valor em reais válido.");
        }
    }

    private void validateList(Object value) {
        if (!(value instanceof List<?> list) || list.size() > 100
                || list.stream().anyMatch(entry -> !(entry instanceof String text) || text.length() > 500)) {
            bad("Informe uma lista válida, com até 100 itens.");
        }
    }

    private String cleanUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 2000) {
            bad("O endereço oficial excede o limite permitido.");
        }
        String safe = urls.sanitize(value);
        if (safe == null) {
            bad("Use um endereço HTTP ou HTTPS válido.");
        }
        return safe;
    }

    private void bounds(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            bad("Parâmetros de página inválidos.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
