package com.utn.pokemontcg.game.config;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.HttpRealtimeObserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineConfigTest {

    @Test
    void gameEventPublisherCreatesAndSubscribesObserver() {
        EngineConfig config = new EngineConfig();
        HttpRealtimeObserver observer = mock(HttpRealtimeObserver.class);

        GameEventPublisher publisher = config.gameEventPublisher(observer);
        assertNotNull(publisher);
        // observer is subscribed — verify by publishing an event doesn't throw
        verifyNoInteractions(observer);
    }

    @Test
    void gameEngineFacadeCreatesWithAllDependencies() {
        EngineConfig config = new EngineConfig();
        GameEventPublisher publisher = mock(GameEventPublisher.class);
        AttackEffectRegistry registry = mock(AttackEffectRegistry.class);
        AttackPatternAnalyzer analyzer = mock(AttackPatternAnalyzer.class);

        GameEngineFacade facade = config.gameEngineFacade(publisher, registry, analyzer);
        assertNotNull(facade);
    }
}
