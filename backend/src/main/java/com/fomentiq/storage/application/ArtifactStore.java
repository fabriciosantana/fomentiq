package com.fomentiq.storage.application;

/** Stores immutable source bytes and returns an opaque key suitable for persistence. */
public interface ArtifactStore {
    String put(String key, byte[] content, String mediaType);

    default byte[] get(String key) {
        throw new UnsupportedOperationException("Artifact reads are not configured");
    }
}
