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
import com.fomentiq.funding.domain.FundingAgency;
import com.fomentiq.funding.domain.FundingAgencyRepository;
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

    public JpaIngestionPersistence(FundingAgencyRepository agencies, CallForProposalsRepository calls,
                                   CallVersionRepository callVersions, CallDocumentRepository documents,
                                   CallDocumentVersionRepository documentVersions, SourceSnapshotRepository snapshots,
                                   IngestionRunRepository runs) {
        this.agencies = agencies;
        this.calls = calls;
        this.callVersions = callVersions;
        this.documents = documents;
        this.documentVersions = documentVersions;
        this.snapshots = snapshots;
        this.runs = runs;
    }

    @Override
    public UUID startRun(String sourceType, Instant startedAt) {
        return runs.saveAndFlush(new IngestionRun(sourceType, startedAt, IngestionRunStatus.RUNNING)).getId();
    }

    @Override
    public UUID upsertCall(SourceConnector.SourceCall source, Instant detectedAt) {
        FundingAgency agency = agencies.findByAcronym("FINEP").orElseGet(() -> agencies.saveAndFlush(
                new FundingAgency("Financiadora de Estudos e Projetos", "FINEP", "https://www.finep.gov.br")));
        CallStatus status = callStatus(source.status());
        CallForProposals call = calls.findByAgencyAcronymAndExternalId(agency.getAcronym(), source.externalId())
                .orElseGet(() -> calls.save(new CallForProposals(agency, null, source.externalId(), source.title(),
                        source.summary(), null, status, source.publicationDate(), source.openingDate(), source.deadline(),
                        source.canonicalUri().toString(), detectedAt, detectedAt)));
        call.updateFromSource(source.portalEntryId(), source.title(), source.summary(), status,
                source.publicationDate(), source.openingDate(), source.deadline(), source.canonicalUri().toString(), detectedAt);
        return calls.saveAndFlush(call).getId();
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
    public void recordSnapshot(UUID runId, UUID callId, SourceSnapshotType type, URI sourceUri,
                               String contentHash, String storageKey, String mediaType, Instant capturedAt) {
        snapshots.saveAndFlush(new SourceSnapshot(callId, runs.getReferenceById(runId), type,
                sourceUri.toString(), contentHash, storageKey, mediaType, capturedAt));
    }

    @Override
    public UUID upsertDocument(UUID callId, SourceConnector.SourceDocument source) {
        CallDocument document = documents.findByCallIdAndExternalId(callId, source.externalId())
                .orElseGet(() -> documents.save(new CallDocument(callId, source.externalId(),
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
