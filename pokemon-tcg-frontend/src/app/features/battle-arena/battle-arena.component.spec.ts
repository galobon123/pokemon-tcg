import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';

import { BattleArenaComponent } from './battle-arena.component';
import { GameService } from '../../core/services/game.service';
import { GameStateService } from '../../core/services/game-state.service';
import { ToastService } from '../../core/services/toast.service';
import { WebSocketService, ChatMessage } from '../../core/services/websocket.service';
import { GameStateResponse } from '../../core/models/game.models';
import { GameEventEnvelope } from '../../core/models/websocket.models';

const MY_PLAYER_ID = 'p1';
const OPPONENT_ID = 'p2';

function baseState(overrides: Partial<GameStateResponse> = {}): GameStateResponse {
  return {
    gameId: 'game-1',
    state: 'ACTIVE',
    phase: 'MAIN',
    setupPhase: null,
    currentTurnPlayer: MY_PLAYER_ID,
    firstPlayer: MY_PLAYER_ID,
    winner: null,
    coinFlipWinner: null,
    coinResult: null,
    coinFlipChoice: null,
    playerOne: MY_PLAYER_ID,
    playerTwo: OPPONENT_ID,
    setupByPlayer: {},
    activePokemonByPlayer: {},
    benchByPlayer: {},
    handSizesByPlayer: {},
    deckCardsRemainingByPlayer: {},
    prizeCardsRemainingByPlayer: { [MY_PLAYER_ID]: 6, [OPPONENT_ID]: 6 },
    discardPilesByPlayer: {},
    activeDamageCountersByPlayer: {},
    activeAttachedEnergyByPlayer: {},
    attachedEnergiesByPlayer: {},
    turnFlags: {
      energyAttached: false,
      retreated: false,
      supporterPlayed: false,
      stadiumPlayed: false,
      attacked: false,
      abilitiesUsed: [],
      evolvedSlots: [],
    },
    statusByPlayer: {},
    damageCountersBySlot: {},
    toolsBySlot: {},
    activeStadium: null,
    activeStadiumOwner: null,
    suddenDeath: false,
    waitingReplacement: false,
    replacementPlayerId: null,
    pendingForcedSwitchPlayerId: null,
    playerNames: { [MY_PLAYER_ID]: 'Me', [OPPONENT_ID]: 'Rival' },
    playerAvatars: {},
    activePokemonInfo: {},
    benchInfo: {},
    revealedHandCardsByPlayer: {},
    countdownEndsAt: null,
    firstTurn: false,
    ...overrides,
  } as GameStateResponse;
}

