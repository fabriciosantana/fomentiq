package com.fomentiq.documents.application;

import java.util.Optional;
import java.util.UUID;

/** Internal lookup for an immutable document version scoped to its opportunity. */
public interface PreservedDocumentVersionQuery {
    Optional<VersionObject> find(UUID callId, UUID documentId, UUID versionId);

    record VersionObject(String storageKey, String contentHash, String mediaType) { }
}
