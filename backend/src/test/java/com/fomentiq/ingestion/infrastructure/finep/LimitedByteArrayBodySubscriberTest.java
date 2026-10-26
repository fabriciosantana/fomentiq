package com.fomentiq.ingestion.infrastructure.finep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LimitedByteArrayBodySubscriberTest {
    @Test
    void cancelsStreamInsteadOfAcceptingTruncatedBody() {
        var subscriber = new LimitedByteArrayBodySubscriber(3, Duration.ofSeconds(2));
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { }
            @Override public void cancel() { cancelled.set(true); }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4})));

        assertThat(cancelled).isTrue();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasMessageContaining("Finep response byte limit exceeded");
    }

    @Test
    void returnsTheEntireBodyWhenWithinLimit() throws Exception {
        var subscriber = new LimitedByteArrayBodySubscriber(4, Duration.ofSeconds(2));
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { }
            @Override public void cancel() { }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2}), ByteBuffer.wrap(new byte[] {3, 4})));
        subscriber.onComplete();
        assertThat(subscriber.getBody().toCompletableFuture().get()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void cancelsAnIdleReadWhenItsTimeoutExpires() {
        var subscriber = new LimitedByteArrayBodySubscriber(4, Duration.ofMillis(50));
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { }
            @Override public void cancel() { cancelled.set(true); }
        });

        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasMessageContaining("Finep response read timeout exceeded");
        assertThat(cancelled).isTrue();
    }
}
