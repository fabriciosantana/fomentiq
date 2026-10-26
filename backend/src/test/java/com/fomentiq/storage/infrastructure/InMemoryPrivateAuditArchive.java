package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.PrivateAuditArchive;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPrivateAuditArchive implements PrivateAuditArchive {
    private final Map<String, byte[]> artifacts = new ConcurrentHashMap<>();
    private volatile boolean failWrites;

    @Override
    public String putImmutable(String relativeKey, byte[] content) {
        if (failWrites) throw new IllegalStateException("audit_archive_write_failed");
        byte[] prior = artifacts.putIfAbsent(relativeKey, content.clone());
        if (prior != null && !java.util.Arrays.equals(prior, content)) {
            throw new IllegalStateException("immutable_artifact_conflict");
        }
        return relativeKey;
    }

    @Override
    public byte[] read(String relativeKey) {
        byte[] value = artifacts.get(relativeKey);
        if (value == null) throw new IllegalStateException("audit_artifact_missing");
        return value.clone();
    }

    @Override public boolean contains(String relativeKey) { return artifacts.containsKey(relativeKey); }

    public Map<String, byte[]> artifacts() { return Map.copyOf(artifacts); }
    public void remove(String relativeKey) { artifacts.remove(relativeKey); }
    public void failWrites(boolean enabled) { failWrites = enabled; }
    public void clear() { artifacts.clear(); failWrites = false; }
}
