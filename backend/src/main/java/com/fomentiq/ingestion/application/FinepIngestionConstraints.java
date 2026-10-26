package com.fomentiq.ingestion.application;

import com.fomentiq.extraction.application.StructuredExtractionProvider.ExecutionLimits;
import java.net.URI;

/** Exact call identity and acquisition budget supplied only to a controlled Finep validation run. */
public record FinepIngestionConstraints(String externalId, String portalEntryId, URI canonicalUrl,
                                        IngestionLimits limits, ExecutionLimits extractionLimits) {
    public FinepIngestionConstraints {
        if (externalId == null || portalEntryId == null || canonicalUrl == null || limits == null
                || extractionLimits == null) {
            throw new IllegalArgumentException("Finep ingestion constraints are incomplete");
        }
    }
}
