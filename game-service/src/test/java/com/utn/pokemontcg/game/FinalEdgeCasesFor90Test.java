package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.strategy.*;
import com.utn.pokemontcg.game.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalEdgeCasesFor90Test {

    // --- User model ---
    @Test
    void user_getPlayerIdGeneratesIfNull() {
        User u = new User();
        assertNotNull(u.getPlayerId());
    }

    @Test
    void user_setPlayerIdHandlesNull() {
        User u = new User();
        u.setPlayerId(null);
        assertNotNull(u.getPlayerId());
    }

    @Test
    void user_getAuthorities() {
        User u = new User();
        assertEquals(1, u.getAuthorities().size());
    }

    @Test
    void user_getUsernameReturnsEmail() {
        User u = new User();
        u.setEmail("test@test.com");
        assertEquals("test@test.com", u.getUsername());
    }

    @Test
    void user_displayUsername() {
        User u = new User();
        u.setUsername("Player1");
        assertEquals("Player1", u.displayUsername());
    }

    @Test
    void user_securityMethods() {
        User u = new User();
        assertTrue(u.isAccountNonExpired());
        assertTrue(u.isAccountNonLocked());
        assertTrue(u.isCredentialsNonExpired());
        assertTrue(u.isEnabled());
    }

    @Test
    void user_getPassword() {
        User u = new User();
        u.setPassword("pass");
        assertEquals("pass", u.getPassword());
    }

    @Test
    void user_setAvatarNullUsesDefault() {
        User u = new User();
        u.setAvatar(null);
        assertTrue(u.getAvatar().contains("pokemon"));
    }

    @Test
    void user_setRoleNullUsesDefault() {
        User u = new User();
        u.setRole(null);
        assertEquals("ROLE_USER", u.getRole());
    }

    // --- GameCard uncovered ---
    @Test
    void gameCard_isBasicWithNullSubtypes() {
        GameCard c = new GameCard("c1", "Charmander", "Pokemon", null, 60, 10, 1);
        assertEquals(Set.of(), c.subtypes());
    }

    @Test
    void gameCard_nullAttackCost() {
        GameCard c = new GameCard("c1", "Charmander", "Pokemon", Set.of("Basic"), 60, 10, 1,
            null, null, "Fire", null, 1, null, null);
        assertTrue(c.attackCost().isEmpty());
    }

    // --- TurnFlags Edge ---
    @Test
    void turnFlags_evolvedSlotsEmptyInitially() {
        TurnFlags f = new TurnFlags();
        assertTrue(f.evolvedSlots().isEmpty());
    }

    @Test
    void turnFlags_abilitiesUsedEmptyInitially() {
        TurnFlags f = new TurnFlags();
        assertTrue(f.abilitiesUsed().isEmpty());
    }

    // --- GameAggregate Edge ---
    @Test
    void gameAggregate_cardAtSlotInvalidBench() {
        GameAggregate g = game();
        assertNull(g.cardAtSlot(g.playerOne(), "BENCH_99"));
    }

    @Test
    void gameAggregate_placeSetupBenchInitializesBench() {
        GameAggregate g = game();
        g.placeSetupBench(g.playerOne(), List.of("b1"));
        assertEquals("b1", g.cardAtSlot(g.playerOne(), "BENCH_0"));
    }

    @Test
    void gameAggregate_benchPokemonFillsNullSlots() {
        GameAggregate g = game();
        g.benchPokemon(g.playerOne(), "b1", null);
        assertEquals("b1", g.cardAtSlot(g.playerOne(), "BENCH_0"));
    }

    @Test
    void gameAggregate_normalizeSlotReturnsActiveForNull() {
        GameAggregate g = game();
        assertEquals("ACTIVE", g.normalizeSlot(null));
    }

    @Test
    void gameAggregate_benchIndexInvalid() {
        GameAggregate g = game();
        assertEquals(-1, g.benchIndex("invalid"));
        assertEquals(-1, g.benchIndex("BENCH_X"));
    }

    @Test
    void gameAggregate_clearStadium() {
        GameAggregate g = game();
        g.putStadium(g.playerOne(), "stadium-1");
        assertEquals("stadium-1", g.clearStadium());
        assertNull(g.clearStadium());
    }

    @Test
    void gameAggregate_removeCardAtSlotActive() {
        GameAggregate g = game();
        assertNotNull(g.removeCardAtSlot(g.playerOne(), "ACTIVE"));
        assertNull(g.activePokemon().get(g.playerOne()));
    }

    @Test
    void gameAggregate_promoteBenchToActiveInvalidIndex() {
        GameAggregate g = game();
        assertNull(g.promoteBenchToActive(g.playerOne(), 99));
    }

    @Test
    void gameAggregate_confirmSetup() {
        GameAggregate g = game();
        UUID pid = g.playerOne();
        g.confirmSetup(pid);
        assertTrue(g.setupConfirmed(pid));
        assertFalse(g.setupConfirmed(UUID.randomUUID()));
    }

    @Test
    void gameAggregate_removeCardAtSlotBench() {
        GameAggregate g = game();
        g.placeSetupBench(g.playerOne(), List.of("b1"));
        assertNotNull(g.removeCardAtSlot(g.playerOne(), "BENCH_0"));
        assertNull(g.cardAtSlot(g.playerOne(), "BENCH_0"));
    }

    @Test
    void gameAggregate_setPlayerOneTwo() {
        GameAggregate g = new GameAggregate();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        g.setPlayerOne(p1);
        g.setPlayerTwo(p2);
        assertEquals(p1, g.playerOne());
        assertEquals(p2, g.playerTwo());
    }

    @Test
    void gameAggregate_swapActiveWithHandThrowsWhenNotInHand() {
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.initializeZonesFor(g.playerOne(), List.of());
        assertThrows(IllegalArgumentException.class,
            () -> g.swapActiveWithHand(g.playerOne(), "nonexistent"));
    }

    @Test
    void gameAggregate_swapActiveWithHandThrowsWrongPokemon() {
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.initializeZonesFor(g.playerOne(), List.of());
        g.hand().get(g.playerOne()).add("c1");
        g.registerCard(new GameCard("c1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(IllegalArgumentException.class,
            () -> g.swapActiveWithHand(g.playerOne(), "c1"));
    }

    @Test
    void energyDiscard_lightningMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Lightning"));
        AttackContext c = ctx(g, "Bolt", "Discard a [L] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_metallMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Metal"));
        AttackContext c = ctx(g, "Steel", "Discard a [M] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void abilityEffectRegistry_constructor() {
        List<AbilityEffectStrategy> list = List.of(
            new MysticalFireAbilityEffectStrategy(),
            new FurCoatAbilityEffectStrategy()
        );
        var registry = new AbilityEffectRegistry(list);
        assertTrue(registry.find("Mystical Fire").isPresent());
        assertTrue(registry.find("Fur Coat").isPresent());
        assertTrue(registry.find("Unknown").isEmpty());
    }

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = new GameAggregate(p1);
        g.setPlayerTwo(p2);
        g.setCurrentTurnPlayer(p1);
        g.setFirstPlayer(p1);
        g.initializeZonesFor(p1, List.of());
        g.initializeZonesFor(p2, List.of());
        g.activePokemon().put(p1, "a1");
        g.activePokemon().put(p2, "a2");
        g.registerCard(new GameCard("a1", "Att", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("a2", "Def", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return g;
    }
}
