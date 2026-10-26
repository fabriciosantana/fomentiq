package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.domain.IngestionRunStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits ingestion-run audit transitions independently from a call's business transaction. */
@Service
public class IngestionRunLifecycle {
    private final IngestionPersistence persistence;
    private final Clock clock;

    @Autowired
    public IngestionRunLifecycle(IngestionPersistence persistence) {
        this(persistence, Clock.systemUTC());
    }

    IngestionRunLifecycle(IngestionPersistence persistence, Clock clock) {
        this.persistence = persistence;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(String sourceType) {
        return persistence.startRun(sourceType, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID runId, IngestionRunStatus status, int detectedCount,
                       int changedCount, String errorSummary) {
        persistence.finishRun(runId, clock.instant(), status, detectedCount, changedCount, errorSummary);
    }
}
