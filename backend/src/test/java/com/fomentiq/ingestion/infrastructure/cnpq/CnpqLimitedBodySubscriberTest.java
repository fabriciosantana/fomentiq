package com.fomentiq.ingestion.infrastructure.cnpq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class CnpqLimitedBodySubscriberTest {
    @Test
    void cancelsOversizedResponseInsteadOfAcceptingTruncatedBytes() {
        CnpqLimitedBodySubscriber subscriber = new CnpqLimitedBodySubscriber(4, Duration.ofSeconds(1));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5})));

        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);
        assertThat(subscription.cancelled).isTrue();
    }

    @Test
    void readTimeoutCancelsAStalledResponse() {
        CnpqLimitedBodySubscriber subscriber = new CnpqLimitedBodySubscriber(100, Duration.ofMillis(10));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);

        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);
        assertThat(subscription.cancelled).isTrue();
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private volatile boolean cancelled;
        @Override public void request(long amount) { }
        @Override public void cancel() { cancelled = true; }
    }
}
