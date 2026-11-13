import { GameStateResponse, StatusCondition } from './models/game.models';

export interface GameActions {
  canConfirmSetup: boolean;
  canDraw: boolean;
  canAttachEnergy: boolean;
  canBenchInMain: boolean;
  canEvolve: boolean;
  canPlayTrainer: boolean;
  canUseAbility: boolean;
  canRetreat: boolean;
  canAttack: boolean;
  canPassTurn: boolean;
}

export function computeAllowedActions(
  state: GameStateResponse,
  playerId: string | null,
  overrides?: {
    hasEmptyBench?: boolean;
    hasBenchPokemon?: boolean;
    activeStatus?: StatusCondition | null;
    turnsStarted?: number;
  },
): GameActions {
  const playerIsTurn = !!playerId && state.currentTurnPlayer === playerId;
  const inGame = state.state !== 'WAITING' && state.state !== 'FINISHED';
  const notSetup = state.state !== 'SETUP';
  const notWaiting = state.state !== 'WAITING';
  const isMain = state.phase === 'MAIN';
  const isAttack = state.phase === 'ATTACK';
  const isDraw = state.phase === 'DRAW';
  const isMainOrAttack = state.phase === 'MAIN' || state.phase === 'ATTACK';
  const isFirstTurn =
    state.firstTurn === true && state.firstPlayer === playerId;
  const energyAttached = state.turnFlags?.energyAttached === true;
  const retreated = state.turnFlags?.retreated === true;
  const activeStatus = overrides?.activeStatus ?? null;
  const hasEmptyBenchSlot = overrides?.hasEmptyBench ?? true;
  const hasBench = overrides?.hasBenchPokemon ?? true;
  const turnsStarted = overrides?.turnsStarted ?? 2;

  const hasActiveTarget =
    !!state.activePokemonByPlayer?.[playerId ?? ''] &&
    !!Object.keys(state.activePokemonByPlayer ?? {}).some(
      (k) => k !== playerId,
    );

  const base =
    inGame && playerIsTurn && notSetup && notWaiting;

  return {
    canConfirmSetup:
      state.state === 'SETUP',

    canDraw: base && isDraw,

    canAttachEnergy: base && isMain && !isFirstTurn && !energyAttached,

    canBenchInMain: base && isMain && hasEmptyBenchSlot && !isFirstTurn,

    canEvolve: base && isMain && !isFirstTurn && turnsStarted > 1,

    canPlayTrainer: base && isMain,

    canUseAbility: base && isMain,

    canRetreat:
      base && isMain && !retreated && hasBench,

    canAttack:
      base &&
      isAttack &&
      !isFirstTurn &&
      hasActiveTarget &&
      activeStatus !== 'ASLEEP' &&
      activeStatus !== 'PARALYZED',

    canPassTurn: base && isMainOrAttack,
  };
}
