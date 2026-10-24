package com.fomentiq.ingestion.application;

import com.fomentiq.ingestion.domain.IngestionRunStatus;
import java.util.UUID;

public record FinepIngestionOutcome(UUID runId, IngestionRunStatus status, int detectedCount,
                                   int changedCount, int documentFailureCount) { }
