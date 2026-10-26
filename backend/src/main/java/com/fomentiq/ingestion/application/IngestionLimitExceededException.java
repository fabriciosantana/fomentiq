package com.fomentiq.ingestion.application;

/** Safe, non-sensitive terminal exception for an approved ingestion bound. */
public final class IngestionLimitExceededException extends RuntimeException {
    public IngestionLimitExceededException(String message) { super(message); }
}
