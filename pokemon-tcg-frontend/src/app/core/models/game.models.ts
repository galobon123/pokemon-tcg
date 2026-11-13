import type { CardAttackResponse } from './card.models';

export type GameState = 'WAITING' | 'SETUP' | 'COIN_FLIP' | 'ACTIVE' | 'WAITING_REPLACEMENT' | 'FINISHED';

export type SetupPhase = 'SETUP_WAITING_ACTIVE' | 'SETUP_WAITING_BENCH' | 'SETUP_REVEAL' | 'SETUP_COUNTDOWN';

export type TurnPhase = 'DRAW' | 'MAIN' | 'ATTACK' | 'BETWEEN_TURNS';

export type GameActionType =
  | 'DRAW'
  | 'SETUP_PLACE_ACTIVE'
  | 'SETUP_PLACE_BENCH'
  | 'SETUP_CONFIRM'
  | 'BENCH_POKEMON'
  | 'EVOLVE_POKEMON'
  | 'ATTACH_ENERGY'
  | 'PLAY_TRAINER'
  | 'PLAY_SUPPORTER'
  | 'EQUIP_TOOL'
  | 'USE_ABILITY'
  | 'RETREAT'
  | 'ATTACK'
  | 'END_TURN'
  | 'TAKE_PRIZE'
  | 'APPLY_SPECIAL_CONDITION'
  | 'RESOLVE_BETWEEN_TURNS';

export type StatusCondition = 'ASLEEP' | 'PARALYZED' | 'CONFUSED' | 'POISONED' | 'BURNED';

export type CoinResult = 'HEADS' | 'TAILS';

export type TrainerCategory = 'Item' | 'Tool' | 'Supporter' | 'Stadium';

export type WinReason = 'PRIZES' | 'KO_TOTAL' | 'DECK_OUT' | 'CONCEDE' | 'UNKNOWN' | 'INACTIVITY';

export type SlotReference = 'ACTIVE' | `BENCH_${number}`;

export type CardInstanceId = string;

export interface AttachedEnergy {
  cardId: CardInstanceId;
  energyType: string;
}

export type AttachedEnergiesBySlot = Record<string, AttachedEnergy[]>;
export type AttachedEnergiesByPlayer = Record<string, AttachedEnergiesBySlot>;

export interface TurnFlags {
  energyAttached: boolean;
  retreated: boolean;
  supporterPlayed: boolean;
  stadiumPlayed: boolean;
  attacked: boolean;
  abilitiesUsed: string[];
  evolvedSlots: string[];
}

export interface CreateGameRequest {
  playerId?: string;
  deckId: string;
}

export interface JoinGameRequest {
  playerId?: string;
  deckId: string;
}

export interface CoinFlipRequest {
  choice: CoinResult;
}

export interface ChooseFirstRequest {
  first: boolean;
}

export interface SetupGameRequest {
  playerOneDeckSize?: number;
  playerOneBasicCount?: number;
  playerTwoDeckSize?: number;
  playerTwoBasicCount?: number;
  playerOneDeckId?: string;
  playerTwoDeckId?: string;
  interactive?: boolean;
}

export interface SetupPlaceActiveRequest {
  cardInstanceId: string;
}

export interface SetupPlaceBenchRequest {
  cardInstanceIds: string[];
}

export interface SetupConfirmRequest {}

export interface GameActionRequest {
  actionType: GameActionType;
  condition?: StatusCondition;
  energyCardId?: string;
  targetSlot?: string;
  energyCardIds?: string[];
  attackIndex?: number;
  coinFlipResults?: boolean[];
  targetPlayerId?: string;
  selectedCardIds?: string[];
  selectedEnergyCardIds?: string[];
  selectedCondition?: string;
  acceptedOptionalEffect?: boolean;
  selectedOpponentAttackIndex?: number;
}

export interface BenchPokemonRequest {
  cardInstanceId: string;
  targetSlot?: string;
}

export interface EvolvePokemonRequest {
  fromCardInstanceId: string;
  toCardInstanceId: string;
  targetSlot: string;
}

export interface PlayTrainerRequest {
  cardInstanceId: string;
  targetId?: string;
  targetSlot?: string;
  targetPlayerId?: string;
  selectedCardIds?: string[];
  selectedEnergyCardIds?: string[];
  selectedCondition?: string;
  acceptedOptionalEffect?: boolean;
  coinFlipResults?: boolean[];
}

export interface UseAbilityRequest {
  slotReference: string;
  abilityIndex: number;
  targetId?: string;
}

export interface VisibleCardInfo {
  cardId: string;
  setId: string;
  name: string;
  supertype: string;
  subtypes: string[];
  hp: number;
  attackName: string | null;
  attackText: string | null;
  attackDamage: number | null;
  attackRequiredEnergy: number | null;
  energyType: string | null;
  attackCost: string[];
  retreatCost: number;
  evolvesFrom: string | null;
  abilities: string[];
  weaknessType: string | null;
  weaknessAmount: number | null;
  resistanceType: string | null;
  resistanceAmount: number | null;
  instanceId: string;
  attacks?: CardAttackResponse[];
}

