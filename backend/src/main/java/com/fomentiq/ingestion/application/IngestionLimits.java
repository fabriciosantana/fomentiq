package com.fomentiq.ingestion.application;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Immutable per-run acquisition bounds with a shared request counter. */
public record IngestionLimits(int maxDocuments, long maxBytesPerDocument, long maxAggregateDocumentBytes,
        long maxDiscoveryResponseBytes, long maxMetadataResponseBytes, long maxOAuthPageBytes,
        long maxTokenResponseBytes, int maxHttpRequests, int maxRedirects, Duration connectTimeout,
        Duration readTimeout, Duration operationTimeout, Instant deadline, RequestCounter requestCounter) {

    public IngestionLimits {
        if (maxDocuments < 1 || maxBytesPerDocument < 1 || maxAggregateDocumentBytes < maxBytesPerDocument
                || maxDiscoveryResponseBytes < 1 || maxMetadataResponseBytes < 1 || maxOAuthPageBytes < 1
                || maxTokenResponseBytes < 1 || maxHttpRequests < 1 || maxRedirects < 0
                || connectTimeout == null || readTimeout == null || operationTimeout == null || deadline == null
                || requestCounter == null || maxDocuments > 18 || maxBytesPerDocument > 10L * 1024 * 1024
                || maxAggregateDocumentBytes > 25L * 1024 * 1024
                || maxDiscoveryResponseBytes > 1024L * 1024 || maxMetadataResponseBytes > 2L * 1024 * 1024
                || maxOAuthPageBytes > 2L * 1024 * 1024 || maxTokenResponseBytes > 64L * 1024
                || maxHttpRequests > 150 || maxRedirects > 4 || connectTimeout.isNegative() || connectTimeout.isZero()
                || connectTimeout.compareTo(Duration.ofSeconds(10)) > 0 || readTimeout.isNegative() || readTimeout.isZero()
                || readTimeout.compareTo(Duration.ofSeconds(30)) > 0 || operationTimeout.isNegative()
                || operationTimeout.isZero() || operationTimeout.compareTo(Duration.ofSeconds(45)) > 0
                || !deadline.isAfter(Instant.now()) || deadline.isAfter(Instant.now().plusSeconds(901))) {
            throw new IllegalArgumentException("Invalid bounded ingestion limits");
        }
    }

    public void requireTimeRemaining() {
        if (!Instant.now().isBefore(deadline)) throw new IngestionLimitExceededException("Ingestion deadline exceeded");
    }

    public Duration remainingOperationTimeout() {
        requireTimeRemaining();
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.compareTo(operationTimeout) < 0 ? remaining : operationTimeout;
    }

    public void requireTimeWindow(Duration minimum) {
        requireTimeRemaining();
        if (Duration.between(Instant.now(), deadline).compareTo(minimum) < 0) {
            throw new IngestionLimitExceededException("Insufficient time remains for the next bounded operation");
        }
    }

    public int reserveHttpRequest() {
        int attempt = requestCounter.increment();
        if (attempt > maxHttpRequests) throw new IngestionLimitExceededException("HTTP request limit exceeded");
        return attempt;
    }

    public static final class RequestCounter {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<IngestionLimitExceededException> violation = new AtomicReference<>();
        public int increment() { return count.incrementAndGet(); }
        public int current() { return count.get(); }
        public void recordViolation(IngestionLimitExceededException failure) { violation.compareAndSet(null, failure); }
        public IngestionLimitExceededException violation() { return violation.get(); }
    }
}
