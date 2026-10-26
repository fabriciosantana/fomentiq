package com.fomentiq.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fomentiq.ingestion.application.IngestionLimitExceededException;
import com.fomentiq.ingestion.application.IngestionLimits;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestionLimitsTest {
    @Test
    void enforcesRequestCountAndRejectsExpiredOrOverlongDeadline() {
        var now = Instant.now();
        var limits = new IngestionLimits(18, 5L * 1024 * 1024, 12L * 1024 * 1024,
                256L * 1024, 256L * 1024, 2L * 1024 * 1024, 64L * 1024,
                1, 4, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(45),
                now.plusSeconds(900), new IngestionLimits.RequestCounter());
        assertThat(limits.reserveHttpRequest()).isEqualTo(1);
        assertThatThrownBy(limits::reserveHttpRequest).isInstanceOf(IngestionLimitExceededException.class);
        assertThatThrownBy(() -> new IngestionLimits(19, 5L * 1024 * 1024, 12L * 1024 * 1024,
                256L * 1024, 256L * 1024, 2L * 1024 * 1024, 64L * 1024,
                10, 4, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(45),
                now.plusSeconds(900), new IngestionLimits.RequestCounter()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWorkAfterTheGlobalDeadline() throws Exception {
        Instant now = Instant.now();
        var limits = new IngestionLimits(18, 5L * 1024 * 1024, 12L * 1024 * 1024,
                256L * 1024, 256L * 1024, 2L * 1024 * 1024, 64L * 1024,
                150, 4, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(45),
                now.plusMillis(100), new IngestionLimits.RequestCounter());
        Thread.sleep(150);
        assertThatThrownBy(limits::requireTimeRemaining).isInstanceOf(IngestionLimitExceededException.class)
                .hasMessage("Ingestion deadline exceeded");
    }
}
