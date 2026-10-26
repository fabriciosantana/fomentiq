package com.fomentiq.ingestion.infrastructure.finep.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.extraction.application.ExtractionSchemaV1;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Private CLI gate; it starts no Spring context unless the separately approved execution flag is present. */
public final class FinepLiveValidationCli {
    private static final Set<String> PLACEHOLDER_PROPERTY_NAMES = Set.of("DATABASE_URL", "DATABASE_USERNAME",
            "DATABASE_PASSWORD", "S3_ENABLED", "S3_ENDPOINT", "S3_BUCKET", "S3_REGION", "S3_ACCESS_KEY",
            "S3_SECRET_KEY", "OPENAI_API_KEY", "OPENAI_MODEL", "EXTRACTION_REASONING_EFFORT",
            "EXTRACTION_TIMEOUT_SECONDS", "EXTRACTION_MAX_RETRIES", "EXTRACTION_MAX_INPUT_TOKENS",
            "EXTRACTION_MAX_OUTPUT_TOKENS", "FINEP_CANONICAL_URL");
    private static final java.util.concurrent.ExecutorService DB_TIMEOUT_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "finep-validation-db-timeout");
                thread.setDaemon(true);
                return thread;
            });
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final FinepLiveValidationManifestLoader loader = new FinepLiveValidationManifestLoader(mapper);
    private final FinepLiveValidationPolicy policy = new FinepLiveValidationPolicy();
    private final FinepLiveValidationIsolationGuard isolationGuard = new FinepLiveValidationIsolationGuard();
    private final FinepLiveStorageBucketGuard storageBucketGuard = new FinepLiveStorageBucketGuard();

    public static boolean requested(String[] args) {
        return java.util.Arrays.asList(args).contains("--fomentiq.finep.live-validation.enabled=true");
    }

    public int execute(String[] args, Map<String, String> environment) {
        FinepLiveValidationManifest validatedManifest = null;
        boolean liveExecutionRequested = false;
        try {
            rejectRuntimeOverrides(args, environment, System.getProperties());
            Map<String, String> options = options(args);
            if (!"true".equals(options.get("--fomentiq.finep.live-validation.enabled"))) return 2;
            String executeOption = options.get("--fomentiq.finep.live-validation.execute");
            if (executeOption != null && !Set.of("true", "false").contains(executeOption)) {
                throw new IllegalArgumentException("Private runner execution flag is invalid");
            }
            String approval = options.get("--fomentiq.finep.live-validation.approval-reference");
            boolean liveExecution = "true".equals(executeOption);
            liveExecutionRequested = liveExecution;
            Path manifestPath = Path.of(required(options, "--fomentiq.finep.live-validation.manifest"));
            FinepLiveValidationManifest manifest = loader.load(manifestPath);
            String model = environment.getOrDefault("OPENAI_MODEL", "gpt-6-luna");
            policy.validate(manifest, approval, model, ExtractionSchemaV1.VERSION);
            if (liveExecution) policy.requireLiveApprovalReference(manifest, approval);
            validatedManifest = manifest;
            validateProviderRuntime(manifest, environment);
            validateRuntimeConfiguration(manifest, environment);
            isolationGuard.verify(manifest, runtime(manifest, environment), inspectCompose(manifest));
            verifyDatabaseIdentity(manifest.isolation(), environment.get("DATABASE_PASSWORD"));
            verifyStorageBucketAbsent(manifest, environment);
            var estimate = policy.estimatedMaximumCost(manifest.provider());
            boolean withinBudget = estimate.compareTo(manifest.provider().maxSpendUsd()) <= 0;
            String reason = withinBudget ? "requires_separate_live_execution_approval"
                    : "estimated_cost_exceeds_approved_budget";
            var reportNode = mapper.createObjectNode().put("runId", manifest.runId())
                    .put("status", withinBudget ? "READY_FOR_SEPARATE_LIVE_APPROVAL" : "ESCALATE")
                    .put("estimatedMaximumUsd", estimate.toPlainString())
                    .put("manifestBudgetUsd", manifest.provider().maxSpendUsd().toPlainString())
                    .put("model", manifest.provider().model()).put("schema", manifest.provider().schemaVersion())
                    .put("reasoningEffort", manifest.provider().reasoningEffort())
                    .put("attempts", manifest.provider().maxAttempts())
                    .put("pricingSource", manifest.provider().pricingSource().toString())
                    .put("pricingDate", manifest.provider().pricingEffectiveDate().toString())
                    .put("externalRequests", 0).put("reason", reason);
            if (!withinBudget) {
                writeReport(manifest, reportNode, false);
                System.out.println(mapper.writeValueAsString(reportNode));
                return 3;
            }
            if (!liveExecution) {
                writeReport(manifest, reportNode, false);
                System.out.println(mapper.writeValueAsString(reportNode));
                return 0;
            }
            FinepLiveExecutionReservation.Reservation reservation = new FinepLiveExecutionReservation(
                    Path.of("docs/engineering/verification/finep-live-validation")).reserve(manifest.runId());
            FinepLiveExecutionRunner.Result execution = new FinepLiveExecutionRunner(mapper, policy)
                    .execute(manifest, approval);
            writeReport(manifest, execution.report(), true);
            reservation.complete();
            System.out.println(mapper.writeValueAsString(execution.report()));
            return execution.exitCode();
        } catch (RuntimeException | IOException failure) {
            if (validatedManifest != null) {
                try {
                    var failureReport = mapper.createObjectNode().put("runId", validatedManifest.runId())
                            .put("status", liveExecutionRequested ? "ESCALATE" : "FAILED")
                            .put("reason", liveExecutionRequested ? "execution_or_reporting_failed" : "preflight_failed");
                    if (liveExecutionRequested) failureReport.put("externalRequestsUnknown", true);
                    else failureReport.put("externalRequests", 0);
                    writeReport(validatedManifest, failureReport, liveExecutionRequested);
                    System.out.println(mapper.writeValueAsString(failureReport));
                } catch (IOException | RuntimeException ignored) {
                    System.err.println("Finep live validation refused; sanitized report could not be written");
                }
            }
            System.err.println("Finep live validation refused; details omitted from output");
            return liveExecutionRequested ? 3 : 2;
        }
    }

    private void writeReport(FinepLiveValidationManifest manifest, JsonNode report, boolean live) throws IOException {
        if (!Path.of(manifest.reportFile()).normalize().equals(Path.of("docs/engineering/verification/finep-live-validation")
                .resolve(manifest.runId() + ".json"))) {
            throw new IllegalStateException("Sanitized report path is outside the approved directory");
        }
        Path reportPath = reportPath(manifest, live);
        java.nio.file.Files.createDirectories(reportPath.getParent());
        java.nio.file.Files.writeString(reportPath, mapper.writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE);
    }

    static Path reportPath(FinepLiveValidationManifest manifest, boolean live) {
        String phase = live ? "live" : "preflight";
        return Path.of("docs/engineering/verification/finep-live-validation")
                .resolve(manifest.runId() + "-" + phase + ".json");
    }

    private void validateProviderRuntime(FinepLiveValidationManifest manifest, Map<String, String> env) {
        try {
            var provider = manifest.provider();
            int configuredRetries = Integer.parseInt(env.getOrDefault("EXTRACTION_MAX_RETRIES", "0"));
            int configuredInput = Integer.parseInt(env.getOrDefault("EXTRACTION_MAX_INPUT_TOKENS", "64000"));
            int configuredOutput = Integer.parseInt(env.getOrDefault("EXTRACTION_MAX_OUTPUT_TOKENS", "12000"));
            int configuredTimeout = Integer.parseInt(env.getOrDefault("EXTRACTION_TIMEOUT_SECONDS", "60"));
            String configuredReasoning = env.getOrDefault("EXTRACTION_REASONING_EFFORT", "low");
            if (configuredRetries != provider.maxAttempts() - 1 || configuredInput != provider.maxInputTokens()
                    || configuredOutput != provider.maxOutputTokens() || configuredTimeout != provider.timeoutSeconds()
                    || !provider.reasoningEffort().equals(configuredReasoning)) {
                throw new IllegalStateException("Effective OpenAI limits do not match the manifest");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Effective OpenAI limits are invalid");
        }
    }

    private void validateRuntimeConfiguration(FinepLiveValidationManifest manifest, Map<String, String> env) {
        var expected = manifest.isolation();
        if (!expected.jdbcUrl().equals(env.get("DATABASE_URL"))
                || !expected.username().equals(env.get("DATABASE_USERNAME"))
                || !expected.storageEndpoint().toString().equals(env.get("S3_ENDPOINT"))
                || !expected.storageBucket().equals(env.get("S3_BUCKET"))
                || !"true".equalsIgnoreCase(env.get("S3_ENABLED"))
                || env.getOrDefault("S3_ACCESS_KEY", "").isBlank()
                || env.getOrDefault("S3_SECRET_KEY", "").isBlank()
                || !FinepLiveValidationPolicy.REFERENCE_URL.toString().equals(env.getOrDefault("FINEP_CANONICAL_URL",
                FinepLiveValidationPolicy.REFERENCE_URL.toString()))
                || env.getOrDefault("OPENAI_API_KEY", "").isBlank()) {
            throw new IllegalStateException("Effective validation targets do not match the manifest");
        }
        String actualDatabase = expected.jdbcUrl().substring(expected.jdbcUrl().lastIndexOf('/') + 1);
        if (!expected.database().equals(actualDatabase)) throw new IllegalStateException("Effective database is ambiguous");
    }

    private void verifyStorageBucketAbsent(FinepLiveValidationManifest manifest, Map<String, String> env) {
        var isolation = manifest.isolation();
        try (S3Client client = S3Client.builder().endpointOverride(isolation.storageEndpoint())
                .region(Region.of(env.getOrDefault("S3_REGION", "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        env.get("S3_ACCESS_KEY"), env.get("S3_SECRET_KEY"))))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(java.time.Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(3)).build())
                .forcePathStyle(true).build()) {
            storageBucketGuard.requireAbsent(client, isolation.storageBucket());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Validation storage bucket could not be isolated");
        }
    }

    private FinepLiveValidationIsolationGuard.RuntimeTargets runtime(FinepLiveValidationManifest manifest,
                                                                      Map<String, String> env) {
        return new FinepLiveValidationIsolationGuard.RuntimeTargets(env.get("DATABASE_URL"),
                manifest.isolation().database(), env.get("DATABASE_USERNAME"), URI.create(env.get("S3_ENDPOINT")),
                env.get("S3_BUCKET"));
    }

    private void verifyDatabaseIdentity(FinepLiveValidationManifest.Isolation isolation, String password) {
        if (password == null || password.isBlank()) throw new IllegalStateException("Database credentials are unavailable");
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("user", isolation.username());
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "5");
        try (var connection = java.sql.DriverManager.getConnection(isolation.jdbcUrl(), properties)) {
            connection.setReadOnly(true);
            connection.setNetworkTimeout(DB_TIMEOUT_EXECUTOR, 5_000);
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT current_database(), current_user")) {
                if (!result.next() || !isolation.database().equals(result.getString(1))
                        || !isolation.username().equals(result.getString(2))) {
                    throw new IllegalStateException("Connected database identity does not match the manifest");
                }
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Isolated database identity could not be verified");
        }
    }

    private FinepLiveValidationIsolationGuard.ComposeProject inspectCompose(FinepLiveValidationManifest manifest) {
        try {
            byte[] stdout = runDocker("docker", "compose", "--project-name", manifest.isolation().composeProject(),
                    "-f", "compose.yaml", "ps", "--format", "json");
            JsonNode nodes = parseComposePs(mapper, stdout);
            if (!nodes.isArray() || nodes.size() != 2) throw new IllegalStateException("Dedicated Compose project is not isolated");
            Map<String, FinepLiveValidationIsolationGuard.Service> services = new HashMap<>();
            for (JsonNode node : nodes) {
                String serviceName = node.path("Service").asText("");
                String containerId = node.path("ID").asText("");
                if (containerId.isBlank() || !Set.of("postgres", "object-storage").contains(serviceName)) {
                    throw new IllegalStateException("Dedicated Compose project identity is ambiguous");
                }
                services.put(serviceName, inspectContainer(containerId));
            }
            return new FinepLiveValidationIsolationGuard.ComposeProject(manifest.isolation().composeProject(), services);
        } catch (IOException exception) {
            throw new IllegalStateException("Dedicated Compose project could not be verified");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dedicated Compose project could not be verified");
        }
    }

    static JsonNode parseComposePs(ObjectMapper mapper, byte[] stdout) throws IOException {
        JsonNode parsed = mapper.readTree(stdout);
        if (parsed != null && parsed.isArray()) return parsed;
        var nodes = mapper.createArrayNode();
        String output = new String(stdout, StandardCharsets.UTF_8);
        for (String line : output.split("\\R")) {
            if (line.isBlank()) continue;
            JsonNode node = mapper.readTree(line);
            if (node == null || !node.isObject()) throw new IllegalStateException("Compose inspection output is invalid");
            nodes.add(node);
        }
        return nodes;
    }

    private FinepLiveValidationIsolationGuard.Service inspectContainer(String id) throws IOException, InterruptedException {
        byte[] stdout = runDocker("docker", "inspect", id);
        JsonNode root = mapper.readTree(stdout).path(0);
        String project = root.path("Config").path("Labels").path("com.docker.compose.project").asText("");
        String service = root.path("Config").path("Labels").path("com.docker.compose.service").asText("");
        if (project.isBlank() || service.isBlank()) throw new IllegalStateException("Compose labels are missing");
        Set<String> mounts = new HashSet<>();
        root.path("Mounts").forEach(mount -> mounts.add(mount.path("Name").asText("")));
        Set<Integer> ports = new HashSet<>();
        root.path("NetworkSettings").path("Ports").fields().forEachRemaining(entry -> {
            entry.getValue().forEach(binding -> {
                requireLoopbackHost(binding.path("HostIp").asText(""));
                try { ports.add(Integer.parseInt(binding.path("HostPort").asText())); }
                catch (NumberFormatException ignored) { }
            });
        });
        if (!project.startsWith("fomentiq-finep-live-") || !Set.of("postgres", "object-storage").contains(service)) {
            throw new IllegalStateException("Compose container belongs to an unapproved project");
        }
        return new FinepLiveValidationIsolationGuard.Service(project, service, mounts, ports);
    }

    static void requireLoopbackHost(String host) {
        if (!Set.of("127.0.0.1", "::1").contains(host)) {
            throw new IllegalStateException("Dedicated Compose port is not bound to loopback");
        }
    }

    private byte[] runDocker(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var capture = java.util.concurrent.CompletableFuture.supplyAsync(() -> capture(process.getInputStream()));
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            capture.cancel(true);
            throw new IllegalStateException("Docker inspection timed out");
        }
        ProcessCapture result;
        try {
            result = capture.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            process.destroyForcibly();
            capture.cancel(true);
            throw new IllegalStateException("Docker inspection output could not be bounded");
        }
        if (process.exitValue() != 0 || result.overflow()) {
            throw new IllegalStateException("Docker inspection failed or exceeded output limit");
        }
        return result.bytes();
    }

    private static ProcessCapture capture(java.io.InputStream input) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            boolean overflow = false;
            int count;
            while ((count = input.read(buffer)) != -1) {
                int retained = Math.min(count, Math.max(0, 256 * 1024 - total));
                if (retained > 0) output.write(buffer, 0, retained);
                total += count;
                if (total > 256 * 1024) overflow = true;
            }
            return new ProcessCapture(output.toByteArray(), overflow);
        } catch (IOException exception) {
            throw new IllegalStateException("Docker inspection output failed");
        }
    }

    private record ProcessCapture(byte[] bytes, boolean overflow) { }

    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) throw new IllegalArgumentException("Invalid private runner arguments");
            int separator = arg.indexOf('=');
            String key = arg.substring(0, separator);
            if (!Set.of("--fomentiq.finep.live-validation.enabled",
                    "--fomentiq.finep.live-validation.manifest",
                    "--fomentiq.finep.live-validation.approval-reference",
                    "--fomentiq.finep.live-validation.execute").contains(key)) {
                throw new IllegalArgumentException("Private runner refuses runtime configuration overrides");
            }
            if (values.putIfAbsent(key, arg.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Duplicate private runner argument");
            }
        }
        return values;
    }

    static void rejectRuntimeOverrides(String[] args, Map<String, String> environment,
                                       java.util.Properties systemProperties) {
        options(args);
        boolean environmentOverride = environment.keySet().stream()
                .anyMatch(key -> key.startsWith("SPRING_") || key.startsWith("FOMENTIQ_"));
        boolean propertyOverride = systemProperties.stringPropertyNames().stream()
                .anyMatch(key -> key.startsWith("spring.") || key.startsWith("fomentiq.")
                        || PLACEHOLDER_PROPERTY_NAMES.contains(key));
        if (environmentOverride || propertyOverride) {
            throw new IllegalArgumentException("Private runner refuses Spring runtime configuration overrides");
        }
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required private runner argument is missing");
        return value;
    }
}
