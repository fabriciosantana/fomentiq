package com.fomentiq.storage.application;

/** The stored object exceeds a bounded read requested by the caller. */
public final class ArtifactTooLargeException extends RuntimeException {
    public ArtifactTooLargeException() {
        super("Stored artifact exceeds the permitted read size");
    }
}
