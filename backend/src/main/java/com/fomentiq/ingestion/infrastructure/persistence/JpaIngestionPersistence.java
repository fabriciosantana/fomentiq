package com.fomentiq.ingestion.infrastructure.persistence;

import com.fomentiq.documents.domain.CallDocument;
import com.fomentiq.documents.domain.CallDocumentRepository;
import com.fomentiq.documents.domain.CallDocumentType;
import com.fomentiq.documents.domain.CallDocumentVersion;
import com.fomentiq.documents.domain.CallDocumentVersionRepository;
import com.fomentiq.funding.domain.CallForProposals;
import com.fomentiq.funding.domain.CallForProposalsRepository;
import com.fomentiq.funding.domain.CallStatus;
import com.fomentiq.funding.domain.CallVersion;
import com.fomentiq.funding.domain.CallVersionRepository;
import com.fomentiq.funding.domain.CallFundingAgency;
import com.fomentiq.funding.domain.CallSourceReference;
import com.fomentiq.funding.domain.FundingAgency;
import com.fomentiq.funding.domain.FundingAgencyRepository;
import com.fomentiq.funding.domain.SourcePublisher;
import com.fomentiq.ingestion.application.IngestionPersistence;
import com.fomentiq.ingestion.application.SourceConnector;
import com.fomentiq.ingestion.domain.IngestionRun;
import com.fomentiq.ingestion.domain.IngestionRunRepository;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.ingestion.domain.SourceSnapshot;
import com.fomentiq.ingestion.domain.SourceSnapshotRepository;
import com.fomentiq.ingestion.domain.SourceSnapshotType;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Relational persistence adapter joining existing module repositories for the ingestion port. */
@Repository
public class JpaIngestionPersistence implements IngestionPersistence {
    private final FundingAgencyRepository agencies;
    private final CallForProposalsRepository calls;
    private final CallVersionRepository callVersions;
    private final CallDocumentRepository documents;
    private final CallDocumentVersionRepository documentVersions;
    private final SourceSnapshotRepository snapshots;
    private final IngestionRunRepository runs;
    private final NamedParameterJdbcTemplate jdbc;

    public JpaIngestionPersistence(FundingAgencyRepository agencies, CallForProposalsRepository calls,
                                   CallVersionRepository callVersions, CallDocumentRepository documents,
                                   CallDocumentVersionRepository documentVersions, SourceSnapshotRepository snapshots,
                                   IngestionRunRepository runs, NamedParameterJdbcTemplate jdbc) {
        this.agencies = agencies;
        this.calls = calls;
        this.callVersions = callVersions;
        this.documents = documents;
        this.documentVersions = documentVersions;
        this.snapshots = snapshots;
        this.runs = runs;
        this.jdbc = jdbc;
    }

    @Override
    public UUID startRun(String sourceType, Instant startedAt) {
        return runs.saveAndFlush(new IngestionRun(sourceType, startedAt, IngestionRunStatus.RUNNING)).getId();
    }

