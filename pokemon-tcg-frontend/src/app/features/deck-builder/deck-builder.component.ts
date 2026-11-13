import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { CardAttackResponse, CardSummaryResponse } from '../../core/models/card.models';
import { DeckCardInput, DeckResponse } from '../../core/models/deck.models';
import { AuthService } from '../../core/services/auth.service';
import { DeckService } from '../../core/services/deck.service';
import { DeckStorageService } from '../../core/services/deck-storage.service';
import { CardCatalogComponent } from './card-catalog/card-catalog.component';
import { DeckEditorComponent, SaveDeckForm } from './deck-editor/deck-editor.component';
import { DeckEntry } from './deck-entry.model';

@Component({
  selector: 'app-deck-builder',
  imports: [CommonModule, RouterModule, CardCatalogComponent, DeckEditorComponent],
  templateUrl: './deck-builder.component.html',
  styleUrl: './deck-builder.component.scss',
})
export class DeckBuilderComponent implements OnInit {
  private readonly deckService = inject(DeckService);
  private readonly deckStorage = inject(DeckStorageService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly entries = signal<DeckEntry[]>([]);
  readonly deckName = signal('Mazo principal');
  readonly selectedDeckId = signal<string | null>(null);
  readonly validationErrors = signal<string[]>([]);
  readonly saveError = signal<string | null>(null);
  readonly saveMessage = signal<string | null>(null);
  readonly saving = signal(false);
  darkMode = false;

  ngOnInit(): void {
    const saved = localStorage.getItem('app-dark-mode');
    this.darkMode = saved === 'true';
    const deckId = this.route.snapshot.queryParamMap.get('deckId');

    if (deckId) {
      this.selectDeck(deckId);
    }
  }

  toggleDarkMode(): void {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }

  addCard(card: CardSummaryResponse): void {
    if (this.totalCards() >= 60) {
      return;
    }

    this.entries.update((entries) => {
      const existingEntry = entries.find((entry) => entry.card.id === card.id);

      if (existingEntry) {
        return entries.map((entry) =>
          entry.card.id === card.id ? { ...entry, quantity: entry.quantity + 1 } : entry,
        );
      }

      return [...entries, { card, quantity: 1 }];
    });
  }

  increaseQuantity(cardId: string): void {
    if (this.totalCards() >= 60) {
      return;
    }

    this.entries.update((entries) =>
      entries.map((entry) =>
        entry.card.id === cardId ? { ...entry, quantity: entry.quantity + 1 } : entry,
      ),
    );
  }

  decreaseQuantity(cardId: string): void {
    this.entries.update((entries) =>
      entries
        .map((entry) =>
          entry.card.id === cardId ? { ...entry, quantity: entry.quantity - 1 } : entry,
        )
        .filter((entry) => entry.quantity > 0),
    );
  }

  selectDeck(deckId: string): void {
    if (!deckId) {
      return;
    }

    this.saveMessage.set(null);
    this.saveError.set(null);

    this.deckService.get(deckId).subscribe({
      next: (deck) => {
        this.selectedDeckId.set(deck.id);
        this.entries.set(this.toEntries(deck));
        this.deckName.set(deck.name);
      },
      error: (error: unknown) => {
        const message = this.getSaveErrorMessage(error);
        this.saveError.set(`No se pudo cargar el mazo: ${message}`);
      },
    });
  }

  saveDeck(form: SaveDeckForm): void {
    const user = this.authService.getCurrentUser();
    if (!user?.token) {
      this.validationErrors.set(['Debes iniciar sesion antes de guardar un mazo.']);
      this.saveError.set('No se puede guardar el mazo: falta el token de sesion.');
      this.saveMessage.set(null);
      this.saving.set(false);
      return;
    }

    const cards = this.toDeckCards();
    const validationErrors = this.validateDeck(cards);

    if (validationErrors.length > 0) {
      this.validationErrors.set(validationErrors);
      this.saveError.set('No se puede guardar el mazo: no se cumplen las condiciones listadas.');
      this.saveMessage.set(null);
      this.saving.set(false);
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);
    this.saveMessage.set(null);
    this.validationErrors.set([]);

    const request = {
      name: form.name,
      cards,
    };

    const selectedDeckId = this.selectedDeckId();
    const wasUpdate = Boolean(selectedDeckId);
    const saveAction = selectedDeckId
      ? this.deckService.update(selectedDeckId, request)
      : this.deckService.create(request);

    saveAction.subscribe({
      next: (deck) => this.handleSavedDeck(deck, wasUpdate),
      error: (error: unknown) => {
        console.error('Save deck error:', error);
        const message = this.getSaveErrorMessage(error);
        this.saveError.set(`No se pudo guardar el mazo: ${message}`);
        this.validationErrors.set([message]);
        this.saving.set(false);
      },
    });
  }

  private totalCards(): number {
    return this.entries().reduce((total, entry) => total + entry.quantity, 0);
  }

  private handleSavedDeck(deck: DeckResponse, wasUpdate: boolean): void {
    this.selectedDeckId.set(deck.id);
    this.deckName.set(deck.name);
    this.validationErrors.set(deck.validation.valid ? [] : deck.validation.errors);
    this.saveMessage.set(deck.validation.valid ? `Mazo guardado: ${deck.name}` : null);
    this.saveError.set(
      deck.validation.valid
        ? null
        : 'El backend guardo la respuesta, pero marco reglas de mazo incumplidas.',
    );
    this.saving.set(false);

    if (deck.validation.valid) {
      this.deckStorage.save(deck);
      const status = wasUpdate ? 'updated' : 'created';
      this.router.navigate(['/deck-manager'], { queryParams: { status } }).catch(() => {
        this.saveError.set('No se pudo volver a la lista de mazos.');
      });
    }
  }

  private toEntries(deck: DeckResponse): DeckEntry[] {
    return deck.cards.map((card) => ({
      card: this.toCardSummary(card),
      quantity: card.quantity,
    }));
  }

  private toCardSummary(card: DeckCardInput): CardSummaryResponse {
    return {
      id: card.cardId,
      name: card.name,
      setId: card.setId ?? '',
      supertype: this.inferSupertype(card),
      subtypes: this.buildSubtypes(card),
      types: card.type ? [card.type] : [],
      hp: card.hp ?? null,
      attacks: this.buildAttacks(card),
      weaknesses: [],
      resistances: [],
      retreatCost: card.retreatCost ?? 0,
      evolvesFrom: card.evolvesFrom ?? null,
      abilities: (card.abilities ?? []).map(a => typeof a === 'string' ? { name: a, text: '' } : a),
      rules: [],
      imageUrl: '',
    };
  }

  private buildAttacks(card: DeckCardInput): CardAttackResponse[] {
    if (card.attackDamage === null && card.attackRequiredEnergy === null) {
      return [];
    }

    return [
      {
        name: 'Ataque guardado',
        text: '',
        damage: card.attackDamage === null ? '' : String(card.attackDamage),
        cost: [],
        convertedEnergyCost: card.attackRequiredEnergy ?? 0,
      },
    ];
  }

  private buildSubtypes(card: DeckCardInput): string[] {
    const subtypes: string[] = [];

    if (card.subtype) {
      card.subtype.split(',').forEach(s => {
        const trimmed = s.trim();
        if (trimmed) subtypes.push(trimmed);
      });
    }

    if (card.basicPokemon || card.basicEnergy) {
      subtypes.push('Basic');
    }

    if (card.aceSpec) {
      subtypes.push('ACE SPEC');
    }

    return [...new Set(subtypes)];
  }

  private inferSupertype(card: DeckCardInput): string {
    const normalizedType = this.normalize(card.type ?? '');

    if (normalizedType === 'energy' || card.basicEnergy) {
      return 'Energy';
    }

    if (normalizedType === 'trainer') {
      return 'Trainer';
    }

    return 'Pokemon';
  }

  private toDeckCards(): DeckCardInput[] {
    return this.entries().map((entry) => {
      const card = entry.card;
      const primaryAttack = card.attacks[0];
      const weakness = card.weaknesses[0];
      const resistance = card.resistances[0];
      return {
        cardId: card.id,
        name: card.name,
        setId: card.setId,
        quantity: entry.quantity,
        type: card.types[0] ?? card.supertype,
        subtype: card.subtypes.length > 0 ? card.subtypes.join(',') : null,
        basicEnergy: this.hasSubtype(card, 'Basic') && this.isSupertype(card, 'Energy'),
        basicPokemon: this.hasSubtype(card, 'Basic') && this.isSupertype(card, 'Pokemon'),
        aceSpec: this.hasSubtype(card, 'ACE SPEC'),
        hp: card.hp,
        attackDamage: this.parseAttackDamage(card),
        attackRequiredEnergy: primaryAttack?.convertedEnergyCost ?? null,
        attackName: primaryAttack?.name ?? null,
        attackText: primaryAttack?.text ?? null,
        energyType: primaryAttack?.cost[0] ?? card.types[0] ?? null,
        attackCost: primaryAttack?.cost ?? [],
        retreatCost: card.retreatCost ?? 0,
        evolvesFrom: card.evolvesFrom ?? null,
        abilities: card.abilities.map((ability) => ability.name),
        weaknessType: weakness?.type ?? null,
        weaknessAmount: weakness ? Number.parseInt(weakness.value, 10) || null : null,
        resistanceType: resistance?.type ?? null,
        resistanceAmount: resistance ? Number.parseInt(resistance.value, 10) || null : null,
        attacks: card.attacks,
      };
    });
  }

  private validateDeck(cards: DeckCardInput[]): string[] {
    const errors: string[] = [];
    const total = cards.reduce((sum, card) => sum + card.quantity, 0);

    if (total !== 60) {
      errors.push(`El mazo debe tener exactamente 60 cartas. Ahora tiene ${total}.`);
    }

    if (!cards.some((card) => card.basicPokemon)) {
      errors.push('El mazo debe incluir al menos 1 Pokemon Basico.');
    }

    cards
      .filter((card) => !card.basicEnergy && card.quantity > 4)
      .forEach((card) => errors.push(`La carta '${card.name}' excede el maximo de 4 copias.`));

    cards
      .filter((card) => !this.isRequiredSet(card))
      .forEach((card) => errors.push(`La carta '${card.name}' no pertenece al set obligatorio xy1.`));

    const aceSpecCount = cards
      .filter((card) => card.aceSpec)
      .reduce((sum, card) => sum + card.quantity, 0);

    if (aceSpecCount > 1) {
      errors.push('Solo se permite 1 carta AS TACTICO en todo el mazo.');
    }

    return errors;
  }

  private getSaveErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'el navegador no pudo conectarse con game-service; revisa que el backend este levantado y CORS este activo.';
      }

