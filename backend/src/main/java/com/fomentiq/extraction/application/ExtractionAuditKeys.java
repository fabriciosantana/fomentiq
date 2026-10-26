package com.fomentiq.extraction.application;

import java.util.UUID;

/** Stable archive keys linked to existing extraction_execution identity fields. */
public final class ExtractionAuditKeys {
    private ExtractionAuditKeys() { }

    public static String input(UUID runId, UUID callVersionId, String schemaVersion, String inputDigest) {
        return "extraction-input/" + runId + "/" + callVersionId + "/" + schemaVersion + "/" + inputDigest + ".json";
    }

    public static String result(UUID runId, UUID callVersionId, String schemaVersion, String inputDigest) {
        return "extraction-result/" + runId + "/" + callVersionId + "/" + schemaVersion + "/" + inputDigest + ".json";
    }
}
