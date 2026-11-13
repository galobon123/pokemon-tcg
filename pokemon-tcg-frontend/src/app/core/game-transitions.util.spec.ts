import { describe, expect, it } from 'vitest';
import { computeAllowedActions } from './game-transitions.util';
import { GameStateResponse } from './models/game.models';

const MY_ID = 'p1';
const OPP_ID = 'p2';

function makeState(overrides: Partial<GameStateResponse> = {}): GameStateResponse {
  return {
    gameId: 'g1',
    state: 'ACTIVE',
    phase: 'MAIN',
    setupPhase: null,
    currentTurnPlayer: MY_ID,
    firstPlayer: OPP_ID,
    firstTurn: false,
    winner: null,
    coinFlipWinner: null,
    coinResult: null,
    coinFlipChoice: null,
    playerOne: MY_ID,
    playerTwo: OPP_ID,
    setupByPlayer: {},
    activePokemonByPlayer: { [MY_ID]: 'my-poke', [OPP_ID]: 'opp-poke' },
    benchByPlayer: {},
    handSizesByPlayer: {},
    deckCardsRemainingByPlayer: {},
    prizeCardsRemainingByPlayer: {},
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
    playerNames: { [MY_ID]: 'Me', [OPP_ID]: 'Rival' },
    playerAvatars: {},
    activePokemonInfo: {},
    benchInfo: {},
    revealedHandCardsByPlayer: {},
    countdownEndsAt: null,
    ...overrides,
  } as GameStateResponse;
}

describe('computeAllowedActions', () => {
  describe('MAIN phase — turno propio', () => {
    it('habilita attachEnergy si no adjuntó energía aún', () => {
      const actions = computeAllowedActions(makeState(), MY_ID);
      expect(actions.canAttachEnergy).toBe(true);
    });

    it('deshabilita attachEnergy si ya adjuntó energía en el turno', () => {
      const actions = computeAllowedActions(
        makeState({ turnFlags: { energyAttached: true, retreated: false, supporterPlayed: false, stadiumPlayed: false, attacked: false, abilitiesUsed: [], evolvedSlots: [] } }),
        MY_ID,
      );
      expect(actions.canAttachEnergy).toBe(false);
    });

    it('habilita playTrainer en MAIN', () => {
      expect(computeAllowedActions(makeState(), MY_ID).canPlayTrainer).toBe(true);
    });

    it('habilita retreat si no retiró y hay banca', () => {
      const actions = computeAllowedActions(makeState(), MY_ID, { hasBenchPokemon: true });
      expect(actions.canRetreat).toBe(true);
    });

    it('deshabilita retreat si ya retiró en el turno', () => {
      const actions = computeAllowedActions(
        makeState({ turnFlags: { energyAttached: false, retreated: true, supporterPlayed: false, stadiumPlayed: false, attacked: false, abilitiesUsed: [], evolvedSlots: [] } }),
        MY_ID,
        { hasBenchPokemon: true },
      );
      expect(actions.canRetreat).toBe(false);
    });

    it('deshabilita retreat si no hay banca', () => {
      const actions = computeAllowedActions(makeState(), MY_ID, { hasBenchPokemon: false });
      expect(actions.canRetreat).toBe(false);
    });

    it('habilita evolve si turnsStarted > 1', () => {
      expect(computeAllowedActions(makeState(), MY_ID, { turnsStarted: 2 }).canEvolve).toBe(true);
    });

    it('deshabilita evolve si turnsStarted = 1 (primer turno del jugador)', () => {
      expect(computeAllowedActions(makeState(), MY_ID, { turnsStarted: 1 }).canEvolve).toBe(false);
    });

    it('deshabilita attachEnergy en primer turno del jugador (firstTurn)', () => {
      const actions = computeAllowedActions(
        makeState({ firstTurn: true, firstPlayer: MY_ID }),
        MY_ID,
      );
      expect(actions.canAttachEnergy).toBe(false);
    });
  });

  describe('ATTACK phase', () => {
    it('habilita canAttack en fase ATTACK con activos en juego', () => {
      const actions = computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID);
      expect(actions.canAttack).toBe(true);
    });

    it('deshabilita canAttack si el activo está dormido', () => {
      const actions = computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID, { activeStatus: 'ASLEEP' });
      expect(actions.canAttack).toBe(false);
    });

    it('deshabilita canAttack si el activo está paralizado', () => {
      const actions = computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID, { activeStatus: 'PARALYZED' });
      expect(actions.canAttack).toBe(false);
    });

    it('permite canAttack si está envenenado (no bloquea ataques)', () => {
      const actions = computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID, { activeStatus: 'POISONED' });
      expect(actions.canAttack).toBe(true);
    });

    it('deshabilita canAttack en primer turno del primer jugador', () => {
      const actions = computeAllowedActions(
        makeState({ phase: 'ATTACK', firstTurn: true, firstPlayer: MY_ID }),
        MY_ID,
      );
      expect(actions.canAttack).toBe(false);
    });

    it('deshabilita playTrainer en fase ATTACK', () => {
      expect(computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID).canPlayTrainer).toBe(false);
    });
  });

  describe('DRAW phase', () => {
    it('habilita canDraw en fase DRAW', () => {
      expect(computeAllowedActions(makeState({ phase: 'DRAW' }), MY_ID).canDraw).toBe(true);
    });

    it('deshabilita canAttachEnergy en fase DRAW', () => {
      expect(computeAllowedActions(makeState({ phase: 'DRAW' }), MY_ID).canAttachEnergy).toBe(false);
    });
  });

  describe('turno del oponente', () => {
    it('deshabilita todas las acciones si no es tu turno', () => {
      const actions = computeAllowedActions(makeState({ currentTurnPlayer: OPP_ID }), MY_ID);
      expect(actions.canAttachEnergy).toBe(false);
      expect(actions.canAttack).toBe(false);
      expect(actions.canPlayTrainer).toBe(false);
      expect(actions.canRetreat).toBe(false);
      expect(actions.canDraw).toBe(false);
    });
  });

  describe('estado SETUP', () => {
    it('habilita canConfirmSetup en estado SETUP', () => {
      const actions = computeAllowedActions(makeState({ state: 'SETUP' }), MY_ID);
      expect(actions.canConfirmSetup).toBe(true);
    });

    it('deshabilita acciones de juego en SETUP', () => {
      const actions = computeAllowedActions(makeState({ state: 'SETUP' }), MY_ID);
      expect(actions.canAttachEnergy).toBe(false);
      expect(actions.canAttack).toBe(false);
    });
  });

  describe('estado FINISHED', () => {
    it('deshabilita todo en estado FINISHED', () => {
      const actions = computeAllowedActions(makeState({ state: 'FINISHED' }), MY_ID);
      expect(actions.canConfirmSetup).toBe(false);
      expect(actions.canDraw).toBe(false);
      expect(actions.canAttachEnergy).toBe(false);
      expect(actions.canAttack).toBe(false);
    });
  });

  describe('canPassTurn', () => {
    it('habilita canPassTurn en MAIN', () => {
      expect(computeAllowedActions(makeState({ phase: 'MAIN' }), MY_ID).canPassTurn).toBe(true);
    });

    it('habilita canPassTurn en ATTACK', () => {
      expect(computeAllowedActions(makeState({ phase: 'ATTACK' }), MY_ID).canPassTurn).toBe(true);
    });

    it('deshabilita canPassTurn en DRAW', () => {
      expect(computeAllowedActions(makeState({ phase: 'DRAW' }), MY_ID).canPassTurn).toBe(false);
    });
  });
});
