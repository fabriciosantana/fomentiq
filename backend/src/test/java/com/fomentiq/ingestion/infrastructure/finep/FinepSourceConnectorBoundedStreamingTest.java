package com.fomentiq.ingestion.infrastructure.finep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.ingestion.application.IngestionLimitExceededException;
import com.fomentiq.ingestion.application.IngestionLimits;
import com.fomentiq.ingestion.application.SourceConnector.SourceDocument;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FinepSourceConnectorBoundedStreamingTest {
    @Test
    void controlledHttpServerProvesResponseOverflowIsRejectedWithoutTruncation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/document", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) { body.write(new byte[] {'%', 'P', 'D', 'F', '-', 'x', 'y'}); }
            catch (java.io.IOException ignored) { /* client correctly cancels after the byte ceiling */ }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            FinepUrlPolicy localTestPolicy = new FinepUrlPolicy(host -> new InetAddress[]{
                    InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34})},
                    Set.of("127.0.0.1"), true, true);
            URI canonical = URI.create("http://127.0.0.1:" + port + "/call/751092");
            FinepSourceConnector connector = new FinepSourceConnector(new ObjectMapper(), canonical, localTestPolicy,
                    HttpClient.newHttpClient(), timeout -> HttpClient.newBuilder().connectTimeout(timeout).build());
            var document = new SourceDocument("document-1", "test.pdf", "EDITAL",
                    URI.create("http://127.0.0.1:" + port + "/document"));

            assertThatThrownBy(() -> connector.download(document, 5, limits(5)))
                    .isInstanceOf(IngestionLimitExceededException.class)
                    .hasMessage("Finep response byte limit exceeded");
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void controlledHttpServerReturnsCompleteBodyWhenExactlyAtCeiling() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/document", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 5);
            exchange.getResponseBody().write(new byte[] {'%', 'P', 'D', 'F', '-'});
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            FinepUrlPolicy localTestPolicy = new FinepUrlPolicy(host -> new InetAddress[]{
                    InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34})},
                    Set.of("127.0.0.1"), true, true);
            URI canonical = URI.create("http://127.0.0.1:" + port + "/call/751092");
            FinepSourceConnector connector = new FinepSourceConnector(new ObjectMapper(), canonical, localTestPolicy,
                    HttpClient.newHttpClient(), timeout -> HttpClient.newBuilder().connectTimeout(timeout).build());
            var document = new SourceDocument("document-1", "test.pdf", "EDITAL",
                    URI.create("http://127.0.0.1:" + port + "/document"));
            assertThat(connector.download(document, 5, limits(5)).content()).containsExactly('%', 'P', 'D', 'F', '-');
        } finally {
            server.stop(0);
        }
    }

    @Test
    void controlledHttpServerIsCancelledWhenReadBecomesIdle() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/document", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(new byte[]{'%', 'P'});
                body.flush();
                Thread.sleep(150);
                body.write(new byte[]{'D', 'F', '-'});
            } catch (java.io.IOException ignored) {
                // The bounded client cancels the response after the idle timeout.
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            FinepUrlPolicy localTestPolicy = new FinepUrlPolicy(host -> new InetAddress[]{
                    InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34})},
                    Set.of("127.0.0.1"), true, true);
            URI canonical = URI.create("http://127.0.0.1:" + port + "/call/751092");
            FinepSourceConnector connector = new FinepSourceConnector(new ObjectMapper(), canonical, localTestPolicy,
                    HttpClient.newHttpClient(), timeout -> HttpClient.newBuilder().connectTimeout(timeout).build());
            var document = new SourceDocument("document-1", "test.pdf", "EDITAL",
                    URI.create("http://127.0.0.1:" + port + "/document"));

            IngestionLimits runLimits = limits(10, Duration.ofMillis(50));
            long startedAt = System.nanoTime();
            Throwable failure = catchThrowable(() -> connector.download(document, 10, runLimits));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            assertThat(failure).isInstanceOfAny(IllegalStateException.class, IngestionLimitExceededException.class);
            if (failure instanceof IngestionLimitExceededException) {
                assertThat(failure).hasMessage("Finep response read timeout exceeded");
            }
            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private static IngestionLimits limits(long maxBytes) {
        return limits(maxBytes, Duration.ofSeconds(2));
    }

    private static IngestionLimits limits(long maxBytes, Duration readTimeout) {
        return new IngestionLimits(1, maxBytes, maxBytes, 1024, 1024, 1024, 1024,
                10, 1, Duration.ofSeconds(1), readTimeout, Duration.ofSeconds(3),
                Instant.now().plusSeconds(10), new IngestionLimits.RequestCounter());
    }
}
