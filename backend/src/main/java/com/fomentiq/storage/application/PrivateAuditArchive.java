package com.fomentiq.storage.application;

/** Private, non-S3 archive used for immutable audit artifacts and packages. */
public interface PrivateAuditArchive {
    String putImmutable(String relativeKey, byte[] content);

    byte[] read(String relativeKey);

    default boolean contains(String relativeKey) { return false; }
}
