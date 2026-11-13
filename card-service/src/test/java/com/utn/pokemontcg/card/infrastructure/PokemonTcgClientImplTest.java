package com.utn.pokemontcg.card.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.utn.pokemontcg.card.application.CardProviderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonTcgClientImplTest {

    private HttpServer server;
    private int port;
    private PokemonTcgClientImpl clientWithKey;
    private PokemonTcgClientImpl clientWithoutKey;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
        String baseUrl = "http://127.0.0.1:" + port;
        clientWithKey = new PokemonTcgClientImpl(baseUrl, "secret-key-123");
        clientWithoutKey = new PokemonTcgClientImpl(baseUrl, "");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchReturnsParsedMapOnSuccess() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        server.createContext("/cards", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedQuery.set(exchange.getRequestURI().getQuery());
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            respondJson(exchange, 200, "{\"data\":[{\"id\":\"xy1-1\"}],\"count\":1}");
        });

        Map<String, Object> result = clientWithKey.search("pikachu", 20, 1);

        assertEquals(1, result.get("count"));
        assertNotNull(result.get("data"));
        assertEquals("/cards", capturedPath.get());
        assertTrue(capturedQuery.get().contains("q=pikachu"), "query should include q parameter");
        assertTrue(capturedQuery.get().contains("pageSize=20"), "query should include pageSize");
        assertTrue(capturedQuery.get().contains("page=1"), "query should include page");
        assertEquals("secret-key-123", capturedApiKey.get(), "X-Api-Key header must be sent when configured");
    }

    @Test
    void searchWithoutApiKeyOmitsHeader() {
        server.createContext("/cards", exchange -> {
            String apiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
            assertEquals(null, apiKey, "No X-Api-Key header should be sent when api key is blank");
            respondJson(exchange, 200, "{\"data\":[]}");
        });

        Map<String, Object> result = clientWithoutKey.search("charizard", 10, 2);

        assertNotNull(result);
        assertTrue(result.containsKey("data"));
    }

    @Test
    void searchWithoutApiKeyStillSucceedsWhenKeyIsNull() {
        PokemonTcgClientImpl client = new PokemonTcgClientImpl("http://127.0.0.1:" + port, null);
        server.createContext("/cards", exchange -> {
            String apiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
            assertEquals(null, apiKey, "No X-Api-Key header should be sent when api key is null");
            respondJson(exchange, 200, "{\"data\":[]}");
        });

        Map<String, Object> result = client.search("mewtwo", 5, 1);

        assertNotNull(result);
    }

    @Test
    void searchWithPageSizeOverloadDelegatesToFullSearch() {
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/cards", exchange -> {
            requestCount.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            assertTrue(query.contains("page=1"), "overload must default to page=1");
            respondJson(exchange, 200, "{\"data\":[]}");
        });

        Map<String, Object> result = clientWithKey.search("pikachu", 50);

        assertNotNull(result);
        assertEquals(1, requestCount.get());
    }

    @Test
    void getByIdReturnsParsedMapOnSuccess() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        server.createContext("/cards", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            respondJson(exchange, 200, "{\"data\":{\"id\":\"xy1-1\",\"name\":\"Pikachu\"}}");
        });

        Map<String, Object> result = clientWithKey.getById("xy1-1");

        assertNotNull(result);
        assertEquals("/cards/xy1-1", capturedPath.get());
        assertNotNull(result.get("data"));
    }

    @Test
    void getSetReturnsParsedMapOnSuccess() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        server.createContext("/sets", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            respondJson(exchange, 200, "{\"data\":{\"id\":\"xy1\",\"name\":\"XY\"}}");
        });

        Map<String, Object> result = clientWithKey.getSet("xy1");

        assertNotNull(result);
        assertEquals("/sets/xy1", capturedPath.get());
    }

    @Test
    void searchWrapsRestClientExceptionIntoCardProviderException() {
        server.createContext("/cards", exchange -> respondJson(exchange, 500, "{\"error\":\"server down\"}"));

        CardProviderException ex = assertThrows(CardProviderException.class,
            () -> clientWithKey.search("pikachu", 20, 1));

        assertTrue(ex.getMessage().contains("pokemontcg.io"));
        assertNotNull(ex.getCause(), "original cause must be preserved");
    }

    @Test
    void getByIdWrapsRestClientExceptionIntoCardProviderException() {
        server.createContext("/cards", exchange -> respondJson(exchange, 404, "{\"error\":\"not found\"}"));

        CardProviderException ex = assertThrows(CardProviderException.class,
            () -> clientWithKey.getById("missing-id"));

        assertTrue(ex.getMessage().contains("pokemontcg.io"));
        assertNotNull(ex.getCause());
    }

    @Test
    void getSetWrapsRestClientExceptionIntoCardProviderException() {
        server.createContext("/sets", exchange -> respondJson(exchange, 500, "{\"error\":\"upstream down\"}"));

        CardProviderException ex = assertThrows(CardProviderException.class,
            () -> clientWithKey.getSet("xy1"));

        assertTrue(ex.getMessage().contains("set"));
        assertTrue(ex.getMessage().contains("pokemontcg.io"));
        assertNotNull(ex.getCause());
    }

    @Test
    void searchWrapsConnectionFailureIntoCardProviderException() {
        PokemonTcgClientImpl deadClient = new PokemonTcgClientImpl(
            "http://127.0.0.1:1", "key"
        );

        CardProviderException ex = assertThrows(CardProviderException.class,
            () -> deadClient.search("pikachu", 1, 1));

        assertNotNull(ex.getCause());
        assertInstanceOf(ResourceAccessException.class, ex.getCause(),
            "connection failures should surface as ResourceAccessException");
    }

    private void respondJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
