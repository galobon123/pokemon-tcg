import { Component, OnInit, OnDestroy, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { filter, Subject, takeUntil } from 'rxjs';
import { ModalService } from '../../core/services/modal.service';
import { DeckService } from '../../core/services/deck.service';
import { DeckStorageService } from '../../core/services/deck-storage.service';
import { DeckResponse } from '../../core/models/deck.models';
import { MatchHistoryEntry } from '../../core/models/game.models';
import { AuthService } from '../../core/services/auth.service';
import { GameService } from '../../core/services/game.service';
import { ConfirmationModalComponent } from '../shared/confirmation-modal.component';
import { DeckViewOverlayComponent } from '../shared/deck-view-overlay.component';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ConfirmationModalComponent, DeckViewOverlayComponent],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.scss'
})
export class LobbyComponent implements OnInit, OnDestroy {
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private modalService = inject(ModalService);
  private deckService = inject(DeckService);
  private deckStorage = inject(DeckStorageService);
  private authService = inject(AuthService);
  private gameService = inject(GameService);

  currentUser: {
    name: string;
    level: number;
    avatar: string;
    playerId: string;
  } = {
    name: 'Entrenador Pokémon',
    level: 25,
    avatar: 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png',
    playerId: '',
  };

  showUserDashboard = false;
  showAvatarSelection = false;
  availableAvatars: string[] = [
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png', // Pikachu
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png',  // Bulbasaur
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/4.png',  // Charmander
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/7.png',  // Squirtle
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/133.png',// Eevee
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/151.png',// Mew
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/94.png', // Gengar
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/143.png',// Snorlax
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/175.png',// Togepi
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/448.png',// Lucario
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png',
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/master-ball.png',
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/ultra-ball.png',
    'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/rare-candy.png',
  ];

  showDeckSelection = false;
  isDeckLoading = false;
  decksLoaded = false;
  deckLoadError: string | null = null;
  availableDecks: DeckResponse[] = [];
  selectedDeckId: string | null = null;
  showDeckPreview = false;
  previewDeck: DeckResponse | null = null;
  deckSelectionMode: 'local' | 'online' | null = null;

  // Game creation / join state
  showGameCode = false;
  createdGameId: string | null = null;
  gameCreateError: string | null = null;
  isCreatingGame = false;
  gameCodeCopied = false;
  joinGameId = '';
  showJoinDeckSelection = false;
  joinDeckError: string | null = null;
  isJoiningGame = false;

  darkMode = false;

  // Partida online activa (si el usuario salió al Lobby sin rendirse)
  activeGameId: string | null = null;

  // Match history
  matchHistory: MatchHistoryEntry[] = [];
  isHistoryLoading = false;
  historyLoadError: string | null = null;
  isClearingHistory = false;
  private destroy$ = new Subject<void>();

