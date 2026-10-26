package com.fomentiq.ingestion.infrastructure.finep.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/** Atomically claims a run ID before any live request and keeps failed/uncertain runs fail-closed. */
final class FinepLiveExecutionReservation {
    private final Path reportDirectory;

    FinepLiveExecutionReservation(Path reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    Reservation reserve(String runId) {
        try {
            UUID.fromString(runId);
            Files.createDirectories(reportDirectory);
            Path lock = reportDirectory.resolve(runId + ".live.lock");
            Path report = reportDirectory.resolve(runId + "-live.json");
            Files.writeString(lock, "reservedAt=" + Instant.now() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (Files.exists(report)) {
                Files.deleteIfExists(lock);
                throw new IllegalStateException("This live validation run ID already has a report");
            }
            return new Reservation(lock);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Live validation run ID could not be reserved");
        }
    }

    static final class Reservation {
        private final Path lock;

        private Reservation(Path lock) { this.lock = lock; }

        /** Called only after a durable sanitized live report is written. */
        void complete() {
            try {
                Files.delete(lock);
            } catch (IOException failure) {
                throw new IllegalStateException("Live validation reservation could not be finalized");
            }
        }
    }
}
