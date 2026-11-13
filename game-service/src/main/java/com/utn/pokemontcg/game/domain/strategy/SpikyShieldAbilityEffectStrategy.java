package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SpikyShieldAbilityEffectStrategy implements AbilityEffectStrategy {

    private static final int DAMAGE_COUNTERS = 3;

    @Override
    public String abilityName() {
        return "Spiky Shield";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        throw new InvalidActionException(
            "Spiky Shield se activa automaticamente cuando este Pokemon recibe dano de un ataque"
        );
    }

    public TrainerEffectResult executeSpikyShield(GameAggregate game, UUID defendingPlayer, UUID attackingPlayer) {
        game.addDamageCounters(attackingPlayer, "ACTIVE", DAMAGE_COUNTERS);

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Spiky Shield");
        payload.put("defendingPlayer", defendingPlayer.toString());
        payload.put("attackingPlayer", attackingPlayer.toString());
        payload.put("damageCountersPlaced", DAMAGE_COUNTERS);

        return TrainerEffectResult.of("SPIKY_SHIELD_ACTIVATED", payload);
    }
}
