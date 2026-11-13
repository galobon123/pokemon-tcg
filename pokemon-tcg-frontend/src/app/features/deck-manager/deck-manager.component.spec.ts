import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DeckResponse } from '../../core/models/deck.models';
import { AuthService, User } from '../../core/services/auth.service';
import { DeckService } from '../../core/services/deck.service';
import { DeckManagerComponent } from './deck-manager.component';

describe('DeckManagerComponent', () => {
  let deckService: Pick<DeckService, 'listMine'>;
  let listMine: ReturnType<typeof vi.fn<() => ReturnType<DeckService['listMine']>>>;
  let authService: Pick<AuthService, 'getCurrentUser'>;
  let getCurrentUser: ReturnType<typeof vi.fn<() => User | null>>;
  let router: Pick<Router, 'navigate'>;
  let navigate: ReturnType<typeof vi.fn<(commands: unknown[], extras?: unknown) => Promise<boolean>>>;

  beforeEach(async () => {
    listMine = vi.fn();
    deckService = { listMine };
    getCurrentUser = vi.fn();
    authService = { getCurrentUser };
    navigate = vi.fn().mockResolvedValue(true);
    router = { navigate };

    await TestBed.configureTestingModule({
      imports: [DeckManagerComponent],
      providers: [
        provideHttpClient(),
        { provide: DeckService, useValue: deckService },
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
  });

  it('loads decks for the current player', () => {
    const fixture = TestBed.createComponent(DeckManagerComponent);
    const component = fixture.componentInstance;
    getCurrentUser.mockReturnValue(validUser());
    listMine.mockReturnValue(
      of([
        {
          id: 'deck-1',
          playerId: 'player-1',
          name: 'Mazo principal',
          cards: [],
          validation: { valid: true, errors: [] },
        } satisfies DeckResponse,
      ]),
    );

    component.ngOnInit();

    expect(listMine).toHaveBeenCalled();
    expect(component.decks()).toHaveLength(1);
  });

  it('shows a status message when redirected after create', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { queryParamMap: convertToParamMap({ status: 'created' }) } },
    });
    const fixture = TestBed.createComponent(DeckManagerComponent);
    const component = fixture.componentInstance;
    getCurrentUser.mockReturnValue(validUser());
    listMine.mockReturnValue(of([]));

    component.ngOnInit();

    expect(component.statusMessage()).toBe('Mazo creado!');
  });

  it('navigates to the builder when selecting a deck', () => {
    const fixture = TestBed.createComponent(DeckManagerComponent);
    const component = fixture.componentInstance;

    component.selectDeck('deck-1');

    expect(navigate).toHaveBeenCalledWith(['/deck-builder'], { queryParams: { deckId: 'deck-1' } });
  });

  it('stores an error when deck loading fails', () => {
    const fixture = TestBed.createComponent(DeckManagerComponent);
    const component = fixture.componentInstance;
    getCurrentUser.mockReturnValue(validUser());
    listMine.mockReturnValue(throwError(() => ({ status: 500 })));

    component.ngOnInit();

    expect(component.decksError()).toContain('No se pudieron cargar los mazos');
  });

  function validUser(): User {
    return { playerId: '11111111-1111-4111-8111-111111111111', username: 'Ash', token: 'jwt' };
  }
});
