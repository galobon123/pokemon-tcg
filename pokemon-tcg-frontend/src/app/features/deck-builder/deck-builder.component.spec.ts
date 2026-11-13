import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CardSummaryResponse } from '../../core/models/card.models';
import { DeckCardInput, DeckResponse, DeckUpsertRequest } from '../../core/models/deck.models';
import { AuthService, User } from '../../core/services/auth.service';
import { DeckService } from '../../core/services/deck.service';
import { DeckBuilderComponent } from './deck-builder.component';

describe('DeckBuilderComponent', () => {
  const card: CardSummaryResponse = {
    id: 'xy1-1',
    name: 'Venusaur-EX',
    setId: 'xy1',
    supertype: 'Pokemon',
    subtypes: ['Basic', 'EX'],
    types: ['Grass'],
    hp: 180,
    attacks: [{ name: 'Poison Powder', text: '', damage: '60', cost: ['Grass'], convertedEnergyCost: 3 }],
    weaknesses: [],
    resistances: [],
    retreatCost: 0,
    evolvesFrom: null,
    abilities: [],
    rules: [],
    imageUrl: '',
  };

  let deckService: Pick<DeckService, 'create' | 'update' | 'get'>;
  let createDeck: ReturnType<typeof vi.fn<(request: DeckUpsertRequest) => ReturnType<DeckService['create']>>>;
  let updateDeck: ReturnType<
    typeof vi.fn<(deckId: string, request: DeckUpsertRequest) => ReturnType<DeckService['update']>>
  >;
  let getDeck: ReturnType<typeof vi.fn<(deckId: string) => ReturnType<DeckService['get']>>>;
  let authService: Pick<AuthService, 'getCurrentUser'>;
  let getCurrentUser: ReturnType<typeof vi.fn<() => User | null>>;

  beforeEach(async () => {
    createDeck = vi.fn();
    updateDeck = vi.fn();
    getDeck = vi.fn();
    deckService = {
      create: createDeck,
      update: updateDeck,
      get: getDeck,
    };
    getCurrentUser = vi.fn();
    authService = { getCurrentUser };

    await TestBed.configureTestingModule({
      imports: [DeckBuilderComponent],
      providers: [
        provideHttpClient(),
        { provide: DeckService, useValue: deckService },
        { provide: AuthService, useValue: authService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
  });

  it('adds and removes cards from the deck', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;

    component.addCard(card);
    component.addCard(card);
    component.decreaseQuantity(card.id);

    expect(component.entries()).toEqual([{ card, quantity: 1 }]);
  });


  it('selects a deck and loads its cards', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { queryParamMap: convertToParamMap({ deckId: 'deck-1' }) } },
    });
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    const storedCard = storedDeckCard();
    getDeck.mockReturnValue(
      of({
        id: 'deck-1',
        playerId: 'player-1',
        name: 'Mazo guardado',
        cards: [storedCard],
        validation: { valid: true, errors: [] },
      } satisfies DeckResponse),
    );

    component.ngOnInit();

    expect(getDeck).toHaveBeenCalledWith('deck-1');
    expect(component.entries()).toEqual([
      expect.objectContaining({
        quantity: 2,
        card: expect.objectContaining({ id: storedCard.cardId, name: storedCard.name }),
      }),
    ]);
    expect(component.deckName()).toBe('Mazo guardado');
  });

  it('shows local deck rules when the deck cannot be saved', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    getCurrentUser.mockReturnValue(validUser());

    component.addCard(card);
    component.saveDeck({ name: 'Mazo principal' });

    expect(createDeck).not.toHaveBeenCalled();
    expect(component.saveError()).toBe(
      'No se puede guardar el mazo: no se cumplen las condiciones listadas.',
    );
    expect(component.validationErrors()).toContain('El mazo debe tener exactamente 60 cartas. Ahora tiene 1.');
  });

  it('shows a local error when player id is not a uuid', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    component.entries.set(validEntries());
    getCurrentUser.mockReturnValue({ playerId: 'player-1', username: 'Ash', token: 'jwt' });

    createDeck.mockReturnValue(
      of({
        id: 'deck-1',
        playerId: 'player-1',
        name: 'Mazo principal',
        cards: [],
        validation: { valid: true, errors: [] },
      } satisfies DeckResponse),
    );

    component.saveDeck({ name: 'Mazo principal' });

    expect(createDeck).toHaveBeenCalled();
  });

  it('sends card metadata when saving a valid deck', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    component.entries.set(validEntries());
    getCurrentUser.mockReturnValue(validUser());
    createDeck.mockReturnValue(
      of({
        id: 'deck-1',
        playerId: 'player-1',
        name: 'Mazo principal',
        cards: [
          {
            cardId: 'xy1-1',
            name: 'Card 1',
            setId: 'xy1',
            quantity: 4,
            type: 'Grass',
            subtype: 'Basic',
            basicEnergy: false,
            basicPokemon: true,
            aceSpec: false,
            hp: 180,
            attackDamage: 60,
            attackRequiredEnergy: 3,
          },
        ],
        validation: { valid: true, errors: [] },
      } satisfies DeckResponse),
    );

    component.saveDeck({ name: 'Mazo principal' });

    expect(createDeck).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Mazo principal',
        cards: expect.arrayContaining([
          expect.objectContaining({
            cardId: 'xy1-1',
            name: 'Card 1',
            setId: 'xy1',
            quantity: 4,
            type: 'Grass',
            subtype: 'Basic',
            basicEnergy: false,
            basicPokemon: true,
            aceSpec: false,
            hp: 180,
            attackDamage: 60,
            attackRequiredEnergy: 3,
          }),
        ]),
      }),
    );
    expect(component.saveMessage()).toBe('Mazo guardado: Mazo principal');
  });

  it('updates the selected deck when saving', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    component.entries.set(validEntries());
    component.selectedDeckId.set('deck-1');
    getCurrentUser.mockReturnValue(validUser());
    updateDeck.mockReturnValue(
      of({
        id: 'deck-1',
        playerId: 'player-1',
        name: 'Mazo actualizado',
        cards: [],
        validation: { valid: true, errors: [] },
      } satisfies DeckResponse),
    );

    component.saveDeck({ name: 'Mazo actualizado' });

    expect(updateDeck).toHaveBeenCalledWith(
      'deck-1',
      expect.objectContaining({
        name: 'Mazo actualizado',
      }),
    );
  });

  it('shows the backend condition when the deck cannot be saved', () => {
    const fixture = TestBed.createComponent(DeckBuilderComponent);
    const component = fixture.componentInstance;
    component.entries.set(validEntries());
    getCurrentUser.mockReturnValue(validUser());
    createDeck.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'El mazo debe tener exactamente 60 cartas.' },
          }),
      ),
    );

    component.saveDeck({ name: 'Mazo principal' });

    expect(component.saveError()).toBe(
      'No se pudo guardar el mazo: El mazo debe tener exactamente 60 cartas.',
    );
    expect(component.validationErrors()).toEqual(['El mazo debe tener exactamente 60 cartas.']);
    expect(component.saving()).toBe(false);
  });

  function validEntries() {
    return Array.from({ length: 15 }, (_, index) => ({
      card: {
        ...card,
        id: `xy1-${index + 1}`,
        name: `Card ${index + 1}`,
      },
      quantity: 4,
    }));
  }

  function storedDeckCard(): DeckCardInput {
    return {
      cardId: 'xy1-1',
      name: 'Stored Card',
      setId: 'xy1',
      quantity: 2,
      type: 'Grass',
      subtype: 'Basic',
      basicEnergy: false,
      basicPokemon: true,
      aceSpec: false,
      hp: 70,
      attackDamage: 20,
      attackRequiredEnergy: 1,
    };
  }

  function validUser(): User {
    return { playerId: '11111111-1111-4111-8111-111111111111', username: 'Ash', token: 'jwt' };
  }
});
