package com.utn.pokemontcg.game.infrastructure.client;

import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.CardData;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Branch coverage push for CardServiceClient.
 * Targets the 4/16 missed branches in the findById path:
 *  - the response == null branch
 *  - the RestClientException catch arm
 *
 * The {@code restClient} field is private final with no setter, so we use
 * reflection to swap in a mocked RestClient. The RestClient fluent API has
 * self-referential generic bounds that make {@code when().thenReturn()} awkward
 * to mock — we use {@code doReturn()} instead, which sidesteps the type system.
 */
class CardServiceClientBranchCoverageTest {

    @Test
    void findByIdReturnsEmptyWhenBodyIsNull() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri("/api/cards/{id}", "xy1-1");
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(null).when(responseSpec).body(Map.class);

        CardServiceClient client = newWithRestClient(restClient);

        Optional<CardData> result = client.findById("xy1-1");

        assertTrue(result.isEmpty(), "null body debe producir Optional.empty()");
    }

    @Test
    void findByIdReturnsEmptyOnRestClientException() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri("/api/cards/{id}", "broken-id");
        doReturn(responseSpec).when(uriSpec).retrieve();
        doThrow(new RestClientException("connection refused"))
            .when(responseSpec).body(Map.class);

        CardServiceClient client = newWithRestClient(restClient);

        Optional<CardData> result = client.findById("broken-id");

        assertTrue(result.isEmpty(),
            "RestClientException debe manejarse y devolver Optional.empty()");
    }

    @Test
    void findByIdReturnsParsedCardOnSuccess() throws Exception {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        Map<String, Object> body = Map.of(
            "id", "xy1-1",
            "name", "Pikachu",
            "supertype", "Pokemon"
        );
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri("/api/cards/{id}", "xy1-1");
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(body).when(responseSpec).body(Map.class);

        CardServiceClient client = newWithRestClient(restClient);

        Optional<CardData> result = client.findById("xy1-1");

        assertTrue(result.isPresent());
        assertEquals("Pikachu", result.orElseThrow().name());
    }

    private static CardServiceClient newWithRestClient(RestClient restClient) throws Exception {
        CardServiceClient client = new CardServiceClient("http://unused");
        Field field = CardServiceClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
        return client;
    }
}
