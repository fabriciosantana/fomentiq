package com.fomentiq.storage.application;

/** Stores immutable source bytes and returns an opaque key suitable for persistence. */
public interface ArtifactStore {
    String put(String key, byte[] content, String mediaType);
}
