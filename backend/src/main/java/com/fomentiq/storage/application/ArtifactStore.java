package com.fomentiq.storage.application;

/** Stores immutable source bytes and returns an opaque key suitable for persistence. */
public interface ArtifactStore {
    String put(String key, byte[] content, String mediaType);

    default byte[] get(String key) {
        throw new UnsupportedOperationException("Artifact reads are not configured");
    }

    /** Reads an object with a caller-provided upper bound. */
    default byte[] get(String key, int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        throw new UnsupportedOperationException("Bounded artifact reads are not configured");
    }
}
