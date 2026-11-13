package com.utn.pokemontcg.card.infrastructure;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.utn.pokemontcg.card.application.CardProviderException;

@Component
public class PokemonTcgClientImpl implements PokemonTcgClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public PokemonTcgClientImpl(@Value("${pokemontcg.base-url}") @NonNull String baseUrl,
                                @Value("${pokemontcg.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(CONNECT_TIMEOUT.toMillis()));
        factory.setReadTimeout(Math.toIntExact(READ_TIMEOUT.toMillis()));

        RestClient.Builder builder = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-Api-Key", apiKey);
        }
        this.restClient = builder.build();
    }

    @Override
    public Map<String, Object> search(String query, int pageSize, int page) {
        try {
            return Objects.requireNonNull(restClient.get()
                .uri(uri -> uri.path("/cards")
                    .queryParam("q", query)
                    .queryParam("pageSize", pageSize)
                    .queryParam("page", page)
                    .build())
                .retrieve()
                .body(MAP_TYPE));
        } catch (RestClientException ex) {
            throw new CardProviderException("No se pudo consultar pokemontcg.io", ex);
        }
    }

    @Override
    public Map<String, Object> search(String query, int pageSize) {
        return search(query, pageSize, 1);
    }

    @Override
    public Map<String, Object> getById(String id) {
        try {
            return Objects.requireNonNull(restClient.get()
                .uri("/cards/{id}", id)
                .retrieve()
                .body(MAP_TYPE));
        } catch (RestClientException ex) {
            throw new CardProviderException("No se pudo consultar pokemontcg.io", ex);
        }
    }

    @Override
    public Map<String, Object> getSet(String id) {
        try {
            return Objects.requireNonNull(restClient.get()
                .uri("/sets/{id}", id)
                .retrieve()
                .body(MAP_TYPE));
        } catch (RestClientException ex) {
            throw new CardProviderException("No se pudo consultar el set en pokemontcg.io", ex);
        }
    }
}
