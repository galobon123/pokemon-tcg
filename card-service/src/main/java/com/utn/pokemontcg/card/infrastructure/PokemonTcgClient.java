package com.utn.pokemontcg.card.infrastructure;

import java.util.Map;

public interface PokemonTcgClient {
    Map<String, Object> search(String query, int pageSize, int page);
    Map<String, Object> search(String query, int pageSize);
    Map<String, Object> getById(String id);
    Map<String, Object> getSet(String id);
}
