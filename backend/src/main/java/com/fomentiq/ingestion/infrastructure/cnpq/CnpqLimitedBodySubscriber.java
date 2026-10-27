package com.fomentiq.ingestion.infrastructure.cnpq;

import com.fomentiq.ingestion.application.IngestionLimitExceededException;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Cancels an oversized or stalled public response without accepting a truncated artifact. */
final class CnpqLimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "cnpq-bounded-http-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final long maxBytes;
    private final Duration readTimeout;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final AtomicReference<ScheduledFuture<?>> timer = new AtomicReference<>();
    private Flow.Subscription subscription;
    private long received;

    CnpqLimitedBodySubscriber(long maxBytes, Duration readTimeout) {
        this.maxBytes = maxBytes;
        this.readTimeout = readTimeout;
    }

    @Override public CompletionStage<byte[]> getBody() { return body; }

    @Override public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        resetTimer();
        value.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            int length = buffer.remaining();
            if (received + length > maxBytes) {
                fail("CNPq response byte limit exceeded");
                return;
            }
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            output.writeBytes(chunk);
            received += length;
        }
        resetTimer();
        subscription.request(1);
    }

    @Override public void onError(Throwable failure) {
        cancelTimer();
        body.completeExceptionally(new IllegalStateException("CNPq response stream failed"));
    }

    @Override public void onComplete() {
        cancelTimer();
        body.complete(output.toByteArray());
    }

    private void resetTimer() {
        cancelTimer();
        timer.set(TIMER.schedule(() -> fail("CNPq response read timeout exceeded"),
                readTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    private void cancelTimer() {
        ScheduledFuture<?> current = timer.getAndSet(null);
        if (current != null) current.cancel(false);
    }

    private void fail(String message) {
        cancelTimer();
        if (subscription != null) subscription.cancel();
        body.completeExceptionally(new IngestionLimitExceededException(message));
    }
}
