import { StatusCondition, TurnPhase, WinReason, CoinResult } from './game.models';

export type GameEventType =
  | 'GAME_CREATED'
  | 'PLAYER_JOINED'
  | 'GAME_SETUP'
  | 'SETUP_ACTIVE_PLACED'
  | 'SETUP_BENCH_PLACED'
  | 'SETUP_CONFIRMED'
  | 'SETUP_COMPLETED'
  | 'COIN_FLIP'
  | 'FIRST_PLAYER_CHOSEN'
  | 'GAME_STARTED'
  | 'TURN_STARTED'
  | 'TURN_PHASE_CHANGED'
  | 'CARD_DRAWN'
  | 'POKEMON_BENCHED'
  | 'POKEMON_EVOLVED'
  | 'ENERGY_ATTACHED'
  | 'TRAINER_PLAYED'
  | 'SUPPORTER_PLAYED'
  | 'STADIUM_PLAYED'
  | 'TOOL_EQUIPPED'
  | 'ABILITY_USED'
  | 'RETREAT_DECLARED'
  | 'ATTACK_RESOLVED'
  | 'KO'
  | 'ACTIVE_REPLACED'
  | 'PRIZE_TAKEN'
  | 'STATUS_APPLIED'
  | 'BETWEEN_TURNS_RESOLVED'
  | 'GAME_FINISHED'
  | 'SUDDEN_DEATH_STARTED'
  | 'STATE_SYNCED';

export interface GameEventEnvelope<TPayload = Record<string, unknown>> {
  sequence: number;
  schemaVersion: number;
  gameId: string;
  type: GameEventType;
  payload: TPayload;
  occurredAt: string;
  receivedAt: string;
}

export interface GameCreatedPayload {
  playerOne: string;
}

export interface PlayerJoinedPayload {
  playerTwo: string;
}

export interface GameSetupPayload {
  gameId: string;
}

export interface SetupActivePlacedPayload {
  playerId: string;
  cardInstanceId: string;
}

export interface SetupBenchPlacedPayload {
  playerId: string;
  cardInstanceIds: string[];
}

export interface SetupConfirmedPayload {
  playerId: string;
}

export interface SetupCompletedPayload {
  gameId: string;
}

export interface CoinFlipPayload {
  coinResult: CoinResult;
  coinFlipWinner: string;
  playerOne: string;
  playerTwo: string;
}

export interface FirstPlayerChosenPayload {
  firstPlayer: string;
  secondPlayer: string;
}

export interface GameStartedPayload {
  firstPlayer: string;
  currentTurnPlayer: string;
  phase: TurnPhase;
}

export interface TurnStartedPayload {
  currentTurnPlayer: string;
  phase: TurnPhase;
  turnNumber: number;
}

export interface TurnPhaseChangedPayload {
  phase: TurnPhase;
  previousPhase: TurnPhase;
}

export interface CardDrawnPayload {
  player: string;
  handSize: number;
  deckRemaining: number;
}

export interface PokemonBenchedPayload {
  playerId: string;
  cardInstanceId: string;
  slot: string;
}

export interface PokemonEvolvedPayload {
  playerId: string;
  fromCardInstanceId: string;
  toCardInstanceId: string;
  slot: string;
}

export interface EnergyAttachedPayload {
  playerId: string;
  targetSlot: string;
  energyType: string;
  energyCardId: string;
  remainingHandSize: number;
}

export interface RevealedCardInfo {
  cardId: string;
  setId: string;
  name: string;
  instanceId?: string;
}

export interface TrainerEffectPayload {
  cardIds?: string[];
  revealedCards?: RevealedCardInfo[];
  [key: string]: unknown;
}

export interface TrainerPlayedPayload {
  playerId: string;
  cardId?: string;
  cardInstanceId?: string;
  cardName?: string;
  trainerType?: string;
  targetSlot?: string;
  targetId?: string;
  result?: string;
  effect?: TrainerEffectPayload;
}

export interface AbilityUsedPayload {
  playerId: string;
  slotReference: string;
  abilityIndex: number;
  abilityName: string;
}

export interface RetreatDeclaredPayload {
  playerId: string;
  fromSlot: string;
  toSlot: string;
  discardedEnergyCount: number;
}

export interface AttackResolvedPayload {
  attacker: string;
  defender: string;
  attackingPokemon: string;
  defendingPokemon: string;
  damage: number;
  baseDamage: number;
  cancelled: boolean;
  weaknessMultiplier: number;
  resistanceReduction: number;
  auditTrail: string[];
  coinFlipResults?: boolean[];
  revealedCards?: RevealedCardInfo[];
}

export interface KoPayload {
  attacker: string;
  defender: string;
  knockedOutCard: string;
  promotedCard: string | null;
  defenderHasNoPokemon: boolean;
}

export interface ActiveReplacedPayload {
  playerId: string;
  newActiveCardInstanceId: string;
  fromSlot: string;
}

export interface PrizeTakenPayload {
  player: string;
  prizesTaken: number;
  prizeCardsRemaining: number;
}

export interface StatusAppliedPayload {
  target: string;
  condition: StatusCondition;
}

export interface BetweenTurnsResolvedPayload {
  playerOneHp: number;
  playerTwoHp: number;
}

export interface GameFinishedPayload {
  winner: string;
  reason: WinReason;
}

export interface SuddenDeathStartedPayload {
  firstPlayer: string;
  currentTurnPlayer: string;
  prizeCardsPerPlayer: number;
}

export interface StateSyncedPayload {
  gameId: string;
  syncedAt: string;
}
