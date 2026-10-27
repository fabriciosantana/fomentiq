package com.fomentiq.ingestion.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.ingestion.application.FinepCallIngestionTransaction.CallIngestionResult;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import com.fomentiq.storage.application.PrivateAuditArchive;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates discovery, per-call committed work, and independently committed run audit. */
@Service
public class FinepIngestionService {
    private final SourceConnector connector;
    private final IngestionRunLifecycle runLifecycle;
    private final FinepCallIngestionTransaction callTransaction;
    private final PrivateAuditArchive auditArchive;
    private final ObjectMapper mapper;
    private final String codeRevision;

    @Autowired
    public FinepIngestionService(SourceConnector connector, IngestionRunLifecycle runLifecycle,
                                 FinepCallIngestionTransaction callTransaction,
                                 PrivateAuditArchive auditArchive, ObjectMapper mapper,
                                 @Value("${fomentiq.audit.code-revision:}") String codeRevision) {
        this.connector = connector;
        this.runLifecycle = runLifecycle;
        this.callTransaction = callTransaction;
        this.auditArchive = auditArchive;
        this.mapper = mapper;
        this.codeRevision = codeRevision == null ? "" : codeRevision.strip();
    }

    public FinepIngestionOutcome ingest() {
        return ingestInternal(connector, null);
    }

    public FinepIngestionOutcome ingest(FinepIngestionConstraints constraints) {
        if (constraints == null) throw new IllegalArgumentException("Finep validation constraints are required");
        return ingestInternal(connector, constraints);
    }

    /** Runs an explicitly selected connector through the same audited, transactional ingestion path. */
    public FinepIngestionOutcome ingest(SourceConnector selectedConnector) {
        if (selectedConnector == null) throw new IllegalArgumentException("A source connector is required");
        if (selectedConnector == connector) return ingest();
        return ingestInternal(selectedConnector, null);
    }

    private FinepIngestionOutcome ingestInternal(SourceConnector selectedConnector,
                                                 FinepIngestionConstraints constraints) {
        UUID runId = runLifecycle.start(selectedConnector.sourceType());
        int detected = 0;
        int changed = 0;
        int documentFailures = 0;
        List<String> documentFailureDetails = new java.util.ArrayList<>();
        IngestionRunStatus finalStatus = IngestionRunStatus.SUCCEEDED;
        String errorSummary = null;
        try {
            var runOrigin = mapper.createObjectNode().put("ingestionRunId", runId.toString())
                    .put("sourceType", selectedConnector.sourceType()).put("artifactOrigin", selectedConnector.artifactOrigin().name())
                    .put("capturedAt", Instant.now().toString());
            if (!codeRevision.isBlank()) runOrigin.put("codeRevision", codeRevision);
            auditArchive.putImmutable("run-origin/" + runId + ".json", mapper.writeValueAsBytes(runOrigin));
            if (constraints != null) constraints.limits().requireTimeRemaining();
            List<SourceConnector.SourceCall> discovered = constraints == null
                    ? selectedConnector.discover() : selectedConnector.discover(constraints.limits());
            detected = discovered.size();
            if (constraints != null && (discovered.size() != 1
                    || !constraints.externalId().equals(discovered.getFirst().externalId())
                    || !constraints.portalEntryId().equals(discovered.getFirst().portalEntryId())
                    || !constraints.canonicalUrl().equals(discovered.getFirst().canonicalUri()))) {
                throw new IllegalStateException("Finep discovery identity did not match the approved target");
            }
            for (SourceConnector.SourceCall sourceCall : discovered) {
                if (constraints != null) constraints.limits().requireTimeRemaining();
                CallIngestionResult result = constraints == null
                        ? callTransaction.ingest(selectedConnector, runId, sourceCall, Instant.now(), null)
                        : callTransaction.ingest(selectedConnector, runId, sourceCall, Instant.now(), constraints);
                changed += result.changedCount();
                documentFailures += result.documentFailureCount();
                documentFailureDetails.addAll(result.documentFailureDetails());
                if (result.extractionFailed()) {
                    finalStatus = IngestionRunStatus.FAILED;
                    errorSummary = "Structured extraction failed";
                    break;
                }
            }
            if (finalStatus == IngestionRunStatus.SUCCEEDED && documentFailures > 0) {
                finalStatus = IngestionRunStatus.PARTIAL;
                errorSummary = documentFailures + " call document(s) could not be preserved: "
                        + String.join(",", documentFailureDetails);
            }
        } catch (RuntimeException | IOException failure) {
            finalStatus = IngestionRunStatus.FAILED;
            String stage = failure instanceof com.fomentiq.extraction.application.StructuredExtractionService.ExtractionFailure
                    ? "Structured extraction failed" : selectedConnector.sourceType() + " source discovery or preservation failed";
            errorSummary = stage + " (" + failure.getClass().getSimpleName() + ")";
        }
        // Any failed call transaction has rolled back before this REQUIRES_NEW update begins.
        runLifecycle.finish(runId, finalStatus, detected, changed, errorSummary);
        return new FinepIngestionOutcome(runId, finalStatus, detected, changed, documentFailures);
    }
}