describe('BattleArenaComponent (toast integration)', () => {
  let fixture: ComponentFixture<BattleArenaComponent>;
  let component: BattleArenaComponent;
  let gameStateService: GameStateService;
  let toastShow: ReturnType<typeof vi.fn>;
  let gameServiceMock: {
    getState: ReturnType<typeof vi.fn>;
    getLogs: ReturnType<typeof vi.fn>;
    getHand: ReturnType<typeof vi.fn>;
    executeAction: ReturnType<typeof vi.fn>;
    playTrainer: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    localStorage.setItem(
      'pokemon_auth',
      JSON.stringify({ playerId: MY_PLAYER_ID, username: 'Ash', token: 'jwt' }),
    );

    toastShow = vi.fn().mockReturnValue('t-1');
    const events$ = new Subject<GameEventEnvelope>();
    const chatEvents$ = new Subject<ChatMessage>();
    const webSocketService = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      send: vi.fn(),
      events$,
      chatEvents$,
    };
    gameServiceMock = {
      getState: vi.fn().mockReturnValue(of(baseState())),
      getLogs: vi.fn().mockReturnValue(of({ entries: [] })),
      getHand: vi.fn().mockReturnValue(of({ gameId: 'game-1', playerId: MY_PLAYER_ID, cardIds: [], handSize: 0 })),
      executeAction: vi.fn().mockReturnValue(of(baseState())),
      playTrainer: vi.fn().mockReturnValue(of(baseState())),
    };

    await TestBed.configureTestingModule({
      imports: [BattleArenaComponent],
      providers: [
        GameStateService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ gameId: 'game-1' }),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } as unknown as Router },
        {
          provide: GameService,
          useValue: gameServiceMock,
        },
        { provide: WebSocketService, useValue: webSocketService },
        { provide: ToastService, useValue: { show: toastShow } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BattleArenaComponent);
    component = fixture.componentInstance;
    gameStateService = TestBed.inject(GameStateService);

    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    component.ngOnDestroy();
    localStorage.clear();
  });

  it('shows an info toast when the player takes one prize', () => {
    gameStateService.setState(
      baseState({ prizeCardsRemainingByPlayer: { [MY_PLAYER_ID]: 5, [OPPONENT_ID]: 6 } }),
    );

    expect(toastShow).toHaveBeenCalledWith('+1 premio', 'info', 1200);
  });

  it('shows an info toast when the player takes two prizes (EX)', () => {
    gameStateService.setState(
      baseState({ prizeCardsRemainingByPlayer: { [MY_PLAYER_ID]: 4, [OPPONENT_ID]: 6 } }),
    );

    expect(toastShow).toHaveBeenCalledWith('+2 premios (EX)', 'info', 1200);
  });

  it('does not send client coin flip results when attacking', () => {
    component.gameId = 'game-1';
    component.player.active = {
      cardId: 'attacker',
      name: 'Attacker',
      image: '',
      instanceId: 'attacker-1',
      setId: 'xy1',
      quantity: 1,
      type: 'Pokemon',
      subtype: 'Basic',
      basicEnergy: false,
      basicPokemon: true,
      aceSpec: false,
      hp: 60,
      attackName: 'Quick Attack',
      attackText: 'Flip a coin. If heads, this attack does 20 more damage.',
      attackDamage: 20,
      attackRequiredEnergy: 0,
      attackCost: [],
      energyType: 'Lightning',
      retreatCost: 0,
      evolvesFrom: null,
      abilities: [],
      weaknessType: null,
      weaknessAmount: null,
      resistanceType: null,
      resistanceAmount: null,
      attacks: [{ name: 'Quick Attack', text: 'Flip a coin.', damage: '20+', cost: [], convertedEnergyCost: 0 }],
    };
    component.opponent.active = { ...component.player.active, cardId: 'defender', instanceId: 'defender-1', name: 'Defender' };
    (component as unknown as { allowedActions: { canAttack: boolean } }).allowedActions = { canAttack: true };

    component.performAttack(0, { targetSlot: 'ACTIVE' });

    expect(gameServiceMock.executeAction).toHaveBeenCalled();
    expect(gameServiceMock.executeAction.mock.calls[0][1]).not.toHaveProperty('coinFlipResults');
  });

  it('shows damage animation from ATTACK_RESOLVED payload', () => {
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'ATTACK_RESOLVED',
      payload: {
        attacker: MY_PLAYER_ID,
        defender: OPPONENT_ID,
        attackingPokemon: 'attacker-1',
        defendingPokemon: 'defender-1',
        damage: 40,
        baseDamage: 40,
        cancelled: false,
        weaknessMultiplier: 1,
        resistanceReduction: 0,
        auditTrail: [],
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.damageAnimationValue).toBe(40);
    expect(component.damageAnimationTarget).toBe('opp');
    expect(component.screenShake).toBe(true);
  });

  it('shows revealed cards from opponent attack search', () => {
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'ATTACK_RESOLVED',
      payload: {
        attacker: OPPONENT_ID,
        defender: MY_PLAYER_ID,
        attackingPokemon: 'attacker-1',
        defendingPokemon: 'defender-1',
        damage: 0,
        baseDamage: 0,
        cancelled: false,
        weaknessMultiplier: 1,
        resistanceReduction: 0,
        auditTrail: [],
        revealedCards: [{ cardId: 'xy1-1', setId: 'xy1', name: 'Lightning Energy', instanceId: 'xy1-1:1' }],
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.cardRevealNotification?.playerName).toBe('Rival');
    expect(component.cardRevealNotification?.cardNames).toEqual(['Lightning Energy']);
    expect(component.cardRevealNotification?.cardImages).toEqual(['https://images.pokemontcg.io/xy1/1.png']);
  });

  it('shows revealed cards from opponent trainer search', () => {
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'TRAINER_PLAYED',
      payload: {
        playerId: OPPONENT_ID,
        cardId: 'trainer-1',
        cardName: "Professor's Letter",
        effect: {
          cardsFound: 1,
          cardIds: ['xy1-2'],
          revealedCards: [{ cardId: 'xy1-2', setId: 'xy1', name: 'Fire Energy', instanceId: 'xy1-2:1' }],
        },
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.cardRevealNotification?.playerName).toBe('Rival');
    expect(component.cardRevealNotification?.cardNames).toEqual(['Fire Energy']);
    expect(component.cardRevealNotification?.cardImages).toEqual(['https://images.pokemontcg.io/xy1/2.png']);
  });

  it('shows warning toast when attack effect is skipped and attack dealt damage', () => {
    toastShow.mockClear();
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'ATTACK_RESOLVED',
      payload: {
        attacker: MY_PLAYER_ID,
        defender: OPPONENT_ID,
        attackingPokemon: 'attacker-1',
        defendingPokemon: 'defender-1',
        damage: 40,
        baseDamage: 40,
        cancelled: false,
        weaknessMultiplier: 1,
        resistanceReduction: 0,
        auditTrail: ['AttackEffect:tool_discard_none'],
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(toastShow).toHaveBeenCalledWith('El ataque se realizó pero solo hizo daño.', 'warning', 3000);
  });

  it('shows warning toast when attack effect is skipped and attack did no damage', () => {
    toastShow.mockClear();
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'ATTACK_RESOLVED',
      payload: {
        attacker: MY_PLAYER_ID,
        defender: OPPONENT_ID,
        attackingPokemon: 'attacker-1',
        defendingPokemon: 'defender-1',
        damage: 0,
        baseDamage: 0,
        cancelled: false,
        weaknessMultiplier: 1,
        resistanceReduction: 0,
        auditTrail: ['AttackEffect:bench_damage_no_target'],
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(toastShow).toHaveBeenCalledWith('No hay acción a realizar.', 'warning', 3000);
  });

  it('does not show skipped-effect toast for normal attacks', () => {
    toastShow.mockClear();
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'ATTACK_RESOLVED',
      payload: {
        attacker: MY_PLAYER_ID,
        defender: OPPONENT_ID,
        attackingPokemon: 'attacker-1',
        defendingPokemon: 'defender-1',
        damage: 60,
        baseDamage: 60,
        cancelled: false,
        weaknessMultiplier: 1,
        resistanceReduction: 0,
        auditTrail: ['AttackEffect:no_special_effect'],
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(toastShow).not.toHaveBeenCalled();
  });

  it('requires extra energy for typed plus colorless attack costs', () => {
    component.playerActiveEnergies = [{ cardId: 'fire-1', energyType: 'Fire' }];

    expect(component.canAffordAttack({ name: 'Flare', text: '', damage: '60', cost: ['Fire', 'Colorless'], convertedEnergyCost: 2 })).toBe(false);

    component.playerActiveEnergies = [
      { cardId: 'fire-1', energyType: 'Fire' },
      { cardId: 'water-1', energyType: 'Water' },
    ];

    expect(component.canAffordAttack({ name: 'Flare', text: '', damage: '60', cost: ['Fire', 'Colorless'], convertedEnergyCost: 2 })).toBe(true);
  });

  it('triggers trainer action animations from confirmPlayTrainer via action log', async () => {
    gameServiceMock.getLogs.mockReturnValue(
      of({
        entries: [{
          id: 1,
          gameId: 'game-1',
          action: 'PLAY_TRAINER',
          playerId: MY_PLAYER_ID,
          result: 'BASIC_ENERGY_SEARCHED',
          payload: {
            effectResult: 'BASIC_ENERGY_SEARCHED',
            effectPayload: { cardsFound: 2, cardIds: ['xy1-2', 'xy1-3'] },
          },
          createdAt: new Date().toISOString(),
        }],
      }),
    );

    component.gameId = 'game-1';
    component.currentPhase = 'MAIN';
    component.isMyTurn = true;
    const card = {
      cardId: 'prof-1',
      name: "Professor's Letter",
      image: '',
      instanceId: 'prof-1:1',
      setId: 'xy1',
      quantity: 1,
      type: 'Trainer',
      subtype: 'Item',
      basicEnergy: false,
      basicPokemon: false,
      aceSpec: false,
      hp: 0,
      attackName: null,
      attackText: null,
      attackDamage: null,
      attackRequiredEnergy: null,
      attackCost: [],
      energyType: null,
      retreatCost: 0,
      evolvesFrom: null,
      abilities: [],
      weaknessType: null,
      weaknessAmount: null,
      resistanceType: null,
      resistanceAmount: null,
    };
    component.selectedTrainerCard = card;
    component.showTrainerModal = true;

    component.confirmPlayTrainer();

    expect(gameServiceMock.playTrainer).toHaveBeenCalled();
    // After the synchronous next handler runs, getLogs should have been called
    expect(gameServiceMock.getLogs).toHaveBeenCalled();
    // The animations should be triggered since we returned a matching log entry
    expect(component.attackActionAnimations.length).toBeGreaterThan(0);
    expect(component.attackActionAnimations[0].label).toBe('Tu entrenador buscó 2 cartas');
  });

  it('triggers trainer action animations from TRAINER_PLAYED websocket event', () => {
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'TRAINER_PLAYED',
      payload: {
        playerId: OPPONENT_ID,
        cardId: 'shauna-1',
        cardInstanceId: 'shauna-1:1',
        cardName: 'Shauna',
        result: 'HAND_SHUFFLED_AND_DRAWN',
        effect: { cardsDrawn: 5 },
        phase: 'MAIN',
        currentTurnPlayer: OPPONENT_ID,
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.attackActionAnimations.length).toBeGreaterThan(0);
    expect(component.attackActionAnimations[0].label).toBe('El rival barajó su mano');
  });

  it('skips duplicate TRAINER_PLAYED animation when already played locally', () => {
    // Simulate local animation first by setting the dedup key
    (component as unknown as { lastAnimatedTrainerKey: string | null }).lastAnimatedTrainerKey = 'shauna-1:1:HAND_SHUFFLED_AND_DRAWN';
    component.attackActionAnimations = [
      { id: 99, type: 'discard', label: 'Tu entrenador barajó su mano', count: 1, copies: [0], targetRole: 'unknown' },
    ];

    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'TRAINER_PLAYED',
      payload: {
        playerId: MY_PLAYER_ID,
        cardId: 'shauna-1',
        cardInstanceId: 'shauna-1:1',
        cardName: 'Shauna',
        result: 'HAND_SHUFFLED_AND_DRAWN',
        effect: { cardsDrawn: 5 },
        phase: 'MAIN',
        currentTurnPlayer: MY_PLAYER_ID,
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    // Should keep the existing animations (no double animation)
    expect(component.attackActionAnimations).toHaveLength(1);
    expect((component as unknown as { lastAnimatedTrainerKey: string | null }).lastAnimatedTrainerKey).toBeNull();
  });

  it('clears trainer overlay on SUPPORTER_PLAYED when played by own player', () => {
    component.showTrainerModal = true;
    component.selectedTrainerCard = { cardId: 'supp-1', name: 'Supporter', image: '', instanceId: 'supp-1:1', setId: '', quantity: 1, type: 'Trainer', subtype: 'Supporter', basicEnergy: false, basicPokemon: false, aceSpec: false, hp: 0, attackName: null, attackText: null, attackDamage: null, attackRequiredEnergy: null, attackCost: [], energyType: null, retreatCost: 0, evolvesFrom: null, abilities: [], weaknessType: null, weaknessAmount: null, resistanceType: null, resistanceAmount: null };

    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'SUPPORTER_PLAYED',
      payload: {
        player: MY_PLAYER_ID,
        phase: 'MAIN',
        currentTurnPlayer: MY_PLAYER_ID,
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.showTrainerModal).toBe(false);
    expect(component.selectedTrainerCard).toBeNull();
  });

  it('triggers stadium animation from STADIUM_PLAYED websocket event', () => {
    (component as unknown as { handleWsEvent: (event: GameEventEnvelope) => void }).handleWsEvent({
      sequence: 1,
      schemaVersion: 1,
      gameId: 'game-1',
      type: 'STADIUM_PLAYED',
      payload: {
        playerId: OPPONENT_ID,
        cardId: 'stadium-1',
        cardInstanceId: 'stadium-1:1',
        cardName: 'Training Center',
        result: 'STADIUM_ACTIVE',
        effect: {},
        phase: 'MAIN',
        currentTurnPlayer: OPPONENT_ID,
      },
      occurredAt: new Date().toISOString(),
      receivedAt: new Date().toISOString(),
    });

    expect(component.attackActionAnimations.length).toBeGreaterThan(0);
    expect(component.attackActionAnimations[0].label).toBe('El rival jugó un Estadio');
  });
});
