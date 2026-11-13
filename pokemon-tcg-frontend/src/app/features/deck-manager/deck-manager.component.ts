import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

import { DeckCardInput, DeckResponse } from '../../core/models/deck.models';
import { CardAttackResponse, CardSummaryResponse } from '../../core/models/card.models';
import { AuthService } from '../../core/services/auth.service';
import { DeckService } from '../../core/services/deck.service';
import { CardDetailOverlayComponent } from '../deck-builder/card-catalog/card-detail-overlay/card-detail-overlay.component';

@Component({
  selector: 'app-deck-manager',
  standalone: true,
  imports: [CommonModule, RouterModule, CardDetailOverlayComponent],
  templateUrl: './deck-manager.component.html',
  styleUrl: './deck-manager.component.scss',
})
export class DeckManagerComponent implements OnInit {
  private readonly deckService = inject(DeckService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly decks = signal<DeckResponse[]>([]);
  readonly loadingDecks = signal(false);
  readonly selectingDeckId = signal<string | null>(null);
  readonly decksError = signal<string | null>(null);
  readonly statusMessage = signal<string | null>(null);
  readonly selectedPreviewCard = signal<CardSummaryResponse | null>(null);
  darkMode = false;

  ngOnInit(): void {
    const saved = localStorage.getItem('app-dark-mode');
    this.darkMode = saved === 'true';
    this.readStatusMessage();
    this.loadDecks();
  }

  toggleDarkMode(): void {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }

  loadDecks(): void {
    const user = this.authService.getCurrentUser();
    if (!user?.token) {
      this.decksError.set('Debes iniciar sesion para ver tus mazos guardados.');
      return;
    }

    this.loadingDecks.set(true);
    this.decksError.set(null);

    this.deckService.listMine().subscribe({
      next: (decks) => {
        this.decks.set(decks);
        this.loadingDecks.set(false);
      },
      error: (error: unknown) => {
        const message = this.getLoadErrorMessage(error);
        this.decksError.set(`No se pudieron cargar los mazos: ${message}`);
        this.loadingDecks.set(false);
      },
    });
  }

  selectDeck(deckId: string): void {
    if (!deckId) {
      return;
    }

    this.selectingDeckId.set(deckId);
    this.router.navigate(['/deck-builder'], { queryParams: { deckId } }).finally(() => {
      this.selectingDeckId.set(null);
    });
  }

  createNewDeck(): void {
    this.router.navigate(['/deck-builder']).catch(() => {
      this.decksError.set('No se pudo abrir el creador de mazos.');
    });
  }

  totalCards(deck: DeckResponse): number {
    return deck.cards.reduce((total, card) => total + card.quantity, 0);
  }

  cardImage(card: DeckCardInput): string {
    if (!card.setId || !card.cardId) {
      return '/images/cardBack.png';
    }

    const prefix = `${card.setId}-`;
    const cardRef = card.cardId.startsWith(prefix)
      ? card.cardId.slice(prefix.length)
      : card.cardId.split('-').pop();

    return cardRef
      ? `https://images.pokemontcg.io/${card.setId}/${cardRef}.png`
      : '/images/cardBack.png';
  }

  onPreviewImageError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (img) img.src = '/images/cardBack.png';
  }

  openPreviewCard(card: DeckCardInput): void {
    this.selectedPreviewCard.set(this.toCardSummary(card));
  }

  closePreviewCard(): void {
    this.selectedPreviewCard.set(null);
  }

  private toCardSummary(card: DeckCardInput): CardSummaryResponse {
    return {
      id: card.cardId,
      name: card.name,
      setId: card.setId ?? '',
      supertype: this.inferSupertype(card),
      subtypes: this.buildSubtypes(card),
      types: this.buildTypes(card),
      hp: card.hp ?? null,
      attacks: this.buildAttacks(card),
      weaknesses: card.weaknessType ? [{ type: card.weaknessType, value: this.modifierValue(card.weaknessAmount, 'x') }] : [],
      resistances: card.resistanceType ? [{ type: card.resistanceType, value: this.modifierValue(card.resistanceAmount, '-') }] : [],
      retreatCost: card.retreatCost ?? null,
      evolvesFrom: card.evolvesFrom ?? null,
      abilities: (card.abilities ?? []).map((ability) => ({ name: ability, text: '' })),
      rules: [],
      imageUrl: this.cardImage(card),
    };
  }

  private buildAttacks(card: DeckCardInput): CardAttackResponse[] {
    if (card.attacks?.length) {
      return card.attacks;
    }

    if (card.attackName || card.attackText || card.attackDamage !== null || card.attackRequiredEnergy !== null) {
      return [
        {
          name: card.attackName || 'Ataque guardado',
          text: card.attackText || '',
          damage: card.attackDamage === null || card.attackDamage === undefined ? '' : String(card.attackDamage),
          cost: card.attackCost ?? [],
          convertedEnergyCost: card.attackRequiredEnergy ?? card.attackCost?.length ?? 0,
        },
      ];
    }

    return [];
  }

  private buildSubtypes(card: DeckCardInput): string[] {
    const subtypes = [card.subtype ?? '', card.basicPokemon || card.basicEnergy ? 'Basic' : '', card.aceSpec ? 'ACE SPEC' : '']
      .filter(Boolean);
    return [...new Set(subtypes)];
  }

  private buildTypes(card: DeckCardInput): string[] {
    if (card.energyType) {
      return [card.energyType];
    }

    const type = card.type ?? '';
    return ['Trainer', 'Energy'].includes(type) ? [] : [type].filter(Boolean);
  }

  private inferSupertype(card: DeckCardInput): string {
    const normalizedType = (card.type ?? '').trim().toLowerCase();
    if (normalizedType === 'energy' || card.basicEnergy) return 'Energy';
    if (normalizedType === 'trainer') return 'Trainer';
    return 'Pokémon';
  }

  private modifierValue(amount: number | null | undefined, prefix: 'x' | '-'): string {
    if (amount === null || amount === undefined) {
      return prefix === 'x' ? 'x2' : '-20';
    }

    return prefix === 'x' ? `x${amount}` : `-${amount}`;
  }

  private readStatusMessage(): void {
    const status = this.route.snapshot.queryParamMap.get('status');
    if (status === 'created') {
      this.statusMessage.set('Mazo creado!');
      return;
    }

    if (status === 'updated') {
      this.statusMessage.set('Mazo editado!');
      return;
    }

    this.statusMessage.set(null);
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
}
