package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.application.FinepCallIngestionTransaction.CallIngestionResult;
import com.fomentiq.ingestion.domain.IngestionRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Coordinates discovery, per-call committed work, and independently committed run audit. */
@Service
public class FinepIngestionService {
    private final SourceConnector connector;
    private final IngestionRunLifecycle runLifecycle;
    private final FinepCallIngestionTransaction callTransaction;

    @Autowired
    public FinepIngestionService(SourceConnector connector, IngestionRunLifecycle runLifecycle,
                                 FinepCallIngestionTransaction callTransaction) {
        this.connector = connector;
        this.runLifecycle = runLifecycle;
        this.callTransaction = callTransaction;
    }

    public FinepIngestionOutcome ingest() {
        return ingestInternal(null);
    }

    public FinepIngestionOutcome ingest(FinepIngestionConstraints constraints) {
        if (constraints == null) throw new IllegalArgumentException("Finep validation constraints are required");
        return ingestInternal(constraints);
    }

    private FinepIngestionOutcome ingestInternal(FinepIngestionConstraints constraints) {
        UUID runId = runLifecycle.start(connector.sourceType());
        int detected = 0;
        int changed = 0;
        int documentFailures = 0;
        IngestionRunStatus finalStatus = IngestionRunStatus.SUCCEEDED;
        String errorSummary = null;
        try {
            if (constraints != null) constraints.limits().requireTimeRemaining();
            List<SourceConnector.SourceCall> discovered = constraints == null
                    ? connector.discover() : connector.discover(constraints.limits());
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
                        ? callTransaction.ingest(runId, sourceCall, Instant.now())
                        : callTransaction.ingest(runId, sourceCall, Instant.now(), constraints);
                changed += result.changedCount();
                documentFailures += result.documentFailureCount();
                if (result.extractionFailed()) {
                    finalStatus = IngestionRunStatus.FAILED;
                    errorSummary = "Structured extraction failed";
                    break;
                }
            }
            if (finalStatus == IngestionRunStatus.SUCCEEDED && documentFailures > 0) {
                finalStatus = IngestionRunStatus.PARTIAL;
                errorSummary = documentFailures + " call document(s) could not be preserved";
            }
        } catch (RuntimeException failure) {
            finalStatus = IngestionRunStatus.FAILED;
            String stage = failure instanceof com.fomentiq.extraction.application.StructuredExtractionService.ExtractionFailure
                    ? "Structured extraction failed" : "Finep source discovery or preservation failed";
            errorSummary = stage + " (" + failure.getClass().getSimpleName() + ")";
        }
        // Any failed call transaction has rolled back before this REQUIRES_NEW update begins.
        runLifecycle.finish(runId, finalStatus, detected, changed, errorSummary);
        return new FinepIngestionOutcome(runId, finalStatus, detected, changed, documentFailures);
    }
}
