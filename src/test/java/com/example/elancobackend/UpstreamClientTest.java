package com.example.elancobackend;

import com.example.elancobackend.client.UpstreamClient;
import com.example.elancobackend.client.UpstreamSighting;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Uses a tiny real HTTP server instead of mocks so the real timeouts, status handling
// and the real @Retryable proxy are all exercised. Retry settings come from application-test.yml
// (max-retries 2, so at most 3 calls).
@SpringBootTest
@ActiveProfiles("test")
class UpstreamClientTest {

    private static final String ONE_SIGHTING = """
            [{"id":"a1","date":"2025-08-14T17:30:00","location":"London",
              "species":"Marsh tick","latinName":"Ixodes apronophorus"}]""";

    private static final HttpServer server = startServer();
    private static final AtomicInteger calls = new AtomicInteger();
    // Decides the status code for the Nth call (1-based). Body is only sent for 200.
    private static volatile java.util.function.IntUnaryOperator statusForCall = n -> 200;
    private static volatile long delayMillis = 0;

    @Autowired
    UpstreamClient client;

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("upstream.base-url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        delayMillis = 0;
        statusForCall = n -> 200;
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void parsesSightingsOnSuccess() {
        List<UpstreamSighting> result = client.fetchAllSightings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).location()).isEqualTo("London");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesServerErrorsThenSucceeds() {
        statusForCall = n -> n < 3 ? 500 : 200;

        assertThat(client.fetchAllSightings()).hasSize(1);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxRetries() {
        statusForCall = n -> 500;

        assertThatThrownBy(() -> client.fetchAllSightings()).isInstanceOf(HttpServerErrorException.class);
        assertThat(calls.get()).isEqualTo(3); // 1 first attempt + 2 retries
    }

    @Test
    void doesNotRetryClientErrors() {
        statusForCall = n -> 429;

        assertThatThrownBy(() -> client.fetchAllSightings()).isInstanceOf(HttpClientErrorException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesOnReadTimeout() {
        delayMillis = 1500; // longer than the 1s read timeout in application-test.yml

        assertThatThrownBy(() -> client.fetchAllSightings()).isInstanceOf(ResourceAccessException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

    private static HttpServer startServer() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/tick-sightings", exchange -> {
                int status = statusForCall.applyAsInt(calls.incrementAndGet());
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = status == 200 ? ONE_SIGHTING.getBytes(StandardCharsets.UTF_8) : new byte[0];
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                // Length -1 means "no body"; sending a body for a failed call isn't needed.
                exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            // Several threads, otherwise a slow (sleeping) request would block the next retry.
            s.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