    @Override
    public CallIdentity upsertCall(String publisherCode, SourceConnector.SourceCall source, Instant detectedAt) {
        SourcePublisher publisher = jdbc.queryForObject("""
                SELECT id, code, name, official_url FROM source_publisher WHERE code = :code
                """, new MapSqlParameterSource("code", publisherCode), (rs, row) ->
                new SourcePublisher(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("official_url")));
        if (publisher == null) throw new IllegalStateException("Unknown source publisher");
        FundingAgency agency = agency(source.primaryAgencyAcronym());
        CallStatus status = callStatus(source.status());
        CallSourceReference reference = jdbc.query("""
                SELECT id, call_id, publisher_id, external_id, portal_entry_id, canonical_url
                FROM call_source_reference WHERE publisher_id = :publisherId AND external_id = :externalId
                """, new MapSqlParameterSource().addValue("publisherId", publisher.id())
                .addValue("externalId", source.externalId()), (rs, row) -> new CallSourceReference(
                rs.getObject("id", UUID.class), rs.getObject("call_id", UUID.class),
                rs.getObject("publisher_id", UUID.class), rs.getString("external_id"),
                rs.getString("portal_entry_id"), rs.getString("canonical_url"))).stream().findFirst().orElse(null);
        CallForProposals call;
        if (reference != null) {
            if (!reference.canonicalUrl().equals(source.canonicalUri().toString())) {
                throw new IllegalStateException("Source reference canonical URL changed unexpectedly");
            }
            call = calls.findById(reference.callId()).orElseThrow();
        } else {
            // Source-native identity is scoped to its publisher. Never infer cross-publisher identity
            // from the legacy agency/external-id pair; migration backfills known Finep references.
            call = calls.save(new CallForProposals(agency, null, source.externalId(), source.title(),
                    source.summary(), null, status, source.publicationDate(), source.openingDate(), source.deadline(),
                    source.canonicalUri().toString(), detectedAt, detectedAt));
            call = calls.saveAndFlush(call);
            UUID referenceId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO call_source_reference (id, call_id, publisher_id, external_id, portal_entry_id, canonical_url)
                    VALUES (:id, :callId, :publisherId, :externalId, :portalEntryId, :canonicalUrl)
                    """, new MapSqlParameterSource().addValue("id", referenceId).addValue("callId", call.getId())
                    .addValue("publisherId", publisher.id()).addValue("externalId", source.externalId())
                    .addValue("portalEntryId", source.portalEntryId()).addValue("canonicalUrl", source.canonicalUri().toString()));
            reference = new CallSourceReference(referenceId, call.getId(), publisher.id(), source.externalId(),
                    source.portalEntryId(), source.canonicalUri().toString());
        }
        call.updateFromSource(source.portalEntryId(), source.title(), source.summary(), status,
                source.publicationDate(), source.openingDate(), source.deadline(), source.canonicalUri().toString(), detectedAt);
        UUID callId = calls.saveAndFlush(call).getId();
        jdbc.update("UPDATE call_funding_agency SET is_primary = FALSE WHERE call_id = :callId",
                new MapSqlParameterSource("callId", callId));
        persistFundingAgency(new CallFundingAgency(callId, agency.getId(), true));
        for (String acronym : source.additionalFundingAgencyAcronyms()) {
            FundingAgency additional = agency(acronym);
            if (additional.getId().equals(agency.getId())) continue;
            persistFundingAgency(new CallFundingAgency(callId, additional.getId(), false));
        }
        return new CallIdentity(callId, reference.id());
    }

    @Override
    public boolean recordCallVersion(UUID callId, String contentHash, Instant detectedAt) {
        Optional<CallVersion> latest = callVersions.findTopByCall_IdOrderByVersionNumberDesc(callId);
        if (latest.isPresent() && latest.get().getContentHash().equals(contentHash)) return false;
        CallForProposals call = calls.getReferenceById(callId);
        int nextVersion = latest.map(version -> version.getVersionNumber() + 1).orElse(1);
        callVersions.saveAndFlush(new CallVersion(call, nextVersion, contentHash, detectedAt, latest.orElse(null)));
        return true;
    }

    @Override
    public UUID latestCallVersionId(UUID callId) {
        return callVersions.findTopByCall_IdOrderByVersionNumberDesc(callId).orElseThrow().getId();
    }

    @Override
    public void recordSnapshot(UUID runId, UUID callId, UUID sourceReferenceId, SourceSnapshotType type, URI sourceUri,
                               String contentHash, String storageKey, String mediaType, Instant capturedAt) {
        snapshots.saveAndFlush(new SourceSnapshot(callId, sourceReferenceId, runs.getReferenceById(runId), type,
                sourceUri.toString(), contentHash, storageKey, mediaType, capturedAt));
    }

    @Override
    public UUID upsertDocument(UUID callId, UUID sourceReferenceId, SourceConnector.SourceDocument source) {
        CallDocument document = documents.findBySourceReferenceIdAndExternalId(sourceReferenceId, source.externalId())
                .orElseGet(() -> documents.save(new CallDocument(callId, sourceReferenceId, source.externalId(),
                        documentType(source.category()), source.title(), source.sourceUri().toString())));
        document.updateSource(documentType(source.category()), source.title(), source.sourceUri().toString());
        return documents.saveAndFlush(document).getId();
    }

    @Override
    public Optional<String> latestDocumentVersionHash(UUID documentId) {
        return documentVersions.findTopByDocument_IdOrderByDownloadedAtDesc(documentId)
                .map(CallDocumentVersion::getContentHash);
    }

    @Override
    public boolean recordDocumentVersion(UUID documentId, String contentHash, String storageKey,
                                         String mediaType, String sourceUrl, Instant downloadedAt) {
        Optional<CallDocumentVersion> latest = documentVersions.findTopByDocument_IdOrderByDownloadedAtDesc(documentId);
        if (latest.isPresent() && latest.get().getContentHash().equals(contentHash)) return false;
        CallDocument document = documents.getReferenceById(documentId);
        documentVersions.saveAndFlush(new CallDocumentVersion(document, contentHash, storageKey,
                mediaType, sourceUrl, downloadedAt, null));
        return true;
    }

    @Override
    public UUID latestDocumentVersionId(UUID documentId) {
        return documentVersions.findTopByDocument_IdOrderByDownloadedAtDesc(documentId).orElseThrow().getId();
    }

    @Override
    public void finishRun(UUID runId, Instant finishedAt, IngestionRunStatus status, int detectedCount,
                          int changedCount, String errorSummary) {
        IngestionRun run = runs.findById(runId).orElseThrow();
        run.finish(finishedAt, status, detectedCount, changedCount, errorSummary);
        runs.saveAndFlush(run);
    }

    private static CallStatus callStatus(String value) {
        try { return CallStatus.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException exception) { return CallStatus.UNKNOWN; }
    }

    private FundingAgency agency(String acronym) {
        return agencies.findByAcronym(acronym).orElseGet(() -> switch (acronym) {
            case "FINEP" -> agencies.saveAndFlush(new FundingAgency("Financiadora de Estudos e Projetos",
                    "FINEP", "https://www.finep.gov.br"));
            case "CNPq" -> agencies.saveAndFlush(new FundingAgency(
                    "Conselho Nacional de Desenvolvimento Científico e Tecnológico", "CNPq", "https://www.gov.br/cnpq"));
            case "CAPES" -> agencies.saveAndFlush(new FundingAgency(
                    "Coordenação de Aperfeiçoamento de Pessoal de Nível Superior", "CAPES", "https://www.gov.br/capes"));
            default -> throw new IllegalArgumentException("Unsupported responsible funding agency");
        });
    }

    private void persistFundingAgency(CallFundingAgency association) {
        jdbc.update("""
                INSERT INTO call_funding_agency (call_id, agency_id, is_primary)
                VALUES (:callId, :agencyId, :primary)
                ON CONFLICT (call_id, agency_id) DO UPDATE SET is_primary = EXCLUDED.is_primary
                """, new MapSqlParameterSource().addValue("callId", association.callId())
                .addValue("agencyId", association.agencyId()).addValue("primary", association.primary()));
    }

    private static CallDocumentType documentType(String category) {
        String value = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (value.contains("edital")) return CallDocumentType.NOTICE;
        if (value.contains("anexo")) return CallDocumentType.ANNEX;
        if (value.contains("pergunta") || value.contains("faq")) return CallDocumentType.FAQ;
        if (value.contains("rerrat") || value.contains("retifica")) return CallDocumentType.AMENDMENT;
        if (value.contains("resultado") && value.contains("preliminar")) return CallDocumentType.RESULT_PRELIMINARY;
        if (value.contains("resultado") || value.contains("mérito") || value.contains("merito")) {
            return CallDocumentType.RESULT_FINAL;
        }
        return CallDocumentType.OTHER;
    }
}