  ngOnInit() {
    const saved = localStorage.getItem('app-dark-mode');
    this.darkMode = saved === 'true';
    this.activeGameId = localStorage.getItem('active_game_id');
    this.loadUserFromStorage();
    this.loadDecks();
    this.loadMatchHistory();

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd && event.urlAfterRedirects === '/lobby'),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      // Al volver al Lobby refrescamos también la partida activa: pudo haber
      // terminado/concedido (clave removida) o iniciado en otra pestaña.
      this.activeGameId = localStorage.getItem('active_game_id');
      this.loadMatchHistory();
    });
  }

  goToActiveGame(): void {
    if (this.activeGameId) {
      this.playClick();
      this.router.navigate(['/battle-arena/game', this.activeGameId]);
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadUserFromStorage() {
    try {
      const user = this.authService.getCurrentUser();
      if (user) {
        this.currentUser.name = user.username;
        this.currentUser.playerId = user.playerId;
        if (user.avatar) {
          this.currentUser.avatar = user.avatar;
        }
      }
    } catch (error) {
      console.error('Error loading user from storage:', error);
    }
  }

  openAvatarSelection() {
    this.playClick();
    this.showAvatarSelection = true;
  }

  closeAvatarSelection() {
    this.showAvatarSelection = false;
  }

  changeAvatar(avatarUrl: string) {
    this.playClick();
    this.authService.updateAvatar(avatarUrl);
    this.currentUser.avatar = avatarUrl;
    this.closeAvatarSelection();
    this.cdr.detectChanges();
  }

  private loadDecks(): void {
    const user = this.authService.getCurrentUser();
    if (!user?.token) return;
    this.isDeckLoading = true;
    this.deckService.listMine().subscribe({
      next: (decks) => {
        this.availableDecks = decks;
        this.isDeckLoading = false;
        this.decksLoaded = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isDeckLoading = false;
        this.decksLoaded = true;
        this.cdr.detectChanges();
      },
    });
  }

  private loadMatchHistory(): void {
    const user = this.authService.getCurrentUser();
    if (!user?.token) return;
    this.isHistoryLoading = true;
    this.historyLoadError = null;
    this.gameService.getHistory().subscribe({
      next: (history) => {
        this.matchHistory = history;
        this.isHistoryLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isHistoryLoading = false;
        this.historyLoadError = 'No se pudo cargar el historial.';
        this.cdr.detectChanges();
      },
    });
  }

  formatDate(isoString: string): string {
    if (!isoString) return '';
    const d = new Date(isoString);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    const hours = String(d.getHours()).padStart(2, '0');
    const mins = String(d.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year} ${hours}:${mins}`;
  }

  async clearHistory(): Promise<void> {
    const confirmed = await this.modalService.openModal({
      title: 'Borrar historial',
      message: '¿Estás seguro de que deseas borrar todo el historial de partidas?',
      confirmText: 'Sí, borrar',
      cancelText: 'No, volver'
    });

    if (confirmed) {
      this.isClearingHistory = true;
      this.gameService.clearHistory().subscribe({
        next: () => {
          this.isClearingHistory = false;
          this.matchHistory = [];
          this.cdr.detectChanges();
        },
        error: () => {
          this.isClearingHistory = false;
          this.cdr.detectChanges();
        }
      });
    }
  }

  reasonLabel(reason: string): string {
    switch (reason) {
      case 'PRIZES': return 'Premios';
      case 'KO_TOTAL': return 'Sin Pokémon';
      case 'DECK_OUT': return 'Sin Mazo';
      case 'CONCEDE': return 'Rendición';
      default: return 'Desconocido';
    }
  }

  playClick() {
    const audio = new Audio('sounds/click.mp3');
    const volume = localStorage.getItem('sound-volume');
    audio.volume = volume !== null ? Number(volume) / 100 : 1;
    audio.play().catch(err => console.error('Error al reproducir sonido:', err));
  }

  private openDeckSelection(mode: 'local' | 'online'): void {
    this.playClick();
    this.deckSelectionMode = mode;
    this.selectedDeckId = null;
    this.deckLoadError = null;

    const user = this.authService.getCurrentUser();
    if (!user?.token) {
      this.deckLoadError = mode === 'online'
        ? 'Debes iniciar sesion para crear una partida.'
        : 'Debes iniciar sesion para ver tus mazos guardados.';
    }

    this.showDeckSelection = true;
    this.closeDeckPreview();
  }

  createRoom() {
    this.openDeckSelection('local');
  }

  openCreateGame(): void {
    this.openDeckSelection('online');
  }

  closeDeckSelection() {
    this.showDeckSelection = false;
    this.deckSelectionMode = null;
    this.selectedDeckId = null;
    this.isDeckLoading = false;
    this.deckLoadError = null;
    this.closeDeckPreview();
  }

  selectDeck(deckId: string) {
    this.selectedDeckId = this.selectedDeckId === deckId ? null : deckId;
  }

  openDeckPreview(deck: DeckResponse, event?: Event): void {
    event?.stopPropagation();
    this.previewDeck = deck;
    this.showDeckPreview = true;
  }

  closeDeckPreview(): void {
    this.showDeckPreview = false;
    this.previewDeck = null;
  }

  confirmCreateRoom() {
    if (!this.selectedDeckId) return;
    this.playClick();

    const selectedDeck = this.availableDecks.find(d => d.id === this.selectedDeckId);
    if (selectedDeck) {
      this.deckStorage.save(selectedDeck);
    }

    this.showDeckSelection = false;
    this.closeDeckPreview();
    this.router.navigate(['/battle-arena', this.selectedDeckId]);
  }

  // ── Game creation & join ─────────────────────────────────────────────────

  confirmCreateGame(): void {
    if (!this.selectedDeckId) return;
    this.playClick();
    this.isCreatingGame = true;
    this.gameCreateError = null;

    const selectedDeck = this.availableDecks.find(d => d.id === this.selectedDeckId);
    if (selectedDeck) {
      this.deckStorage.save(selectedDeck);
    }

    this.gameService.create({ deckId: this.selectedDeckId }).subscribe({
      next: (state) => {
        this.isCreatingGame = false;
        this.createdGameId = state.gameId;
        this.showDeckSelection = false;
        this.gameCodeCopied = false;
        this.showGameCode = true;
        this.cdr.detectChanges();
      },
      error: (error: unknown) => {
        this.isCreatingGame = false;
        this.gameCreateError = this.getLoadErrorMessage(error);
        this.cdr.detectChanges();
      },
    });
  }

  closeGameCode(): void {
    this.showGameCode = false;
    this.createdGameId = null;
    this.gameCreateError = null;
  }

  goToCreatedGame(): void {
    if (!this.createdGameId) return;
    const gameId = this.createdGameId;
    const deckId = this.selectedDeckId;
    this.closeGameCode();
    this.router.navigate(['/battle-arena/game', gameId], {
      queryParams: { deckId },
    });
  }

  copyGameCode(): void {
    if (!this.createdGameId) return;
    navigator.clipboard.writeText(this.createdGameId).then(() => {
      this.gameCodeCopied = true;
      this.cdr.detectChanges();
      setTimeout(() => {
        this.gameCodeCopied = false;
        this.cdr.detectChanges();
      }, 2000);
    }).catch(() => {
      // fallback: seleccionar manualmente
      const el = document.querySelector('.game-code-value') as HTMLElement;
      if (el) {
        const range = document.createRange();
        range.selectNodeContents(el);
        const sel = window.getSelection();
        sel?.removeAllRanges();
        sel?.addRange(range);
      }
    });
  }

  openJoinGame(): void {
    this.playClick();
    if (!this.joinGameId.trim()) return;

    this.availableDecks = [];
    this.joinDeckError = null;
    this.selectedDeckId = null;
    this.showJoinDeckSelection = true;
    this.closeDeckPreview();

    const user = this.authService.getCurrentUser();
    if (!user?.token) {
      this.joinDeckError = 'Debes iniciar sesion para unirte a una partida.';
      return;
    }

    this.isDeckLoading = true;
    this.deckService.listMine().subscribe({
      next: (decks) => {
        this.availableDecks = decks;
        this.isDeckLoading = false;
        this.cdr.detectChanges();
      },
      error: (error: unknown) => {
        const message = this.getLoadErrorMessage(error);
        this.joinDeckError = `No se pudieron cargar los mazos: ${message}`;
        this.isDeckLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  closeJoinDeckSelection(): void {
    this.showJoinDeckSelection = false;
    this.selectedDeckId = null;
    this.isDeckLoading = false;
    this.joinDeckError = null;
  }

  confirmJoinGame(): void {
    if (!this.selectedDeckId || !this.joinGameId.trim()) return;
    this.playClick();
    this.isJoiningGame = true;

    const selectedDeck = this.availableDecks.find(d => d.id === this.selectedDeckId);
    if (selectedDeck) {
      this.deckStorage.save(selectedDeck);
    }

    this.gameService.join(this.joinGameId.trim(), { deckId: this.selectedDeckId }).subscribe({
      next: () => {
        this.isJoiningGame = false;
        this.showJoinDeckSelection = false;
        this.router.navigate(['/battle-arena/game', this.joinGameId.trim()], {
          queryParams: { deckId: this.selectedDeckId },
        });
      },
      error: (error: unknown) => {
        this.isJoiningGame = false;
        this.joinDeckError = this.getLoadErrorMessage(error);
        this.cdr.detectChanges();
      },
    });
  }

  deckCardCount(deck: DeckResponse): number {
    return deck.cards.reduce((total, card) => total + card.quantity, 0);
  }

  openDecks() {
    this.playClick();
  }

  toggleUserDashboard() {
    this.showUserDashboard = !this.showUserDashboard;
  }

  closeUserDashboard() {
    this.showUserDashboard = false;
  }

  logout() {
    this.playClick();
    this.showLogoutConfirmation();
  }

  private async showLogoutConfirmation() {
    const confirmed = await this.modalService.openModal({
      title: 'Confirmar cierre de sesión',
      message: '¿Estás seguro de que deseas cerrar sesión?',
      confirmText: 'Sí, cerrar',
      cancelText: 'No, volver'
    });

    if (confirmed) {
      this.performLogout();
    } else {
      this.closeUserDashboard();
    }
  }

  private performLogout() {
    this.authService.logout();
    
    // Navegar al login usando el Router de Angular
    this.router.navigate(['/login']);
  }

  openSettings() {
    this.playClick();
    this.closeUserDashboard();
    this.router.navigate(['/settings']);
  }

  private getLoadErrorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = (error as { status?: number }).status;
      if (status === 0) {
        return 'el navegador no pudo conectarse con game-service; revisa que el backend este levantado y CORS este activo.';
      }

      if (typeof status === 'number') {
        return `game-service respondio HTTP ${status}.`;
      }
    }

    return 'game-service rechazo la solicitud, pero no envio un motivo legible.';
  }


  toggleDarkMode() {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }
}
