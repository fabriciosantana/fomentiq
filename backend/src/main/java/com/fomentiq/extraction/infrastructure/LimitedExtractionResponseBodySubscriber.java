package com.fomentiq.extraction.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Cancels OpenAI response streaming on byte or inactivity limits instead of buffering without bounds. */
final class LimitedExtractionResponseBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "openai-bounded-response-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final long maxBytes;
    private final Duration readTimeout;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private Flow.Subscription subscription;
    private ScheduledFuture<?> timer;
    private long received;

    LimitedExtractionResponseBodySubscriber(long maxBytes, Duration readTimeout) {
        this.maxBytes = maxBytes;
        this.readTimeout = readTimeout;
    }

    @Override public CompletionStage<byte[]> getBody() { return body; }

    @Override public synchronized void onSubscribe(Flow.Subscription value) {
        subscription = value;
        resetTimer();
        value.request(1);
    }

    @Override public synchronized void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            int size = buffer.remaining();
            if (received + size > maxBytes) {
                fail("OpenAI response exceeded configured size limit");
                return;
            }
            byte[] bytes = new byte[size];
            buffer.get(bytes);
            output.writeBytes(bytes);
            received += size;
        }
        resetTimer();
        subscription.request(1);
    }

    @Override public synchronized void onError(Throwable ignored) {
        cancelTimer();
        body.completeExceptionally(new IOException("OpenAI response stream failed"));
    }

    @Override public synchronized void onComplete() {
        cancelTimer();
        body.complete(output.toByteArray());
    }

    private synchronized void resetTimer() {
        cancelTimer();
        timer = TIMER.schedule(() -> fail("OpenAI response read timeout exceeded"),
                readTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelTimer() {
        if (timer != null) timer.cancel(false);
        timer = null;
    }

    private synchronized void fail(String message) {
        cancelTimer();
        if (subscription != null) subscription.cancel();
        body.completeExceptionally(new IOException(message));
    }
}
