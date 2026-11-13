package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FairyGardenStadiumEffect implements StadiumEffectStrategy {

    public static final String EFFECT_KEY = "FAIRY_GARDEN";

    @Override
    public String cardName() {
        return "Fairy Garden";
    }

    @Override
    public void onPlayed(GameAggregate game, UUID playerId) {
        game.addActiveEffect(game.playerOne(), EFFECT_KEY, 1);
        game.addActiveEffect(game.playerTwo(), EFFECT_KEY, 1);
    }

    @Override
    public void onRemoved(GameAggregate game) {
        game.removeActiveEffect(game.playerOne(), EFFECT_KEY);
        game.removeActiveEffect(game.playerTwo(), EFFECT_KEY);
    }
}