      return this.readBackendMessage(error.error) ?? `game-service respondio HTTP ${error.status}.`;
    }

    return 'game-service rechazo la solicitud, pero no envio un motivo legible.';
  }

  private readBackendMessage(errorBody: unknown): string | null {
    if (this.hasMessage(errorBody)) {
      return errorBody.message;
    }

    if (typeof errorBody === 'string' && errorBody.trim()) {
      return errorBody;
    }

    return null;
  }

  private hasMessage(value: unknown): value is { message: string } {
    return (
      typeof value === 'object' &&
      value !== null &&
      'message' in value &&
      typeof value.message === 'string' &&
      value.message.trim().length > 0
    );
  }

  private parseAttackDamage(card: CardSummaryResponse): number | null {
    const damage = card.attacks[0]?.damage.match(/\d+/)?.[0];
    return damage === undefined ? null : Number(damage);
  }

  private hasSubtype(card: CardSummaryResponse, subtype: string): boolean {
    return card.subtypes.some((cardSubtype) => this.normalize(cardSubtype) === this.normalize(subtype));
  }

  private isSupertype(card: CardSummaryResponse, supertype: string): boolean {
    return this.normalize(card.supertype) === this.normalize(supertype);
  }

  private isRequiredSet(card: DeckCardInput): boolean {
    const setId = card.setId?.trim() || card.cardId.split('-')[0] || '';
    return setId.toLowerCase() === 'xy1';
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  }
}
