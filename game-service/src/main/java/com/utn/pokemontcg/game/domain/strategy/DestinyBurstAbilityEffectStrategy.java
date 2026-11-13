package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DestinyBurstAbilityEffectStrategy implements AbilityEffectStrategy {

    private static final int DAMAGE_COUNTERS = 5;

    @Override
    public String abilityName() {
        return "Destiny Burst";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        UUID defendingPlayer = context.player();

        if (game.activeHp().getOrDefault(defendingPlayer, 1) > 0) {
            throw new InvalidActionException(
                "Destiny Burst solo se activa automaticamente cuando este Pokemon queda Fuera de Combate por el dano de un ataque"
            );
        }

        UUID attackingPlayer = UUID.fromString(context.targetId());

        return executeCoinFlipAndDamage(game, defendingPlayer, attackingPlayer);
    }

    public TrainerEffectResult executeCoinFlipAndDamage(GameAggregate game, UUID defendingPlayer, UUID attackingPlayer) {
        boolean heads = Math.random() < 0.5;

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Destiny Burst");
        payload.put("coinResult", heads ? "HEADS" : "TAILS");
        payload.put("defendingPlayer", defendingPlayer.toString());
        payload.put("attackingPlayer", attackingPlayer.toString());

        if (heads) {
            game.addDamageCounters(attackingPlayer, "ACTIVE", DAMAGE_COUNTERS);
            payload.put("damageCountersPlaced", DAMAGE_COUNTERS);
            return TrainerEffectResult.of("DESTINY_BURST_ACTIVATED", payload);
        }

        return TrainerEffectResult.of("DESTINY_BURST_FLIP_TAILS", payload);
    }
}
