package br.com.brasildecide;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class TseClientTest {
    @Test void conditionalRequestsAnd304ReuseValidatedBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> conditional = new AtomicReference<>();
        server.createContext("/result", exchange -> {
            calls.incrementAndGet();
            conditional.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            if (conditional.get() != null) exchange.sendResponseHeaders(304, -1);
            else {
                byte[] body = "{\"valid\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("ETag", "\"v1\"");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            var client = new TseClient(WebClient.create(), new ObjectMapper(), Duration.ofSeconds(2));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/result");
            var first = client.fetch(uri, j -> assertTrue(j.path("valid").asBoolean()));
            assertEquals(first, client.fetch(uri, j -> assertTrue(j.path("valid").asBoolean())));
            assertEquals("\"v1\"", conditional.get());
            assertEquals(2, calls.get());
        } finally { server.stop(0); }
    }
    @Test void doesNotRetry404OrCacheRejectedBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger missingCalls = new AtomicInteger();
        AtomicReference<String> conditional = new AtomicReference<>();
        server.createContext("/missing", exchange -> { missingCalls.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.createContext("/invalid", exchange -> {
            conditional.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            byte[] body = "{}".getBytes(); exchange.getResponseHeaders().set("ETag", "\"invalid\"");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new TseClient(WebClient.create(), new ObjectMapper(), Duration.ofSeconds(2));
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThrows(WebClientResponseException.class, () -> client.fetch(URI.create(base + "/missing"), j -> {}));
            assertEquals(1, missingCalls.get());
            for (int i = 0; i < 2; i++) assertThrows(IllegalArgumentException.class,
                    () -> client.fetch(URI.create(base + "/invalid"), j -> { throw new IllegalArgumentException("Rejected"); }));
            assertNull(conditional.get());
        } finally { server.stop(0); }
    }
}
