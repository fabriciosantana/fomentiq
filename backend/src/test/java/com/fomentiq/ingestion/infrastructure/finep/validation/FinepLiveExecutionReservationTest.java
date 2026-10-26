package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinepLiveExecutionReservationTest {
    @TempDir Path directory;

    @Test
    void reservesRunAtomicallyAndRefusesConcurrentOrPreviouslyReportedExecution() throws Exception {
        String runId = "00000000-0000-4000-8000-000000000001";
        var reservation = new FinepLiveExecutionReservation(directory).reserve(runId);
        assertThatThrownBy(() -> new FinepLiveExecutionReservation(directory).reserve(runId))
                .isInstanceOf(IllegalStateException.class);
        reservation.complete();

        Files.writeString(directory.resolve(runId + "-live.json"), "{}");
        assertThatThrownBy(() -> new FinepLiveExecutionReservation(directory).reserve(runId))
                .isInstanceOf(IllegalStateException.class);
    }
}
