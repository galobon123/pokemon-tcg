import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CdkDragDrop,
  DragDropModule,
  CdkDrag,
  CdkDropList,
} from '@angular/cdk/drag-drop';
import { Subscription } from 'rxjs';
import { DeckStorageService } from '../../core/services/deck-storage.service';
import { DeckResponse, DeckCardInput } from '../../core/models/deck.models';
import { DeckViewOverlayComponent } from '../shared/deck-view-overlay.component';
import { ConfirmationModalComponent } from '../shared/confirmation-modal.component';
import { ModalService } from '../../core/services/modal.service';
import { CardDetailOverlayComponent } from '../deck-builder/card-catalog/card-detail-overlay/card-detail-overlay.component';
import { CardSummaryResponse, CardAttackResponse, CardAbilityResponse } from '../../core/models/card.models';
import { GameService } from '../../core/services/game.service';
import { GameStateService } from '../../core/services/game-state.service';
import { WebSocketService, ChatMessage } from '../../core/services/websocket.service';
import { AuthService } from '../../core/services/auth.service';
import { DeckService } from '../../core/services/deck.service';
import { CardService } from '../../core/services/card.service';
import { ToastService } from '../../core/services/toast.service';
import { GameStateResponse, TurnPhase, SetupPhase, StatusCondition, AttachedEnergy, ActionLogEntry, GameActionType, VisibleCardInfo, CoinResult, TrainerCategory, WinReason, GameActionRequest, PlayTrainerRequest, AttackRequirementsResponse, AttackRequirementStep } from '../../core/models/game.models';
import { GroupedEnergy, groupEnergiesByType } from '../../shared/components/energy-type-icon/energy-grouping.util';
import { computeAllowedActions, GameActions } from '../../core/game-transitions.util';
import { GameEventEnvelope, AttackResolvedPayload, RevealedCardInfo, TrainerPlayedPayload, TrainerEffectPayload } from '../../core/models/websocket.models';
import { resolveWinReasonMessage } from './win-reason-message';

type PreviewCardSummary = CardSummaryResponse & { statusCondition?: StatusCondition | null };

interface HandCard extends DeckCardInput {
  image: string;
  instanceId: string;
  attacks?: CardAttackResponse[];
}

type PreviewCard = { name: string; image: string; instanceId?: string; cardId?: string; setId?: string | null };

type ActionItemType = 'attack' | 'ability' | 'retreat';

// Destinos posibles al sacar una carta en el Sandbox.
type SandboxPickTarget = 'HAND' | 'MY_ACTIVE' | 'MY_BENCH' | 'OPP_ACTIVE' | 'OPP_BENCH';

interface ActionItem {
  type: ActionItemType;
  name: string;
  index: number;
  disabled: boolean;
  disabledReason?: string;
  attack?: CardAttackResponse;
  ability?: CardAbilityResponse;
  retreatCost?: number;
  availableEnergies?: number;
}

type AttackActionAnimationType = 'draw' | 'search' | 'discard' | 'energy-discard' | 'energy-attach' | 'energy-move' | 'switch';

interface AttackActionAnimation {
  id: number;
  type: AttackActionAnimationType;
  label: string;
  count: number;
  copies: number[];
  targetRole: 'attacker' | 'defender' | 'unknown';
}

interface PlayerData {
  name: string;
  playerId: string | null;
  avatar: string;
  hand: HandCard[];
  active: HandCard | null;
  bench: (HandCard | null)[];
  deckCount: number;
  deckName: string;
  prizes: (HandCard | null)[];
  discard: HandCard[];
}

