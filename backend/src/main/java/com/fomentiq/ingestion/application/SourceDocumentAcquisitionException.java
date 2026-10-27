package com.fomentiq.ingestion.application;

/** Safe, stable failure classification for a source document that cannot be accepted. */
public final class SourceDocumentAcquisitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public SourceDocumentAcquisitionException(Reason reason) {
        super("Source document acquisition rejected: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }

    public enum Reason { UNSUPPORTED_CONTENT_TYPE, INVALID_DOCUMENT_CONTENT }
}
