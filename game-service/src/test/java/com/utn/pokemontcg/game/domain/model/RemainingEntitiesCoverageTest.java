package com.utn.pokemontcg.game.domain.model;

import com.utn.pokemontcg.game.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch/line coverage push for:
 *  - GlobalExceptionHandler (95% instr / 50% branch — null default message branch)
 *  - PlayerSetupState        (96% instr / 50% branch — null handCardIds branch)
 *  - DeckEntity / DeckCardEntity / GameActionLogEntity constructors + setters
 *    (all 87-96% instr, just need explicit setter coverage).
 */
class RemainingEntitiesCoverageTest {

    // ====================================================================
    // GlobalExceptionHandler — handleValidation with null default message
    // ====================================================================

    @Test
    void globalExceptionHandlerValidationFallsBackWhenDefaultMessageIsNull() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "obj");
        // FieldError with explicit null default message
        bindingResult.addError(new FieldError("obj", "blankField", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);

        assertEquals(400, res.getStatusCode().value());
        List<Map<String, String>> errors = (List<Map<String, String>>) res.getBody().get("errors");
        assertEquals(1, errors.size());
        Map<String, String> error = errors.get(0);
        assertEquals("blankField", error.get("field"));
        assertEquals("Valor invalido", error.get("message"),
            "defaultMessage == null debe usar el fallback 'Valor invalido'");
    }

    @Test
    void globalExceptionHandlerValidationPreservesDefaultMessageWhenPresent() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "fieldName", "must not be null"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);

        assertEquals(400, res.getStatusCode().value());
        List<Map<String, String>> errors = (List<Map<String, String>>) res.getBody().get("errors");
        assertEquals(1, errors.size());
        assertEquals("must not be null", errors.get(0).get("message"));
    }

    @Test
    void globalExceptionHandlerAccessDeniedReturnsForbidden() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> res = handler.handleAccessDenied(
            new AccessDeniedException("denied"));
        assertEquals(403, res.getStatusCode().value());
        assertEquals("Acceso denegado", res.getBody().get("error"));
    }

    @Test
    void globalExceptionHandlerAuthenticationReturnsUnauthorized() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> res = handler.handleAuthentication(
            new AuthenticationException("no auth") {});
        assertEquals(401, res.getStatusCode().value());
        assertEquals("No autorizado", res.getBody().get("error"));
    }

    // ====================================================================
    // PlayerSetupState — null/non-null handCardIds
    // ====================================================================

    @Test
    void playerSetupStateNullHandCardIdsStaysNull() {
        PlayerSetupState state = new PlayerSetupState();
        state.setRevealedHandCardIds(null);
        assertNull(state.revealedHandCardIds(),
            "setRevealedHandCardIds(null) debe dejar el campo en null");
    }

    @Test
    void playerSetupStateNonNullHandCardIdsCopiesList() {
        PlayerSetupState state = new PlayerSetupState();
        List<String> ids = new java.util.ArrayList<>(List.of("c1", "c2"));
        state.setRevealedHandCardIds(ids);
        List<String> stored = state.revealedHandCardIds();
        assertNotNull(stored);
        assertEquals(List.of("c1", "c2"), stored);
        // mutating original must not affect stored list
        ids.add("c3");
        assertEquals(2, stored.size(),
            "setRevealedHandCardIds debe copiar la lista (defensive copy)");
    }

    @Test
    void playerSetupStateSettersAndGettersRoundTrip() {
        PlayerSetupState state = new PlayerSetupState();
        state.setMulligans(2);
        state.setHandSize(7);
        state.setBenchCount(5);
        state.setPrizeCount(6);
        state.setHasActive(true);

        assertEquals(2, state.mulligans());
        assertEquals(7, state.handSize());
        assertEquals(5, state.benchCount());
        assertEquals(6, state.prizeCount());
        assertTrue(state.hasActive());
    }

    // ====================================================================
    // DeckEntity — setters + getter for cards list
    // ====================================================================

    @Test
    void deckEntityGettersAndSettersRoundTrip() {
        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity entity =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity();
        UUID playerId = UUID.randomUUID();

        entity.setPlayerId(playerId);
        entity.setName("My Deck");

        assertEquals(playerId, entity.getPlayerId());
        assertEquals("My Deck", entity.getName());
        assertNotNull(entity.getCards(),
            "cards list debe inicializarse para que JPA pueda agregar");
        assertTrue(entity.getCards().isEmpty());
    }

    @Test
    void deckEntitySetCardsReplacesTheList() {
        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity entity =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity();
        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity card =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity();
        card.setCardId("xy1-1");
        card.setName("Pikachu");
        card.setQuantity(2);

        entity.setCards(List.of(card));

        assertEquals(1, entity.getCards().size());
        assertEquals("xy1-1", entity.getCards().get(0).getCardId());
    }

    // ====================================================================
    // DeckCardEntity — setters round-trip
    // ====================================================================

    @Test
    void deckCardEntityAllSettersRoundTrip() {
        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity card =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity();

        card.setCardId("xy1-25");
        card.setName("Charizard");
        card.setSetId("xy1");
        card.setQuantity(3);
        card.setType("Pokemon");
        card.setSubtype("Stage 2");
        card.setBasicEnergy(false);
        card.setBasicPokemon(false);
        card.setAceSpec(false);
        card.setHp(150);
        card.setAttackDamage(200);
        card.setAttackRequiredEnergy(3);
        card.setAttackName("Fire Spin");
        card.setAttackText("Discard 2 energy.");
        card.setEnergyType("Fire");
        card.setAttackCost("Fire,Fire,Fire");
        card.setRetreatCost(3);
        card.setEvolvesFrom("Charmeleon");
        card.setAbilities("[\"name\"]");
        card.setWeaknessType("Water");
        card.setWeaknessAmount(2);
        card.setResistanceType("Fighting");
        card.setResistanceAmount(-20);
        card.setAttacksJson("[]");

        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity deck =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity();
        card.setDeck(deck);

        assertEquals("xy1-25", card.getCardId());
        assertEquals("Charizard", card.getName());
        assertEquals("xy1", card.getSetId());
        assertEquals(3, card.getQuantity());
        assertEquals("Pokemon", card.getType());
        assertEquals("Stage 2", card.getSubtype());
        assertFalse(card.isBasicEnergy());
        assertFalse(card.isBasicPokemon());
        assertFalse(card.isAceSpec());
        assertEquals(150, card.getHp());
        assertEquals(200, card.getAttackDamage());
        assertEquals(3, card.getAttackRequiredEnergy());
        assertEquals("Fire Spin", card.getAttackName());
        assertEquals("Discard 2 energy.", card.getAttackText());
        assertEquals("Fire", card.getEnergyType());
        assertEquals("Fire,Fire,Fire", card.getAttackCost());
        assertEquals(3, card.getRetreatCost());
        assertEquals("Charmeleon", card.getEvolvesFrom());
        assertEquals("[\"name\"]", card.getAbilities());
        assertEquals("Water", card.getWeaknessType());
        assertEquals(2, card.getWeaknessAmount());
        assertEquals("Fighting", card.getResistanceType());
        assertEquals(-20, card.getResistanceAmount());
        assertEquals("[]", card.getAttacksJson());
        assertEquals(deck, card.getDeck());
    }

    @Test
    void deckCardEntityDefaultsToXy1SetId() {
        com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity card =
            new com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity();
        assertEquals("xy1", card.getSetId(),
            "El setId por defecto debe ser 'xy1'");
    }

    // ====================================================================
    // GameActionLogEntity — setters round-trip
    // ====================================================================

    @Test
    void gameActionLogEntityAllSettersRoundTrip() {
        com.utn.pokemontcg.game.infrastructure.persistence.GameActionLogEntity log =
            new com.utn.pokemontcg.game.infrastructure.persistence.GameActionLogEntity();
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        com.utn.pokemontcg.game.domain.model.GameActionType action =
            com.utn.pokemontcg.game.domain.model.GameActionType.DRAW;

        log.setGameId(gameId);
        log.setEntry("Player drew a card");
        log.setAction(action);
        log.setPlayerId(playerId);
        log.setResult("OK");
        log.setPayload("{\"cardId\":\"xy1-1\"}");
        log.setCreatedAt(now);

        assertEquals(gameId, log.getGameId());
        assertEquals("Player drew a card", log.getEntry());
        assertEquals(action, log.getAction());
        assertEquals(playerId, log.getPlayerId());
        assertEquals("OK", log.getResult());
        assertEquals("{\"cardId\":\"xy1-1\"}", log.getPayload());
        assertEquals(now, log.getCreatedAt());
    }
}