@Component({
  selector: 'app-battle-arena',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    DeckViewOverlayComponent,
    ConfirmationModalComponent,
    CardDetailOverlayComponent,
  ],
  templateUrl: './battle-arena.component.html',
  styleUrl: './battle-arena.component.scss',
})
export class BattleArenaComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private deckStorage = inject(DeckStorageService);
  private modalService = inject(ModalService);
  private cdr = inject(ChangeDetectorRef);
  private gameService = inject(GameService);
  private gameStateService = inject(GameStateService);
  private webSocketService = inject(WebSocketService);
  private authService = inject(AuthService);
  private deckService = inject(DeckService);
  private cardService = inject(CardService);
  private toastService = inject(ToastService);

  deck: DeckResponse | null = null;
  deckId: string | null = null;
  deckLoadError: string | null = null;
  showDeckView = false;
  previewedCard: PreviewCard | null = null;
  previewedCardSummary: PreviewCardSummary | null = null;
  previewedCardEnergies: AttachedEnergy[] = [];
  previewedCardStatus: StatusCondition | null = null;
  gameId: string | null = null;
  // Modo Sala de Práctica (Sandbox) local: el tablero se maneja en memoria,
  // sin comunicarse con el servidor (no hay gameId).
  sandboxMode = false;
  // Cartas únicas del mazo que el usuario puede sacar a la mano a voluntad.
  sandboxCardPool: HandCard[] = [];
  showSandboxPicker = false;
  // Origen del selector: "deck" = tu mazo, "catalog" = todas las cartas (sin mazo).
  sandboxPickSource: 'deck' | 'catalog' = 'deck';
  sandboxCatalogCards: HandCard[] = [];
  sandboxCatalogLoading = false;
  sandboxCatalogLoaded = false;
  sandboxCatalogError: string | null = null;
  sandboxCatalogFilter = '';
  // Filtro: mostrar solo cartas que tengan al menos una habilidad.
  sandboxOnlyWithAbility = false;
  // Destino de la carta seleccionada (tu tablero o el del rival).
  sandboxPickTarget: SandboxPickTarget = 'HAND';
  private sandboxInstanceSeq = 0;
  currentPhase: TurnPhase | SetupPhase | null = null;
  isMyTurn = false;
  turnNumber = 1;
  private turnStartCount = 0;
  endingTurn = false;
  drawing = false;
  actionError: string | null = null;
  waitingForOpponent = false;
  isSetupMode = false;
  setupPhase: SetupPhase | null = null;
  setupConfirmed = false;
  setupCountdownSeconds = 0;
  private countdownInterval: ReturnType<typeof setInterval> | null = null;
  settingUp = false;
  deckShuffleAnimating = false;
  initialDrawAnimating = false;
  drawAnimating = false;
  drawCardBackOnly = false;
  drawnCardPreview: HandCard | null = null;
  slidingToHand = false;
  drawEnterIndex = -1;
  private isDrawingCard = false;
  prizeAnimating = false;
  prizeAnimatingCount = 0;
  prizeSliding = false;
  private prizeAnimTimer: ReturnType<typeof setTimeout> | null = null;
  private prizeAnimTimer2: ReturnType<typeof setTimeout> | null = null;
  // Ability hand-discard animation (Water Shuriken & similar)
  handDiscardAnimating = false;
  handDiscardCard: HandCard | null = null;
  setupError: string | null = null;
  showMulliganOverlay = false;
  mulliganAlreadyShown = false;
  myMulligans = 0;
  opponentMulligans = 0;
  extraCardsForMe = 0;
  extraCardsForOpponent = 0;
  mulliganPhase: 'cards' | 'shuffle' | 'draw' | 'extra' | 'done' = 'cards';
  mulliganCardsRevealed = false;
  opponentRevealedHand: VisibleCardInfo[] = [];
  myRevealedHand: VisibleCardInfo[] = [];
  private mulliganAnimTimer: ReturnType<typeof setTimeout> | null = null;
  chatMessages: ChatMessage[] = [];
  chatInput = '';
  private lastGameState: GameStateResponse | null = null;
  private gameStateSub: Subscription | null = null;
  private toolsBySlot: Record<string, Record<string, string>> = {};
  private wsSub: Subscription | null = null;
  private chatSub: Subscription | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private setupPollTimer: ReturnType<typeof setInterval> | null = null;
  private coinFlipPollTimer: ReturnType<typeof setInterval> | null = null;
  private stateVersion = 0;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  attachingEnergy = false;
  benchingPokemon = false;
  attacking = false;
  evolving = false;
  damageAnimationValue: number | null = null;
  damageAnimationTarget: 'me' | 'opp' | null = null;
  private damageAnimationTimer: ReturnType<typeof setTimeout> | null = null;
  attackAnimating = false;
  screenShake = false;
  screenFlash = false;
  private attackAnimTimer: ReturnType<typeof setTimeout> | null = null;
  private shakeTimer: ReturnType<typeof setTimeout> | null = null;
  private flashTimer: ReturnType<typeof setTimeout> | null = null;
  actionLog: ActionLogEntry[] = [];
  attackResult: AttackResolvedPayload | null = null;
  attackActionAnimations: AttackActionAnimation[] = [];
  private attackActionAnimationTimer: ReturnType<typeof setTimeout> | null = null;
  private attackActionAnimationId = 0;
  private attackTurnResolveTimer: ReturnType<typeof setTimeout> | null = null;
  private attackDamageAnimationTimer: ReturnType<typeof setTimeout> | null = null;
  private lastAnimatedTrainerKey: string | null = null;
  showAuditTrail = false;

  gameOverMessage: string | null = null;
  gameOverTimer = 5;
  private gameOverInterval: ReturnType<typeof setInterval> | null = null;

  playerActiveDamage = 0;
  opponentActiveDamage = 0;
  damageBySlot: Record<string, Record<string, number>> = {};
  playerEnergyCount = 0;
  opponentEnergyCount = 0;
  playerActiveEnergies: AttachedEnergy[] = [];
  playerBenchEnergies: AttachedEnergy[][] = [[], [], [], [], []];
  opponentActiveEnergies: AttachedEnergy[] = [];
  opponentBenchEnergies: AttachedEnergy[][] = [[], [], [], [], []];
  playerStatus: StatusCondition | null = null;
  opponentStatus: StatusCondition | null = null;
  energyAttachedThisTurn = false;
  retreatedThisTurn = false;
  toastMessage: string | null = null;
  private toastTimeout: ReturnType<typeof setTimeout> | null = null;
  private allowedActions: GameActions = {
    canConfirmSetup: false,
    canDraw: false,
    canAttachEnergy: false,
    canBenchInMain: false,
    canEvolve: false,
    canPlayTrainer: false,
    canUseAbility: false,
    canRetreat: false,
    canAttack: false,
    canPassTurn: false,
  };
  showRetreatModal = false;
  retreating = false;
  selectedBenchForRetreat: number | null = null;
  retreatEnergySelection: { energy: AttachedEnergy; selected: boolean }[] = [];
  retreatError: string | null = null;
  retreatSwapAnimating = false;
  retreatActiveOut: HandCard | null = null;
  retreatBenchIn: HandCard | null = null;
  retreatBenchSlot = -1;
  retreatEnergyDiscardAnimating = false;
  retreatDiscardedEnergies: { energyType: string; id: number }[] = [];
  private retreatEnergyIdCounter = 0;
  showTrainerModal = false;
  selectedTrainerCard: HandCard | null = null;
  trainerTargetSlot: string | null = null;
  trainerTargetId: string | null = null;
  selectedTrainerEnergyCardIds: string[] = [];
  selectedTrainerCardIds: string[] = []; // used for discard/top-deck selections
  trainerPlaying = false;
  trainerError: string | null = null;

  // Deck preview for trainers that need top-of-deck selection (Great Ball, Evosoda)
  deckPreview: { instanceId: string; name: string; image: string; type: string }[] = [];
  deckPreviewLoading = false;

  showAttackTargetModal = false;
  pendingTargetAttackIndex: number | null = null;
  pendingTargetAttack: CardAttackResponse | null = null;
  pendingAttackRequirements: AttackRequirementsResponse | null = null;
  selectedAttackTargetSlot: string | null = null;
  selectedAttackTargetPlayerId: string | null = null;
  // For multi-target attacks (e.g. Hydro Bombard hits 2, Geomancy attaches to 2). The single
  // fields above mirror the first selection for the single-target code paths.
  selectedAttackTargets: { playerId: string; slot: string }[] = [];
  attackDeckCards: VisibleCardInfo[] = [];
  selectedAttackCardIds: string[] = [];
  attackHandCards: VisibleCardInfo[] = [];
  selectedAttackHandCardIds: string[] = [];
  selectedAttackEnergyCardIds: string[] = [];
  selectedAttackCondition: StatusCondition | null = null;
  selectedAttackAcceptedOptionalEffect: boolean | null = null;
  selectedAttackOpponentAttackIndex: number | null = null;
  attackDeckLoading = false;
  attackTargetError: string | null = null;
  isSuddenDeath = false;
  opponentDiscardCount = 0;
  playerDiscardCount = 0;
  koNotification: string | null = null;
  private previousActivePlayerCard: HandCard | null = null;
  private previousActivePlayerEnergies: AttachedEnergy[] = [];
  private previousActiveOpponentCard: HandCard | null = null;
  private previousActiveOpponentEnergies: AttachedEnergy[] = [];
  koDiscardAnimating = false;
  koDiscardImage = '';
  koDiscardName = '';
  koDiscardSide: 'me' | 'opp' = 'me';
  koDiscardEnergies: { energyType: string; id: number }[] = [];
  isDropTargetActive = false;
  private koNotificationTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Reveal animation ──
  revealPhase: 'idle' | 'pending' | 'hidden' | 'revealing' | 'done' = 'idle';
  private revealAnimTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Ability state ──
  abilityMode = false;
  pendingAbilitySlot = '';
  pendingAbilityIndex = -1;
  pendingAbilityName = '';
  pendingAbilityCard: HandCard | null = null;

  // ── Ability target modal (multi-step abilities) ──
  showAbilityTargetModal = false;
  abilityModalMode: 'fairy-transfer' | 'stance-change' | null = null;

  // Fairy Transfer state
  abilitySrcSlot: string | null = null;
  abilityDestSlot: string | null = null;
  selectedAbilityEnergyCardId: string | null = null;

  // Stance Change state
  selectedAbilityHandCardId: string | null = null;

  abilityTargetError: string | null = null;

  // ── Card reveal notification ──
  cardRevealNotification: { playerName: string; cardNames: string[]; cardImages: string[] } | null = null;

  dismissCardReveal(): void {
    this.cardRevealNotification = null;
  }

  private showCardRevealNotification(playerId: string | null | undefined, revealedCards: RevealedCardInfo[] | null | undefined): void {
    if (!playerId || this.isCurrentPlayer(playerId) || !revealedCards?.length) return;
    const cards = revealedCards.filter(card => !!card.cardId || !!card.instanceId);
    if (cards.length === 0) return;

    const playerName = playerId === this.opponentPlayerId ? this.opponent.name : 'Rival';
    this.cardRevealNotification = {
      playerName: playerName || 'Rival',
      cardNames: cards.map(card => card.name || card.cardId || card.instanceId || 'Carta revelada'),
      cardImages: cards.map(card => this.resolveCardImageFromParts(card.setId ?? '', card.cardId || this.parseCardIdFromInstanceId(card.instanceId ?? ''))),
    };
  }

  private revealedCardsFromIds(cardIds: string[] | null | undefined): RevealedCardInfo[] {
    if (!cardIds?.length) return [];
    return cardIds.map((id) => {
      const cardId = this.parseCardIdFromInstanceId(id);
      const deckMatch = this.deck?.cards?.find(card => card.cardId === cardId);
      return {
        cardId,
        setId: deckMatch?.setId ?? '',
        name: deckMatch?.name ?? cardId,
        instanceId: id,
      };
    });
  }

  // ── Trainer coin flip ──
  showTrainerCoinFlip = false;
  trainerCoinFlipResults: boolean[] = [];
  trainerCoinFlipAnimating = false;
  trainerCoinFlipDone = false;

  // ── Attack coin flip ──
  showAttackCoinFlip = false;
  attackCoinFlipResolved = false;
  attackCoinFlipCount = 0;
  attackCoinFlipResults: boolean[] = [];
  attackCoinFlipCurrent = 0;
  attackCoinFlipAnimating = false;
  pendingAttackIndex: number | null = null;
  pendingAttack: CardAttackResponse | null = null;
  attackCoinFlipAttackName = '';
  attackCoinFlipKey = 0;
  private pendingAttackSelection: Partial<GameActionRequest> | null = null;

  // ── Between-turns checkup (Pokémon Checkup) ──
  showCheckupBanner = false;
  checkupBannerText = '';
  showCheckupCoin = false;
  checkupCoinAnimating = false;
  checkupCoinRevealed = false;
  checkupCoinHeads = false;
  checkupCoinTitle = '';
  checkupCoinOutcome = '';
  checkupDamageFloat: number | null = null;
  checkupDamageTarget: 'me' | 'opp' | null = null;
  private checkupTimers: ReturnType<typeof setTimeout>[] = [];
  private checkupRunning = false;

  showCoinFlip = false;
  coinFlipResult: CoinResult | null = null;
  coinFlipWinner: string | null = null;
  coinFlipPlayerOne: string | null = null;
  coinFlipPlayerTwo: string | null = null;
  coinFlipAnimationDone = false;
  coinFlipPending = false;
  showCoinFlipChoice = false;
  choosingFirst = false;
  showCoinSideChoice = false;
  isCoinFlipPhase = false;
  showCoinFlipWaiting = false;
  ngOnInit(): void {
    const gameId = this.route.snapshot.paramMap.get('gameId');
    if (gameId) {
      this.initFromGame(gameId);
      return;
    }

    this.deckId = this.route.snapshot.paramMap.get('deckId');
    if (!this.deckId) {
      this.deckLoadError = 'Mazo no encontrado.';
      return;
    }

    // Modo local / Sala de Práctica (Sandbox): no viene gameId en la ruta.
    // El GameStateService y el WebSocketService son singletons que pueden
    // conservar en memoria el estado y las suscripciones de una partida online
    // anterior; los limpiamos para que el tablero no cargue esa partida.
    this.sandboxMode = true;
    this.gameStateService.clearState();
    this.webSocketService.disconnect();

    const found = this.deckStorage.getAll().find((deck) => deck.id === this.deckId);
    if (!found) {
      this.deckLoadError = 'Mazo no encontrado.';
      return;
    }

    this.deckLoadError = null;
    this.deck = found;
    this.player.deckName = found.name;
    // Pool de cartas del mazo (una por carta única) para poder sacar a mano
    // cualquier carta a voluntad en el Sandbox, sin azar.
    this.sandboxCardPool = found.cards.map(c => this.convertToHandCard(c));
    this.initializePlayerHand(found);
    this.initSandboxBoard();

    this.gameStateSub = this.gameStateService.state$.subscribe((state) => {
      // En modo local ignoramos cualquier estado online residual.
      if (!this.gameId) return;
      if (!state) return;
      this.applyGameState(state);
    });

    this.wsSub = this.webSocketService.events$.subscribe((event) => {
      // En modo local ignoramos eventos de partidas online.
      if (!this.gameId) return;
      this.handleWsEvent(event);
    });

    this.chatSub = this.webSocketService.chatEvents$.subscribe((msg) => {
      if (!this.gameId) return;
      if (this.chatMessages.some(m => m.id === msg.id)) return;
      this.chatMessages.push(msg);
      if (this.chatMessages.length > 100) {
        this.chatMessages = this.chatMessages.slice(-100);
      }
      this.cdr.detectChanges();
      this.scrollChatToBottom();
    });

    const user = this.authService.getCurrentUser();
    if (user?.avatar) {
      this.player.avatar = user.avatar;
    }
  }

  private initFromGame(gameId: string): void {
    this.gameId = gameId;
    this.sandboxMode = false;
    this.deckLoadError = null;
    this.waitingForOpponent = true;

    // Recordamos la partida online activa para poder ofrecer "Volver a la
    // partida" desde el Lobby si el usuario sale temporalmente sin rendirse.
    localStorage.setItem('active_game_id', gameId);

    this.gameStateSub = this.gameStateService.state$.subscribe((state) => {
      if (!this.gameId) return;
      if (!state) return;
      this.applyGameState(state);
      this.fetchPlayerHandWhenReady();
      this.tryStartSetup();
      this.startPollingIfWaiting();
      this.startSetupPolling();
      this.cdr.detectChanges();
    });

    this.wsSub = this.webSocketService.events$.subscribe((event) => {
      if (!this.gameId) return;
      this.handleWsEvent(event);
      this.cdr.detectChanges();
    });

    this.chatSub = this.webSocketService.chatEvents$.subscribe((msg) => {
      if (!this.gameId) return;
      if (this.chatMessages.some(m => m.id === msg.id)) return;
      this.chatMessages.push(msg);
      if (this.chatMessages.length > 100) {
        this.chatMessages = this.chatMessages.slice(-100);
      }
      this.cdr.detectChanges();
      this.scrollChatToBottom();
    });

    const deckId = this.route.snapshot.queryParamMap.get('deckId');
    if (deckId) {
      this.deckId = deckId;
      this.deckService.get(deckId).subscribe({
        next: (deck) => {
          this.deck = deck;
          this.player.deckName = deck.name;
          this.tryStartSetup();
          this.fetchPlayerHandWhenReady();
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadDeckFromStorage();
          this.cdr.detectChanges();
        },
      });
    } else {
      this.loadDeckFromStorage();
      this.cdr.detectChanges();
    }

    this.gameService.getState(gameId).subscribe({
      next: (state) => {
        this.gameStateService.setState(state);
        this.applyGameState(state);
        if (state.state === 'FINISHED') {
          // Late join into a finished game: show the modal with whatever reason
          // we can derive; auto-redirect will fire after 5s.
          localStorage.removeItem('active_game_id');
          const iWon = this.isCurrentPlayer(state.winner);
          this.gameOverMessage = resolveWinReasonMessage(undefined, iWon ? 'winner' : 'loser');
          this.gameOverTimer = 5;
          this.cdr.detectChanges();
          this.startGameOverTimer();
          return;
        }
        const token = this.authService.getToken() || undefined;
        this.webSocketService.connect(gameId, token);
        this.startPollingIfWaiting();
        this.fetchPlayerHandWhenReady();
        this.tryStartSetup();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading game state:', err);
        this.deckLoadError = 'No se pudo cargar la partida. Verificá el código e intentá de nuevo.';
        this.cdr.detectChanges();
      },
    });
  }

  private tryStartSetup(): void {
    if (!this.gameId || this.settingUp) return;
    if (this.isSetupMode || !this.waitingForOpponent) return;
    if (!this.lastGameState) return;
    if (!this.deck) return;

    const hasBothPlayers = this.lastGameState.playerNames
      && Object.keys(this.lastGameState.playerNames).length >= 2;
    if (!hasBothPlayers) return;

    this.settingUp = true;
    this.gameService.setup(this.gameId, { interactive: true }).subscribe({
      next: (state) => {
        this.settingUp = false;
        this.deckShuffleAnimating = true;
        this.cdr.detectChanges();

        setTimeout(() => {
          this.deckShuffleAnimating = false;
          this.initialDrawAnimating = true;
          this.gameStateService.setState(state);
          this.checkMulligan(state);
          this.fetchPlayerHand();
          this.cdr.detectChanges();

          setTimeout(() => {
            this.initialDrawAnimating = false;
            this.cdr.detectChanges();
          }, 1500); // Wait for draw animation to finish
        }, 1200); // Wait for shuffle animation to finish
      },
      error: () => {
        this.settingUp = false;
        this.cdr.detectChanges();
      },
    });
  }

  chooseCoinSide(side: CoinResult): void {
    if (!this.gameId || this.coinFlipPending) return;
    this.showCoinSideChoice = false;
    this.coinFlipPending = true;
    this.cdr.detectChanges();
    this.gameService.coinFlip(this.gameId, side).subscribe({
      next: (state) => {
        this.coinFlipPending = false;
        this.gameStateService.setState(state);
        if (!this.showCoinFlip) {
          this.showCoinFlipFromState(state);
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.coinFlipPending = false;
        this.cdr.detectChanges();
      },
    });
  }

  private showCoinFlipFromState(state: GameStateResponse): void {
    if (this.showCoinFlip) return;
    const playerOneId = state.playerOne ?? Object.keys(state.playerNames)[0];
    const playerTwoId = state.playerTwo ?? Object.keys(state.playerNames)[1];
    this.coinFlipPlayerOne = playerOneId;
    this.coinFlipPlayerTwo = playerTwoId;

    if (state.coinResult) {
      this.coinFlipResult = state.coinResult;
    } else if (state.coinFlipChoice && playerOneId) {
      this.coinFlipResult = state.coinFlipWinner === playerOneId
        ? state.coinFlipChoice as CoinResult
        : (state.coinFlipChoice === 'HEADS' ? 'TAILS' : 'HEADS');
    } else {
      this.coinFlipResult = state.coinFlipWinner === playerOneId ? 'HEADS' : 'TAILS';
    }

    this.coinFlipWinner = state.coinFlipWinner;
    this.showCoinFlipWaiting = false;
    this.stopCoinFlipPolling();
    this.showCoinFlip = true;
    this.coinFlipAnimationDone = false;
    setTimeout(() => {
      this.dismissCoinFlip();
    }, 2500);
  }

  private checkMulligan(state: GameStateResponse): void {
    if (this.mulliganAlreadyShown) return;
    if (state.state !== 'SETUP') return;
    if (state.setupPhase !== 'SETUP_WAITING_ACTIVE') return;
    const myId = this.myPlayerId;
    const oppId = this.opponentPlayerId;
    if (!myId || !oppId) return;

    const mySetup = state.setupByPlayer?.[myId];
    const oppSetup = state.setupByPlayer?.[oppId];
    if (!mySetup || !oppSetup) return;

    this.myMulligans = mySetup.mulligans;
    this.opponentMulligans = oppSetup.mulligans;
    this.extraCardsForMe = oppSetup.mulligans;
    this.extraCardsForOpponent = mySetup.mulligans;

    if (state.revealedHandCardsByPlayer) {
      this.opponentRevealedHand = state.revealedHandCardsByPlayer[oppId] ?? [];
      this.myRevealedHand = state.revealedHandCardsByPlayer[myId] ?? [];
    }

    if (this.myMulligans > 0 || this.opponentMulligans > 0) {
      this.showMulliganOverlay = true;
      this.mulliganAlreadyShown = true;
      this.startMulliganAnimation();
    }
  }

  private startMulliganAnimation(): void {
    if (this.mulliganAnimTimer) clearTimeout(this.mulliganAnimTimer);
    this.mulliganPhase = 'cards';
    this.mulliganCardsRevealed = false;
    this.cdr.detectChanges();

    this.mulliganAnimTimer = setTimeout(() => {
      this.mulliganCardsRevealed = true;
      this.cdr.detectChanges();
    }, 400);

    this.mulliganAnimTimer = setTimeout(() => {
      this.mulliganPhase = 'shuffle';
      this.cdr.detectChanges();
    }, 2500);

    this.mulliganAnimTimer = setTimeout(() => {
      this.mulliganPhase = 'draw';
      this.cdr.detectChanges();
    }, 3800);

    if (this.extraCardsForMe > 0 || this.extraCardsForOpponent > 0) {
      this.mulliganAnimTimer = setTimeout(() => {
        this.mulliganPhase = 'extra';
        this.cdr.detectChanges();
      }, 4800);
    }
  }

  dismissMulligan(): void {
    if (this.mulliganAnimTimer) clearTimeout(this.mulliganAnimTimer);
    this.showMulliganOverlay = false;
    this.mulliganPhase = 'cards';
    this.mulliganCardsRevealed = false;
    this.opponentRevealedHand = [];
    this.myRevealedHand = [];
    this.cdr.detectChanges();
  }

  staggerDelay(i: number): string {
    return `${0.08 + i * 0.12}s`;
  }

  resolveCardImageFromInfo(info: VisibleCardInfo): string {
    if (!info.setId || !info.cardId) return '/images/cardBack.png';
    const prefix = `${info.setId}-`;
    const cardRef = info.cardId.startsWith(prefix)
      ? info.cardId.slice(prefix.length)
      : info.cardId;
    if (!cardRef || !/^\d+$/.test(cardRef)) return '/images/cardBack.png';
    return `https://images.pokemontcg.io/${info.setId}/${cardRef}.png`;
  }

  onMulliganImgError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = '/images/cardBack.png';
  }

  get myPlayerId(): string | null {
    const pid = this.resolveMyPlayerId();
    return pid || null;
  }

  get isReplacementPending(): boolean {
    return this.lastGameState?.waitingReplacement === true
      && this.lastGameState?.replacementPlayerId === this.myPlayerId;
  }

  get isForcedSwitchPending(): boolean {
    return this.isReplacementPending
      && this.lastGameState?.pendingForcedSwitchPlayerId === this.myPlayerId;
  }

  private isCurrentPlayer(playerId: string | null | undefined): boolean {
    return !!playerId && playerId === this.myPlayerId;
  }

  slotForBench(index: number): string {
    return `BENCH_${index}`;
  }

  private resolveMyPlayerId(): string | undefined {
    const pid = this.playerIdFromToken();
    if (pid) return pid;

    if (!this.lastGameState) return undefined;
    const user = this.authService.getCurrentUser();
    const names = this.lastGameState.playerNames;
    if (!names) return undefined;

    if (user?.playerId && names[user.playerId]) return user.playerId;
    if (user?.username) {
      const entry = Object.entries(names).find(([, v]) => v === user.username);
      if (entry) return entry[0];
    }
    const keys = Object.keys(names);
    return keys.length === 1 ? keys[0] : undefined;
  }

  private playerIdFromToken(): string | undefined {
    const token = this.authService.getToken();
    if (!token) return undefined;
    try {
      const payload = token.split('.')[1];
      if (!payload) return undefined;
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json).playerId;
    } catch {
      return undefined;
    }
  }

  private buildHandCardFromInstanceId(instanceId: string, deckMap: Map<string, DeckCardInput>): HandCard {
    const cardId = instanceId.includes(':')
      ? instanceId.substring(0, instanceId.indexOf(':'))
      : instanceId;
    const cardDef = deckMap.get(cardId);
    if (cardDef) {
      return { ...this.convertToHandCard(cardDef), instanceId };
    }
    const isEnergy = cardId.toLowerCase().includes('energy');
    const lowerName = instanceId.toLowerCase();
    const isEnergyName = lowerName.includes('energy') || lowerName.includes('fire') && lowerName.includes('energy')
      || lowerName.includes('water') && lowerName.includes('energy')
      || lowerName.includes('grass') && lowerName.includes('energy')
      || lowerName.includes('lightning') && lowerName.includes('energy')
      || lowerName.includes('psychic') && lowerName.includes('energy')
      || lowerName.includes('fighting') && lowerName.includes('energy')
      || lowerName.includes('darkness') && lowerName.includes('energy')
      || lowerName.includes('metal') && lowerName.includes('energy')
      || lowerName.includes('fairy') && lowerName.includes('energy')
      || lowerName.includes('dragon') && lowerName.includes('energy')
      || lowerName.includes('colorless') && lowerName.includes('energy')
      || lowerName.includes('basic') && lowerName.includes('energy');
    const energyType = isEnergy || isEnergyName ? 'Energy' : 'Pokemon';
    return {
      instanceId,
      cardId,
      setId: '',
      name: cardId,
      quantity: 0,
      type: energyType,
      subtype: null,
      basicEnergy: isEnergy || isEnergyName,
      basicPokemon: /^pikachu|^charmander|^squirtle|^bulbasaur|^mewtwo|^mew\b|^jigglypuff|^magikarp|^weedle|^caterpie|^rattata|^pidgey|^oddish|^bellsprout|^geodude|^gastly|^abra|^jackwalker/i.test(cardId) && !isEnergy && !isEnergyName,
      aceSpec: false,
      hp: 0,
      attackDamage: null,
      attackRequiredEnergy: null,
      image: '/images/cardBack.png',
    };
  }

  private fetchPlayerHand(): void {
    if (!this.gameId || !this.deck) return;
    const pid = this.myPlayerId;
    if (!pid) return;
    if (this.isDrawingCard) return;

    this.gameService.getHand(this.gameId, pid).subscribe({
      next: (handResponse) => {
        const deckMap = new Map<string, DeckCardInput>();
        for (const card of this.deck!.cards) {
          deckMap.set(card.cardId, card);
        }

        this.player.hand = handResponse.cardIds.map((instanceId) =>
          this.buildHandCardFromInstanceId(instanceId, deckMap)
        );
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  private fetchPlayerHandWhenReady(): void {
    if (!this.gameId || !this.deck) return;
    // Durante setup, cargar la mano aunque no haya lastGameState completo
    if (!this.lastGameState && !this.isSetupMode) return;
    this.fetchPlayerHand();
  }

  private startSetupPolling(): void {
    if (!this.isSetupMode || !this.setupConfirmed) {
      this.stopSetupPolling();
      return;
    }
    if (this.setupPollTimer) return;
    this.setupPollTimer = setInterval(() => {
      if (!this.gameId) { this.stopSetupPolling(); return; }
      this.gameService.getState(this.gameId).subscribe({
        next: (state) => {
          this.gameStateService.setState(state);
          if (state.state !== 'SETUP') {
            this.stopSetupPolling();
          }
        },
        error: () => {},
      });
    }, 2000);
  }

  private stopSetupPolling(): void {
    if (this.setupPollTimer) {
      clearInterval(this.setupPollTimer);
      this.setupPollTimer = null;
    }
  }

  private startCoinFlipPolling(): void {
    this.stopCoinFlipPolling();
    if (!this.isCoinFlipPhase || !this.gameId) return;
    this.coinFlipPollTimer = setInterval(() => {
      if (!this.gameId) { this.stopCoinFlipPolling(); return; }
      this.gameService.getState(this.gameId).subscribe({
        next: (state) => {
          this.gameStateService.setState(state);
          if (state.state !== 'COIN_FLIP') {
            this.stopCoinFlipPolling();
          }
        },
        error: () => {},
      });
    }, 2000);
  }

  private stopCoinFlipPolling(): void {
    if (this.coinFlipPollTimer) {
      clearInterval(this.coinFlipPollTimer);
      this.coinFlipPollTimer = null;
    }
  }

  private startCountdown(endsAt: string): void {
    this.stopCountdown();
    const endTime = new Date(endsAt).getTime();
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((endTime - Date.now()) / 1000));
      this.setupCountdownSeconds = remaining;
      if (remaining <= 0) {
        this.stopCountdown();
        this.refreshGameState();
      }
      this.cdr.detectChanges();
    };
    tick();
    this.countdownInterval = setInterval(tick, 1000);
  }

  private stopCountdown(): void {
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
      this.countdownInterval = null;
    }
  }

  private loadDeckFromStorage(): void {
    const savedDecks = this.deckStorage.getAll();
    const savedDeck = savedDecks.length > 0 ? savedDecks[0] : null;
    if (!savedDeck) return;
    this.deck = savedDeck;
    this.player.deckName = savedDeck.name;
    this.tryStartSetup();
    this.fetchPlayerHandWhenReady();
    this.cdr.detectChanges();
  }

  get opponentPlayerId(): string | null {
    if (!this.lastGameState) return null;
    const pid = this.myPlayerId;
    if (!pid) return null;
    const keys = Object.keys(this.lastGameState.playerNames);
    return keys.find(k => k !== pid) || null;
  }

  private startPollingIfWaiting(): void {
    this.stopPolling();
    if (!this.waitingForOpponent || !this.gameId) return;
    this.pollTimer = setInterval(() => {
      if (!this.gameId) { this.stopPolling(); return; }
      this.gameService.getState(this.gameId).subscribe({
        next: (state) => {
          this.gameStateService.setState(state);
          if (state.state !== 'WAITING') {
            this.stopPolling();
          }
        },
        error: () => {},
      });
    }, 3000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private applyGameState(state: GameStateResponse): void {
    this.lastGameState = state;
    this.currentPhase = state.phase as TurnPhase | SetupPhase;
    const user = this.authService.getCurrentUser();
    const myId = this.myPlayerId;
    const opponentId = this.opponentPlayerId;
    this.isMyTurn = this.isCurrentPlayer(state.currentTurnPlayer);
    this.fetchActionLog();

    const wasWaiting = this.waitingForOpponent;
    this.waitingForOpponent = state.state === 'WAITING';
    this.isSetupMode = state.state === 'SETUP';
    this.isCoinFlipPhase = state.state === 'COIN_FLIP';
    this.setupPhase = state.setupPhase;

    if (state.firstPlayer) {
      this.showCoinFlip = false;
      this.showCoinFlipChoice = false;
      this.showCoinSideChoice = false;
      this.showCoinFlipWaiting = false;
      this.isCoinFlipPhase = false;
      this.coinFlipResult = null;
      this.coinFlipWinner = null;
      this.coinFlipAnimationDone = false;
      this.stopCoinFlipPolling();
    }

    if (state.state === 'COIN_FLIP' && !state.firstPlayer && !state.coinFlipWinner) {
      this.tryStartCoinFlip();
      this.startCoinFlipPolling();
    }

    if (state.coinFlipWinner && state.state === 'COIN_FLIP' && !this.showCoinFlip && !this.showCoinFlipChoice && !this.showCoinSideChoice && !this.coinFlipPending) {
      this.showCoinFlipFromState(state);
    }

    if (this.isSetupMode) {
      this.setupError = null;
      if (state.countdownEndsAt) {
        this.startCountdown(state.countdownEndsAt);
      }
    }

    if (state.state === 'FINISHED') {
      this.waitingForOpponent = false;
      this.isSetupMode = false;
      this.isCoinFlipPhase = false;
      this.showCoinFlipWaiting = false;
      // La partida terminó: ya no hay nada a lo que "volver" desde el Lobby.
      localStorage.removeItem('active_game_id');
    }

    if (wasWaiting && !this.waitingForOpponent) {
      this.stopPolling();
    }

    if (state.playerNames) {
      this.player.name = user?.username || 'TÚ';
      if (opponentId && state.playerNames[opponentId]) {
        this.opponent.name = state.playerNames[opponentId];
      }
    }

    if (state.playerAvatars) {
      if (myId && state.playerAvatars[myId]) {
        this.player.avatar = state.playerAvatars[myId];
      }
      if (opponentId && state.playerAvatars[opponentId]) {
        this.opponent.avatar = state.playerAvatars[opponentId];
      }
    }

    if (state.activeStadium) {
      const stadiumCardId = this.parseCardIdFromInstanceId(state.activeStadium);
      const stadiumName = state.activeStadiumName ?? this.deck?.cards?.find(c => c.cardId === stadiumCardId)?.name ?? stadiumCardId;
      const dashIdx = stadiumCardId.indexOf('-');
      const setId = dashIdx > 0 ? stadiumCardId.substring(0, dashIdx) : stadiumCardId;
      const stadiumImage = this.resolveCardImageFromParts(setId, stadiumCardId);
      this.stadium = { name: stadiumName, image: stadiumImage };
      this.stadiumOwnerName = state.activeStadiumOwner && state.playerNames
        ? state.playerNames[state.activeStadiumOwner] ?? null
        : null;
    } else {
      this.stadium = null;
      this.stadiumOwnerName = null;
    }

    if (state.toolsBySlot) {
      this.toolsBySlot = state.toolsBySlot;
    } else {
      this.toolsBySlot = {};
    }

    if (opponentId) {
      this.opponent.playerId = opponentId;
    }

    if (state.deckCardsRemainingByPlayer) {
      if (opponentId && state.deckCardsRemainingByPlayer[opponentId] !== undefined) {
        this.opponent.deckCount = state.deckCardsRemainingByPlayer[opponentId];
      }
      if (myId && state.deckCardsRemainingByPlayer[myId] !== undefined) {
        this.player.deckCount = state.deckCardsRemainingByPlayer[myId];
      }
    }

    if (state.activePokemonInfo) {
      if (opponentId) {
        this.previousActiveOpponentCard = this.opponent.active;
        const oppInfo = state.activePokemonInfo[opponentId];
        this.opponent.active = oppInfo
          ? this.makeHandCardFromVisible(oppInfo)
          : null;
      }
      if (myId) {
        this.previousActivePlayerCard = this.player.active;
        const myInfo = state.activePokemonInfo[myId];
        this.player.active = myInfo
          ? this.makeHandCardFromVisible(myInfo)
          : null;
      }
    }

    // Capture energies BEFORE they get overwritten
    this.previousActivePlayerEnergies = [...this.playerActiveEnergies];
    this.previousActiveOpponentEnergies = [...this.opponentActiveEnergies];

    // Detect KO for player
    if (myId && this.previousActivePlayerCard && !this.player.active) {
      this.triggerKoNotification(
        this.previousActivePlayerCard.name,
        this.previousActivePlayerCard.image,
        'me',
        this.previousActivePlayerEnergies
      );
    }
    // Detect KO for opponent
    if (opponentId && this.previousActiveOpponentCard && !this.opponent.active) {
      this.triggerKoNotification(
        this.previousActiveOpponentCard.name,
        this.previousActiveOpponentCard.image,
        'opp',
        this.previousActiveOpponentEnergies
      );
    }

    if (state.benchInfo) {
      const fillBench = (playerKey: string | null): (HandCard | null)[] => {
        if (!playerKey) return [null, null, null, null, null];
        const benchCards = state.benchInfo[playerKey] || [];
        const slots: (HandCard | null)[] = benchCards.map(c =>
          c ? this.makeHandCardFromVisible(c) : null);
        while (slots.length < 5) { slots.push(null); }
        return slots;
      };
      this.opponent.bench = fillBench(opponentId);
      this.player.bench = fillBench(myId);
    }

    if (state.handSizesByPlayer) {
      if (opponentId && state.handSizesByPlayer[opponentId] !== undefined) {
        this.opponentHandCountValue = state.handSizesByPlayer[opponentId];
      }
    }

    if (state.prizeCardsRemainingByPlayer) {
      if (opponentId && state.prizeCardsRemainingByPlayer[opponentId] !== undefined) {
        const count = state.prizeCardsRemainingByPlayer[opponentId];
        const prev = this.opponent.prizes.length;
        this.opponent.prizes = new Array(count).fill(null);
        this.showPrizeTakenToast(prev - count);
      }
      if (myId && state.prizeCardsRemainingByPlayer[myId] !== undefined) {
        const count = state.prizeCardsRemainingByPlayer[myId];
        const prev = this.player.prizes.length;
        this.player.prizes = new Array(count).fill(null);
        this.showPrizeTakenToast(prev - count);
      }
    }

    if (state.activeDamageCountersByPlayer) {
      this.playerActiveDamage = state.activeDamageCountersByPlayer[myId ?? ''] ?? 0;
      this.opponentActiveDamage = state.activeDamageCountersByPlayer[opponentId ?? ''] ?? 0;
    }

    if (state.damageCountersBySlot) {
      this.damageBySlot = state.damageCountersBySlot as Record<string, Record<string, number>>;
    }

    if (state.activeAttachedEnergyByPlayer) {
      this.playerEnergyCount = state.activeAttachedEnergyByPlayer[myId ?? ''] ?? 0;
      this.opponentEnergyCount = state.activeAttachedEnergyByPlayer[opponentId ?? ''] ?? 0;
    }

    if (state.attachedEnergiesByPlayer) {
      const emptyBench: AttachedEnergy[][] = [[], [], [], [], []];
      const mySlotEnergies = state.attachedEnergiesByPlayer[myId ?? ''] ?? {};
      const oppSlotEnergies = state.attachedEnergiesByPlayer[opponentId ?? ''] ?? {};
      this.playerActiveEnergies = (mySlotEnergies['ACTIVE'] as AttachedEnergy[]) ?? [];
      this.opponentActiveEnergies = (oppSlotEnergies['ACTIVE'] as AttachedEnergy[]) ?? [];
      this.playerBenchEnergies = emptyBench.map((_, i) => ((mySlotEnergies[`BENCH_${i}`] as AttachedEnergy[]) ?? []));
      this.opponentBenchEnergies = emptyBench.map((_, i) => ((oppSlotEnergies[`BENCH_${i}`] as AttachedEnergy[]) ?? []));
    }

    if (state.statusByPlayer) {
      this.playerStatus = this.firstStatus(state.statusByPlayer[myId ?? '']);
      this.opponentStatus = this.firstStatus(state.statusByPlayer[opponentId ?? '']);
      if (this.previewedCard) {
        this.previewedCardStatus = this.getStatusForPreviewedCard();
        if (this.previewedCardSummary) {
          this.previewedCardSummary = { ...this.previewedCardSummary, statusCondition: this.previewedCardStatus };
        }
      }
    }

    this.energyAttachedThisTurn = state.turnFlags?.energyAttached === true;
    this.retreatedThisTurn = state.turnFlags?.retreated === true;
    this.isSuddenDeath = state.suddenDeath === true;

    this.allowedActions = computeAllowedActions(state, myId, {
      hasEmptyBench: this.player.bench.some(slot => slot === null),
      hasBenchPokemon: this.player.bench.some(slot => slot !== null),
      activeStatus: this.playerStatus,
      turnsStarted: myId ? (state.turnsStartedByPlayer?.[myId] ?? 1) : 1,
    });

    if (state.discardPilesByPlayer) {
      this.playerDiscardCount = (state.discardPilesByPlayer[myId ?? ''] as unknown[])?.length ?? 0;
      this.opponentDiscardCount = (state.discardPilesByPlayer[opponentId ?? ''] as unknown[])?.length ?? 0;
    }

    if (state.state === 'SETUP' && !this.showMulliganOverlay && !this.setupConfirmed) {
      this.checkMulligan(state);
    }

    if (this.revealPhase === 'pending') {
      this.startRevealAnimation();
    }
  }

  private opponentHandCountValue = 6;

  private makeHandCardFromVisible(info: VisibleCardInfo): HandCard {
    const isEnergy = info.supertype === 'Energy';
    const isPokemon = info.supertype === 'Pokemon' || info.supertype === 'Pokémon';
    const attacks: CardAttackResponse[] = info.attacks && info.attacks.length > 0
      ? info.attacks
      : (info.attackDamage != null && info.attackName != null
        ? [{
            name: info.attackName ?? 'Attack',
            text: info.attackText ?? '',
            damage: String(info.attackDamage ?? 0),
            cost: info.attackCost ?? [],
            convertedEnergyCost: info.attackRequiredEnergy ?? 0,
          }]
        : []);
    return {
      instanceId: info.instanceId ?? '',
      cardId: info.cardId,
      setId: info.setId,
      name: info.name,
      quantity: 1,
      type: info.supertype ?? 'Pokemon',
      subtype: info.subtypes && info.subtypes.length > 0 ? info.subtypes.join(',') : null,
      basicEnergy: isEnergy && (info.subtypes?.includes('Basic') ?? false),
      basicPokemon: isPokemon && (info.subtypes?.includes('Basic') ?? false),
      aceSpec: false,
      hp: info.hp,
      attackDamage: info.attackDamage,
      attackRequiredEnergy: info.attackRequiredEnergy,
      attackName: info.attackName,
      attackText: info.attackText,
      attackCost: info.attackCost,
      energyType: info.energyType,
      retreatCost: info.retreatCost,
      evolvesFrom: info.evolvesFrom,
      abilities: info.abilities,
      weaknessType: info.weaknessType,
      weaknessAmount: info.weaknessAmount,
      resistanceType: info.resistanceType,
      resistanceAmount: info.resistanceAmount,
      attacks,
      image: this.resolveCardImageFromParts(info.setId, info.cardId),
    };
  }

  private resolveCardImageFromParts(setId: string, cardId: string): string {
    if (!setId || !cardId) return '/images/cardBack.png';
    const prefix = `${setId}-`;
    const cardRef = cardId.startsWith(prefix) ? cardId.slice(prefix.length) : '';
    if (!cardRef) return '/images/cardBack.png';
    return `https://images.pokemontcg.io/${setId}/${cardRef}.png`;
  }

  private refreshGameState(): void {
    if (!this.gameId) return;
    // Hold board refreshes while the between-turns checkup animation runs so the
    // visual sequence is not overwritten by concurrent events (TURN_STARTED, etc.).
    if (this.checkupRunning) return;
    const snapshot = this.stateVersion;
    this.gameService.getState(this.gameId).subscribe({
      next: (state) => {
        if (this.stateVersion > snapshot) return;
        this.gameStateService.setState(state);
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error refreshing game state:', err),
    });
  }

  private scheduleRefresh(delay = 150): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    this.refreshTimer = setTimeout(() => {
      this.refreshTimer = null;
      this.refreshGameState();
    }, delay);
  }

  private handleWsEvent(event: GameEventEnvelope): void {
    switch (event.type) {
      case 'PLAYER_JOINED':
        this.refreshGameState();
        this.tryStartSetup();
        this.startPollingIfWaiting();
        return;

      case 'TURN_PHASE_CHANGED': {
        const phase = event.payload['phase'] as TurnPhase | SetupPhase;
        if (phase) this.currentPhase = phase;
        this.cdr.detectChanges();
        this.scheduleRefresh();
        return;
      }

      case 'TURN_STARTED': {
        const turnPlayer = event.payload['currentTurnPlayer'] as string;
        // Usar this.myPlayerId (derivado del JWT) como fuente de verdad, igual que
        // en applyGameState (820) y FIRST_PLAYER_CHOSEN (1177). El user.playerId de
        // localStorage puede estar desactualizado y romper la deteccion de turno.
        this.isMyTurn = this.isCurrentPlayer(turnPlayer);
        const phase = event.payload['phase'] as TurnPhase | SetupPhase;
        if (phase) this.currentPhase = phase;
        this.turnStartCount++;
        this.turnNumber = Math.floor(this.turnStartCount / 2) + 1;
        this.attackResult = null;
        this.cdr.detectChanges();
        this.scheduleRefresh();
        this.fetchActionLog();
        return;
      }

      case 'COIN_FLIP': {
        if (this.showCoinFlip) return;
        this.showCoinFlipWaiting = false;
        this.stopCoinFlipPolling();
        const cfPayload = event.payload as unknown as import('../../core/models/websocket.models').CoinFlipPayload;
        this.coinFlipResult = cfPayload.coinResult;
        this.coinFlipWinner = cfPayload.coinFlipWinner;
        this.coinFlipPlayerOne = cfPayload.playerOne;
        this.coinFlipPlayerTwo = cfPayload.playerTwo;
        this.showCoinFlip = true;
        this.coinFlipAnimationDone = false;
        this.cdr.detectChanges();
        setTimeout(() => {
          this.dismissCoinFlip();
        }, 2500);
        return;
      }

      case 'FIRST_PLAYER_CHOSEN': {
        const fp = event.payload['firstPlayer'] as string;
        this.coinFlipWinner = null;
        this.showCoinFlipChoice = false;
        this.showCoinFlipWaiting = false;
        this.showCoinSideChoice = false;
        this.coinFlipAnimationDone = true;
        this.showCoinFlip = false;
        this.isCoinFlipPhase = false;
        this.stopCoinFlipPolling();
        this.cdr.detectChanges();
        return;
      }

      // Eventos que alteran la mano privada
      case 'CARD_DRAWN':
      case 'ENERGY_ATTACHED':
      case 'SUPPORTER_PLAYED':
      case 'PRIZE_TAKEN':
      case 'POKEMON_BENCHED':
      case 'POKEMON_EVOLVED':
      case 'TRAINER_PLAYED': {
        if (event.type === 'ENERGY_ATTACHED') {
          this.attachingEnergy = false;
        }
        if (event.type === 'PRIZE_TAKEN') {
          const pp = event.payload as { player?: string; prizesTaken?: number };
          if (pp.prizesTaken && this.isCurrentPlayer(pp.player ?? '')) {
            this.animatePrizeCards(pp.prizesTaken);
          }
        }
        const p = event.payload['phase'] as TurnPhase | undefined;
        const tp = event.payload['currentTurnPlayer'] as string | undefined;
        if (p) this.currentPhase = p;
        if (tp) this.isMyTurn = this.isCurrentPlayer(tp);
        if (event.type === 'TRAINER_PLAYED') {
          const trainerPayload = event.payload as unknown as TrainerPlayedPayload;
          const key = `${trainerPayload.cardInstanceId}:${trainerPayload.result}`;
          if (this.lastAnimatedTrainerKey === key) {
            this.lastAnimatedTrainerKey = null;
          } else {
            this.showCardRevealNotification(
              trainerPayload.playerId,
              trainerPayload.effect?.revealedCards ?? this.revealedCardsFromIds(trainerPayload.effect?.cardIds),
            );
            this.triggerTrainerActionAnimations(trainerPayload);
          }
        }
        if (event.type === 'SUPPORTER_PLAYED') {
          const spPayload = event.payload as { player?: string };
          if (spPayload.player && this.isCurrentPlayer(spPayload.player)) {
            this.showTrainerModal = false;
            this.selectedTrainerCard = null;
            this.trainerTargetSlot = null;
            this.trainerTargetId = null;
            this.selectedTrainerEnergyCardIds = [];
            this.selectedTrainerCardIds = [];
            this.revealedDeckSelection = null;
            this.revealPhase = 'idle';
            this.trainerPlaying = false;
          }
        }
        this.scheduleRefresh();
        this.fetchPlayerHand();
        this.fetchActionLog();
        return;
      }
      case 'STADIUM_PLAYED':
      case 'TOOL_EQUIPPED':
      case 'ABILITY_USED': {
        if (event.type === 'STADIUM_PLAYED' || event.type === 'TOOL_EQUIPPED') {
          const trainerPayload = event.payload as unknown as TrainerPlayedPayload;
          const key = `${trainerPayload.cardInstanceId}:${trainerPayload.result}`;
          if (this.lastAnimatedTrainerKey === key) {
            this.lastAnimatedTrainerKey = null;
          } else {
            this.triggerTrainerActionAnimations(trainerPayload);
          }
        }
        const p2 = event.payload['phase'] as TurnPhase | undefined;
        const tp2 = event.payload['currentTurnPlayer'] as string | undefined;
        if (p2) this.currentPhase = p2;
        if (tp2) this.isMyTurn = this.isCurrentPlayer(tp2);
        this.scheduleRefresh();
        this.fetchPlayerHand();
        this.fetchActionLog();
        return;
      }

      case 'GAME_STARTED': {
        if (this.showCoinFlip && !this.coinFlipAnimationDone) {
          return;
        }
        this.turnNumber = 1;
        this.turnStartCount = 0;
        this.isCoinFlipPhase = false;
        this.stopCoinFlipPolling();
        this.revealPhase = 'pending';
        this.refreshGameState();
        this.fetchPlayerHand();
        return;
      }

      // Eventos que NO alteran la mano directamente
      case 'BETWEEN_TURNS_RESOLVED': {
        this.startBetweenTurnsCheckup();
        return;
      }

      case 'RETREAT_DECLARED':
      case 'KO':
      case 'STATUS_APPLIED':
      case 'ACTIVE_REPLACED':
      case 'SETUP_ACTIVE_PLACED':
      case 'SETUP_BENCH_PLACED':
      case 'SETUP_CONFIRMED':
      case 'SETUP_COMPLETED': {
        this.scheduleRefresh();
        this.fetchActionLog();
        return;
      }

      case 'ATTACK_RESOLVED': {
        this.attackResult = event.payload as unknown as AttackResolvedPayload;
        this.showCardRevealNotification(this.attackResult.attacker, this.attackResult.revealedCards);
        this.showResolvedAttackCoinFlip(this.attackResult);
        this.triggerAttackActionAnimations(this.attackResult);
        this.scheduleAttackDamageAnimation(this.attackResult);
        this.scheduleAttackTurnResolution(this.attackResult);
        this.scheduleRefresh();
        this.fetchPlayerHand();
        this.fetchActionLog();
        this.showSkippedEffectToast(this.attackResult);
        setTimeout(() => {
          this.attackResult = null;
          this.cdr.detectChanges();
        }, 5000);
        return;
      }

      case 'GAME_FINISHED': {
        this.scheduleRefresh();
        const winner = event.payload['winner'] as string | null;
        const reason = event.payload['reason'] as WinReason | null | undefined;
        const iWon = this.isCurrentPlayer(winner);
        this.gameOverMessage = resolveWinReasonMessage(reason, iWon ? 'winner' : 'loser');
        this.gameOverTimer = 5;
        this.cdr.detectChanges();
        this.startGameOverTimer();
        return;
      }
      case 'SUDDEN_DEATH_STARTED':
      case 'STATE_SYNCED':
        this.scheduleRefresh();
        return;

      default:
        break;
    }
  }

  sendChatMessage(): void {
    const text = this.chatInput.trim();
    if (!text || !this.gameId) return;
    const user = this.authService.getCurrentUser();
    const myId = this.myPlayerId;
    const playerName = this.player.name || user?.username || 'TÚ';
    if (myId) {
      const id = crypto.randomUUID();
      const timestamp = new Date().toISOString();
      this.chatMessages.push({ playerId: myId, playerName, text, timestamp, id });
      this.scrollChatToBottom();
      this.webSocketService.send(`/app/chat/${this.gameId}`, JSON.stringify({
        playerId: myId, playerName, text, timestamp, id,
      }));
    }
    this.chatInput = '';
    setTimeout(() => {
      const el = document.querySelector('.chat-input') as HTMLInputElement;
      el?.focus();
    }, 0);
  }

  private scrollChatToBottom(): void {
    const el = document.querySelector('.chat-messages');
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  private startGameOverTimer(): void {
    this.gameOverInterval = setInterval(() => {
      this.gameOverTimer--;
      this.cdr.detectChanges();
      if (this.gameOverTimer <= 0) {
        this.stopGameOverTimer();
        this.router.navigate(['/lobby']);
      }
    }, 1000);
  }

  private stopGameOverTimer(): void {
    if (this.gameOverInterval) {
      clearInterval(this.gameOverInterval);
      this.gameOverInterval = null;
    }
  }

  ngOnDestroy(): void {
    this.stopGameOverTimer();
    this.gameStateSub?.unsubscribe();
    this.wsSub?.unsubscribe();
    this.chatSub?.unsubscribe();
    this.webSocketService.disconnect();
    this.stopPolling();
    this.stopSetupPolling();
    this.stopCoinFlipPolling();
    this.stopCountdown();
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (this.koNotificationTimer) {
      clearTimeout(this.koNotificationTimer);
      this.koNotificationTimer = null;
    }
    if (this.prizeAnimTimer) {
      clearTimeout(this.prizeAnimTimer);
      this.prizeAnimTimer = null;
    }
    if (this.prizeAnimTimer2) {
      clearTimeout(this.prizeAnimTimer2);
      this.prizeAnimTimer2 = null;
    }
    if (this.revealAnimTimer) {
      clearTimeout(this.revealAnimTimer);
      this.revealAnimTimer = null;
    }
    if (this.attackActionAnimationTimer) {
      clearTimeout(this.attackActionAnimationTimer);
      this.attackActionAnimationTimer = null;
    }
    if (this.attackTurnResolveTimer) {
      clearTimeout(this.attackTurnResolveTimer);
      this.attackTurnResolveTimer = null;
    }
    if (this.attackDamageAnimationTimer) {
      clearTimeout(this.attackDamageAnimationTimer);
      this.attackDamageAnimationTimer = null;
    }
    if (this.damageAnimationTimer) {
      clearTimeout(this.damageAnimationTimer);
      this.damageAnimationTimer = null;
    }
    if (this.shakeTimer) {
      clearTimeout(this.shakeTimer);
      this.shakeTimer = null;
    }
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
      this.flashTimer = null;
    }
    this.clearCheckupTimers();
  }

  private startRevealAnimation(): void {
    this.revealPhase = 'hidden';
    this.cdr.detectChanges();

    setTimeout(() => {
      this.revealPhase = 'revealing';
      this.cdr.detectChanges();
    }, 400);

    this.revealAnimTimer = setTimeout(() => {
      this.revealPhase = 'done';
      this.revealAnimTimer = null;
      this.cdr.detectChanges();
    }, 3200);
  }

  getRevealOrder(playerSide: 'me' | 'opponent', slot: 'active' | 'bench', index: number): number {
    if (slot === 'active') {
      return playerSide === 'me' ? 0 : 6;
    }
    return playerSide === 'me' ? 1 + index : 7 + index;
  }

  // ── Setup actions ──────────────────────────────────────────────────────────

  get canConfirmSetup(): boolean {
    return this.allowedActions.canConfirmSetup
      && !this.setupConfirmed
      && !this.waitingForOpponent;
  }

  confirmSetup(): void {
    if (!this.canConfirmSetup || !this.gameId) return;
    this.setupError = null;
    this.setupConfirmed = true;
    this.gameService.setupConfirm(this.gameId).subscribe({
      next: () => {
        this.startSetupPolling();
        this.refreshGameState();
      },
      error: (err) => {
        this.setupConfirmed = false;
        this.setupError = err.error?.message || 'Error al confirmar setup. Esperá a que el otro jugador coloque su activo.';
        this.cdr.detectChanges();
      },
    });
  }

  dismissCoinFlip(): void {
    this.coinFlipAnimationDone = true;
    if (this.coinFlipWinner && this.coinFlipWinner === this.myPlayerId) {
      this.showCoinFlipChoice = true;
    }
    this.cdr.detectChanges();
  }

  private tryStartCoinFlip(): void {
    if (!this.gameId || this.coinFlipPending) return;
    if (this.showCoinFlip || this.showCoinFlipChoice || this.showCoinSideChoice) return;

    if (this.isPlayerOne) {
      this.showCoinSideChoice = true;
      this.showCoinFlipWaiting = false;
      this.cdr.detectChanges();
    } else {
      this.showCoinFlipWaiting = true;
      this.cdr.detectChanges();
    }
  }

  chooseFirst(chooseFirst: boolean): void {
    if (!this.gameId || this.choosingFirst) return;
    this.choosingFirst = true;
    this.gameService.chooseFirstPlayer(this.gameId, chooseFirst).subscribe({
      next: () => {
        this.choosingFirst = false;
        this.showCoinFlip = false;
        this.showCoinFlipChoice = false;
        this.showCoinSideChoice = false;
        this.coinFlipResult = null;
        this.coinFlipWinner = null;
        this.coinFlipAnimationDone = false;
        this.refreshGameState();
        this.fetchPlayerHand();
      },
      error: () => this.choosingFirst = false,
    });
  }

  private startSetupAfterCoinFlip(): void {
    this.showCoinFlip = false;
    this.showCoinFlipChoice = false;
    this.showCoinSideChoice = false;
    this.coinFlipResult = null;
    this.coinFlipWinner = null;
    this.coinFlipPlayerOne = null;
    this.coinFlipPlayerTwo = null;
    this.coinFlipAnimationDone = false;
    this.cdr.detectChanges();

    this.refreshGameState();
    this.fetchPlayerHand();
  }

  get coinFlipWinnerName(): string {
    if (!this.coinFlipWinner || !this.lastGameState?.playerNames) return 'Jugador';
    return this.lastGameState.playerNames[this.coinFlipWinner] || 'Jugador';
  }

  get isCoinFlipWinner(): boolean {
    return this.coinFlipWinner === this.myPlayerId;
  }

  private get isPlayerOne(): boolean {
    if (this.lastGameState?.playerOne) {
      return this.lastGameState.playerOne === this.myPlayerId;
    }
    if (!this.lastGameState?.playerNames) return false;
    const keys = Object.keys(this.lastGameState.playerNames);
    return keys.length > 0 && keys[0] === this.myPlayerId;
  }

  get isFirstPlayerFirstTurn(): boolean {
    return this.lastGameState?.firstTurn === true
      && this.lastGameState?.firstPlayer === this.myPlayerId;
  }

  get playerToolsBySlot(): Record<string, string> {
    return this.toolsBySlot[this.myPlayerId ?? ''] ?? {};
  }

  get opponentToolsBySlot(): Record<string, string> {
    const oppId = this.opponentPlayerId;
    return oppId ? (this.toolsBySlot[oppId] ?? {}) : {};
  }

  slotHasTool(slot: string): boolean {
    return !!this.playerToolsBySlot[slot];
  }

  private parseCardIdFromInstanceId(instanceId: string): string {
    return instanceId.includes(':') ? instanceId.split(':')[0] : instanceId;
  }

  toolNameForSlot(slot: string): string {
    const instanceId = this.playerToolsBySlot[slot];
    if (!instanceId) return '';
    const cardId = this.parseCardIdFromInstanceId(instanceId);
    const deckMatch = this.deck?.cards?.find(c => c.cardId === cardId);
    return deckMatch?.name ?? cardId;
  }

  toolImageForSlot(slot: string): string {
    const instanceId = this.playerToolsBySlot[slot];
    if (!instanceId) return '/images/cardBack.png';
    const cardId = this.parseCardIdFromInstanceId(instanceId);
    const dashIdx = cardId.indexOf('-');
    if (dashIdx <= 0) return '/images/cardBack.png';
    const setId = cardId.substring(0, dashIdx);
    const cardRef = cardId.substring(dashIdx + 1);
    return `https://images.pokemontcg.io/${setId}/${cardRef}.png`;
  }

  opponentToolNameForSlot(slot: string): string {
    const instanceId = this.opponentToolsBySlot[slot];
    if (!instanceId) return '';
    const cardId = this.parseCardIdFromInstanceId(instanceId);
    const deckMatch = this.deck?.cards?.find(c => c.cardId === cardId);
    return deckMatch?.name ?? cardId;
  }

  opponentToolImageForSlot(slot: string): string {
    const instanceId = this.opponentToolsBySlot[slot];
    if (!instanceId) return '/images/cardBack.png';
    const cardId = this.parseCardIdFromInstanceId(instanceId);
    const dashIdx = cardId.indexOf('-');
    if (dashIdx <= 0) return '/images/cardBack.png';
    const setId = cardId.substring(0, dashIdx);
    const cardRef = cardId.substring(dashIdx + 1);
    return `https://images.pokemontcg.io/${setId}/${cardRef}.png`;
  }

  // ── Drag & Drop handlers ────────────────────────────────────────────────────

  onDropActive(event: CdkDragDrop<HandCard[]>): void {
    if (this.isReplacementPending) {
      this.onDropActiveForReplacement(event);
      return;
    }

    const card: HandCard = event.item.data;
    if (this.sandboxMode) {
      if (card?.instanceId) this.onDropActiveSandbox(card);
      return;
    }
    if (!card.instanceId || !this.gameId) return;

    if (this.isSetupMode) {
      if (!card.basicPokemon) return;
      this.setupError = null;
      this.removeFromHand(card.instanceId);
      this.gameService.setupPlaceActive(this.gameId, card.instanceId).subscribe({
        next: (state) => {
          this.gameStateService.setState(state);
          this.fetchPlayerHand();
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.setupError = err.error?.message || 'Error al colocar activo';
          this.restoreToHand(card);
          this.refreshGameState();
        },
      });
    } else if (card.basicEnergy && this.canAttachEnergy) {
      this.removeFromHand(card.instanceId);
      this.attachEnergyFromCard(card, 'ACTIVE');
    } else if (card.type !== 'Energy' && this.isEvolutionCard(card) && this.canEvolve) {
      this.handleEvolutionDrop(card, 'ACTIVE');
    } else if (!card.basicEnergy && card.basicPokemon && this.canAttachEnergy) {
      this.actionError = 'Solo se pueden adjuntar cartas de Energía, no Pokémon. Arrastrá una carta de Energía.';
      this.cdr.detectChanges();
    }
  }

  onDropActiveForReplacement(event: CdkDragDrop<HandCard[]>): void {
    if (!this.isReplacementPending || !this.gameId) return;
    const sourceId = event.previousContainer?.id;
    if (!sourceId || !sourceId.startsWith('bench-drop-zone-')) return;
    const benchIndex = parseInt(sourceId.substring('bench-drop-zone-'.length), 10);
    if (Number.isNaN(benchIndex) || benchIndex < 0 || benchIndex >= 5) return;
    if (!this.player.bench[benchIndex]) return;

    this.isDropTargetActive = false;
    this.gameService.replaceActive(this.gameId, benchIndex).subscribe({
      next: (state) => {
        this.gameStateService.setState(state);
        this.koNotification = null;
        if (this.koNotificationTimer) {
          clearTimeout(this.koNotificationTimer);
          this.koNotificationTimer = null;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'No se pudo reemplazar el Pokémon activo';
        this.refreshGameState();
        this.cdr.detectChanges();
      },
    });
  }

  onDropBench(event: CdkDragDrop<HandCard[]>, index: number): void {
    const card: HandCard = event.item.data;
    if (this.sandboxMode) {
      if (card?.instanceId) this.onDropBenchSandbox(card, index);
      return;
    }
    if (!card.instanceId || !this.gameId) return;

    if (this.isSetupMode) {
      if (!card.basicPokemon) return;
      this.setupError = null;
      this.removeFromHand(card.instanceId);
      this.gameService.setupPlaceBench(this.gameId, [card.instanceId]).subscribe({
        next: (state) => {
          this.gameStateService.setState(state);
          this.fetchPlayerHand();
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.setupError = err.error?.message || 'Error al colocar en banca';
          this.restoreToHand(card);
          this.refreshGameState();
        },
      });
    } else if (card.basicEnergy && this.canAttachEnergy) {
      this.removeFromHand(card.instanceId);
      this.attachEnergyFromCard(card, this.slotForBench(index));
    } else if (card.basicPokemon && this.canBenchInMain) {
      this.removeFromHand(card.instanceId);
      this.benchingPokemon = true;
      this.gameService.benchPokemon(this.gameId, { cardInstanceId: card.instanceId, targetSlot: this.slotForBench(index) }).subscribe({
        next: (state) => {
          this.benchingPokemon = false;
          this.gameStateService.setState(state);
          this.fetchPlayerHand();
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.actionError = err.error?.message || 'No se pudo colocar el Pokémon en la banca';
          this.benchingPokemon = false;
          this.restoreToHand(card);
          this.refreshGameState();
        },
      });
    } else if (this.isEvolutionCard(card) && this.canEvolve) {
      this.handleEvolutionDrop(card, this.slotForBench(index));
    }
  }

  onlyBasicPokemonOrEnergy = (drag: CdkDrag<HandCard>): boolean => {
    const card = drag.data;
    if (!card) return false;
    // Sandbox sin reglas: cualquier carta puede soltarse en cualquier zona.
    if (this.sandboxMode) return true;
    if (card.basicPokemon) return true;
    if (card.basicEnergy) return true;
    if (card.type !== 'Energy' && card.evolvesFrom && this.canEvolve) return true;
    return false;
  };

  onlyBasicPokemonDuringSetup = (drag: CdkDrag<HandCard>): boolean => {
    if (this.sandboxMode) return true;
    return drag.data?.basicPokemon === true;
  };

  acceptAnyBenchPokemonForReplacement = (drag: CdkDrag<HandCard>): boolean => {
    const card = drag.data;
    if (!card) return false;
    return card.type !== 'Energy';
  };

  get canAttachEnergy(): boolean {
    if (this.sandboxMode) return !this.attachingEnergy;
    return this.allowedActions.canAttachEnergy
      && this.gameId !== null
      && !this.attachingEnergy;
  }

  get canAttack(): boolean {
    if (this.sandboxMode) {
      return !this.attacking
        && this.player.active !== null
        && this.opponent.active !== null;
    }
    return this.allowedActions.canAttack
      && this.gameId !== null
      && !this.attacking
      && this.player.active !== null
      && this.opponent.active !== null;
  }

  get canRetreat(): boolean {
    return this.allowedActions.canRetreat
      && this.gameId !== null
      && !this.retreating
      && this.player.active !== null;
  }

  get activeCardAttacks(): CardAttackResponse[] {
    if (!this.player.active) return [];
    const summary = this.buildCardSummary(this.player.active);
    return summary?.attacks ?? [];
  }

  get activeCardAbilities(): CardAbilityResponse[] {
    if (!this.player.active) return [];
    const summary = this.buildCardSummary(this.player.active);
    return summary?.abilities ?? [];
  }

  private attackDisabledReason(atk: CardAttackResponse): string | undefined {
    if (this.sandboxMode) {
      return this.canAffordAttack(atk) ? undefined : 'Energía insuficiente para este ataque';
    }
    if (!this.isMyTurn) return 'No es tu turno';
    if (this.currentPhase !== 'ATTACK') return 'Primero pasá a la fase de ataque';
    if (this.isFirstPlayerFirstTurn) return 'No podés atacar en el primer turno';
    if (this.playerStatus === 'ASLEEP') return 'Tu Pokémon está dormido';
    if (this.playerStatus === 'PARALYZED') return 'Tu Pokémon está paralizado';
    if (!this.canAffordAttack(atk)) return 'Energía insuficiente para este ataque';
    return undefined;
  }

  private retreatDisabledReason(): string | undefined {
    if (!this.isMyTurn) return 'No es tu turno';
    if (this.isFirstPlayerFirstTurn) return 'No podés retirar en el primer turno';
    if (this.retreatedThisTurn) return 'Ya retiraste este turno';
    const hasBench = this.player.bench.some(b => b !== null);
    if (!hasBench) return 'No tenés Pokémon en banca';
    return undefined;
  }

  get activeCardActions(): ActionItem[] {
    const items: ActionItem[] = [];
    const attacks = this.activeCardAttacks;
    for (let i = 0; i < attacks.length; i++) {
      const atk = attacks[i];
      const disabled = !this.canAttack || !this.canAffordAttack(atk);
      items.push({
        type: 'attack',
        name: atk.name ?? 'Ataque',
        index: i,
        disabled,
        disabledReason: disabled ? this.attackDisabledReason(atk) : undefined,
        attack: atk,
      });
    }
    const abilities = this.activeCardAbilities;
    for (let i = 0; i < abilities.length; i++) {
      const ab = abilities[i];
      const used = this.isAbilityUsed(ab.name);
      const disabled = used || !this.canUseAbility;
      items.push({
        type: 'ability',
        name: ab.name,
        index: i,
        disabled,
        disabledReason: disabled ? (used ? 'Habilidad ya usada este turno' : 'No podés usar habilidades ahora') : undefined,
        ability: ab,
      });
    }
    if (this.canRetreat || this.player.active) {
      const summary = this.player.active ? this.buildCardSummary(this.player.active) : null;
      const retreatCost = summary?.retreatCost ?? 0;
      const disabled = !this.canRetreat;
      items.push({
        type: 'retreat',
        name: 'RETIRAR',
        index: 0,
        disabled,
        disabledReason: disabled ? this.retreatDisabledReason() : undefined,
        retreatCost,
        availableEnergies: this.playerActiveEnergies.length,
      });
    }
    return items;
  }

  showToast(message: string): void {
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.toastMessage = message;
    this.toastTimeout = setTimeout(() => {
      this.toastMessage = null;
      this.cdr.markForCheck();
    }, 2500);
  }

  executeActionItem(item: ActionItem): void {
    if (item.disabled) {
      if (item.disabledReason) this.showToast(item.disabledReason);
      return;
    }
    if (item.type === 'attack') {
      this.tryAttack(item.index);
    } else if (item.type === 'ability') {
      if (this.player.active) {
        this.enterAbilityMode(this.player.active, 'ACTIVE', item.index, item.ability?.name ?? '');
      }
    } else if (item.type === 'retreat') {
      this.openRetreatModal();
    }
  }

  get canUseAbility(): boolean {
    return this.allowedActions.canUseAbility
      && this.gameId !== null
      && !this.abilityMode
      && !this.showAbilityTargetModal;
  }

  isAbilityUsed(name: string): boolean {
    return this.lastGameState?.turnFlags?.abilitiesUsed?.includes(name) ?? false;
  }

  getAbilitiesForCard(card: HandCard): CardAbilityResponse[] {
    const summary = this.buildCardSummary(card);
    return summary?.abilities ?? [];
  }

  attackNeedsCoinFlip(attack: CardAttackResponse): boolean {
    if (this.pendingAttackRequirements?.coinFlipCount) return this.pendingAttackRequirements.coinFlipCount > 0;
    // UX-only heuristic before requirements load: show flip badge if text contains "flip"
    const text = ((attack.name ?? '') + ' ' + (attack.text ?? '')).toLowerCase();
    return text.includes('flip');
  }

  attackFlipCount(attack: CardAttackResponse): number {
    if (this.pendingAttackRequirements?.coinFlipCount) return this.pendingAttackRequirements.coinFlipCount;
    return 1;
  }

  private attackNeedsCoinFlipFromRequirements(_attack: CardAttackResponse, requirements: AttackRequirementsResponse | null): boolean {
    return (requirements?.coinFlipCount ?? 0) > 0;
  }

  private attackFlipCountFromRequirements(_attack: CardAttackResponse, requirements: AttackRequirementsResponse | null): number {
    return requirements?.coinFlipCount ?? 0;
  }

  tryAttack(attackIndex: number): void {
    if (this.sandboxMode) {
      if (!this.canAttack) return;
      const sandboxAttack = this.activeCardAttacks[attackIndex];
      if (!sandboxAttack || !this.canAffordAttack(sandboxAttack)) return;
      this.performSandboxAttack(attackIndex);
      return;
    }
    if (!this.canAttack || !this.gameId) return;
    const attacks = this.activeCardAttacks;
    const attack = attacks[attackIndex];
    if (!attack) return;
    if (!this.canAffordAttack(attack)) return;

    this.gameService.getAttackRequirements(this.gameId, attackIndex).subscribe({
      next: (requirements) => this.continueAttackAfterRequirements(attackIndex, attack, requirements),
      error: () => this.continueAttackAfterRequirements(attackIndex, attack, null),
    });
  }

  private continueAttackAfterRequirements(
    attackIndex: number,
    attack: CardAttackResponse,
    requirements: AttackRequirementsResponse | null,
  ): void {
    const hasRequirementSteps = (requirements?.steps?.length ?? 0) > 0;

    if (hasRequirementSteps || this.attackNeedsActionModal()) {
      this.openAttackTargetModal(attackIndex, attack, requirements);
      return;
    }

    this.performAttack(attackIndex);
  }

  private showResolvedAttackCoinFlip(result: AttackResolvedPayload): void {
    const coinFlipResults = result.coinFlipResults ?? [];
    if (coinFlipResults.length === 0) return;

    this.attackCoinFlipResolved = true;
    this.attackCoinFlipResults = coinFlipResults;
    this.attackCoinFlipCount = coinFlipResults.length;
    this.attackCoinFlipCurrent = coinFlipResults.length;
    this.attackCoinFlipAnimating = false;
    this.attackCoinFlipAttackName = 'Resultado del ataque';
    this.showAttackCoinFlip = true;
    this.cdr.detectChanges();

    setTimeout(() => {
      if (this.attackCoinFlipResolved) {
        this.cancelAttackCoinFlip();
      }
    }, 2500);
  }

  cancelAttackCoinFlip(): void {
    this.showAttackCoinFlip = false;
    this.attackCoinFlipResolved = false;
    this.pendingAttackIndex = null;
    this.pendingAttack = null;
    this.pendingAttackSelection = null;
    this.attackCoinFlipAttackName = '';
    this.attackCoinFlipAnimating = false;
    this.cdr.detectChanges();
  }

  private showSkippedEffectToast(result: AttackResolvedPayload): void {
    if (!result.auditTrail) return;
    const skipped = result.auditTrail.some(e =>
        e.endsWith('_discard_none') || e.endsWith('_no_target') || e.endsWith('_no_source')
    );
    if (!skipped) return;
    const didDamage = (result.damage ?? 0) > 0;
    this.toastService.show(
        didDamage ? 'El ataque se realizó pero solo hizo daño.' : 'No hay acción a realizar.',
        'warning',
        3000
    );
  }

  // ── Trainer coin flip ──
  private startTrainerCoinFlip(): void {
    this.trainerCoinFlipResults = [];
    this.trainerCoinFlipAnimating = false;
    this.trainerCoinFlipDone = false;
    this.showTrainerCoinFlip = true;
    this.cdr.detectChanges();
    setTimeout(() => this.animateTrainerCoinFlip(), 600);
  }

  private animateTrainerCoinFlip(): void {
    this.trainerCoinFlipAnimating = true;
    this.cdr.detectChanges();
    const result = Math.random() < 0.5;
    setTimeout(() => {
      this.trainerCoinFlipAnimating = false;
      this.trainerCoinFlipResults = [result];
      this.trainerCoinFlipDone = true;
      this.cdr.detectChanges();
    }, 1200);
  }

  confirmTrainerCoinFlip(): void {
    this.showTrainerCoinFlip = false;
    this.confirmPlayTrainer();
  }

  cancelTrainerCoinFlip(): void {
    this.showTrainerCoinFlip = false;
    this.trainerCoinFlipResults = [];
    this.trainerCoinFlipAnimating = false;
    this.trainerCoinFlipDone = false;
    this.cdr.detectChanges();
  }

  get headsCount(): number {
    return this.attackCoinFlipResults.filter(r => r).length;
  }

  get flipEffectDescription(): string {
    const total = this.attackCoinFlipCount;
    const done = this.attackCoinFlipResults.length === total && !this.attackCoinFlipAnimating;
    if (!done) return '';
    const heads = this.headsCount;
    const tails = total - heads;
    const effects = this.pendingAttackRequirements?.effects ?? [];
    const hasPersistent = effects.some(e => e.type === 'PERSISTENT_EFFECT');
    const hasStatus = effects.some(e => e.type === 'STATUS');
    const hasConditional = effects.some(e => e.type === 'CONDITIONAL_DAMAGE');

    if (heads === 0 && (hasPersistent || hasStatus)) {
      return `${heads} cara, ${tails} cruz${tails !== 1 ? 'es' : ''} — El ataque falló`;
    }
    if (hasConditional) {
      return `${heads} cara${heads !== 1 ? 's' : ''} de ${total} — Daño condicional aplicado`;
    }
    return `${heads} de ${total} — ${heads > 0 ? '¡Éxito!' : 'Falló'}`;
  }

  canAffordAttack(attack: CardAttackResponse): boolean {
    if (!attack.cost || attack.cost.length === 0) return true;
    const energyCounts: Record<string, number> = {};
    for (const energy of this.playerActiveEnergies) {
      const type = energy.energyType || 'Colorless';
      energyCounts[type] = (energyCounts[type] || 0) + 1;
    }

    let colorlessNeeded = 0;
    for (const requiredType of attack.cost) {
      const type = requiredType || 'Colorless';
      if (type === 'Colorless') {
        colorlessNeeded++;
        continue;
      }

      const available = energyCounts[type] || 0;
      if (available <= 0) return false;
      energyCounts[type] = available - 1;
    }

    const remainingAnyEnergy = Object.values(energyCounts).reduce((total, count) => total + count, 0);
    return remainingAnyEnergy >= colorlessNeeded;
  }

  performAttack(attackIndex?: number, selection?: Partial<GameActionRequest>): void {
    if (!this.canAttack || !this.gameId) return;
    this.attacking = true;
    this.actionError = null;
    this.attackResult = null;
    this.stateVersion++;

    this.attackAnimating = true;
    this.cdr.detectChanges();

    setTimeout(() => {
      this.attackAnimating = false;
      this.cdr.detectChanges();
    }, 700);

    const attack = this.activeCardAttacks[attackIndex ?? 0];
    const request: GameActionRequest = {
      actionType: 'ATTACK',
      attackIndex: attackIndex ?? 0,
      ...this.buildAttackSelection(attack),
      ...(selection ?? {}),
    };

    this.gameService.executeAction(this.gameId, request).subscribe({
      next: (state) => {
        this.attacking = false;
        this.stateVersion++;
        this.gameStateService.setState(state);
        this.scheduleRefresh();
        this.fetchPlayerHand();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'Error al atacar';
        this.attacking = false;
        this.attackAnimating = false;
        this.refreshGameState();
      },
    });
  }

  private buildAttackSelection(attack: CardAttackResponse | undefined): Partial<GameActionRequest> {
    const myId = this.myPlayerId;
    const opponentId = this.opponentPlayerId;
    if (!attack || !opponentId) return {};

    const scope = this.attackTargetRequirementStep()?.scope;

    if (scope === 'OWN_BENCH') {
      const firstBenchIndex = this.player.bench.findIndex(Boolean);
      return firstBenchIndex >= 0
        ? { targetPlayerId: myId ?? undefined, targetSlot: this.slotForBench(firstBenchIndex) }
        : { targetPlayerId: myId ?? undefined, targetSlot: 'ACTIVE' };
    }

    if (scope === 'OWN_POKEMON' || scope === 'OWN_ACTIVE') {
      return { targetPlayerId: myId ?? undefined, targetSlot: 'ACTIVE' };
    }

    if (scope === 'OPPONENT_BENCH') {
      const firstBenchIndex = this.opponent.bench.findIndex(Boolean);
      return firstBenchIndex >= 0
        ? { targetPlayerId: opponentId, targetSlot: this.slotForBench(firstBenchIndex) }
        : { targetPlayerId: opponentId, targetSlot: 'ACTIVE' };
    }

    return { targetPlayerId: opponentId, targetSlot: 'ACTIVE' };
  }

  private attackNeedsTargetModal(): boolean {
    return !!this.attackTargetRequirementStep();
  }

  private attackNeedsActionModal(): boolean {
    return !!this.attackTargetRequirementStep() || !!this.attackDeckRequirementStep();
  }

  private openAttackTargetModal(
    attackIndex: number,
    attack: CardAttackResponse,
    requirements: AttackRequirementsResponse | null = null,
  ): void {
    this.pendingTargetAttackIndex = attackIndex;
    this.pendingTargetAttack = attack;
    this.pendingAttackRequirements = requirements;
    this.selectedAttackTargetSlot = null;
    this.selectedAttackTargetPlayerId = null;
    this.selectedAttackTargets = [];
    this.attackDeckCards = [];
    this.selectedAttackCardIds = [];
    this.attackHandCards = [];
    this.selectedAttackHandCardIds = [];
    this.selectedAttackEnergyCardIds = [];
    this.selectedAttackCondition = null;
    this.selectedAttackAcceptedOptionalEffect = this.attackOptionalRequirementStep() ? true : null;
    this.selectedAttackOpponentAttackIndex = null;
    this.attackTargetError = null;
    this.showAttackTargetModal = true;

    const deckStep = this.attackDeckRequirementStep();
    if (deckStep?.cards?.length) {
      this.attackDeckCards = deckStep.cards;
      if (deckStep.type === 'ORDER_DECK_TOP') {
        this.selectedAttackCardIds = deckStep.cards.map(card => card.instanceId);
        this.selectedAttackEnergyCardIds = deckStep.cards
          .filter(card => this.isVisibleEnergyCard(card))
          .map(card => card.instanceId);
      }
    } else if (this.attackNeedsDeckSelection() && deckStep?.type !== 'VIEW_DECK_TOP') {
      this.loadAttackDeckCards();
    }

    const handStep = this.attackHandRequirementStep();
    if (handStep?.cards?.length) {
      this.attackHandCards = handStep.cards;
    } else if (handStep && handStep.type === 'SELECT_HAND_CARD') {
      this.loadAttackHandCards();
    }
  }

  selectAttackTarget(playerId: string | null | undefined, slot: string): void {
    if (!playerId) return;
    const max = this.attackTargetMax();
    if (max > 1) {
      const idx = this.selectedAttackTargets.findIndex(t => t.playerId === playerId && t.slot === slot);
      if (idx >= 0) {
        this.selectedAttackTargets.splice(idx, 1);
      } else if (this.selectedAttackTargets.length < max) {
        this.selectedAttackTargets.push({ playerId, slot });
      }
    } else {
      this.selectedAttackTargets = [{ playerId, slot }];
    }
    const first = this.selectedAttackTargets[0];
    this.selectedAttackTargetPlayerId = first?.playerId ?? null;
    this.selectedAttackTargetSlot = first?.slot ?? null;
    this.attackTargetError = null;
  }

  isSelectedAttackTarget(playerId: string | null | undefined, slot: string): boolean {
    return !!playerId
      && this.selectedAttackTargets.some(t => t.playerId === playerId && t.slot === slot);
  }

  private attackTargetMax(): number {
    return this.attackTargetRequirementStep()?.max ?? 1;
  }

  get canConfirmAttackTarget(): boolean {
    if (!this.pendingTargetAttack || this.attacking || this.attackDeckLoading) return false;
    if (this.attackTargetError) return false;
    const targetOk = !this.attackNeedsTargetModal()
      || (!!this.selectedAttackTargetPlayerId && !!this.selectedAttackTargetSlot);
    const deckOptions = this.attackDeckOptions(this.pendingTargetAttack);
    const deckStep = this.attackDeckRequirementStep();
    const deckMin = deckStep?.min ?? 1;
    const deckOk = !this.attackNeedsDeckSelection()
      || this.isAttackDeckViewOnlyMode(this.pendingTargetAttack)
      || (this.attackNeedsTopDeckOrder(this.pendingTargetAttack)
        ? this.selectedAttackCardIds.length === deckOptions.length
        : this.selectedAttackCardIds.length >= deckMin || deckOptions.length === 0);
    const energyStep = this.attackEnergyRequirementStep();
    const energyOptions = this.attackAttachedEnergyOptions();
    const energyOk = !energyStep
      || this.selectedAttackEnergyCardIds.length >= energyStep.min
      || energyOptions.length === 0;
    const handStep = this.attackHandRequirementStep();
    const handOptions = this.attackHandOptions();
    const handOk = !this.attackNeedsHandSection()
      || this.isAttackHandViewOnlyMode()
      || this.selectedAttackHandCardIds.length >= (handStep?.min ?? 1)
      || handOptions.length === 0;
    const conditionOk = !this.attackConditionRequirementStep() || !!this.selectedAttackCondition;
    const opponentAttackOk = !this.attackNeedsOpponentAttackSection()
      || this.selectedAttackOpponentAttackIndex !== null;
    return targetOk && deckOk && energyOk && handOk && conditionOk && opponentAttackOk;
  }

  confirmAttackTarget(): void {
    if (!this.pendingTargetAttack || this.pendingTargetAttackIndex === null) return;
    if (!this.canConfirmAttackTarget) {
      if (this.attackConditionRequirementStep() && !this.selectedAttackCondition) {
        this.attackTargetError = 'Seleccioná la condición especial para el ataque.';
      } else if (this.attackEnergyRequirementStep() && this.selectedAttackEnergyCardIds.length === 0 && this.attackAttachedEnergyOptions().length > 0) {
        this.attackTargetError = 'Seleccioná la energía que requiere el ataque.';
      } else if (this.attackNeedsDeckSelection() && this.selectedAttackCardIds.length === 0) {
        this.attackTargetError = this.attackNeedsTopDeckOrder(this.pendingTargetAttack)
          ? 'Confirmá el orden de las cartas superiores del mazo.'
          : 'Seleccioná una carta del mazo para el ataque.';
      } else if (this.attackNeedsHandSection() && !this.isAttackHandViewOnlyMode() && this.selectedAttackHandCardIds.length === 0 && this.attackHandOptions().length > 0) {
        this.attackTargetError = 'Seleccioná una carta de la mano rival para el ataque.';
      } else if (this.attackNeedsOpponentAttackSection() && this.selectedAttackOpponentAttackIndex === null) {
        this.attackTargetError = 'Seleccioná un ataque del Pokémon rival.';
      } else {
        this.attackTargetError = 'Seleccioná un objetivo para el ataque.';
      }
      return;
    }

    const attack = this.pendingTargetAttack;
    const attackIndex = this.pendingTargetAttackIndex;
    const requirements = this.pendingAttackRequirements;
    // For multi-target attacks the extra chosen slots are carried as slot references inside
    // selectedCardIds (the backend partitions them from real card ids).
    const extraTargetSlots = this.selectedAttackTargets.length > 1
      ? this.selectedAttackTargets.map(target => target.slot)
      : [];
    const selection: Partial<GameActionRequest> = {
      targetPlayerId: this.selectedAttackTargetPlayerId ?? undefined,
      targetSlot: this.selectedAttackTargetSlot ?? undefined,
      selectedCardIds: [...extraTargetSlots, ...this.selectedAttackCardIds, ...this.selectedAttackHandCardIds],
      selectedEnergyCardIds: this.selectedAttackEnergyCardIds,
      selectedCondition: this.selectedAttackCondition ?? undefined,
      acceptedOptionalEffect: this.selectedAttackAcceptedOptionalEffect ?? undefined,
      selectedOpponentAttackIndex: this.selectedAttackOpponentAttackIndex ?? undefined,
    };
    if (!selection.targetPlayerId) delete selection.targetPlayerId;
    if (!selection.targetSlot) delete selection.targetSlot;

    this.closeAttackTargetModal();
    this.performAttack(attackIndex, selection);
  }

  closeAttackTargetModal(): void {
    if (this.attacking) return;
    this.showAttackTargetModal = false;
    this.pendingTargetAttackIndex = null;
    this.pendingTargetAttack = null;
    this.pendingAttackRequirements = null;
    this.selectedAttackTargetSlot = null;
    this.selectedAttackTargetPlayerId = null;
    this.selectedAttackTargets = [];
    this.attackDeckCards = [];
    this.selectedAttackCardIds = [];
    this.attackHandCards = [];
    this.selectedAttackHandCardIds = [];
    this.selectedAttackEnergyCardIds = [];
    this.selectedAttackCondition = null;
    this.selectedAttackAcceptedOptionalEffect = null;
    this.selectedAttackOpponentAttackIndex = null;
    this.attackDeckLoading = false;
    this.attackTargetError = null;
  }

  private attackRequirementStep(type: AttackRequirementStep['type']): AttackRequirementStep | null {
    return this.pendingAttackRequirements?.steps?.find(step => step.type === type) ?? null;
  }

  private attackTargetRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('SELECT_TARGET');
  }

  private attackDeckRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('ORDER_DECK_TOP')
      ?? this.attackRequirementStep('VIEW_DECK_TOP')
      ?? this.attackRequirementStep('SELECT_DECK_CARDS');
  }

  private attackEnergyRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('SELECT_ATTACHED_ENERGY');
  }

  private attackConditionRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('SELECT_CONDITION');
  }

  private attackOptionalRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('CONFIRM_OPTIONAL_EFFECT');
  }

  private attackHandRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('SELECT_HAND_CARD')
      ?? this.attackRequirementStep('CONFIRM_HAND_EFFECT')
      ?? this.attackRequirementStep('VIEW_HAND');
  }

  private attackRequirementScope(type: AttackRequirementStep['type']): string | null {
    return this.attackRequirementStep(type)?.scope ?? null;
  }

  attackEffectPreviews(): string[] {
    return this.pendingAttackRequirements?.effects?.map(effect => effect.label) ?? [];
  }

  attackNeedsEnergySection(): boolean {
    return !!this.attackEnergyRequirementStep();
  }

  attackEnergySectionLabel(): string {
    const scope = this.attackEnergyRequirementStep()?.scope;
    return scope === 'OPPONENT_ACTIVE'
      ? 'Seleccioná energía adjunta del Pokémon rival:'
      : 'Seleccioná energía adjunta de tu Pokémon:';
  }

  attackAttachedEnergyOptions(): AttachedEnergy[] {
    const scope = this.attackEnergyRequirementStep()?.scope;
    return scope === 'OPPONENT_ACTIVE' ? this.opponentActiveEnergies : this.playerActiveEnergies;
  }

  isAttackEnergySelected(cardId: string | null | undefined): boolean {
    return !!cardId && this.selectedAttackEnergyCardIds.includes(cardId);
  }

  toggleAttackAttachedEnergy(cardId: string | null | undefined): void {
    if (!cardId) return;
    const step = this.attackEnergyRequirementStep();
    const max = step?.max ?? 1;
    if (this.selectedAttackEnergyCardIds.includes(cardId)) {
      this.selectedAttackEnergyCardIds = this.selectedAttackEnergyCardIds.filter(id => id !== cardId);
      return;
    }
    this.selectedAttackEnergyCardIds = max === 1
      ? [cardId]
      : [...this.selectedAttackEnergyCardIds, cardId].slice(0, max);
  }

  attackNeedsConditionSection(): boolean {
    return !!this.attackConditionRequirementStep();
  }

  attackConditionOptions(): StatusCondition[] {
    const stepOptions = this.attackConditionRequirementStep()?.options as StatusCondition[] | undefined;
    if (stepOptions && stepOptions.length > 0) return stepOptions;
    return ['POISONED', 'BURNED', 'ASLEEP', 'CONFUSED', 'PARALYZED'];
  }

  selectAttackCondition(condition: StatusCondition): void {
    this.selectedAttackCondition = condition;
    this.attackTargetError = null;
  }

  attackNeedsOptionalEffectSection(): boolean {
    return !!this.attackOptionalRequirementStep();
  }

  setAttackOptionalEffect(value: boolean): void {
    this.selectedAttackAcceptedOptionalEffect = value;
  }

  attackNeedsDeckSection(_attack: CardAttackResponse | null): boolean {
    return !!this.attackDeckRequirementStep();
  }

  attackDeckOptions(attack: CardAttackResponse | null): VisibleCardInfo[] {
    if (!attack) return [];
    if (this.isAttackDeckViewOnlyMode(attack)) return this.attackDeckCards;
    if (this.attackNeedsTopDeckOrder(attack)) {
      const topCards = this.attackDeckCards.slice(0, this.attackDeckSelectionLimit(attack));
      if (this.selectedAttackCardIds.length === 0) return topCards;
      const byId = new Map(topCards.map(card => [card.instanceId, card]));
      const ordered = this.selectedAttackCardIds
        .map(cardId => byId.get(cardId))
        .filter((card): card is VisibleCardInfo => !!card);
      const missing = topCards.filter(card => !this.selectedAttackCardIds.includes(card.instanceId));
      return [...ordered, ...missing];
    }
    if (this.attackRequirementStep('SELECT_DECK_CARDS')) return this.attackDeckCards;
    return [];
  }

  toggleAttackDeckCard(card: VisibleCardInfo): void {
    if (this.pendingTargetAttack && this.isAttackDeckViewOnlyMode(this.pendingTargetAttack)) return;
    if (this.pendingTargetAttack && this.attackNeedsTopDeckOrder(this.pendingTargetAttack)) return;
    const id = card.instanceId;
    if (!id) return;
    const max = this.attackDeckSelectionLimit(this.pendingTargetAttack);
    const selected = this.selectedAttackCardIds.includes(id);
    if (selected) {
      this.selectedAttackCardIds = this.selectedAttackCardIds.filter(cardId => cardId !== id);
      this.selectedAttackEnergyCardIds = this.selectedAttackEnergyCardIds.filter(cardId => cardId !== id);
      return;
    }
    const nextCardIds = max === 1 ? [id] : [...this.selectedAttackCardIds, id].slice(0, max);
    this.selectedAttackCardIds = nextCardIds;
    this.selectedAttackEnergyCardIds = nextCardIds.filter(cardId => {
      const selectedCard = this.attackDeckCards.find(deckCard => deckCard.instanceId === cardId);
      return selectedCard ? this.isVisibleEnergyCard(selectedCard) : false;
    });
  }

  isAttackDeckCardSelected(card: VisibleCardInfo): boolean {
    return this.selectedAttackCardIds.includes(card.instanceId);
  }

  isAttackTopDeckOrderMode(attack: CardAttackResponse | null): boolean {
    return !!attack && this.attackNeedsTopDeckOrder(attack);
  }

  isAttackDeckViewOnlyMode(_attack: CardAttackResponse | null): boolean {
    return !!this.attackRequirementStep('VIEW_DECK_TOP');
  }

  attackDeckSectionLabel(attack: CardAttackResponse | null): string {
    if (this.isAttackDeckViewOnlyMode(attack)) return 'Revisá el mazo rival antes de confirmar:';
    if (this.isAttackTopDeckOrderMode(attack)) return 'Ordená las cartas superiores del mazo:';
    return 'Seleccioná una carta del mazo:';
  }

  attackDeckEmptyLabel(attack: CardAttackResponse | null): string {
    if (this.isAttackDeckViewOnlyMode(attack)) return 'No hay cartas visibles en el mazo rival para este ataque.';
    return 'No hay cartas válidas en el mazo para este ataque.';
  }

  attackDeckOrderPosition(card: VisibleCardInfo): number {
    return this.selectedAttackCardIds.indexOf(card.instanceId) + 1;
  }

  moveAttackDeckCard(card: VisibleCardInfo, direction: -1 | 1): void {
    if (!this.pendingTargetAttack || !this.attackNeedsTopDeckOrder(this.pendingTargetAttack)) return;
    const index = this.selectedAttackCardIds.indexOf(card.instanceId);
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= this.selectedAttackCardIds.length) return;
    const next = [...this.selectedAttackCardIds];
    [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
    this.selectedAttackCardIds = next;
  }

  attackNeedsHandSection(): boolean {
    return !!this.attackHandRequirementStep();
  }

  isAttackHandViewOnlyMode(): boolean {
    const step = this.attackHandRequirementStep();
    return step?.type === 'VIEW_HAND' || step?.type === 'CONFIRM_HAND_EFFECT';
  }

  isAttackHandSelectMode(): boolean {
    return this.attackHandRequirementStep()?.type === 'SELECT_HAND_CARD';
  }

  attackHandOptions(): VisibleCardInfo[] {
    return this.attackHandCards;
  }

  isAttackHandCardSelected(card: VisibleCardInfo): boolean {
    return this.selectedAttackHandCardIds.includes(card.instanceId);
  }

  toggleAttackHandCard(card: VisibleCardInfo): void {
    if (this.isAttackHandViewOnlyMode()) return;
    const id = card.instanceId;
    if (!id) return;
    if (this.selectedAttackHandCardIds.includes(id)) {
      this.selectedAttackHandCardIds = this.selectedAttackHandCardIds.filter(cid => cid !== id);
      return;
    }
    const step = this.attackHandRequirementStep();
    const max = step?.max ?? 1;
    this.selectedAttackHandCardIds = max === 1
      ? [id]
      : [...this.selectedAttackHandCardIds, id].slice(0, max);
  }

  attackHandSectionLabel(): string {
    const step = this.attackHandRequirementStep();
    if (step?.type === 'SELECT_HAND_CARD') return 'Seleccioná cartas de la mano rival:';
    if (step?.type === 'VIEW_HAND') return 'Revisá la mano rival antes de confirmar:';
    return 'Confirmá el efecto sobre la mano rival:';
  }

  attackHandEmptyLabel(): string {
    if (this.isAttackHandSelectMode()) return 'No hay cartas seleccionables en la mano rival.';
    return 'El rival no tiene cartas en la mano.';
  }

  private attackOpponentAttackRequirementStep(): AttackRequirementStep | null {
    return this.attackRequirementStep('SELECT_OPPONENT_ATTACK');
  }

  attackNeedsOpponentAttackSection(): boolean {
    return !!this.attackOpponentAttackRequirementStep();
  }

  attackOpponentAttackOptions(): CardAttackResponse[] {
    const step = this.attackOpponentAttackRequirementStep();
    const card = step?.cards?.[0];
    return card?.attacks ?? [];
  }

  selectAttackOpponentAttack(index: number): void {
    this.selectedAttackOpponentAttackIndex = index;
    this.attackTargetError = null;
  }

  isAttackOpponentAttackSelected(index: number): boolean {
    return this.selectedAttackOpponentAttackIndex === index;
  }

  private loadAttackHandCards(): void {
    if (!this.gameId || !this.opponentPlayerId) return;
    this.attackDeckLoading = true;
    this.gameService.getHand(this.gameId, this.opponentPlayerId).subscribe({
      next: (response) => {
        this.attackHandCards = (response.cardIds ?? []).map(id => ({
          cardId: '',
          setId: '',
          name: 'Carta',
          supertype: 'Pokémon',
          subtypes: [],
          hp: 0,
          attackName: null,
          attackText: null,
          attackDamage: null,
          attackRequiredEnergy: null,
          energyType: null,
          attackCost: [],
          retreatCost: 0,
          evolvesFrom: null,
          abilities: [],
          weaknessType: null,
          weaknessAmount: null,
          resistanceType: null,
          resistanceAmount: null,
          instanceId: id,
        } as VisibleCardInfo));
        this.attackDeckLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.attackTargetError = err.error?.message || 'No se pudo cargar la mano rival.';
        this.attackDeckLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private loadAttackDeckCards(): void {
    if (!this.gameId) return;
    const playerId = this.attackDeckRequirementStep()?.scope === 'OPPONENT_DECK_TOP'
      ? this.opponentPlayerId
      : this.myPlayerId;
    if (!playerId) return;
    this.attackDeckLoading = true;
    this.gameService.getDeck(this.gameId, playerId).subscribe({
      next: (response) => {
        const cards = response.cards ?? [];
        this.attackDeckCards = this.isAttackDeckViewOnlyMode(this.pendingTargetAttack)
          ? cards.slice(0, this.attackDeckSelectionLimit(this.pendingTargetAttack))
          : cards;
        if (this.pendingTargetAttack && this.attackNeedsTopDeckOrder(this.pendingTargetAttack)) {
          const topCards = this.attackDeckCards.slice(0, this.attackDeckSelectionLimit(this.pendingTargetAttack));
          this.selectedAttackCardIds = topCards.map(card => card.instanceId);
          this.selectedAttackEnergyCardIds = topCards
            .filter(card => this.isVisibleEnergyCard(card))
            .map(card => card.instanceId);
        }
        this.attackDeckLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.attackTargetError = err.error?.message || 'No se pudo cargar el mazo.';
        this.attackDeckLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private attackNeedsDeckSelection(): boolean {
    return !!this.attackDeckRequirementStep();
  }

  private attackDeckSelectionLimit(_attack: CardAttackResponse | null): number {
    const deckStep = this.attackDeckRequirementStep();
    if (deckStep) return deckStep.max || deckStep.count || 1;
    return 1;
  }

  private attackNeedsTopDeckOrder(_attack: CardAttackResponse): boolean {
    return !!this.attackRequirementStep('ORDER_DECK_TOP');
  }

  private isVisibleEnergyCard(card: VisibleCardInfo): boolean {
    return card.supertype?.toLowerCase() === 'energy' || !!card.energyType;
  }

  private visibleEnergyType(card: VisibleCardInfo): string | null {
    return card.energyType ?? (card.name?.replace(/\s+energy$/i, '') || null);
  }

  attackNeedsOwnTargetSection(_attack: CardAttackResponse | null): boolean {
    const scope = this.attackRequirementScope('SELECT_TARGET');
    return scope === 'OWN_BENCH' || scope === 'OWN_POKEMON' || scope === 'ANY_POKEMON';
  }

  attackNeedsOpponentTargetSection(_attack: CardAttackResponse | null): boolean {
    const scope = this.attackRequirementScope('SELECT_TARGET');
    return scope === 'OPPONENT_BENCH' || scope === 'OPPONENT_POKEMON' || scope === 'ANY_POKEMON';
  }

  attackAllowsOwnActiveTarget(_attack: CardAttackResponse | null): boolean {
    const scope = this.attackRequirementScope('SELECT_TARGET');
    return scope === 'OWN_POKEMON' || scope === 'ANY_POKEMON';
  }

  attackAllowsOpponentActiveTarget(_attack: CardAttackResponse | null): boolean {
    const scope = this.attackRequirementScope('SELECT_TARGET');
    return scope === 'OPPONENT_POKEMON' || scope === 'ANY_POKEMON';
  }

  openRetreatModal(): void {
    if (!this.canRetreat || !this.gameId) return;
    this.selectedBenchForRetreat = null;
    this.retreatError = null;
    this.retreatEnergySelection = this.playerActiveEnergies.map(e => ({ energy: e, selected: false }));
    this.showRetreatModal = true;
  }

  selectBenchForRetreat(index: number): void {
    this.selectedBenchForRetreat = index;
  }

  toggleEnergyForRetreat(index: number): void {
    this.retreatEnergySelection[index].selected = !this.retreatEnergySelection[index].selected;
  }

  confirmRetreat(): void {
    if (this.selectedBenchForRetreat === null || !this.gameId) return;
    if (!this.player.bench[this.selectedBenchForRetreat]) return;

    const energyCardIds = this.retreatEnergySelection
      .filter(e => e.selected)
      .map(e => e.energy.cardId);

    // Save card references for swap animation before state changes
    const oldActive = this.player.active;
    const promotedBench = this.player.bench[this.selectedBenchForRetreat];
    const slotIndex = this.selectedBenchForRetreat;

    // Save selected energies for discard animation
    const selectedEnergies = this.retreatEnergySelection
      .filter(e => e.selected)
      .map(e => e.energy.energyType);

    this.retreating = true;
    this.retreatError = null;
    this.stateVersion++;
    this.showRetreatModal = false;
    this.cdr.detectChanges();

    this.gameService.executeAction(this.gameId, {
      actionType: 'RETREAT',
      targetSlot: this.slotForBench(this.selectedBenchForRetreat),
      energyCardIds,
    }).subscribe({
      next: (state) => {
        this.stateVersion++;

        // Phase 1: energy discard animation
        if (selectedEnergies.length > 0) {
          this.retreatEnergyDiscardAnimating = true;
          this.retreatDiscardedEnergies = selectedEnergies.map((et, i) => ({
            energyType: et,
            id: ++this.retreatEnergyIdCounter,
          }));
          this.cdr.detectChanges();

          setTimeout(() => {
            this.retreatEnergyDiscardAnimating = false;
            this.retreatDiscardedEnergies = [];
            this.cdr.detectChanges();

            // Phase 2: swap animation
            this.retreatSwapAnimating = true;
            this.retreatActiveOut = oldActive;
            this.retreatBenchIn = promotedBench;
            this.retreatBenchSlot = slotIndex;
            this.cdr.detectChanges();

            setTimeout(() => {
              this.retreatSwapAnimating = false;
              this.retreatActiveOut = null;
              this.retreatBenchIn = null;
              this.retreatBenchSlot = -1;
              this.retreating = false;
              this.gameStateService.setState(state);
              this.scheduleRefresh();
              this.fetchPlayerHand();
              this.cdr.detectChanges();
            }, 600);
          }, 500);
        } else {
          // No energies to discard, just swap
          this.retreatSwapAnimating = true;
          this.retreatActiveOut = oldActive;
          this.retreatBenchIn = promotedBench;
          this.retreatBenchSlot = slotIndex;
          this.cdr.detectChanges();

          setTimeout(() => {
            this.retreatSwapAnimating = false;
            this.retreatActiveOut = null;
            this.retreatBenchIn = null;
            this.retreatBenchSlot = -1;
            this.retreating = false;
            this.gameStateService.setState(state);
            this.scheduleRefresh();
            this.fetchPlayerHand();
            this.cdr.detectChanges();
          }, 600);
        }
      },
      error: (err) => {
        this.retreatError = err.error?.message || 'Error al retirar Pokémon';
        this.retreating = false;
        this.refreshGameState();
      },
    });
  }

  closeRetreatModal(): void {
    if (this.retreating) return;
    this.showRetreatModal = false;
    this.selectedBenchForRetreat = null;
    this.retreatError = null;
  }

  // ── Ability methods ──────────────────────────────────────────────────────────

  enterAbilityMode(card: HandCard, slot: string, abilityIndex: number, abilityName: string): void {
    if (!this.canUseAbility) return;
    this.pendingAbilityCard = card;
    this.pendingAbilitySlot = slot;
    this.pendingAbilityIndex = abilityIndex;
    this.pendingAbilityName = abilityName;

    // Multi-step abilities that need a dedicated modal instead of a single board click.
    const normalizedName = abilityName?.toLowerCase() ?? '';
    if (normalizedName.includes('fairy transfer')) {
      this.abilityModalMode = 'fairy-transfer';
      this.openAbilityTargetModal();
      return;
    }
    if (normalizedName.includes('stance change')) {
      this.abilityModalMode = 'stance-change';
      this.openAbilityTargetModal();
      return;
    }

    this.abilityMode = true;
    this.actionError = null;
    this.cdr.detectChanges();
  }

  cancelAbilityMode(): void {
    this.resetAbilityMode();
  }

  // ── Ability target modal helpers ─────────────────────────────────────────────

  openAbilityTargetModal(): void {
    this.showAbilityTargetModal = true;
    this.resetAbilityTargetSelection();
    this.cdr.detectChanges();
  }

  closeAbilityTargetModal(): void {
    this.showAbilityTargetModal = false;
    this.resetAbilityTargetSelection();
    this.resetAbilityMode();
  }

  resetAbilityTargetSelection(): void {
    this.abilitySrcSlot = null;
    this.abilityDestSlot = null;
    this.selectedAbilityEnergyCardId = null;
    this.selectedAbilityHandCardId = null;
    this.abilityTargetError = null;
  }

  attachedEnergiesForSlot(slot: string, playerId: string): AttachedEnergy[] {
    const isMe = playerId === this.myPlayerId;
    if (slot === 'ACTIVE') {
      return isMe ? this.playerActiveEnergies : this.opponentActiveEnergies;
    }
    const benchIndex = parseInt(slot.split('_')[1], 10);
    if (isMe) {
      return this.playerBenchEnergies[benchIndex] || [];
    }
    return this.opponentBenchEnergies[benchIndex] || [];
  }

  fairyTransferSourceOptions(): { slot: string; label: string; energies: AttachedEnergy[]; name?: string }[] {
    const options: { slot: string; label: string; energies: AttachedEnergy[]; name?: string }[] = [];

    const activeEnergies = this.playerActiveEnergies.filter(e => e.energyType === 'Fairy');
    if (activeEnergies.length > 0 && this.player.active) {
      options.push({ slot: 'ACTIVE', label: 'ACTIVO', energies: activeEnergies, name: this.player.active.name });
    }

    for (let i = 0; i < 5; i++) {
      const benchEnergies = (this.playerBenchEnergies[i] || []).filter(e => e.energyType === 'Fairy');
      const poke = this.player.bench[i];
      if (benchEnergies.length > 0 && poke) {
        options.push({ slot: this.slotForBench(i), label: `BANCA ${i + 1}`, energies: benchEnergies, name: poke.name });
      }
    }

    return options;
  }

  fairyTransferDestOptions(): { slot: string; label: string; name?: string }[] {
    const options: { slot: string; label: string; name?: string }[] = [];

    if (this.abilitySrcSlot !== 'ACTIVE' && this.player.active) {
      options.push({ slot: 'ACTIVE', label: 'ACTIVO', name: this.player.active.name });
    }

    for (let i = 0; i < 5; i++) {
      const benchSlot = this.slotForBench(i);
      const poke = this.player.bench[i];
      if (benchSlot !== this.abilitySrcSlot && poke) {
        options.push({ slot: benchSlot, label: `BANCA ${i + 1}`, name: poke.name });
      }
    }

    return options;
  }

  getEnergiesForSourceSlot(): AttachedEnergy[] {
    if (!this.abilitySrcSlot) return [];
    const sourceOpt = this.fairyTransferSourceOptions().find(o => o.slot === this.abilitySrcSlot);
    return sourceOpt?.energies || [];
  }

  stanceChangeHandOptions(): HandCard[] {
    return this.player.hand.filter(card => card.name?.toLowerCase().includes('aegislash'));
  }

  confirmAbilityTarget(): void {
    if (!this.gameId) return;

    if (this.abilityModalMode === 'fairy-transfer') {
      if (!this.abilitySrcSlot) {
        this.abilityTargetError = 'Seleccioná un Pokémon fuente';
        return;
      }
      if (!this.selectedAbilityEnergyCardId) {
        this.abilityTargetError = 'Seleccioná una energía Fairy';
        return;
      }
      if (!this.abilityDestSlot) {
        this.abilityTargetError = 'Seleccioná un Pokémon destino';
        return;
      }

      const targetId = `${this.abilitySrcSlot}|${this.abilityDestSlot}|${this.selectedAbilityEnergyCardId}`;
      this.executeAbilityWithTarget(targetId);
    } else if (this.abilityModalMode === 'stance-change') {
      if (!this.selectedAbilityHandCardId) {
        this.abilityTargetError = 'Seleccioná un Aegislash de tu mano';
        return;
      }

      this.executeAbilityWithTarget(this.selectedAbilityHandCardId);
    }
  }

  executeAbilityWithTarget(targetId: string): void {
    if (!this.gameId) return;
    this.gameService.useAbility(this.gameId, {
      slotReference: this.pendingAbilitySlot,
      abilityIndex: this.pendingAbilityIndex,
      targetId,
    }).subscribe({
      next: (state) => {
        this.stateVersion++;
        this.closeAbilityTargetModal();
        this.gameStateService.setState(state);
        this.fetchPlayerHand();
        this.scheduleRefresh();
      },
      error: (err) => {
        this.abilityTargetError = err.error?.message || 'Error al usar habilidad';
      },
    });
  }

  selectAbilityTarget(targetSlot: string, targetPlayerId: string): void {
    if (!this.abilityMode || !this.gameId || !this.pendingAbilityCard) return;
    const targetId = `${targetSlot}:${targetPlayerId}`;
    this.abilityMode = false;

    // Save old hand to detect discarded cards after the ability resolves.
    const oldHand = [...this.player.hand];

    this.gameService.useAbility(this.gameId, {
      slotReference: this.pendingAbilitySlot,
      abilityIndex: this.pendingAbilityIndex,
      targetId,
    }).subscribe({
      next: (state) => {
        this.stateVersion++;
        this.resetAbilityMode();
        // Block fetchPlayerHand() from state$ subscriber during discard animation.
        this.isDrawingCard = true;
        this.gameStateService.setState(state);
        this.animateAbilityDiscard(oldHand);
        this.scheduleRefresh();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'Error al usar habilidad';
        this.resetAbilityMode();
        this.refreshGameState();
      },
    });
  }

  useAbilityWithoutTarget(): void {
    if (!this.abilityMode || !this.gameId || !this.pendingAbilityCard) return;
    this.abilityMode = false;

    // Save old hand instance IDs to detect new cards after the ability resolves.
    const oldHandInstanceIds = new Set(this.player.hand.map(c => c.instanceId));

    this.gameService.useAbility(this.gameId, {
      slotReference: this.pendingAbilitySlot,
      abilityIndex: this.pendingAbilityIndex,
    }).subscribe({
      next: (state) => {
        this.stateVersion++;
        this.resetAbilityMode();
        // Block fetchPlayerHand() from the state$ subscriber so the hand
        // stays frozen during the draw animation (see fetchPlayerHand guard).
        this.isDrawingCard = true;
        this.gameStateService.setState(state);
        // Animate new cards flying from deck into hand, one by one.
        this.animateAbilityHandDraw(oldHandInstanceIds);
        this.scheduleRefresh();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'Error al usar habilidad';
        this.resetAbilityMode();
        this.refreshGameState();
      },
    });
  }

  private resetAbilityMode(): void {
    this.abilityMode = false;
    this.pendingAbilityCard = null;
    this.pendingAbilitySlot = '';
    this.pendingAbilityIndex = -1;
    this.pendingAbilityName = '';
    this.cdr.detectChanges();
  }

  // ── Ability card-draw animation (Mystical Fire & similar) ─────────────────

  /**
   * After an ability resolves, detect which cards are new in the player's hand
   * and animate them flying from the deck, reusing the same draw-flying-card
   * template element used by animateDrawCard().  Cards appear one by one so the
   * player can see each individual card they received.
   */
  private animateAbilityHandDraw(oldHandInstanceIds: Set<string>): void {
    if (!this.gameId || !this.deck || !this.myPlayerId) {
      this.isDrawingCard = false;
      this.fetchPlayerHand();
      return;
    }

    const deckMap = new Map<string, DeckCardInput>();
    for (const card of this.deck.cards) {
      deckMap.set(card.cardId, card);
    }

    this.gameService.getHand(this.gameId, this.myPlayerId).subscribe({
      next: (handResponse) => {
        const fullHand = handResponse.cardIds.map((instanceId) =>
          this.buildHandCardFromInstanceId(instanceId, deckMap)
        );

        const newCards = fullHand.filter(c => !oldHandInstanceIds.has(c.instanceId));

        if (newCards.length === 0) {
          // No new cards — just update and release the lock.
          this.player.hand = fullHand;
          this.isDrawingCard = false;
          this.cdr.detectChanges();
          return;
        }

        // Animate the new cards one at a time.
        this.animateAbilityDrawSequence(fullHand, newCards, 0);
      },
      error: () => {
        this.isDrawingCard = false;
        this.fetchPlayerHand();
      },
    });
  }

  /**
   * Recursively animates one card at a time.  Timing is slightly faster than
   * the standard draw-card animation so multiple cards don't feel sluggish.
   */
  private animateAbilityDrawSequence(
    fullHand: HandCard[],
    newCards: HandCard[],
    index: number,
  ): void {
    if (index >= newCards.length) {
      // All cards placed — final state.
      this.player.hand = fullHand;
      this.isDrawingCard = false;
      this.cdr.detectChanges();
      return;
    }

    const newCard = newCards[index];

    // Hand without the cards that are still "in flight" (current + upcoming).
    const handWithoutDrawn = fullHand.filter(
      c => !newCards.slice(0, index + 1).some(nc => nc.instanceId === c.instanceId)
    );

    // Phase 1: show card-back flying from deck.
    this.drawnCardPreview = newCard;
    this.drawAnimating = true;
    this.drawCardBackOnly = true;
    this.slidingToHand = false;
    this.drawEnterIndex = -1;
    this.cdr.detectChanges();

    setTimeout(() => {
      // Phase 2: flip to face.
      this.drawCardBackOnly = false;
      this.cdr.detectChanges();
    }, 300);

    const finalIndex = fullHand.findIndex(c => c.instanceId === newCard.instanceId);

    setTimeout(() => {
      // Phase 3: slide toward hand position.
      this.player.hand = handWithoutDrawn;
      this.slidingToHand = true;
      this.cdr.detectChanges();
    }, 900);

    setTimeout(() => {
      // Phase 4: place card in hand with enter animation.
      // Only show cards that have been animated so far (index 0..index).
      // Cards still pending (index+1..end) must stay hidden so they can
      // animate from the deck on their own turn.
      const processedCards = fullHand.filter(
        c => !newCards.slice(index + 1).some(nc => nc.instanceId === c.instanceId)
      );
      this.player.hand = processedCards;
      this.drawEnterIndex = finalIndex;
      this.cdr.detectChanges();

      setTimeout(() => {
        // Cleanup this card's animation state.
        this.drawAnimating = false;
        this.drawnCardPreview = null;
        this.slidingToHand = false;
        this.drawEnterIndex = -1;
        this.cdr.detectChanges();

        // Proceed to next card after a short pause.
        setTimeout(() => {
          this.animateAbilityDrawSequence(fullHand, newCards, index + 1);
        }, 150);
      }, 350);
    }, 1200);
  }

  // ── Ability card-discard animation (Water Shuriken & similar) ─────────────

  /**
   * After a targeted ability resolves, detect which card was discarded from
   * hand by comparing old vs new hand, then animate it flying toward the
   * discard pile.  Generic — works for any ability that removes a card
   * from the player's hand (Water Shuriken, future discard abilities, etc.).
   */
  private animateAbilityDiscard(oldHand: HandCard[]): void {
    if (!this.gameId || !this.deck || !this.myPlayerId) {
      this.isDrawingCard = false;
      this.fetchPlayerHand();
      return;
    }

    const deckMap = new Map<string, DeckCardInput>();
    for (const card of this.deck.cards) {
      deckMap.set(card.cardId, card);
    }

    this.gameService.getHand(this.gameId, this.myPlayerId).subscribe({
      next: (handResponse) => {
        const newHand = handResponse.cardIds.map((instanceId) =>
          this.buildHandCardFromInstanceId(instanceId, deckMap)
        );

        // Card that was in the old hand but is NOT in the new hand = discarded.
        const discardedCard = oldHand.find(
          c => !newHand.some(nc => nc.instanceId === c.instanceId)
        );

        if (!discardedCard) {
          // Nothing discarded — just update and release.
          this.player.hand = newHand;
          this.isDrawingCard = false;
          this.cdr.detectChanges();
          return;
        }

        // Replace hand with new state so the visual hand shrinks immediately.
        this.player.hand = newHand;

        // Animate the discarded card flying toward the discard pile.
        this.handDiscardCard = discardedCard;
        this.handDiscardAnimating = true;
        this.cdr.detectChanges();

        setTimeout(() => {
          this.handDiscardAnimating = false;
          this.handDiscardCard = null;
          this.isDrawingCard = false;
          this.cdr.detectChanges();
        }, 900);
      },
      error: () => {
        this.isDrawingCard = false;
        this.fetchPlayerHand();
      },
    });
  }

  // ── Between-turns checkup animation (Pokémon Checkup) ────────────────────────

  private startBetweenTurnsCheckup(): void {
    if (!this.gameId) { this.scheduleRefresh(); return; }
    if (this.checkupRunning) return;

    const myId = this.myPlayerId;
    const oppId = this.opponentPlayerId;

    // The checkup applies to the active Pokémon of the player whose turn just ended.
    const endingPlayer = this.lastGameState?.currentTurnPlayer ?? null;
    let target: 'me' | 'opp' | null = null;
    if (endingPlayer && myId && endingPlayer === myId && this.playerStatus) target = 'me';
    else if (endingPlayer && oppId && endingPlayer === oppId && this.opponentStatus) target = 'opp';
    else if (this.playerStatus) target = 'me';
    else if (this.opponentStatus) target = 'opp';

    const beforeStatus = target === 'me' ? this.playerStatus
      : target === 'opp' ? this.opponentStatus : null;

    this.checkupRunning = true;
    this.gameService.getState(this.gameId).subscribe({
      next: (newState) => {
        if (!target || !beforeStatus) {
          this.finishCheckup();
          return;
        }
        this.runCheckupSequence(target, beforeStatus, newState);
      },
      error: () => this.finishCheckup(),
    });
  }

  private runCheckupSequence(
    target: 'me' | 'opp',
    condition: StatusCondition,
    newState: GameStateResponse,
  ): void {
    const targetId = (target === 'me' ? this.myPlayerId : this.opponentPlayerId) ?? '';
    const afterConditions = this.readStatusList(newState, targetId);
    const koDetected = newState.state === 'WAITING_REPLACEMENT' || newState.waitingReplacement === true;

    // Paso 0: banner "Checkup del Pokémon..." durante 1 segundo.
    this.showCheckupBanner = true;
    this.checkupBannerText = 'Checkup del Pokémon...';
    this.cdr.detectChanges();

    this.queueCheckup(1000, () => {
      this.showCheckupBanner = false;
      this.cdr.detectChanges();
      this.runConditionStep(target, condition, afterConditions, koDetected);
    });
  }

  private runConditionStep(
    target: 'me' | 'opp',
    condition: StatusCondition,
    afterConditions: StatusCondition[],
    koDetected: boolean,
  ): void {
    const stillHas = afterConditions.includes(condition);

    switch (condition) {
      case 'POISONED': {
        this.applyCheckupDamage(target, 1);
        if (this.handleCheckupKo(target, koDetected)) return;
        this.queueCheckup(1500, () => this.finishCheckup());
        break;
      }
      case 'BURNED': {
        this.applyCheckupDamage(target, 2);
        if (this.handleCheckupKo(target, koDetected)) return;
        const cured = !stillHas;
        this.runCheckupCoin(
          '¿Se cura el Quemado?',
          cured,
          cured ? '¡Se curó del Quemado!' : 'Sigue quemado...',
          () => {
            if (cured) this.setTargetStatus(target, null);
            this.queueCheckup(800, () => this.finishCheckup());
          },
        );
        break;
      }
      case 'ASLEEP': {
        const woke = !stillHas;
        this.runCheckupCoin(
          '¿Despierta del sueño?',
          woke,
          woke ? '¡Despertó!' : 'Sigue dormido...',
          () => {
            if (woke) this.setTargetStatus(target, null);
            this.queueCheckup(800, () => this.finishCheckup());
          },
        );
        break;
      }
      case 'PARALYZED': {
        // Se cura automáticamente, sin moneda: la badge se desvanece.
        this.setTargetStatus(target, null);
        this.queueCheckup(1000, () => this.finishCheckup());
        break;
      }
      default:
        this.finishCheckup();
    }
  }

  private applyCheckupDamage(target: 'me' | 'opp', counters: number): void {
    if (target === 'me') this.playerActiveDamage += counters;
    else this.opponentActiveDamage += counters;
    this.checkupDamageTarget = target;
    this.checkupDamageFloat = counters * 10;
    this.cdr.detectChanges();
    this.queueCheckup(1200, () => {
      this.checkupDamageFloat = null;
      this.checkupDamageTarget = null;
      this.cdr.detectChanges();
    });
  }

  private setTargetStatus(target: 'me' | 'opp', status: StatusCondition | null): void {
    if (target === 'me') this.playerStatus = status;
    else this.opponentStatus = status;
    this.cdr.detectChanges();
  }

  private handleCheckupKo(target: 'me' | 'opp', koDetected: boolean): boolean {
    if (!koDetected) return false;
    const active = target === 'me' ? this.player.active : this.opponent.active;
    const damage = target === 'me' ? this.playerActiveDamage : this.opponentActiveDamage;
    const maxHp = active?.hp ?? 0;
    // Solo interrumpe si este Pokémon es el que cae por el daño del checkup.
    if (maxHp > 0 && damage * 10 < maxHp) return false;
    if (active?.name) {
      const energies = target === 'me' ? this.previousActivePlayerEnergies : this.previousActiveOpponentEnergies;
      this.triggerKoNotification(active.name, active.image, target, energies);
    }
    this.queueCheckup(1200, () => this.finishCheckup());
    return true;
  }

  private runCheckupCoin(title: string, heads: boolean, outcome: string, done: () => void): void {
    this.checkupCoinTitle = title;
    this.checkupCoinOutcome = '';
    this.checkupCoinHeads = heads;
    this.checkupCoinRevealed = false;
    this.checkupCoinAnimating = true;
    this.showCheckupCoin = true;
    this.cdr.detectChanges();

    this.queueCheckup(1200, () => {
      this.checkupCoinAnimating = false;
      this.checkupCoinRevealed = true;
      this.checkupCoinOutcome = outcome;
      this.cdr.detectChanges();
      this.queueCheckup(1500, () => {
        this.showCheckupCoin = false;
        this.checkupCoinRevealed = false;
        this.cdr.detectChanges();
        done();
      });
    });
  }

  private finishCheckup(): void {
    this.clearCheckupTimers();
    this.showCheckupBanner = false;
    this.showCheckupCoin = false;
    this.checkupCoinAnimating = false;
    this.checkupCoinRevealed = false;
    this.checkupDamageFloat = null;
    this.checkupDamageTarget = null;
    this.checkupRunning = false;
    // Settle to the authoritative latest state (incluye el K.O./cambio de turno).
    this.refreshGameState();
    this.fetchActionLog();
    this.cdr.detectChanges();
  }

  private queueCheckup(delay: number, fn: () => void): void {
    const t = setTimeout(() => {
      this.checkupTimers = this.checkupTimers.filter(x => x !== t);
      fn();
    }, delay);
    this.checkupTimers.push(t);
  }

  private clearCheckupTimers(): void {
    this.checkupTimers.forEach(t => clearTimeout(t));
    this.checkupTimers = [];
  }

  private readStatusList(state: GameStateResponse, playerId: string): StatusCondition[] {
    const raw: unknown = state.statusByPlayer?.[playerId];
    if (!raw) return [];
    if (Array.isArray(raw)) return raw as StatusCondition[];
    if (typeof raw === 'object') return Object.keys(raw as object) as StatusCondition[];
    return [raw as StatusCondition];
  }

  private triggerDamageAnimation(damage: number, target: 'me' | 'opp' = 'opp'): void {
    this.damageAnimationValue = damage;
    this.damageAnimationTarget = target;
    this.screenShake = true;
    this.screenFlash = true;

    if (this.damageAnimationTimer) clearTimeout(this.damageAnimationTimer);
    if (this.shakeTimer) clearTimeout(this.shakeTimer);
    if (this.flashTimer) clearTimeout(this.flashTimer);

    this.cdr.detectChanges();

    this.flashTimer = setTimeout(() => {
      this.screenFlash = false;
      this.flashTimer = null;
      this.cdr.detectChanges();
    }, 120);

    this.shakeTimer = setTimeout(() => {
      this.screenShake = false;
      this.shakeTimer = null;
      this.cdr.detectChanges();
    }, 500);

    this.damageAnimationTimer = setTimeout(() => {
      this.damageAnimationValue = null;
      this.damageAnimationTarget = null;
      this.damageAnimationTimer = null;
      this.cdr.detectChanges();
    }, 1500);
  }

  private scheduleAttackDamageAnimation(result: AttackResolvedPayload): void {
    const damage = Number(result.damage ?? 0);
    if (result.cancelled || damage <= 0) return;
    if (this.attackDamageAnimationTimer) clearTimeout(this.attackDamageAnimationTimer);

    const delay = this.attackDamageAnimationDelay(result);
    if (delay <= 0) {
      this.attackDamageAnimationTimer = null;
      this.triggerDamageAnimation(damage, this.attackDamageTarget(result));
      return;
    }

    this.attackDamageAnimationTimer = setTimeout(() => {
      this.attackDamageAnimationTimer = null;
      this.triggerDamageAnimation(damage, this.attackDamageTarget(result));
    }, delay);
  }

  private attackDamageTarget(result: AttackResolvedPayload): 'me' | 'opp' {
    return this.isCurrentPlayer(result.defender) ? 'me' : 'opp';
  }

  private attackDamageAnimationDelay(result: AttackResolvedPayload): number {
    return (result.coinFlipResults?.length ?? 0) > 0 ? 2600 : 0;
  }

  private triggerAttackActionAnimations(result: AttackResolvedPayload): void {
    const effects = this.buildAttackActionAnimations(result);
    if (effects.length === 0) return;

    if (this.attackActionAnimationTimer) clearTimeout(this.attackActionAnimationTimer);
    this.attackActionAnimations = effects;
    this.cdr.detectChanges();

    this.attackActionAnimationTimer = setTimeout(() => {
      this.attackActionAnimations = [];
      this.attackActionAnimationTimer = null;
      this.cdr.detectChanges();
    }, 1800 + effects.length * 120);
  }

  private scheduleAttackTurnResolution(result: AttackResolvedPayload): void {
    if (!this.gameId || !this.isCurrentPlayer(result.attacker)) return;
    if (this.attackTurnResolveTimer) clearTimeout(this.attackTurnResolveTimer);

    this.attackTurnResolveTimer = setTimeout(() => {
      this.attackTurnResolveTimer = null;
      if (!this.gameId) return;

      this.gameService.getState(this.gameId).subscribe({
        next: (state) => {
          if (state.state !== 'ACTIVE' || state.phase !== 'BETWEEN_TURNS') return;
          if (state.currentTurnPlayer !== result.attacker) return;

          this.gameService.executeAction(this.gameId!, { actionType: 'RESOLVE_BETWEEN_TURNS' }).subscribe({
            next: (resolvedState) => {
              this.stateVersion++;
              this.gameStateService.setState(resolvedState);
              this.fetchActionLog();
            },
            error: () => {
              this.scheduleRefresh();
            },
          });
        },
        error: () => this.scheduleRefresh(),
      });
    }, this.attackTurnResolutionDelay(result));
  }

  private attackTurnResolutionDelay(result: AttackResolvedPayload): number {
    const effectCount = this.attackActionAnimationCount(result);
    return Math.max(2300, 1800 + effectCount * 180, this.attackDamageAnimationDelay(result) + 1700);
  }

  private attackActionAnimationCount(result: AttackResolvedPayload): number {
    return (result.auditTrail ?? []).filter(entry =>
      entry.startsWith('AttackEffect:draw_cards=')
      || entry.startsWith('AttackEffect:deck_to_hand=')
      || entry.startsWith('AttackEffect:discard_top_card=')
      || entry.startsWith('AttackEffect:discard_to_hand=')
      || entry.startsWith('AttackEffect:discard_to_deck=')
      || entry.startsWith('AttackEffect:deck_top_reordered=')
      || entry.startsWith('AttackEffect:energy_discarded=')
      || entry.startsWith('AttackEffect:energy_attached=')
      || entry.startsWith('AttackEffect:energy_moved=')
      || entry.startsWith('AttackEffect:switch_active_bench=')
    ).length;
  }

  private buildAttackActionAnimations(result: AttackResolvedPayload): AttackActionAnimation[] {
    const effects: AttackActionAnimation[] = [];
    for (const entry of result.auditTrail ?? []) {
      const count = this.auditCount(entry);
      if (count <= 0) continue;

      const targetRole = this.auditTargetRole(entry);
      const targetLabel = this.attackTargetLabel(targetRole, result);
      if (entry.startsWith('AttackEffect:draw_cards=')) {
        effects.push(this.attackActionEffect('draw', `${targetLabel} robó ${this.cardCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:deck_to_hand=')) {
        effects.push(this.attackActionEffect('search', `${targetLabel} buscó ${this.cardCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:discard_top_card=')) {
        effects.push(this.attackActionEffect('discard', `${targetLabel} descartó del mazo`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:discard_to_hand=')) {
        effects.push(this.attackActionEffect('draw', `${targetLabel} recuperó ${this.cardCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:discard_to_deck=')) {
        effects.push(this.attackActionEffect('search', `${targetLabel} devolvió carta al mazo`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:deck_top_reordered=')) {
        effects.push(this.attackActionEffect('search', `${targetLabel} ordenó ${this.cardCountLabel(count)} del mazo`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:energy_discarded=')) {
        effects.push(this.attackActionEffect('energy-discard', `${targetLabel} descartó ${this.energyCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:energy_attached=')) {
        effects.push(this.attackActionEffect('energy-attach', `${targetLabel} adjuntó ${this.energyCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:energy_moved=')) {
        effects.push(this.attackActionEffect('energy-move', `${targetLabel} movió ${this.energyCountLabel(count)}`, count, targetRole));
      } else if (entry.startsWith('AttackEffect:switch_active_bench=')) {
        effects.push(this.attackActionEffect('switch', `${targetLabel} cambió Activo y Banca`, 1, targetRole));
      }
    }
    return effects.slice(0, 4);
  }

  private attackActionEffect(type: AttackActionAnimationType, label: string, count: number, targetRole: AttackActionAnimation['targetRole']): AttackActionAnimation {
    return {
      id: ++this.attackActionAnimationId,
      type,
      label,
      count,
      copies: Array.from({ length: Math.min(Math.max(count, 1), 3) }, (_, i) => i),
      targetRole,
    };
  }

  private triggerTrainerActionAnimations(payload: TrainerPlayedPayload): void {
    const effects = this.buildTrainerActionAnimations(payload);
    if (effects.length === 0) return;

    if (this.attackActionAnimationTimer) clearTimeout(this.attackActionAnimationTimer);
    this.attackActionAnimations = effects;
    this.cdr.detectChanges();

    this.attackActionAnimationTimer = setTimeout(() => {
      this.attackActionAnimations = [];
      this.attackActionAnimationTimer = null;
      this.cdr.detectChanges();
    }, 1800 + effects.length * 120);
  }

  private buildTrainerActionAnimations(payload: TrainerPlayedPayload): AttackActionAnimation[] {
    const result = payload.result ?? '';
    const effect = payload.effect;
    const effects: AttackActionAnimation[] = [];
    const actorLabel = this.trainerActorLabel(payload.playerId);
    const affectedEnergyOwnerLabel = this.isCurrentPlayer(payload.playerId) ? 'del rival' : 'tuya';
    const cardsDrawn = this.effectNumber(effect, 'cardsDrawn');
    const cardsFound = this.effectNumber(effect, 'cardsFound') || this.effectListCount(effect, 'cardIds') || this.effectListCount(effect, 'revealedCards');

    if (result === 'HAND_SHUFFLED_AND_DRAWN') {
      effects.push(this.attackActionEffect('discard', `${actorLabel} barajó su mano`, 1, 'unknown'));
      if (cardsDrawn > 0) effects.push(this.attackActionEffect('draw', `${actorLabel} robó ${this.cardCountLabel(cardsDrawn)}`, cardsDrawn, 'unknown'));
    } else if (result === 'HAND_DISCARDED_AND_DRAWN') {
      effects.push(this.attackActionEffect('discard', `${actorLabel} descartó su mano`, 3, 'unknown'));
      if (cardsDrawn > 0) effects.push(this.attackActionEffect('draw', `${actorLabel} robó ${this.cardCountLabel(cardsDrawn)}`, cardsDrawn, 'unknown'));
    } else if (result === 'OPPONENT_HAND_SHUFFLED_AND_DRAWN') {
      effects.push(this.attackActionEffect('discard', this.opponentHandShuffleLabel(payload.playerId), 1, 'unknown'));
      if (cardsDrawn > 0) effects.push(this.attackActionEffect('draw', this.opponentHandDrawLabel(payload.playerId, cardsDrawn), cardsDrawn, 'unknown'));
    } else if (result === 'COIN_FLIPPED') {
      if (cardsDrawn > 0) effects.push(this.attackActionEffect('draw', `${actorLabel} robó ${this.cardCountLabel(cardsDrawn)}`, cardsDrawn, 'unknown'));
    } else if (result === 'BASIC_ENERGY_SEARCHED') {
      if (cardsFound > 0) effects.push(this.attackActionEffect('search', `${actorLabel} buscó ${this.cardCountLabel(cardsFound)}`, cardsFound, 'unknown'));
    } else if (result === 'POKEMON_FOUND') {
      effects.push(this.attackActionEffect('search', `${actorLabel} puso un Pokémon en su mano`, Math.max(cardsFound, 1), 'unknown'));
    } else if (result === 'NO_POKEMON_FOUND' || result === 'NO_EVOLUTION_FOUND') {
      effects.push(this.attackActionEffect('search', `${actorLabel} barajó el mazo`, 1, 'unknown'));
    } else if (result === 'POKEMON_TO_TOP_OF_DECK') {
      effects.push(this.attackActionEffect('search', `${actorLabel} puso un Pokémon sobre el mazo`, 1, 'unknown'));
    } else if (result === 'POKEMON_EVOLVED_FROM_DECK') {
      effects.push(this.attackActionEffect('search', `${actorLabel} evolucionó desde el mazo`, 1, 'unknown'));
    } else if (result === 'POKEMON_RETURNED_TO_DECK') {
      effects.push(this.attackActionEffect('discard', `${actorLabel} devolvió un Pokémon al mazo`, 1, 'unknown'));
    } else if (result === 'DAMAGE_HEALED_AND_ENERGY_DISCARDED') {
      effects.push(this.attackActionEffect('energy-discard', `${actorLabel} descartó energía`, 1, 'unknown'));
    } else if (result === 'OPPONENT_ENERGY_DISCARDED') {
      effects.push(this.attackActionEffect('energy-discard', `${actorLabel} descartó energía ${affectedEnergyOwnerLabel}`, 1, 'unknown'));
    } else if (result === 'ACTIVE_SWITCHED' || result === 'OPPONENT_ACTIVE_SWITCHED' || result === 'ESCAPE_ROPE_RESOLVED') {
      effects.push(this.attackActionEffect('switch', `${actorLabel} cambió Activo y Banca`, 1, 'unknown'));
    } else if (result === 'DAMAGE_HEALED') {
      effects.push(this.attackActionEffect('energy-move', `${actorLabel} curó 30 de daño`, 1, 'unknown'));
    } else if (result === 'DAMAGE_HEALED_60') {
      effects.push(this.attackActionEffect('energy-move', `${actorLabel} curó 60 de daño`, 1, 'unknown'));
    } else if (result === 'TOOL_EQUIPPED') {
      effects.push(this.attackActionEffect('search', `${actorLabel} equipó una Herramienta`, 1, 'unknown'));
    } else if (result === 'STADIUM_ACTIVE') {
      effects.push(this.attackActionEffect('search', `${actorLabel} jugó un Estadio`, 1, 'unknown'));
    }

    return effects.slice(0, 4);
  }

  private trainerActorLabel(playerId: string): string {
    return this.isCurrentPlayer(playerId) ? 'Tu entrenador' : 'El rival';
  }

  private opponentHandShuffleLabel(playerId: string): string {
    return this.isCurrentPlayer(playerId) ? 'El rival barajó su mano' : 'Barajaste tu mano';
  }

  private opponentHandDrawLabel(playerId: string, count: number): string {
    return this.isCurrentPlayer(playerId) ? `El rival robó ${this.cardCountLabel(count)}` : `Robaste ${this.cardCountLabel(count)}`;
  }

  private effectNumber(effect: TrainerPlayedPayload['effect'] | undefined, key: string): number {
    const value = effect?.[key];
    if (typeof value === 'number') return value;
    if (typeof value === 'string') {
      const parsed = parseInt(value, 10);
      return Number.isNaN(parsed) ? 0 : parsed;
    }
    return 0;
  }

  private effectString(effect: TrainerPlayedPayload['effect'] | undefined, key: string): string | null {
    const value = effect?.[key];
    return typeof value === 'string' ? value : null;
  }

  private effectListCount(effect: TrainerPlayedPayload['effect'] | undefined, key: string): number {
    const value = effect?.[key];
    return Array.isArray(value) ? value.length : 0;
  }

  private handleTrainerPlayedLocally(card: HandCard, request: PlayTrainerRequest, effectResult: string, effectPayload: Record<string, unknown> | null): void {
    const playerId = this.myPlayerId;
    if (!playerId) return;

    const payload: TrainerPlayedPayload = {
      playerId,
      cardId: card.cardId,
      cardInstanceId: card.instanceId,
      cardName: card.name,
      targetSlot: request.targetSlot ?? undefined,
      targetId: request.targetId ?? undefined,
      result: effectResult,
      effect: effectPayload as unknown as TrainerPlayedPayload['effect'] ?? undefined,
    };

    const key = `${card.instanceId}:${effectResult}`;
    this.lastAnimatedTrainerKey = key;
    setTimeout(() => {
      if (this.lastAnimatedTrainerKey === key) this.lastAnimatedTrainerKey = null;
    }, 5000);

    this.showCardRevealNotification(
      playerId,
      (payload.effect as TrainerEffectPayload)?.revealedCards ?? this.revealedCardsFromIds((payload.effect as TrainerEffectPayload)?.cardIds),
    );
    this.triggerTrainerActionAnimations(payload);
  }

  private animateTrainerFromLog(card: HandCard, request: PlayTrainerRequest): void {
    if (!this.gameId) return;
    this.gameService.getLogs(this.gameId).subscribe({
      next: (res) => {
        this.actionLog = (res.entries ?? []).slice(-30).reverse();
        const entry = this.actionLog.find(
          e => e.action === 'PLAY_TRAINER' && e.playerId === this.myPlayerId,
        );
        if (entry) {
          this.handleTrainerPlayedLocally(
            card,
            request,
            entry.result,
            (entry.payload?.['effectPayload'] as Record<string, unknown>) ?? null,
          );
        }
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  private auditCount(entry: string): number {
    const match = entry.match(/=(\d+)/);
    return match ? parseInt(match[1], 10) : 1;
  }

  private auditTargetRole(entry: string): AttackActionAnimation['targetRole'] {
    if (entry.includes('target=attacker')) return 'attacker';
    if (entry.includes('target=defender')) return 'defender';
    return 'unknown';
  }

  private attackTargetLabel(role: AttackActionAnimation['targetRole'], result: AttackResolvedPayload): string {
    if (role === 'attacker') return this.isCurrentPlayer(result.attacker) ? 'Tu Pokémon' : 'El rival';
    if (role === 'defender') return this.isCurrentPlayer(result.defender) ? 'Tu Pokémon' : 'El rival';
    return 'El efecto';
  }

  private cardCountLabel(count: number): string {
    return `${count} carta${count === 1 ? '' : 's'}`;
  }

  private energyCountLabel(count: number): string {
    return `${count} energía${count === 1 ? '' : 's'}`;
  }

  private triggerKoNotification(name: string, image: string, side: 'me' | 'opp', energies: AttachedEnergy[]): void {
    this.koNotification = `${name} eliminado`;
    // Start discard animation
    this.koDiscardAnimating = true;
    this.koDiscardImage = image;
    this.koDiscardName = name;
    this.koDiscardSide = side;
    this.koDiscardEnergies = energies.map((e, i) => ({ energyType: e.energyType, id: i }));
    if (this.koNotificationTimer) {
      clearTimeout(this.koNotificationTimer);
    }
    this.koNotificationTimer = setTimeout(() => {
      this.koNotification = null;
      this.koNotificationTimer = null;
      this.koDiscardAnimating = false;
      this.cdr.detectChanges();
    }, 1200);
    this.cdr.detectChanges();
  }

  // Muestra un toast de premios tomados. delta es cuanto bajo el conteo de premios:
  // 1 = Pokemon normal, 2 = Pokemon-EX. Otros valores (reset de setup/muerte subita)
  // se ignoran para no mostrar toasts falsos.
  private showPrizeTakenToast(delta: number): void {
    if (delta !== 1 && delta !== 2) return;
    const label = delta === 2 ? '+2 premios (EX)' : '+1 premio';
    this.toastService.show(label, 'info', 1200);
  }

  private animatePrizeCards(count: number): void {
    this.prizeAnimating = true;
    this.prizeAnimatingCount = count;
    this.prizeSliding = false;
    this.isDrawingCard = true;
    this.scheduleRefresh();
    this.cdr.detectChanges();

    if (this.prizeAnimTimer) clearTimeout(this.prizeAnimTimer);
    if (this.prizeAnimTimer2) clearTimeout(this.prizeAnimTimer2);

    this.prizeAnimTimer = setTimeout(() => {
      this.prizeSliding = true;
      this.cdr.detectChanges();
    }, 1400);

    this.prizeAnimTimer2 = setTimeout(() => {
      this.prizeAnimating = false;
      this.prizeSliding = false;
      this.prizeAnimatingCount = 0;
      this.isDrawingCard = false;
      this.fetchPlayerHand();
      this.cdr.detectChanges();
    }, 1900);
  }

  private removeFromHand(instanceId: string): void {
    this.player.hand = this.player.hand.filter(c => c.instanceId !== instanceId);
    this.cdr.detectChanges();
  }

  private restoreToHand(card: HandCard): void {
    if (!this.player.hand.some(c => c.instanceId === card.instanceId)) {
      this.player.hand.push(card);
    }
    this.cdr.detectChanges();
  }

  // ── Board actions ───────────────────────────────────────────────────────────

  setAsActive(card: HandCard): void {
    if (this.isOnBench(card)) return;
    this.player.active = card;
  }

  addToBench(card: HandCard): void {
    if (this.player.active === card) return;
    if (this.isOnBench(card)) return;
    const emptyIndex = this.player.bench.findIndex(slot => slot === null);
    if (emptyIndex === -1) return;
    this.player.bench[emptyIndex] = card;
  }

  addToBenchAtIndex(card: HandCard, index: number): void {
    if (this.player.active === card) return;
    if (this.isOnBench(card)) return;
    if (this.player.bench[index] !== null) {
      this.addToBench(card);
      return;
    }
    this.player.bench[index] = card;
  }

  isOnBench(card: HandCard): boolean {
    return this.player.bench.some(slot => slot === card);
  }

  // ── Utilities ───────────────────────────────────────────────────────────────

  private initializePlayerHand(deck: DeckResponse): void {
    const allCards: DeckCardInput[] = [];
    for (const card of deck.cards) {
      for (let i = 0; i < card.quantity; i++) {
        allCards.push({ ...card });
      }
    }

    let hand: DeckCardInput[];
    let deckCards: DeckCardInput[];

    do {
      const shuffled = this.shuffleArray(allCards);
      hand = shuffled.slice(0, 7);
      deckCards = shuffled.slice(7);
      if (hand.some(c => c.basicPokemon)) {
        break;
      }
    } while (true);

    // En modo local cada copia necesita un identificador único para poder
    // moverse al tablero, evolucionarse, etc. (online lo provee el backend).
    this.player.hand = hand.map(c => ({
      ...this.convertToHandCard(c),
      instanceId: `sandbox:${c.cardId}:${this.sandboxInstanceSeq++}`,
    }));

    const prizeCardsRaw = deckCards.slice(0, 6);
    this.player.prizes = prizeCardsRaw.map(c => ({ ...this.convertToHandCard(c), instanceId: '' }));

    const remaining = deckCards.slice(6);
    this.player.deckCount = remaining.length;

    const dealtCards = [...hand, ...prizeCardsRaw];
    const dealtCount = new Map<string, number>();
    for (const card of dealtCards) {
      dealtCount.set(card.cardId, (dealtCount.get(card.cardId) || 0) + 1);
    }

    this.deck = {
      ...deck,
      cards: deck.cards.map(c => ({
        ...c,
        quantity: c.quantity - (dealtCount.get(c.cardId) || 0),
      })),
    };
  }

  private shuffleArray<T>(array: T[]): T[] {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
  }

  private convertToHandCard(card: DeckCardInput): HandCard {
    return {
      ...card,
      image: this.resolveCardImage(card),
      instanceId: '',
    };
  }

  private resolveCardImage(card: DeckCardInput): string {
    if (!card.setId || !card.cardId) {
      return '/images/cardBack.png';
    }
    const prefix = `${card.setId}-`;
    const cardRef = card.cardId.startsWith(prefix)
      ? card.cardId.slice(prefix.length)
      : '';
    if (!cardRef) {
      return '/images/cardBack.png';
    }
    return `https://images.pokemontcg.io/${card.setId}/${cardRef}.png`;
  }

  // ── State helpers ──────────────────────────────────────────────────────────

  currentHp(maxHp: number | null, damageCounters: number): number {
    return Math.max(0, (maxHp ?? 0) - damageCounters * 10);
  }

  hpBarPercent(maxHp: number | null, damageCounters: number): number {
    const hp = maxHp ?? 0;
    if (hp <= 0) return 0;
    const current = this.currentHp(hp, damageCounters);
    return Math.max(0, Math.min(100, (current / hp) * 100));
  }

  getBenchDamage(playerId: string | null, slot: string): number {
    if (!playerId) return 0;
    return this.damageBySlot[playerId]?.[slot] ?? 0;
  }

  get canBenchInMain(): boolean {
    if (this.sandboxMode) return !this.benchingPokemon;
    return this.allowedActions.canBenchInMain
      && this.gameId !== null
      && !this.benchingPokemon;
  }

  benchPokemonInMain(cardInstanceId: string): void {
    if (this.sandboxMode) {
      this.benchPokemonSandbox(cardInstanceId);
      return;
    }
    if (!this.canBenchInMain || !this.gameId) return;
    this.benchingPokemon = true;
    this.actionError = null;
    this.stateVersion++;
    this.gameService.benchPokemon(this.gameId, { cardInstanceId }).subscribe({
      next: (state) => {
        this.benchingPokemon = false;
        this.stateVersion++;
        this.gameStateService.setState(state);
        this.fetchPlayerHand();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'No se pudo colocar el Pokémon en la banca';
        this.benchingPokemon = false;
        this.refreshGameState();
      },
    });
  }

  // ── Evolution ─────────────────────────────────────────────────────────────

  get canEvolve(): boolean {
    if (this.sandboxMode) return !this.evolving;
    return this.allowedActions.canEvolve
      && this.gameId !== null
      && !this.evolving;
  }

  isEvolutionCard(card: HandCard): boolean {
    return !card.basicPokemon
      && card.type !== 'Energy'
      && !!card.evolvesFrom;
  }

  private handleEvolutionDrop(card: HandCard, targetSlot: string): void {
    if (!this.canEvolve || !this.gameId || !card.instanceId) return;

    const targetBoardCard = targetSlot === 'ACTIVE'
      ? this.player.active
      : this.player.bench[parseInt(targetSlot.split('_')[1], 10)];

    if (!targetBoardCard) {
      this.actionError = 'No hay Pokémon en esa posición para evolucionar.';
      this.cdr.detectChanges();
      return;
    }

    if (!targetBoardCard.instanceId) {
      this.actionError = 'Error interno: falta el instanceId del Pokémon en el tablero. Actualizá la página.';
      this.cdr.detectChanges();
      return;
    }

    const evolvesFrom = card.evolvesFrom;
    if (!evolvesFrom || targetBoardCard.name.toLowerCase() !== evolvesFrom.toLowerCase()) {
      this.actionError = `${card.name} no evoluciona de ${targetBoardCard.name}.`;
      this.cdr.detectChanges();
      return;
    }

    this.removeFromHand(card.instanceId);
    this.evolving = true;
    this.actionError = null;
    this.stateVersion++;

    this.gameService.evolvePokemon(this.gameId, {
      fromCardInstanceId: targetBoardCard.instanceId,
      toCardInstanceId: card.instanceId,
      targetSlot,
    }).subscribe({
      next: (state) => {
        this.evolving = false;
        this.stateVersion++;
        this.gameStateService.setState(state);
        this.fetchPlayerHand();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'Error al evolucionar el Pokémon';
        this.evolving = false;
        this.restoreToHand(card);
        this.refreshGameState();
      },
    });
  }

  onHandCardClick(card: HandCard): void {
    if (card.basicPokemon && this.canBenchInMain) {
      this.benchPokemonInMain(card.instanceId);
    } else if (this.isTrainerCard(card) && this.canPlayTrainer) {
      this.openTrainerModal(card);
    } else {
      this.toggleCardPreview(card);
    }
  }

  private isTrainerCard(card: HandCard): boolean {
    return card.type === 'Trainer';
  }

  get canPlayTrainer(): boolean {
    return this.allowedActions.canPlayTrainer
      && this.gameId !== null
      && !this.trainerPlaying;
  }

  openTrainerModal(card: HandCard): void {
    this.selectedTrainerCard = card;
    this.trainerTargetSlot = null;
    this.trainerTargetId = null;
    this.selectedTrainerEnergyCardIds = [];
    this.selectedTrainerCardIds = [];
    this.trainerError = null;
    this.deckPreview = [];
    this.deckPreviewLoading = false;
    this.showTrainerModal = true;

    // If the trainer requires selecting from top-of-deck, fetch preview
    if (this.needsDeckPreview(card) && this.gameId && this.myPlayerId) {
      this.fetchDeckPreview();
    }
  }

  // Determine if this trainer should show a TOP-OF-DECK preview (reveal/look at the top N)
  needsDeckPreview(card: HandCard | null): boolean {
    if (!card) return false;
    const rules = (card.rules ?? []).join(' ').toLowerCase();

    // Look for explicit phrasing that means "look/reveal the top"
    if (/look at the top|reveal the top|look at the top 7|reveal the top 7|look at the top 3|reveal the top 3/.test(rules)) return true;

    // Fallback by name for well-known cards
    const name = card.name.toLowerCase().normalize('NFC');
    if (name.includes('great ball') || name.includes('poke ball') || name.includes('pokeball')) return true;
    if (name.includes('ball') && (name.includes('poke') || name.includes('great') || name.includes('ultra'))) return true;

    return false;
  }

  // Determine if this trainer requires SEARCH YOUR DECK (e.g., Evosoda: search your deck for an evolution card)
  needsDeckSearch(card: HandCard | null): boolean {
    if (!card) return false;
    const rules = (card.rules ?? []).join(' ').toLowerCase();
    if (/search your deck|search your deck for|search the deck for/.test(rules)) return true;

    // Heuristic: Evosoda historically is a deck-search/evolution card
    const name = card.name.toLowerCase();
    if (name.includes('evosoda') || name.includes('evo soda') || name.includes('evo-soda')) return true;

    return false;
  }

  // Deck-search trainers that need a Pokémon target (e.g., Evosoda searches for an evolution to put on a specific Pokémon)
  // Unlike generic deck-search (Professor's Letter, Energy Search) which just put cards in hand.
  needsDeckSearchWithTarget(card: HandCard | null): boolean {
    if (!card) return false;
    const name = card.name.toLowerCase();
    return name.includes('evosoda') || name.includes('evo soda') || name.includes('evo-soda');
  }

  // Whether the "MEZCLAR SIN ELEGIR" option should be shown for this trainer card.
  needsShuffleOption(card: HandCard | null): boolean {
    if (!card) return false;

    // Never show shuffle-only for Tool cards (they attach to a Pokémon)
    if (this.isToolCard(card)) return false;

    // Never show shuffle-only for deck-search flows (search your deck should require selection)
    if (this.needsDeckSearch(card)) return false;

    const name = card.name.toLowerCase();

    // Explicit deck-preview based trainers allow shuffle-only only if rules mention shuffling
    if (this.needsDeckPreview(card)) {
      const rules = (card.rules ?? []).join(' ').toLowerCase();
      if (/shuffle|then shuffle|shuffle your deck/.test(rules)) return true;
      // fallback: Great Ball / Poké Ball normally shuffle after revealing
      if (name.includes('great ball') || name.includes('poke ball') || name.includes('pokeball')) return true;
    }

    // If card rules mention shuffle explicitly, allow
    const rules = (card.rules ?? []).join(' ').toLowerCase();
    if (/shuffle|shuffle your deck/.test(rules)) return true;

    return false;
  }

  // Whether the modal should render a dedicated "JUGAR" confirm button (instead of relying on tapping deck preview)
  shouldShowPlayButton(card: HandCard | null): boolean {
    if (!card) return false;
    // If the trainer uses the deck preview flow (Great Ball), selection is immediate on tap -> no Play button
    if (this.needsDeckPreview(card)) return false;

    // Show Play for tools, discard-selection trainers, energy-selection trainers, opponent-targeting trainers, own-targeting trainers, deck-search flows,
    // and general Trainer subtypes that require explicit confirmation (Supporter, Stadium, Item)
    return this.isToolCard(card)
      || this.needsTrainerDiscardSelection(card)
      || this.needsTrainerEnergySelection(card)
      || this.needsDeckEnergySelection(card)
      || this.needsOpponentPokemonTarget(card)
      || this.needsOwnPokemonTrainerTarget(card)
      || this.needsDeckSearch(card)
      || this.isSupporterCard(card)
      || this.isStadiumCard(card)
      || this.isItemCard(card);
  }

  // Whether the Play button should be enabled given current selections
  isPlayButtonEnabled(): boolean {
    if (!this.selectedTrainerCard) return false;
    if (!this.canConfirmTrainer) return false;

    // If trainer requires selecting an own-board Pokémon, ensure a slot is selected
    if (this.needsOwnPokemonTrainerTarget(this.selectedTrainerCard) && !this.trainerTargetSlot) return false;

    // If trainer requires selecting from discard, ensure at least one selected
    if (this.needsTrainerDiscardSelection(this.selectedTrainerCard) && this.selectedTrainerCardIds.length === 0) return false;

    // For deck-search trainers that need a Pokémon target (e.g., Evosoda) we require a target selection.
    if (this.needsDeckSearchWithTarget(this.selectedTrainerCard) && !this.trainerTargetSlot) return false;

    // For deck-energy trainers (Professor's Letter, Energy Search), require at least one selection
    if (this.needsDeckEnergySelection(this.selectedTrainerCard) && this.selectedTrainerCardIds.length === 0) return false;

    // For opponent-targeting trainers, require a target selection
    if (this.needsOpponentPokemonTarget(this.selectedTrainerCard) && !this.trainerTargetId) return false;

    // Otherwise allow
    return true;
  }

  needsTrainerCoinFlip(card: HandCard | null): boolean {
    return card?.name.toLowerCase().includes('roller skates') ?? false;
  }

  // Centralize Play button click handling - delegate deck-search trainers to performEvoSearchAndPlay
  onPlayButtonClick(): void {
    if (this.needsDeckSearchWithTarget(this.selectedTrainerCard)) {
      this.performEvoSearchAndPlay();
    } else if (this.needsTrainerCoinFlip(this.selectedTrainerCard)) {
      this.startTrainerCoinFlip();
    } else {
      this.confirmPlayTrainer();
    }
  }

  // For Evosoda-like trainers: when Play is pressed, search the deck for an evolution that matches the selected target.
  // If found, set selectedTrainerCardIds to the found cardId and confirm; if not found, leave selectedTrainerCardIds empty (buildTrainerRequest will send null) and confirm.
  performEvoSearchAndPlay(): void {
    if (!this.selectedTrainerCard || !this.gameId) return;
    // Must have selected a target slot for deck-search flows
    const target = this.getTargetBoardCard();
    if (!target) {
      this.trainerError = 'Seleccioná primero el Pokémon objetivo.';
      this.cdr.detectChanges();
      return;
    }

    // Prepare UI state
    this.trainerPlaying = true;
    this.trainerError = null;
    this.stateVersion++;

    // Search the local deck for a matching evolution (first match)
    const targetName = target.name.toLowerCase();
    const candidate = (this.deck?.cards || []).find(c => ((c.evolvesFrom ?? '').toLowerCase() === targetName) && ((c.quantity ?? 0) > 0));

    // For reliability prefer server-side auto-search: send no explicit selectedCardIds so backend will scan the deck.
    // Keep the candidate in logs for debugging but do not force a specific cardId selection.
    this.selectedTrainerCardIds = [];

    // Debug logs to help trace why evolution may not occur
    // eslint-disable-next-line no-console
    console.log('[EvoSearch] target=', target?.name, 'candidate=', candidate?.cardId, 'deckCount=', this.deck?.cards?.length, 'willSendSelectedCardIds=null');

    // Delegate to the existing confirm flow which builds the request and calls the backend (selectedCardIds will be null)
    this.confirmPlayTrainer();
  }

  private fetchDeckPreview(limit = 7): void {
    if (!this.gameId || !this.myPlayerId) return;
    this.deckPreviewLoading = true;
    this.deckPreview = [];
    this.gameService.getDeckPreview(this.gameId, this.myPlayerId, limit).subscribe({
      next: (resp) => {
        const ids: string[] = resp.cardIds || [];
        const items = ids.map(id => {
          const cardId = id.includes(':') ? id.split(':')[0] : id;
          const deckMatch = this.deck?.cards?.find(c => c.cardId === cardId);
          const name = deckMatch?.name ?? cardId;
          const image = deckMatch ? this.resolveCardImageFromParts(deckMatch.setId ?? '', deckMatch.cardId) : '/images/cardBack.png';
          const type = deckMatch?.type ?? '';
          return { instanceId: id, name, image, type };
        });
        this.deckPreview = items;
        this.deckPreviewLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.deckPreviewLoading = false;
        this.deckPreview = [];
        this.cdr.detectChanges();
      }
    });
  }

  // Deprecated toggle used previously for multi-select; deck preview selection now immediate upon tap.
  toggleDeckPreviewSelection(instanceId: string): void {
    // Keep for compatibility in case code elsewhere references it — no-op now.
  }

  // --- Deck-search helpers (used for cards like Evosoda that search the deck for a matching evolution) ---
  getSelectedTargetName(): string {
    const target = this.getTargetBoardCard();
    return target ? target.name : '';
  }

  getTargetBoardCard(): HandCard | null {
    if (!this.trainerTargetSlot) return null;
    if (this.trainerTargetSlot === 'ACTIVE') return this.player.active;
    if (this.trainerTargetSlot.startsWith('BENCH_')) {
      const idx = parseInt(this.trainerTargetSlot.split('_')[1], 10);
      return this.player.bench[idx] ?? null;
    }
    return null;
  }

  // Return candidate deck cards matching the selected target (by evolvesFrom)
  deckSearchCandidates(): DeckCardInput[] {
    const target = this.getTargetBoardCard();
    if (!target || !this.deck || !this.deck.cards) return [];
    const targetName = target.name.toLowerCase();
    return this.deck.cards.filter(c => (c.evolvesFrom ?? '').toLowerCase() === targetName && (c.quantity ?? 0) > 0);
  }

  selectDeckSearchCard(cardId: string): void {
    // Use cardId here; backend will normalize if necessary. Store as selectedTrainerCardIds for confirmPlayTrainer.
    this.selectedTrainerCardIds = [cardId];
  }

  // Public wrapper to be used from the template for deck card images
  imageUrlForDeckCard(card: DeckCardInput): string {
    return this.resolveCardImage(card);
  }

  // User tapped a top-of-deck card; show reveal animation then play the trainer choosing that card.
  revealedDeckSelection: string | null = null;

  selectAndPlayDeckPreview(instanceId: string): void {
    if (!this.canConfirmTrainer || !this.selectedTrainerCard || !this.gameId) return;

    const selectedItem = this.deckPreview.find(p => p.instanceId === instanceId);
    if (selectedItem && selectedItem.type !== 'Pokémon') {
      this.trainerError = 'Debes seleccionar un Pokémon válido.';
      return;
    }

    // Capture values now to avoid race conditions and satisfy TS non-null checks
    const trainerCard = this.selectedTrainerCard;
    const trainerTargetId = this.trainerTargetId;
    const trainerTargetSlot = this.trainerTargetSlot;
    const selectedEnergyIds = [...this.selectedTrainerEnergyCardIds];

    // start reveal animation
    this.revealedDeckSelection = instanceId;
    this.revealPhase = 'hidden';
    this.cdr.detectChanges();

    // small delay so the flip animation is visible before backend action
    setTimeout(() => {
      this.revealPhase = 'revealing';
      this.cdr.detectChanges();

      // send request while animation plays
      this.trainerPlaying = true;
      this.trainerError = null;
      this.stateVersion++;

      const selectedOpponentTarget = this.parseTargetId(trainerTargetId);
      const ownTargetPlayerId = trainerTargetSlot ? this.myPlayerId ?? undefined : undefined;

      const request: PlayTrainerRequest = {
        cardInstanceId: trainerCard!.instanceId,
        targetSlot: trainerTargetSlot ?? selectedOpponentTarget?.slot,
        targetId: trainerTargetId ?? undefined,
        targetPlayerId: ownTargetPlayerId ?? selectedOpponentTarget?.playerId,
        selectedCardIds: [instanceId],
        selectedEnergyCardIds: selectedEnergyIds,
      } as any;

      this.gameService.playTrainer(this.gameId!, request).subscribe({
        next: (state) => {
          // show 'done' briefly then close modal
          this.revealPhase = 'done';
          this.cdr.detectChanges();
          this.gameStateService.setState(state);
          this.animateTrainerFromLog(trainerCard!, request);
          setTimeout(() => {
            this.trainerPlaying = false;
            this.stateVersion++;
            this.showTrainerModal = false;
            this.selectedTrainerCard = null;
            this.trainerTargetSlot = null;
            this.trainerTargetId = null;
            this.selectedTrainerEnergyCardIds = [];
            this.selectedTrainerCardIds = [];
            this.revealedDeckSelection = null;
            this.fetchPlayerHand();
            this.scheduleRefresh();
            this.cdr.detectChanges();
          }, 600);
        },
        error: (err) => {
          this.trainerError = err.error?.message || 'Error al jugar el entrenador';
          this.trainerPlaying = false;
          this.revealedDeckSelection = null;
          this.revealPhase = 'idle';
          this.refreshGameState();
        },
      });
    }, 150);
  }

  toggleDiscardCardSelection(instanceId: string): void {
    const idx = this.selectedTrainerCardIds.indexOf(instanceId);
    if (idx >= 0) {
      this.selectedTrainerCardIds.splice(idx, 1);
    } else {
      this.selectedTrainerCardIds.push(instanceId);
    }
  }

  selectTrainerSlot(slot: string): void {
    this.trainerTargetSlot = this.trainerTargetSlot === slot ? null : slot;
    this.selectedTrainerEnergyCardIds = [];
  }

  selectTrainerTarget(targetId: string): void {
    this.trainerTargetId = this.trainerTargetId === targetId ? null : targetId;
    this.selectedTrainerEnergyCardIds = [];
  }

  toggleTrainerEnergy(energyCardId: string): void {
    if (!energyCardId) return;
    this.selectedTrainerEnergyCardIds = this.selectedTrainerEnergyCardIds.includes(energyCardId)
      ? []
      : [energyCardId];
  }

  confirmPlayTrainer(): void {
    if (!this.gameId || !this.selectedTrainerCard) return;
    this.trainerPlaying = true;
    this.trainerError = null;
    this.stateVersion++;
    const card = this.selectedTrainerCard;
    const request = this.buildTrainerRequest(card);
    this.trainerCoinFlipResults = [];
    this.trainerCoinFlipDone = false;

    // Debug log request for trainer plays (helps debug Evosoda flow)
    // eslint-disable-next-line no-console
    console.log('[PlayTrainer] request=', request);

    this.gameService.playTrainer(this.gameId, request).subscribe({
      next: (state) => {
        this.trainerPlaying = false;
        this.stateVersion++;
        this.showTrainerModal = false;
        this.selectedTrainerCard = null;
        this.trainerTargetSlot = null;
        this.trainerTargetId = null;
        this.selectedTrainerEnergyCardIds = [];
        this.selectedTrainerCardIds = [];
        this.gameStateService.setState(state);
        this.cdr.detectChanges();
        this.animateTrainerFromLog(card, request);
        this.fetchPlayerHand();
        this.scheduleRefresh();
      },
      error: (err) => {
        this.trainerError = err.error?.message || 'Error al jugar el entrenador';
        this.trainerPlaying = false;
        this.refreshGameState();
      },
    });
  }

  // Shuffle the top-of-deck without selecting any card.
  // Build a PlayTrainerRequest that sends selectedCardIds = null so the backend receives no explicit selection.
  // Note: backend currently treats null/empty similarly; this sends null explicitly per chosen strategy B.
  shuffleWithoutChoosing(): void {
    if (!this.canConfirmTrainer || !this.selectedTrainerCard) return;
    this.confirmPlayTrainerShuffleOnly();
  }

  private confirmPlayTrainerShuffleOnly(): void {
    if (!this.gameId || !this.selectedTrainerCard) return;
    this.trainerPlaying = true;
    this.trainerError = null;
    this.stateVersion++;
    const card = this.selectedTrainerCard;

    // Build request but send selectedCardIds as null to indicate 'no explicit selection'
    const selectedOpponentTarget = this.parseTargetId(this.trainerTargetId);
    const ownTargetPlayerId = this.trainerTargetSlot ? this.myPlayerId ?? undefined : undefined;
    const request: PlayTrainerRequest = {
      cardInstanceId: card.instanceId,
      targetSlot: this.trainerTargetSlot ?? selectedOpponentTarget?.slot,
      targetId: this.trainerTargetId ?? undefined,
      targetPlayerId: ownTargetPlayerId ?? selectedOpponentTarget?.playerId,
      selectedCardIds: null,
      selectedEnergyCardIds: this.selectedTrainerEnergyCardIds,
    } as any;

    this.gameService.playTrainer(this.gameId, request).subscribe({
      next: (state) => {
        this.trainerPlaying = false;
        this.stateVersion++;
        this.showTrainerModal = false;
        this.selectedTrainerCard = null;
        this.trainerTargetSlot = null;
        this.trainerTargetId = null;
        this.selectedTrainerEnergyCardIds = [];
        this.selectedTrainerCardIds = [];
        this.gameStateService.setState(state);
        this.cdr.detectChanges();
        this.animateTrainerFromLog(card, request);
        this.fetchPlayerHand();
        this.scheduleRefresh();
      },
      error: (err) => {
        this.trainerError = err.error?.message || 'Error al jugar el entrenador';
        this.trainerPlaying = false;
        this.refreshGameState();
      },
    });
  }

  private buildTrainerRequest(card: HandCard): PlayTrainerRequest {
    const selectedOpponentTarget = this.parseTargetId(this.trainerTargetId);
    const ownTargetPlayerId = this.trainerTargetSlot ? this.myPlayerId ?? undefined : undefined;

    return {
      cardInstanceId: card.instanceId,
      targetSlot: this.trainerTargetSlot ?? selectedOpponentTarget?.slot,
      targetId: this.trainerTargetId ?? undefined,
      targetPlayerId: ownTargetPlayerId ?? selectedOpponentTarget?.playerId,
      selectedCardIds: ((this.selectedTrainerCardIds && this.selectedTrainerCardIds.length > 0) ? this.selectedTrainerCardIds : null) as any,
      selectedEnergyCardIds: this.selectedTrainerEnergyCardIds,
      coinFlipResults: this.trainerCoinFlipResults.length > 0 ? this.trainerCoinFlipResults : undefined,
    };
  }

  private parseTargetId(targetId: string | null): { slot: string; playerId: string } | null {
    if (!targetId) return null;
    const [slot, playerId] = targetId.split(':');
    if (!slot || !playerId) return null;
    return { slot, playerId };
  }

  closeTrainerModal(): void {
    if (this.trainerPlaying) return;
    this.showTrainerModal = false;
    this.selectedTrainerCard = null;
    this.trainerTargetSlot = null;
    this.trainerTargetId = null;
    this.selectedTrainerEnergyCardIds = [];
    this.selectedTrainerCardIds = [];
    this.trainerError = null;
    this.trainerCoinFlipResults = [];
    this.trainerCoinFlipDone = false;
  }

  get supporterPlayedThisTurn(): boolean {
    return this.lastGameState?.turnFlags?.supporterPlayed === true;
  }

  get stadiumPlayedThisTurn(): boolean {
    return this.lastGameState?.turnFlags?.stadiumPlayed === true;
  }

  get isSameStadiumActive(): boolean {
    if (!this.stadium || !this.selectedTrainerCard) return false;
    return this.stadium.name.toLowerCase() === this.selectedTrainerCard.name.toLowerCase();
  }

  get trainerBlockedReason(): string | null {
    const card = this.selectedTrainerCard;
    if (!card) return null;
    if (this.isSupporterCard(card) && this.supporterPlayedThisTurn) return 'Ya jugaste un Supporter este turno';
    if (this.isSupporterCard(card) && this.isFirstPlayerFirstTurn) return 'No se puede jugar Supporter en el primer turno';
    if (this.isStadiumCard(card) && this.stadiumPlayedThisTurn) return 'Ya jugaste un Stadium este turno';
    if (this.isStadiumCard(card) && this.isSameStadiumActive) return 'Ese Estadio ya está activo';
    return null;
  }

  get canConfirmTrainer(): boolean {
    return !this.trainerPlaying && !this.trainerBlockedReason;
  }

  isToolCard(card: HandCard): boolean {
    return (card.subtype?.includes('Tool') ?? false);
  }

  needsOwnPokemonTrainerTarget(card: HandCard | null): boolean {
    if (!card) return false;
    // Exclusive checks: if the trainer's action is discard-from-hand/deck-only,
    // it should not show own-board targets. Deck-search flows (e.g., Evosoda) DO require
    // selecting a Pokémon target first, so return true for deck-search trainers.
    if (this.needsTrainerDiscardSelection(card)) return false;
    if (this.needsDeckSearchWithTarget(card)) return true;

    const name = card.name.toLowerCase();
    // Tools and specific named cards intended for own Pokémon
    const toolLike = this.isToolCard(card) || name.includes('muscle band') || name.includes('tool');
    const ownTargets = toolLike || name.includes('super potion') || name.includes('cassius') || name.includes('potion') || name.includes('switch');
    return ownTargets;
  }

  needsOpponentPokemonTarget(card: HandCard | null): boolean {
    if (!card) return false;
    const name = card.name.toLowerCase();
    // Common opponent-targeting trainers (extend the list as we audit cards)
    return name.includes('team flare grunt') || name.includes('team flare') || name.includes('opponent') || name.includes('rival') || name.includes('enemy') || name.includes('lysandre');
  }

  needsTrainerEnergySelection(card: HandCard): boolean {
    const name = card.name.toLowerCase();
    return name.includes('super potion') || name.includes('team flare grunt');
  }

  needsTrainerDiscardSelection(card: HandCard): boolean {
    const name = card.name.toLowerCase();
    return name.includes('max revive');
  }

  needsDeckEnergySelection(card: HandCard | null): boolean {
    if (!card) return false;
    const name = card.name.toLowerCase();
    return name.includes("professor's letter") || name.includes('energy search');
  }

  selectableDeckEnergyCount(card: HandCard | null): number {
    if (!card) return 0;
    const name = card.name.toLowerCase();
    if (name.includes("professor's letter")) return 2;
    return 1;
  }

  get trainerDeckEnergyOptions(): { cardId: string; energyType: string; count: number; image: string }[] {
    if (!this.deck?.cards) return [];
    const grouped = new Map<string, { cardId: string; energyType: string; count: number }>();
    for (const c of this.deck.cards) {
      if (c.basicEnergy && (c.quantity ?? 0) > 0) {
        const existing = grouped.get(c.cardId);
        if (existing) {
          existing.count += c.quantity!;
        } else {
          grouped.set(c.cardId, {
            cardId: c.cardId,
            energyType: c.energyType ?? 'Unknown',
            count: c.quantity ?? 0,
          });
        }
      }
    }
    return Array.from(grouped.values()).map(g => ({
      ...g,
      image: this.resolveCardImageFromParts(this.deck!.cards.find(c => c.cardId === g.cardId)?.setId ?? '', g.cardId),
    }));
  }

  toggleDeckEnergySelection(cardId: string): void {
    const limit = this.selectableDeckEnergyCount(this.selectedTrainerCard);
    if (this.selectedTrainerCardIds.includes(cardId)) {
      this.selectedTrainerCardIds = [];
    } else if (this.selectedTrainerCardIds.length < limit) {
      this.selectedTrainerCardIds = [...this.selectedTrainerCardIds, cardId];
    }
  }

  get discardPokemonOptions(): { instanceId: string; name: string; image: string }[] {
    const myId = this.myPlayerId ?? '';
    const discardIds = this.lastGameState?.discardPilesByPlayer?.[myId] ?? [];
    return discardIds
      .map(id => {
        const templateId = id.includes(':') ? id.substring(0, id.indexOf(':')) : id;
        const card = this.deck?.cards?.find(c => c.cardId === templateId);
        if (!card) return null;
        if (!['Pokemon', 'Pokémon'].includes(card.type)) return null;
        return {
          instanceId: id,
          name: card.name,
          image: this.resolveCardImageFromParts(card.setId ?? '', templateId),
        };
      })
      .filter((x): x is { instanceId: string; name: string; image: string } => x !== null);
  }

  trainerEnergySelectionLabel(card: HandCard): string {
    const name = card.name.toLowerCase();
    if (name.includes('team flare grunt')) return 'Seleccioná una energía rival a descartar:';
    return 'Seleccioná una energía adjunta a descartar:';
  }

  trainerEnergyOptions(card: HandCard): AttachedEnergy[] {
    const name = card.name.toLowerCase();
    if (name.includes('team flare grunt')) {
      const target = this.parseTargetId(this.trainerTargetId);
      const slot = target?.slot ?? 'ACTIVE';
      return slot === 'ACTIVE'
        ? this.opponentActiveEnergies
        : this.opponentBenchEnergies[this.benchIndexFromSlot(slot)] ?? [];
    }

    const slot = this.trainerTargetSlot ?? 'ACTIVE';
    return slot === 'ACTIVE'
      ? this.playerActiveEnergies
      : this.playerBenchEnergies[this.benchIndexFromSlot(slot)] ?? [];
  }

  isTrainerEnergySelected(energyCardId: string): boolean {
    return this.selectedTrainerEnergyCardIds.includes(energyCardId);
  }

  private benchIndexFromSlot(slot: string): number {
    const match = slot.match(/^BENCH_(\d+)$/);
    return match ? parseInt(match[1], 10) : -1;
  }

  isSupporterCard(card: HandCard): boolean {
    return card.subtype?.includes('Supporter') ?? false;
  }

  isStadiumCard(card: HandCard): boolean {
    return card.subtype?.includes('Stadium') ?? false;
  }

  isItemCard(card: HandCard): boolean {
    return card.subtype?.includes('Item') ?? false;
  }

  getTrainerCategory(card: HandCard): TrainerCategory | null {
    const subtype = card.subtype;
    if (subtype?.includes('Stadium')) return 'Stadium';
    if (subtype?.includes('Supporter')) return 'Supporter';
    if (subtype?.includes('Tool')) return 'Tool';
    if (subtype?.includes('Item')) return 'Item';
    return null;
  }

  get canDraw(): boolean {
    return this.allowedActions.canDraw
      && this.gameId !== null
      && !this.drawing;
  }

  drawCard(): void {
    if (!this.canDraw || !this.gameId) return;
    this.drawing = true;
    this.actionError = null;
    this.stateVersion++;
    this.gameService.executeAction(this.gameId, { actionType: 'DRAW' }).subscribe({
      next: (state) => {
        this.drawing = false;
        this.stateVersion++;
        this.isDrawingCard = true;
        this.gameStateService.setState(state);
        this.animateDrawCard();
      },
      error: () => {
        this.actionError = 'No se pudo robar la carta.';
        this.drawing = false;
      },
    });
  }

  private animateDrawCard(): void {
    if (!this.gameId || !this.deck || !this.myPlayerId) {
      this.fetchPlayerHand();
      return;
    }
    const oldInstanceIds = new Set(this.player.hand.map(c => c.instanceId));
    this.gameService.getHand(this.gameId, this.myPlayerId).subscribe({
      next: (handResponse) => {
        const deckMap = new Map<string, DeckCardInput>();
        for (const card of this.deck!.cards) {
          deckMap.set(card.cardId, card);
        }
        const newHand = handResponse.cardIds.map((instanceId) =>
          this.buildHandCardFromInstanceId(instanceId, deckMap)
        );
        const newCard = newHand.find(c => !oldInstanceIds.has(c.instanceId)) ?? null;
        if (newCard) {
          this.drawnCardPreview = newCard;
          this.drawAnimating = true;
          this.drawCardBackOnly = true;
          this.drawEnterIndex = -1;
          this.slidingToHand = false;
          this.cdr.detectChanges();
          setTimeout(() => {
            this.drawCardBackOnly = false;
            this.cdr.detectChanges();
          }, 400);
          const drawnIndex = newHand.findIndex(c => c.instanceId === newCard!.instanceId);
          const handWithoutNew = newHand.filter(c => c.instanceId !== newCard!.instanceId);
          setTimeout(() => {
            this.player.hand = handWithoutNew;
            this.slidingToHand = true;
            this.cdr.detectChanges();
          }, 1400);
          setTimeout(() => {
            this.player.hand = newHand;
            this.drawEnterIndex = drawnIndex;
            this.cdr.detectChanges();
            setTimeout(() => {
              this.drawAnimating = false;
              this.drawnCardPreview = null;
              this.slidingToHand = false;
              this.drawEnterIndex = -1;
              this.isDrawingCard = false;
              this.cdr.detectChanges();
            }, 450);
          }, 1900);
        } else {
          this.player.hand = newHand;
          this.cdr.detectChanges();
        }
      },
      error: () => {
        this.fetchPlayerHand();
      },
    });
  }

  get canPassTurn(): boolean {
    return this.allowedActions.canPassTurn
      && this.gameId !== null
      && !this.endingTurn;
  }

  passTurn(): void {
    if (!this.gameId || !this.canPassTurn) return;
    this.endingTurn = true;
    this.actionError = null;
    this.stateVersion++;
    this.gameService.executeAction(this.gameId, { actionType: 'END_TURN' }).subscribe({
      next: (state) => {
        this.endingTurn = false;
        this.stateVersion++;
        this.gameStateService.setState(state);
      },
      error: () => {
        this.actionError = 'No se pudo pasar el turno.';
        this.endingTurn = false;
      },
    });
  }

  readonly player: PlayerData = {
    name: 'TÚ',
    playerId: null,
    avatar: 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png',
    hand: [],
    active: null,
    bench: [null, null, null, null, null],
    deckCount: 0,
    deckName: '',
    prizes: [null, null, null, null, null, null],
    discard: [],
  };

  readonly opponent: PlayerData = {
    name: 'RIVAL',
    playerId: null,
    avatar: 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/6.png',
    hand: [],
    active: null,
    bench: [null, null, null, null, null],
    deckCount: 0,
    deckName: '',
    prizes: [null, null, null, null, null, null],
    discard: [],
  };

  readonly activeDropData: HandCard[] = [];
  readonly benchDropData: HandCard[][] = [[], [], [], [], []];

  stadium: { name: string; image: string } | null = null;
  stadiumOwnerName: string | null = null;

  get opponentHandCount(): number {
    return this.opponentHandCountValue;
  }

  get connectedDropIds(): string[] {
    return [
      'active-drop-zone',
      ...this.player.bench.map((_, i) => `bench-drop-zone-${i}`),
    ];
  }

  // ── UI helpers ──────────────────────────────────────────────────────────────

  openDeckView(): void {
    this.showDeckView = true;
  }

  closeDeckView(): void {
    this.showDeckView = false;
  }

  toggleCardPreview(card: PreviewCard): void {
    if (this.previewedCard === card) {
      this.previewedCard = null;
      this.previewedCardSummary = null;
      this.previewedCardEnergies = [];
      this.previewedCardStatus = null;
    } else {
      this.previewedCard = card;
      this.previewedCardStatus = this.getStatusForPreviewedCard();
      const summary = this.buildCardSummary(card);
      this.previewedCardSummary = summary ? { ...summary, statusCondition: this.previewedCardStatus } : null;
      this.previewedCardEnergies = this.getEnergiesForPreviewedCard();
    }
  }

  closeCardPreview(): void {
    this.previewedCard = null;
    this.previewedCardSummary = null;
    this.previewedCardEnergies = [];
    this.previewedCardStatus = null;
  }

  async concede(): Promise<void> {
    const confirmed = await this.modalService.openModal({
      title: 'Rendirse',
      message: '¿Estás seguro de que deseas rendirte?',
      confirmText: 'Sí, rendirme',
      cancelText: 'No, continuar',
    });

    if (confirmed) {
      // Rendirse cierra la partida: limpiamos la referencia activa.
      localStorage.removeItem('active_game_id');
      if (this.gameId) {
        this.gameService.concede(this.gameId).subscribe({
          next: (state) => {
            this.gameStateService.setState(state);
            this.router.navigate(['/lobby']);
          },
          error: () => {
            this.router.navigate(['/lobby']);
          }
        });
      } else {
        this.router.navigate(['/lobby']);
      }
    }
  }

  goToLobby(): void {
    this.router.navigate(['/lobby']);
  }

  // ── Modo Sala de Práctica (Sandbox) ─────────────────────────────────────────
  // Todo el flujo local vive en memoria del frontend: no se envían acciones HTTP
  // ni se usa WebSocket. El usuario puede colocar Pokémon, adjuntar energía,
  // evolucionar y atacar a un "Muñeco de práctica" para experimentar.

  private static readonly SANDBOX_DUMMY_IMAGE =
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-doll.png';

  private initSandboxBoard(): void {
    // En la Sala de Práctica siempre es tu turno: el usuario controla todo.
    this.isMyTurn = true;
    this.currentPhase = 'MAIN';
    this.turnNumber = 1;
    this.opponent.name = 'Muñeco de práctica';
    this.opponent.avatar = BattleArenaComponent.SANDBOX_DUMMY_IMAGE;
    this.opponent.deckCount = 0;
    this.opponent.prizes = [];
    this.opponentHandCountValue = 0;
    this.spawnSandboxDummy();
  }

  private spawnSandboxDummy(): void {
    this.opponent.active = {
      instanceId: 'sandbox-dummy',
      cardId: 'sandbox-dummy',
      setId: '',
      name: 'Muñeco de práctica',
      quantity: 1,
      type: 'Pokemon',
      subtype: 'Basic',
      basicEnergy: false,
      basicPokemon: true,
      aceSpec: false,
      hp: 200,
      attackDamage: null,
      attackRequiredEnergy: null,
      image: BattleArenaComponent.SANDBOX_DUMMY_IMAGE,
      attacks: [],
    };
    this.opponentActiveDamage = 0;
    this.opponentActiveEnergies = [];
  }

  // En el Sandbox no aplican reglas: cualquier energía se puede adjuntar y
  // cualquier Pokémon se puede colocar/reemplazar (prueba y error).
  private isSandboxEnergy(card: HandCard): boolean {
    return card.type === 'Energy' || card.basicEnergy === true;
  }

  private isSandboxPokemon(card: HandCard): boolean {
    return card.type !== 'Energy' && card.type !== 'Trainer';
  }

  private onDropActiveSandbox(card: HandCard): void {
    this.actionError = null;
    if (this.isSandboxEnergy(card)) {
      if (!this.player.active) {
        this.actionError = 'Primero colocá un Pokémon en el ACTIVO.';
        this.cdr.detectChanges();
        return;
      }
      this.removeFromHand(card.instanceId);
      this.attachEnergySandbox(card, 'ACTIVE');
      return;
    }
    if (this.isSandboxPokemon(card)) {
      // Colocar o reemplazar el activo libremente (sirve también para evolucionar).
      this.removeFromHand(card.instanceId);
      this.player.active = card;
      this.cdr.detectChanges();
    }
  }

  private onDropBenchSandbox(card: HandCard, index: number): void {
    this.actionError = null;
    const slot = this.slotForBench(index);
    if (this.isSandboxEnergy(card)) {
      if (!this.player.bench[index]) {
        this.actionError = 'No hay ningún Pokémon en esa posición de la banca.';
        this.cdr.detectChanges();
        return;
      }
      this.removeFromHand(card.instanceId);
      this.attachEnergySandbox(card, slot);
      return;
    }
    if (this.isSandboxPokemon(card)) {
      // Colocar o reemplazar el Pokémon de esa posición.
      this.removeFromHand(card.instanceId);
      this.player.bench[index] = card;
      this.cdr.detectChanges();
    }
  }

  private benchPokemonSandbox(cardInstanceId: string): void {
    const card = this.player.hand.find(c => c.instanceId === cardInstanceId);
    if (!card || !this.isSandboxPokemon(card)) return;
    const emptyIndex = this.player.bench.findIndex(slot => slot === null);
    if (emptyIndex === -1) {
      this.actionError = 'La banca está llena (máx. 5).';
      this.cdr.detectChanges();
      return;
    }
    this.actionError = null;
    this.removeFromHand(cardInstanceId);
    this.player.bench[emptyIndex] = card;
    this.cdr.detectChanges();
  }

  private attachEnergySandbox(card: HandCard, targetSlot: string): void {
    this.pushSandboxEnergy('me', targetSlot, card);
  }

  private pushSandboxEnergy(side: 'me' | 'opp', slot: string, card: HandCard): void {
    const energy: AttachedEnergy = {
      cardId: `${card.instanceId || card.cardId}:${this.sandboxInstanceSeq++}`,
      energyType: this.sandboxEnergyType(card),
    };
    if (slot === 'ACTIVE') {
      if (side === 'me') this.playerActiveEnergies = [...this.playerActiveEnergies, energy];
      else this.opponentActiveEnergies = [...this.opponentActiveEnergies, energy];
    } else {
      const idx = parseInt(slot.split('_')[1], 10);
      if (Number.isNaN(idx)) return;
      if (side === 'me' && this.playerBenchEnergies[idx]) {
        this.playerBenchEnergies[idx] = [...this.playerBenchEnergies[idx], energy];
      } else if (side === 'opp' && this.opponentBenchEnergies[idx]) {
        this.opponentBenchEnergies[idx] = [...this.opponentBenchEnergies[idx], energy];
      }
    }
    this.cdr.detectChanges();
  }

  private sandboxEnergyType(card: HandCard): string {
    if (card.energyType) return card.energyType;
    const name = (card.name || '').toLowerCase();
    const types = ['Fire', 'Water', 'Grass', 'Lightning', 'Psychic', 'Fighting', 'Darkness', 'Metal', 'Fairy', 'Dragon', 'Colorless'];
    for (const type of types) {
      if (name.includes(type.toLowerCase())) return type;
    }
    return 'Colorless';
  }

  private performSandboxAttack(attackIndex: number): void {
    if (!this.player.active || !this.opponent.active) return;
    const attack = this.activeCardAttacks[attackIndex];
    if (!attack) return;

    this.attacking = true;
    this.actionError = null;
    this.attackAnimating = true;
    this.cdr.detectChanges();

    const damageHp = this.parseAttackDamage(attack);

    setTimeout(() => {
      this.attackAnimating = false;
      this.attacking = false;

      if (damageHp > 0 && this.opponent.active) {
        this.opponentActiveDamage += Math.round(damageHp / 10);
        this.triggerDamageAnimation(damageHp, 'opp');
        if (this.currentHp(this.opponent.active.hp, this.opponentActiveDamage) <= 0) {
          this.handleSandboxDummyKo();
        }
      }
      this.cdr.detectChanges();
    }, 700);
  }

  private parseAttackDamage(attack: CardAttackResponse): number {
    const raw = (attack.damage ?? '').toString().replace(/[^0-9]/g, '');
    return raw ? parseInt(raw, 10) : 0;
  }

  private handleSandboxDummyKo(): void {
    const dummy = this.opponent.active;
    if (!dummy) return;
    this.triggerKoNotification(dummy.name, dummy.image, 'opp', this.opponentActiveEnergies);
    this.opponent.active = null;
    this.opponentActiveDamage = 0;
    this.opponentActiveEnergies = [];
    this.cdr.detectChanges();
    // Reaparece un muñeco nuevo para seguir practicando.
    setTimeout(() => {
      this.spawnSandboxDummy();
      this.cdr.detectChanges();
    }, 1400);
  }

  // ── Selector de cartas del Sandbox ──────────────────────────────────────────
  // Permite sacar a la mano cualquier carta del mazo (sin azar) para testear.

  openSandboxPicker(): void {
    if (!this.sandboxMode) return;
    this.showSandboxPicker = true;
    this.cdr.detectChanges();
  }

  closeSandboxPicker(): void {
    this.showSandboxPicker = false;
    this.cdr.detectChanges();
  }

  setSandboxPickSource(source: 'deck' | 'catalog'): void {
    this.sandboxPickSource = source;
    if (source === 'catalog') this.loadSandboxCatalog();
    this.cdr.detectChanges();
  }

  setSandboxPickTarget(target: SandboxPickTarget): void {
    this.sandboxPickTarget = target;
    this.cdr.detectChanges();
  }

  // Cartas que muestra el selector según el origen elegido.
  get sandboxPickerCards(): HandCard[] {
    let cards = this.sandboxPickSource === 'catalog'
      ? this.sandboxCatalogCards
      : this.sandboxCardPool;
    if (this.sandboxPickSource === 'catalog') {
      const filter = this.sandboxCatalogFilter.trim().toLowerCase();
      if (filter) {
        cards = cards.filter(c => c.name.toLowerCase().includes(filter));
      }
    }
    if (this.sandboxOnlyWithAbility) {
      cards = cards.filter(c => (c.abilities?.length ?? 0) > 0);
    }
    return cards;
  }

  private loadSandboxCatalog(): void {
    if (this.sandboxCatalogLoaded || this.sandboxCatalogLoading) return;
    this.sandboxCatalogLoading = true;
    this.sandboxCatalogError = null;
    // Set base del juego (XY1): trae todas las cartas para poder usar cualquiera.
    this.cardService.search('set.id:xy1', 250).subscribe({
      next: (res) => {
        this.sandboxCatalogCards = (res.cards ?? []).map(c => this.catalogCardToHandCard(c));
        this.sandboxCatalogLoaded = true;
        this.sandboxCatalogLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.sandboxCatalogError = 'No se pudo cargar el catálogo de cartas.';
        this.sandboxCatalogLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  // Saca la carta seleccionada al destino elegido (mano, tu tablero o el del rival).
  pickSandboxCard(card: HandCard): void {
    if (!this.sandboxMode) return;
    const target = this.sandboxPickTarget;

    if (target === 'HAND') {
      this.player.hand = [...this.player.hand, this.cloneSandboxCard(card)];
      this.showToast(`${card.name} → tu mano`);
      this.cdr.detectChanges();
      return;
    }

    const side: 'me' | 'opp' = (target === 'OPP_ACTIVE' || target === 'OPP_BENCH') ? 'opp' : 'me';
    const toActive = (target === 'MY_ACTIVE' || target === 'OPP_ACTIVE');
    const sideLabel = side === 'me' ? 'tu tablero' : 'el rival';

    if (this.isSandboxEnergy(card)) {
      // La energía se adjunta al Pokémon que esté en ese slot.
      if (toActive) {
        const active = side === 'me' ? this.player.active : this.opponent.active;
        if (!active) { this.showToast('No hay Pokémon en ese ACTIVO'); return; }
        this.pushSandboxEnergy(side, 'ACTIVE', card);
      } else {
        const bench = side === 'me' ? this.player.bench : this.opponent.bench;
        const idx = bench.findIndex(b => b !== null);
        if (idx === -1) { this.showToast('No hay Pokémon en esa BANCA'); return; }
        this.pushSandboxEnergy(side, this.slotForBench(idx), card);
      }
      this.showToast(`Energía → ${sideLabel}`);
      return;
    }

    // Pokémon (u otra carta): se coloca en el slot.
    const placed = this.cloneSandboxCard(card);
    if (toActive) {
      if (side === 'me') {
        this.player.active = placed;
        this.playerActiveDamage = 0;
        this.playerActiveEnergies = [];
      } else {
        this.opponent.active = placed;
        this.opponentActiveDamage = 0;
        this.opponentActiveEnergies = [];
      }
    } else {
      const bench = side === 'me' ? this.player.bench : this.opponent.bench;
      const idx = bench.findIndex(b => b === null);
      if (idx === -1) { this.showToast('La BANCA está llena (máx. 5)'); return; }
      if (side === 'me') {
        this.player.bench[idx] = placed;
        this.playerBenchEnergies[idx] = [];
      } else {
        this.opponent.bench[idx] = placed;
        this.opponentBenchEnergies[idx] = [];
      }
    }
    this.showToast(`${card.name} → ${sideLabel}`);
    this.cdr.detectChanges();
  }

  private cloneSandboxCard(card: HandCard): HandCard {
    return { ...card, instanceId: `sandbox:${card.cardId}:${this.sandboxInstanceSeq++}` };
  }

  // Pasar turno en el Sandbox: alterna de quién es el turno (todo manual, sin IA).
  passTurnSandbox(): void {
    this.isMyTurn = !this.isMyTurn;
    this.turnNumber++;
    this.currentPhase = 'MAIN';
    this.attackResult = null;
    this.showToast(this.isMyTurn ? 'Tu turno' : 'Turno del rival');
    this.cdr.detectChanges();
  }

  private catalogCardToHandCard(c: CardSummaryResponse): HandCard {
    const supertype = c.supertype ?? '';
    const isEnergy = supertype === 'Energy';
    const isPokemon = supertype === 'Pokemon' || supertype === 'Pokémon';
    const subtypes = c.subtypes ?? [];
    const isBasic = subtypes.includes('Basic');
    const types = c.types ?? [];
    const weakness = (c.weaknesses ?? [])[0];
    const resistance = (c.resistances ?? [])[0];
    return {
      instanceId: '',
      cardId: c.id,
      setId: c.setId,
      name: c.name,
      quantity: 1,
      type: isPokemon ? 'Pokemon' : (supertype || 'Pokemon'),
      subtype: subtypes.length > 0 ? subtypes.join(',') : null,
      basicEnergy: isEnergy && isBasic,
      basicPokemon: isPokemon && isBasic,
      aceSpec: false,
      hp: c.hp,
      attackDamage: null,
      attackRequiredEnergy: null,
      energyType: types[0] ?? null,
      retreatCost: c.retreatCost ?? 0,
      evolvesFrom: c.evolvesFrom,
      abilities: (c.abilities ?? []).map(a => a.name),
      weaknessType: weakness?.type ?? null,
      weaknessAmount: weakness ? parseInt(weakness.value.replace(/[^0-9]/g, ''), 10) || null : null,
      resistanceType: resistance?.type ?? null,
      resistanceAmount: resistance ? parseInt(resistance.value.replace(/[^0-9]/g, ''), 10) || null : null,
      attacks: c.attacks ?? [],
      image: c.imageUrl || this.resolveCardImageFromParts(c.setId, c.id),
    };
  }

  sandboxCardKind(card: HandCard): string {
    if (card.basicEnergy || card.type === 'Energy') return 'ENERGÍA';
    if (card.type === 'Trainer') return 'ENTRENADOR';
    if (card.basicPokemon) return 'BÁSICO';
    if (card.evolvesFrom) return 'EVOLUCIÓN';
    return 'POKÉMON';
  }

  sandboxTargetLabel(target: SandboxPickTarget): string {
    switch (target) {
      case 'HAND': return 'Mi mano';
      case 'MY_ACTIVE': return 'Mi activo';
      case 'MY_BENCH': return 'Mi banca';
      case 'OPP_ACTIVE': return 'Activo rival';
      case 'OPP_BENCH': return 'Banca rival';
    }
  }

  private attachEnergyFromCard(card: HandCard, targetSlot: string): void {
    if (!card.instanceId || !this.gameId) return;
    if (card.basicEnergy) {
      this.attachEnergy(card.instanceId, targetSlot, card);
    }
  }

  private attachEnergy(energyCardId: string, targetSlot: string, originalCard?: HandCard): void {
    if (!this.gameId) return;
    this.attachingEnergy = true;
    this.actionError = null;
    this.stateVersion++;
    this.gameService.executeAction(this.gameId, {
      actionType: 'ATTACH_ENERGY',
      energyCardId,
      targetSlot,
    }).subscribe({
      next: (state) => {
        this.attachingEnergy = false;
        this.stateVersion++;
        this.gameStateService.setState(state);
        this.fetchPlayerHand();
      },
      error: (err) => {
        this.actionError = err.error?.message || 'Error al adjuntar energía';
        this.attachingEnergy = false;
        if (originalCard) {
          this.restoreToHand(originalCard);
        }
        this.refreshGameState();
      },
    });
  }

  private buildCardSummary(card: PreviewCard): CardSummaryResponse | null {
    if (this.isHandCardWithFullData(card)) {
      return this.buildSummaryFromHandCard(card as HandCard);
    }
    const visibleInfo = this.findVisibleCardInfo(card);
    if (visibleInfo) {
      return this.buildSummaryFromVisibleInfo(visibleInfo, card.image);
    }
    const deckCards = this.deck?.cards ?? [];
    const lookupId = card.cardId ?? card.name;
    let match = deckCards.find(c => c.cardId === lookupId);
    if (!match && card.setId) {
      match = deckCards.find(c => c.name === card.name && c.setId === card.setId);
    }
    if (!match) {
      match = deckCards.find(c => c.name === card.name);
    }
    if (!match) {
      return {
        id: card.name,
        name: card.name,
        setId: 'xy1',
        supertype: card.image ? 'Pokemon' : 'Energy',
        subtypes: [],
        types: [],
        hp: null,
        attacks: [],
        weaknesses: [],
        resistances: [],
        retreatCost: 0,
        evolvesFrom: null,
        abilities: [],
        rules: [],
        imageUrl: card.image ?? '',
      };
    }
    return this.buildSummaryFromDeckCard(match, card.image);
  }

  private isHandCardWithFullData(card: PreviewCard): boolean {
    const hc = card as unknown as HandCard;
    return hc != null && hc.hp != null && hc.type != null;
  }

  private buildSummaryFromHandCard(hc: HandCard): CardSummaryResponse {
    const attacks: CardAttackResponse[] = hc.attacks && hc.attacks.length > 0
      ? hc.attacks
      : (hc.attackDamage != null && hc.attackName != null
        ? [{
            name: hc.attackName ?? 'Attack',
            text: hc.attackText ?? '',
            damage: String(hc.attackDamage ?? 0),
            cost: hc.attackCost ?? [],
            convertedEnergyCost: hc.attackRequiredEnergy ?? 0,
          }]
        : []);
    const abilities: CardAbilityResponse[] = (hc.abilities ?? []).map(a => typeof a === 'string' ? { name: a, text: '' } : a as CardAbilityResponse);
    const weaknesses = hc.weaknessType ? [{ type: hc.weaknessType, value: String(hc.weaknessAmount ?? 0) }] : [];
    const resistances = hc.resistanceType ? [{ type: hc.resistanceType, value: String(hc.resistanceAmount ?? 0) }] : [];
    const subtypes = hc.subtype ? hc.subtype.split(',').map(s => s.trim()).filter(s => s) : [];
    const types = hc.energyType ? [hc.energyType] : (hc.type ? [hc.type] : []);
    return {
      id: hc.cardId,
      name: hc.name,
      setId: hc.setId ?? 'xy1',
      supertype: hc.type === 'Energy' ? 'Energy' : (hc.type || 'Pokemon'),
      subtypes,
      types,
      hp: hc.hp,
      attacks,
      weaknesses,
      resistances,
      retreatCost: hc.retreatCost ?? 0,
      evolvesFrom: hc.evolvesFrom ?? null,
      abilities,
      rules: [],
      imageUrl: hc.image ?? '',
    };
  }

  private buildSummaryFromVisibleInfo(info: VisibleCardInfo, imageUrl: string): CardSummaryResponse {
    const subtypes: string[] = info.subtypes ? [...info.subtypes] : [];
    const types: string[] = info.energyType ? [info.energyType] : (info.supertype === 'Energy' ? [] : (info.supertype ? [info.supertype] : []));
    const attacks: CardAttackResponse[] = info.attacks && info.attacks.length > 0
      ? info.attacks
      : (info.attackDamage != null && info.attackName != null
        ? [{
            name: info.attackName ?? 'Attack',
            text: info.attackText ?? '',
            damage: String(info.attackDamage ?? 0),
            cost: info.attackCost ?? [],
            convertedEnergyCost: info.attackRequiredEnergy ?? 0,
          }]
        : []);
    const abilities: CardAbilityResponse[] = (info.abilities ?? []).map(a => typeof a === 'string' ? { name: a, text: '' } : a as CardAbilityResponse);
    const weaknesses = info.weaknessType ? [{ type: info.weaknessType, value: String(info.weaknessAmount ?? 0) }] : [];
    const resistances = info.resistanceType ? [{ type: info.resistanceType, value: String(info.resistanceAmount ?? 0) }] : [];
    return {
      id: info.cardId,
      name: info.name,
      setId: info.setId || 'xy1',
      supertype: info.supertype === 'Energy' ? 'Energy' : (info.supertype || 'Pokemon'),
      subtypes,
      types,
      hp: info.hp,
      attacks,
      weaknesses,
      resistances,
      retreatCost: info.retreatCost ?? 0,
      evolvesFrom: info.evolvesFrom ?? null,
      abilities,
      rules: [],
      imageUrl: imageUrl ?? '',
    };
  }

  private buildSummaryFromDeckCard(match: DeckCardInput, imageUrl: string | undefined): CardSummaryResponse {
    const attacks: CardAttackResponse[] = match.attacks && match.attacks.length > 0
      ? match.attacks
      : (match.attackDamage != null && match.attackName != null
        ? [{
            name: match.attackName ?? 'Attack',
            text: match.attackText ?? '',
            damage: String(match.attackDamage ?? 0),
            cost: match.attackCost ?? [],
            convertedEnergyCost: match.attackRequiredEnergy ?? 0,
          }]
        : []);
    return {
      id: match.cardId,
      name: match.name,
      setId: match.setId ?? 'xy1',
      supertype: match.type === 'Energy' ? 'Energy' : match.type,
      subtypes: match.subtype ? match.subtype.split(',').map(s => s.trim()).filter(s => s) : [],
      types: match.energyType ? [match.energyType] : (match.type ? [match.type] : []),
      hp: match.hp,
      attacks,
      weaknesses: match.weaknessType ? [{ type: match.weaknessType, value: String(match.weaknessAmount ?? 0) }] : [],
      resistances: match.resistanceType ? [{ type: match.resistanceType, value: String(match.resistanceAmount ?? 0) }] : [],
      retreatCost: match.retreatCost ?? 0,
      evolvesFrom: match.evolvesFrom ?? null,
      abilities: (match.abilities ?? []).map(a => typeof a === 'string' ? { name: a, text: '' } : a as any),
      rules: [],
      imageUrl: imageUrl ?? '',
    };
  }

  private findVisibleCardInfo(card: PreviewCard): VisibleCardInfo | null {
    const state = this.lastGameState;
    if (!state) return null;
    const myId = this.myPlayerId;
    const oppId = this.opponentPlayerId;
    const baseCardId = card.instanceId && card.instanceId.includes(':')
      ? card.instanceId.substring(0, card.instanceId.indexOf(':'))
      : (card.cardId ?? '');

    const allInfos: VisibleCardInfo[] = [];
    if (state.activePokemonInfo) {
      if (oppId && state.activePokemonInfo[oppId]) allInfos.push(state.activePokemonInfo[oppId]);
      if (myId && state.activePokemonInfo[myId]) allInfos.push(state.activePokemonInfo[myId]);
    }
    if (state.benchInfo) {
      for (const pid of Object.keys(state.benchInfo)) {
        const bench = state.benchInfo[pid];
        if (bench) {
          for (const c of bench) {
            if (c) allInfos.push(c);
          }
        }
      }
    }
    return allInfos.find(info => info.cardId === baseCardId || info.instanceId === card.instanceId) ?? null;
  }

  private previewedCardLocation(): { playerId: string; slot: string } | null {
    if (!this.previewedCard || !this.lastGameState) return null;
    const cardInstanceId = this.previewedCard.instanceId;
    const previewedCardId = this.previewedCard.cardId;
    if (!cardInstanceId && !previewedCardId) return null;

    const baseCardId = cardInstanceId && cardInstanceId.includes(':')
      ? cardInstanceId.substring(0, cardInstanceId.indexOf(':'))
      : cardInstanceId ?? previewedCardId ?? '';

    const playerIds = [this.myPlayerId, this.opponentPlayerId].filter((id): id is string => !!id);
    for (const playerId of playerIds) {
      const activeInfo = this.lastGameState.activePokemonInfo?.[playerId];
      if (activeInfo && (activeInfo.cardId === baseCardId || activeInfo.instanceId === cardInstanceId)) {
        return { playerId, slot: 'ACTIVE' };
      }
      const benchCards = this.lastGameState.benchInfo?.[playerId] ?? [];
      for (let i = 0; i < benchCards.length; i++) {
        const benchCard = benchCards[i];
        if (benchCard && (benchCard.cardId === baseCardId || benchCard.instanceId === cardInstanceId)) {
          return { playerId, slot: `BENCH_${i}` };
        }
      }
    }
    return null;
  }

  private getStatusForPreviewedCard(): StatusCondition | null {
    const location = this.previewedCardLocation();
    if (!location || location.slot !== 'ACTIVE' || !this.lastGameState) return null;
    return this.firstStatus(this.lastGameState.statusByPlayer?.[location.playerId]);
  }

  private firstStatus(value: unknown): StatusCondition | null {
    if (!value) return null;
    if (Array.isArray(value)) return (value[0] ?? null) as StatusCondition | null;
    if (typeof value === 'string') return value as StatusCondition;
    if (typeof value === 'object') return (Object.keys(value)[0] ?? null) as StatusCondition | null;
    return null;
  }

  private getEnergiesForPreviewedCard(): AttachedEnergy[] {
    const state = this.lastGameState;
    const location = this.previewedCardLocation();
    if (!state || !location) return [];
    const slots = state.attachedEnergiesByPlayer?.[location.playerId] ?? {};
    return (slots[location.slot] as AttachedEnergy[]) ?? [];
  }

  energyColor(energyType: string): string {
    const colors: Record<string, string> = {
      Fire: '#E8402E',
      Water: '#2B8ECE',
      Grass: '#3B9C42',
      Lightning: '#E7C94E',
      Psychic: '#A040A0',
      Fighting: '#C03028',
      Darkness: '#505050',
      Metal: '#808080',
      Fairy: '#E88EAD',
      Dragon: '#6C2FA0',
      Colorless: '#A8A878',
    };
    return colors[energyType] ?? '#999';
  }

  energySymbol(energyType: string): string {
    const symbols: Record<string, string> = {
      Fire: '🔥',
      Water: '💧',
      Grass: '🌿',
      Lightning: '⚡',
      Psychic: '🔮',
      Fighting: '🥊',
      Darkness: '🌑',
      Metal: '⚙',
      Fairy: '🧚',
      Dragon: '🐉',
      Colorless: '⭐',
    };
    return symbols[energyType] ?? '⚡';
  }

  retreatCostSymbol(count: number): string {
    return '⭐'.repeat(count) || '—';
  }

  energyImage(energyType: string): string {
    const map: Record<string, string> = {
      Fire: '/images/fire_energy_transparent.png',
      Water: '/images/water_energy_transparent.png',
      Grass: '/images/grass_energy_transparent.png',
      Lightning: '/images/lightning_energy_transparent.png',
      Psychic: '/images/psychic_energy_transparent.png',
      Fighting: '/images/fighting_energy_transparent.png',
      Darkness: '/images/darkness_energy_transparent.png',
      Metal: '/images/metal_energy_transparent.png',
      Fairy: '/images/fairy_energy_transparent.png',
      Dragon: '/images/dragon_energy_transparent.png',
      Colorless: '/images/colorless_energy_transparent.png',
    };
    return map[energyType] ?? '/images/colorless_energy_transparent.png';
  }

  get groupedPlayerActiveEnergies(): GroupedEnergy[] {
    return groupEnergiesByType(this.playerActiveEnergies);
  }

  get groupedOpponentActiveEnergies(): GroupedEnergy[] {
    return groupEnergiesByType(this.opponentActiveEnergies);
  }

  trackByEnergyCard(index: number, energy: AttachedEnergy): string {
    return energy.cardId;
  }

  trackByInstanceId(_index: number, item: { instanceId: string }): string {
    return item.instanceId;
  }

  trackByCardId(_index: number, item: { cardId: string }): string {
    return item.cardId;
  }

  trackByGroup(index: number, group: GroupedEnergy): string {
    return group.energyType;
  }

  groupedPlayerBenchEnergies(i: number): GroupedEnergy[] {
    return groupEnergiesByType(this.playerBenchEnergies[i] ?? []);
  }

  groupedOpponentBenchEnergies(i: number): GroupedEnergy[] {
    return groupEnergiesByType(this.opponentBenchEnergies[i] ?? []);
  }

  actionLabel(action: GameActionType): string {
    const labels: Record<string, string> = {
      DRAW: 'Robó carta',
      SETUP_PLACE_ACTIVE: 'Colocó activo',
      SETUP_PLACE_BENCH: 'Colocó en banca',
      SETUP_CONFIRM: 'Confirmó setup',
      BENCH_POKEMON: 'Bancó Pokémon',
      EVOLVE_POKEMON: 'Evolucionó',
      ATTACH_ENERGY: 'Adjuntó energía',
      PLAY_TRAINER: 'Jugó entrenador',
      PLAY_SUPPORTER: 'Jugó soporte',
      EQUIP_TOOL: 'Equipó herramienta',
      USE_ABILITY: 'Usó habilidad',
      RETREAT: 'Retiró',
      ATTACK: 'Atacó',
      END_TURN: 'Pasó turno',
      TAKE_PRIZE: 'Tomó premio',
      APPLY_SPECIAL_CONDITION: 'Condición especial',
      RESOLVE_BETWEEN_TURNS: 'Resolución entre turnos',
    };
    return labels[action] ?? action;
  }

  logTrainerResult(result: unknown, payload: unknown): string {
    const p = payload as Record<string, unknown> | null;
    const r = String(result ?? '');
    const map: Record<string, string> = {
      COIN_FLIPPED: p?.['heads'] ? '→ Cara' : '→ Cruz',
      POKEMON_TO_TOP_OF_DECK: '→ Pokémon al deck',
      NO_EFFECT_IMPLEMENTED: '→ Sin efecto',
      NO_POKEMON_IN_DISCARD: '→ Sin Pokémon en descarte',
      STADIUM_ACTIVE: '→ Stadium activo',
      BASIC_ENERGY_SEARCHED: '→ Energía buscada',
    };
    return map[r] ?? `→ ${r}`;
  }

  playerDisplayName(playerId: string): string {
    if (playerId === this.myPlayerId) return this.player.name;
    if (playerId === this.opponentPlayerId) return this.opponent.name;
    return playerId.slice(0, 6);
  }

  get damageModifiers(): { label: string; value: number }[] {
    if (!this.attackResult) return [];
    const modifiers: { label: string; value: number }[] = [];
    for (const entry of this.attackResult.auditTrail) {
      if (entry.startsWith('ModifierStep:')) {
        const m = entry.match(/attacker_modifier=(\d+)/);
        if (m && parseInt(m[1]) > 0) {
          modifiers.push({ label: 'Bonus atacante', value: parseInt(m[1]) });
        }
        const d = entry.match(/defender_modifier=(-?\d+)/);
        if (d && parseInt(d[1]) < 0) {
          modifiers.push({ label: 'Reducción defensora', value: parseInt(d[1]) });
        }
      }
      if (entry.startsWith('PreAttackStep:')) {
        const b = entry.match(/bonus_applied=(\d+)/);
        if (b && parseInt(b[1]) > 0) {
          modifiers.push({ label: 'Bonus pre-ataque', value: parseInt(b[1]) });
        }
      }
      if (entry.startsWith('AttackEffect:')) {
        const cb = entry.match(/coin_bonus_damage=(\d+)/);
        if (cb && parseInt(cb[1]) > 0) {
          modifiers.push({ label: 'Bonus moneda', value: parseInt(cb[1]) });
        }
        const cd = entry.match(/conditional_bonus=(\d+)/);
        if (cd && parseInt(cd[1]) > 0) {
          modifiers.push({ label: 'Bonus condicional', value: parseInt(cd[1]) });
        }
      }
    }
    return modifiers;
  }

  get damageAuditEntries(): string[] {
    if (!this.attackResult) return [];
    const entries: string[] = [];
    for (const raw of this.attackResult.auditTrail) {
      const readable = this.mapAuditEntry(raw);
      if (readable) entries.push(readable);
    }
    return entries;
  }

  private mapAuditEntry(entry: string): string | null {
    if (entry.endsWith(':start') || entry.endsWith(':ok')) return null;
    if (entry.startsWith('EnergyValidationStep:cancelled')) return '⛔ Energía insuficiente';
    if (entry.startsWith('ConfusionCheckStep:skip_not_confused')) return 'Sin confusión';
    if (entry.startsWith('ConfusionCheckStep:heads_continue')) return 'Moneda confusión: CARA';
    if (entry.startsWith('ConfusionCheckStep:tails_cancelled')) return 'Moneda confusión: CRUZ';
    if (entry.startsWith('SelectionStep:target_selected=default')) return 'Objetivo: Activo rival';
    if (entry.startsWith('SelectionStep:target_selected=BENCH_')) {
      const idx = entry.match(/BENCH_(\d+)/)?.[1];
      return `Objetivo: Banca rival #${idx ? parseInt(idx) + 1 : '?'}`;
    }
    if (entry.startsWith('PreAttackStep:no_pre_damage_coin_flip')) return 'Sin moneda extra';
    if (entry.startsWith('PreAttackStep:coin_flips=[')) {
      const flips = entry.match(/\[(.*?)\]/)?.[1];
      return `Monedas: ${flips}`;
    }
    if (entry.startsWith('PreAttackStep:attack_cancelled')) return 'Ataque cancelado';
    if (entry.startsWith('PreAttackStep:bonus_applied=')) {
      return `Bonus extra: +${entry.split('=')[1]}`;
    }
    if (entry.startsWith('DamageCalculationStep:damage=')) {
      return `Daño calculado: ${entry.split('=')[1]}`;
    }
    if (entry.startsWith('DamageCalculationStep:cancelled')) return 'Daño cancelado';
    if (entry.startsWith('PostDamageEffectsStep:effects_applied')) return 'Efectos de ataque aplicados';
    if (entry.startsWith('AttackEffect:no_special_effect')) return 'Sin efecto secundario';
    if (entry.startsWith('AttackEffect:coin_bonus_damage=')) {
      return `Bonus moneda extra: +${entry.split('=')[1]}`;
    }
    if (entry.startsWith('AttackEffect:coin_flip_damage_heads=')) {
      const amt = entry.match(/amount=(\d+)/)?.[1];
      return `Daño por moneda: ${amt}`;
    }
    if (entry.startsWith('AttackEffect:conditional_damage=')) {
      return `Daño condicional: ${entry.split('=')[1]}`;
    }
    if (entry.startsWith('AttackEffect:conditional_bonus=')) {
      return `Bonus condicional: +${entry.split('=')[1]}`;
    }
    if (entry.startsWith('AttackEffect:bench_damage')) {
      return 'Daño a Banca';
    }
    if (entry.startsWith('AttackEffect:heal_counters=')) {
      return `Cura: ${entry.split('=')[1]} contadores`;
    }
    if (entry.startsWith('AttackEffect:conditions=[')) {
      const conds = entry.match(/\[(.*?)\]/)?.[1];
      return `Condición aplicada: ${conds}`;
    }
    if (entry.startsWith('AttackEffect:energy_discard_none')) return 'Sin energía para descartar';
    if (entry.startsWith('AttackEffect:energy_discarded=')) {
      const count = entry.match(/energy_discarded=(\d+)/)?.[1];
      return `Energías descartadas: ${count ?? '?'}`;
    }
    if (entry.startsWith('AttackEffect:energy_attached=')) {
      const count = entry.match(/energy_attached=(\d+)/)?.[1];
      return `Energías adjuntadas: ${count ?? '?'}`;
    }
    if (entry.startsWith('AttackEffect:draw_cards=')) {
      const count = entry.match(/draw_cards=(\d+)/)?.[1];
      return `Robo por efecto: ${count ?? '?'} carta(s)`;
    }
    if (entry.startsWith('AttackEffect:deck_to_hand=')) {
      const count = entry.match(/deck_to_hand=(\d+)/)?.[1];
      return `Búsqueda al mazo: ${count ?? '?'} carta(s)`;
    }
    if (entry.startsWith('AttackEffect:discard_top_card=')) return 'Descartó la carta superior del mazo';
    if (entry.startsWith('AttackEffect:discard_to_hand=')) {
      const count = entry.match(/discard_to_hand=(\d+)/)?.[1];
      return `Recuperó del descarte: ${count ?? '?'} carta(s)`;
    }
    if (entry.startsWith('AttackEffect:discard_to_deck=')) return 'Devolvió una carta del descarte al mazo';
    if (entry.startsWith('AttackEffect:energy_moved=')) return 'Energía movida entre Pokémon';
    if (entry.startsWith('AttackEffect:switch_active_bench=')) return 'Cambio entre Activo y Banca';
    if (entry.startsWith('AttackEffect:persistent_')) return 'Efecto persistente aplicado';
    if (entry.startsWith('AttackEffect:utility_applied')) return 'Efecto de utilidad';
    if (entry.startsWith('AttackEffect:bench_damage_no_target')) return 'Sin objetivo en Banca';
    return null;
  }

  private fetchActionLog(): void {
    if (!this.gameId) return;
    this.gameService.getLogs(this.gameId).subscribe({
      next: (res) => {
        this.actionLog = (res.entries ?? []).slice(-30).reverse();
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }
}
