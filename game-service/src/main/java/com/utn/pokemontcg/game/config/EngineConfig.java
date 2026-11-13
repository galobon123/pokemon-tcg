package com.utn.pokemontcg.game.config;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.HttpRealtimeObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public GameEventPublisher gameEventPublisher(HttpRealtimeObserver observer) {
        GameEventPublisher publisher = new GameEventPublisher();
        publisher.subscribe(observer);
        return publisher;
    }

    @Bean
    public GameEngineFacade gameEngineFacade(
        GameEventPublisher publisher,
        AttackEffectRegistry attackEffectRegistry,
        AttackPatternAnalyzer attackPatternAnalyzer
    ) {
        return new GameEngineFacade(publisher, attackEffectRegistry, attackPatternAnalyzer);
    }
}
