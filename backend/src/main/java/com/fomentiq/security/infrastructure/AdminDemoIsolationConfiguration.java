package com.fomentiq.security.infrastructure;

import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Validates isolated demo identities before Flyway or JPA can connect to a configured database. */
@Configuration
@Profile("manual-demo")
@ConditionalOnProperty(name = "fomentiq.admin.demo.enabled", havingValue = "true")
public class AdminDemoIsolationConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateAdminDemoIsolation(Environment environment) {
        return beanFactory -> {
            verifyConfiguration(environment);
            verifyComposeResources(environment.getProperty("FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT"),
                    environment.getProperty("FOMENTIQ_ADMIN_DEMO_POSTGRES_PORT", Integer.class, -1),
                    environment.getProperty("FOMENTIQ_ADMIN_DEMO_S3_PORT", Integer.class, -1),
                    environment.getProperty("FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT", Integer.class, -1));
        };
    }

    static void verifyConfiguration(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        boolean profilesMatch = profiles.length == 1 && "manual-demo".equals(profiles[0]);
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        String databaseUser = environment.getProperty("spring.datasource.username", "");
        String endpoint = environment.getProperty("fomentiq.storage.s3.endpoint", "");
        String bucket = environment.getProperty("fomentiq.storage.s3.bucket", "");
        String project = environment.getProperty("FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT", "");
        int databasePort = environment.getProperty("FOMENTIQ_ADMIN_DEMO_POSTGRES_PORT", Integer.class, -1);
        int storagePort = environment.getProperty("FOMENTIQ_ADMIN_DEMO_S3_PORT", Integer.class, -1);
        int consolePort = environment.getProperty("FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT", Integer.class, -1);
        boolean manual = "fomentiq-manual-evaluation".equals(project)
                && "fomentiq".equals(databaseUser)
                && isDedicatedJdbcUrl(jdbcUrl, 15432, "/fomentiq")
                && "http://127.0.0.1:19000".equals(endpoint)
                && "fomentiq-manual-evaluation".equals(bucket) && consolePort == 19001;
        boolean e2e = project.matches("fomentiq-curation-e2e-[0-9]+")
                && "fomentiq_e2e".equals(databaseUser)
                && isDedicatedJdbcUrl(jdbcUrl, databasePort, "/fomentiq_e2e")
                && endpoint.equals("http://127.0.0.1:" + storagePort)
                && bucket.equals(project) && consolePort > 0;
        boolean matches = profilesMatch && (manual || e2e)
                && "true".equalsIgnoreCase(environment.getProperty("fomentiq.storage.s3.enabled", "false"));
        if (!matches) {
            throw new IllegalStateException("Admin demo requires the isolated fomentiq-manual-evaluation resources");
        }
    }

    static void verifyComposeResources(String project, int postgresPort, int storagePort, int consolePort) {
        String listing = dockerOutput("docker", "ps", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.ID}}|{{.Label \"com.docker.compose.service\"}}|{{.Ports}}");
        verifyComposeListing(listing, postgresPort, storagePort, consolePort);
        Map<String, String> services = parseServices(listing);
        verifyVolume(project, services.get("postgres-id"), project + "_postgres_data", "/var/lib/postgresql/data");
        verifyVolume(project, services.get("object-storage-id"), project + "_object_storage_data", "/data");
    }

    static void verifyComposeListing(String listing, int postgresPort, int storagePort, int consolePort) {
        Map<String, String> services = parseServices(listing);
        if (services.size() != 4 || !services.containsKey("postgres") || !services.containsKey("object-storage")
                || !services.get("postgres").contains("127.0.0.1:" + postgresPort + "->5432/tcp")
                || !services.get("object-storage").contains("127.0.0.1:" + storagePort + "->9000/tcp")
                || !services.get("object-storage").contains("127.0.0.1:" + consolePort + "->9001/tcp")
                || postgresPort < 1 || storagePort < 1 || consolePort < 1) {
            throw new IllegalStateException("Admin demo Compose containers or loopback port bindings do not match");
        }
    }

    private static Map<String, String> parseServices(String output) {
        Map<String, String> result = new HashMap<>();
        for (String line : output.lines().toList()) {
            String[] columns = line.split("\\|", 3);
            if (columns.length != 3 || columns[0].isBlank() || columns[1].isBlank()) continue;
            if (result.putIfAbsent(columns[1], columns[0] + "|" + columns[2]) != null) {
                throw new IllegalStateException("Admin demo Compose service is not unique");
            }
        }
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : result.entrySet()) {
            String[] values = entry.getValue().split("\\|", 2);
            normalized.put(entry.getKey(), values[1]);
            normalized.put(entry.getKey() + "-id", values[0]);
        }
        return normalized;
    }

    private static void verifyVolume(String project, String containerId, String expectedName, String destination) {
        String mounts = dockerOutput("docker", "inspect", "--format",
                "{{range .Mounts}}{{.Name}}|{{.Destination}};{{end}}", containerId);
        boolean found = java.util.Arrays.stream(mounts.split(";"))
                .map(line -> line.split("\\|", 2))
                .anyMatch(parts -> parts.length == 2 && expectedName.equals(parts[0]) && destination.equals(parts[1]));
        if (!found || containerId == null || project.isBlank()) {
            throw new IllegalStateException("Admin demo Compose volume identity does not match");
        }
    }

    private static String dockerOutput(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Admin demo Compose identity check timed out");
            }
            byte[] output = process.getInputStream().readNBytes(8192);
            if (process.exitValue() != 0 || process.getInputStream().read() != -1) {
                throw new IllegalStateException("Admin demo Compose identity check failed");
            }
            return new String(output, StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Docker Compose is required to enable demo administration", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Admin demo Compose identity check was interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static boolean isDedicatedJdbcUrl(String value, int expectedPort, String expectedDatabase) {
        if (value == null || !value.startsWith("jdbc:postgresql://")) return false;
        try {
            URI uri = URI.create(value.substring("jdbc:".length()));
            return ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                    && uri.getPort() == expectedPort && expectedDatabase.equals(uri.getPath())
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