export interface GameStateResponse {
  gameId: string;
  state: GameState;
  phase: string;
  setupPhase: SetupPhase | null;
  currentTurnPlayer: string | null;
  firstPlayer: string | null;
  winner: string | null;
  coinFlipWinner: string | null;
  coinResult: CoinResult | null;
  coinFlipChoice: string | null;
  playerOne: string | null;
  playerTwo: string | null;
  setupByPlayer: Record<string, PlayerSetupSummary>;
  activePokemonByPlayer: Record<string, string>;
  benchByPlayer: Record<string, string[]>;
  handSizesByPlayer: Record<string, number>;
  deckCardsRemainingByPlayer: Record<string, number>;
  prizeCardsRemainingByPlayer: Record<string, number>;
  discardPilesByPlayer: Record<string, string[]>;
  activeDamageCountersByPlayer: Record<string, number>;
  activeAttachedEnergyByPlayer: Record<string, number>;
  attachedEnergiesByPlayer: AttachedEnergiesByPlayer;
  turnFlags: TurnFlags;
  statusByPlayer: Record<string, StatusCondition[]>;
  damageCountersBySlot: Record<string, Record<string, number>>;
  toolsBySlot: Record<string, Record<string, string>>;
  activeStadium: string | null;
  activeStadiumOwner: string | null;
  activeStadiumName: string | null;
  suddenDeath: boolean;
  waitingReplacement: boolean;
  replacementPlayerId: string | null;
  pendingForcedSwitchPlayerId: string | null;
  playerNames: Record<string, string>;
  playerAvatars: Record<string, string>;
  activePokemonInfo: Record<string, VisibleCardInfo>;
  benchInfo: Record<string, (VisibleCardInfo | null)[]>;
  revealedHandCardsByPlayer: Record<string, VisibleCardInfo[]>;
  countdownEndsAt: string | null;
  firstTurn: boolean;
  turnsStartedByPlayer?: Record<string, number>;
}

export interface PlayerSetupSummary {
  mulligans: number;
  handSize: number;
  benchCount: number;
  prizeCount: number;
  hasActive: boolean;
}

export interface HandResponse {
  gameId: string;
  playerId: string;
  cardIds: string[];
  handSize: number;
}

export interface DeckPreviewResponse {
  cardIds: string[];
}

export interface DeckCardsResponse {
  gameId: string;
  playerId: string;
  cardIds: string[];
  cards: VisibleCardInfo[];
  deckSize: number;
}

export type AttackRequirementStepType =
  | 'SELECT_TARGET'
  | 'SELECT_DECK_CARDS'
  | 'VIEW_DECK_TOP'
  | 'ORDER_DECK_TOP'
  | 'SELECT_ATTACHED_ENERGY'
  | 'SELECT_CONDITION'
  | 'CONFIRM_OPTIONAL_EFFECT'
  | 'SELECT_HAND_CARD'
  | 'VIEW_HAND'
  | 'CONFIRM_HAND_EFFECT'
  | 'SELECT_OPPONENT_ATTACK';
export type AttackRequirementScope =
  | 'OWN_DECK'
  | 'OWN_DECK_TOP'
  | 'OPPONENT_DECK_TOP'
  | 'OPPONENT_HAND'
  | 'OWN_BENCH'
  | 'OWN_POKEMON'
  | 'OWN_ACTIVE'
  | 'OPPONENT_ACTIVE'
  | 'OPPONENT_BENCH'
  | 'OPPONENT_POKEMON'
  | 'ANY_POKEMON'
  | 'STATUS_CONDITION'
  | 'OPTIONAL_EFFECT';

export interface AttackRequirementStep {
  type: AttackRequirementStepType;
  scope: AttackRequirementScope | string;
  min: number;
  max: number;
  count: number;
  required: boolean;
  cards: VisibleCardInfo[];
  message?: string;
  options?: string[];
}

export interface AttackEffectPreview {
  type: string;
  label: string;
}

export interface AttackRequirementsResponse {
  gameId: string;
  playerId: string;
  attackIndex: number;
  attackName: string;
  attackText: string;
  steps: AttackRequirementStep[];
  effects: AttackEffectPreview[];
  coinFlipCount: number;
}

export interface MatchHistoryEntry {
  gameId: string;
  opponentName: string;
  opponentAvatar: string;
  won: boolean;
  reason: string;
  finishedAt: string;
}

export interface ActionLogEntry {
  id: number;
  gameId: string;
  action: GameActionType;
  playerId: string;
  result: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface GameSyncResponse {
  state: GameStateResponse;
  actionLog: ActionLogEntry[];
  syncedAt: string;
}
