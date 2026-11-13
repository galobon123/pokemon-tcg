package com.utn.pokemontcg.realtime.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GameAccessValidatorTest {

    private static final String UNREACHABLE_URL = "http://localhost:1";

    @Test
    void shouldReturnTrueWhenPlayerOneMatches() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            when(respSpec.body(Map.class)).thenReturn(Map.of(
                "playerOne", playerId.toString(),
                "playerTwo", otherId.toString()
            ));

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            assertTrue(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldReturnTrueWhenPlayerTwoMatches() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            when(respSpec.body(Map.class)).thenReturn(Map.of(
                "playerOne", otherId.toString(),
                "playerTwo", playerId.toString()
            ));

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            assertTrue(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldReturnFalseWhenStateIsNull() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            when(respSpec.body(Map.class)).thenReturn(null);

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            assertFalse(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldRejectWhenGameIdIsNull() {
        GameAccessValidator validator = new GameAccessValidator(UNREACHABLE_URL);
        assertFalse(validator.canAccess(null, UUID.randomUUID(), "some-token"));
    }

    @Test
    void shouldRejectWhenPlayerIdIsNull() {
        GameAccessValidator validator = new GameAccessValidator(UNREACHABLE_URL);
        assertFalse(validator.canAccess(UUID.randomUUID(), null, "some-token"));
    }

    @Test
    void shouldRejectWhenTokenIsNull() {
        GameAccessValidator validator = new GameAccessValidator(UNREACHABLE_URL);
        assertFalse(validator.canAccess(UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void shouldRejectWhenTokenIsBlank() {
        GameAccessValidator validator = new GameAccessValidator(UNREACHABLE_URL);
        assertFalse(validator.canAccess(UUID.randomUUID(), UUID.randomUUID(), "   "));
    }

    @Test
    void shouldReturnTrueWhenPlayerOneIsUuidObject() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            // playerOne is a UUID object, not String
            when(respSpec.body(Map.class)).thenReturn(Map.of(
                "playerOne", (Object) playerId,
                "playerTwo", (Object) otherId.toString()
            ));

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            assertTrue(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldReturnFalseWhenPlayerOneIsInvalidUuidString() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            // playerOne is an invalid UUID string → uuidFrom catches and returns null
            when(respSpec.body(Map.class)).thenReturn(Map.of(
                "playerOne", "not-a-uuid",
                "playerTwo", otherId.toString()
            ));

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            // Falls through to playerTwo check: otherId != playerId → false
            assertFalse(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldReturnFalseWhenPlayerOneIsInteger() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        try (var mockedBuilder = mockStatic(RestClient.class)) {
            RestClient.Builder builderMock = mock(RestClient.Builder.class);
            RestClient mockClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec reqSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

            mockedBuilder.when(RestClient::builder).thenReturn(builderMock);
            when(builderMock.baseUrl(anyString())).thenReturn(builderMock);
            when(builderMock.build()).thenReturn(mockClient);
            when(mockClient.get()).thenReturn(reqSpec);
            when(reqSpec.uri(anyString(), any(Object[].class))).thenReturn(reqSpec);
            when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
            when(reqSpec.retrieve()).thenReturn(respSpec);
            // Integer is neither UUID nor String → uuidFrom returns null → no match
            when(respSpec.body(Map.class)).thenReturn(Map.of(
                "playerOne", 12345,
                "playerTwo", otherId.toString()
            ));

            GameAccessValidator validator = new GameAccessValidator("http://game-service:8081");
            assertFalse(validator.canAccess(gameId, playerId, "some-token"));
        }
    }

    @Test
    void shouldReturnFalseWhenGameServiceIsUnreachable() {
        GameAccessValidator validator = new GameAccessValidator(UNREACHABLE_URL);
        assertFalse(validator.canAccess(UUID.randomUUID(), UUID.randomUUID(), "valid-looking-token"));
    }
}
