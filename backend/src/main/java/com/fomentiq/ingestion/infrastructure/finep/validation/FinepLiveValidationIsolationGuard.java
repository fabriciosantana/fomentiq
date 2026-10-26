package com.fomentiq.ingestion.infrastructure.finep.validation;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/** Fail-closed comparison of effective runtime targets with an inspected dedicated Compose project. */
public final class FinepLiveValidationIsolationGuard {
    public void verify(FinepLiveValidationManifest manifest, RuntimeTargets runtime, ComposeProject project) {
        var expected = manifest.isolation();
        if (runtime == null || project == null || !expected.jdbcUrl().equals(runtime.jdbcUrl())
                || !expected.database().equals(runtime.database()) || !expected.username().equals(runtime.username())
                || !expected.storageEndpoint().equals(runtime.storageEndpoint())
                || !expected.storageBucket().equals(runtime.storageBucket())
                || !expected.composeProject().equals(project.name())
                || Set.of("fomentiq", "fomentiq-manual-evaluation").contains(project.name())
                || !project.services().keySet().equals(Set.of("postgres", "object-storage"))
                || project.services().entrySet().stream().anyMatch(entry -> !project.name().equals(entry.getValue().project())
                || !entry.getKey().equals(entry.getValue().service()))
                || !project.services().get("postgres").mounts().equals(Set.of(expected.postgresVolume()))
                || !project.services().get("object-storage").mounts().equals(Set.of(expected.storageVolume()))
                || !project.services().get("postgres").publishedPorts().contains(port(expected.jdbcUrl()))
                || !project.services().get("object-storage").publishedPorts().contains(port(expected.storageEndpoint()))) {
            throw new IllegalStateException("Finep validation isolation could not be verified");
        }
    }

    private static int port(String jdbc) {
        String authority = jdbc.substring("jdbc:postgresql://".length()).split("/", 2)[0];
        return Integer.parseInt(authority.substring(authority.lastIndexOf(':') + 1));
    }

    private static int port(URI endpoint) {
        return endpoint.getPort() < 0 ? ("https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80) : endpoint.getPort();
    }

    public record RuntimeTargets(String jdbcUrl, String database, String username,
                                 URI storageEndpoint, String storageBucket) { }

    public record ComposeProject(String name, Map<String, Service> services) { }

    public record Service(String project, String service, Set<String> mounts, Set<Integer> publishedPorts) { }
}
