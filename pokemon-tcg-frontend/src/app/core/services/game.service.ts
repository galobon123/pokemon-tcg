import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ActionLogEntry,
  BenchPokemonRequest,
  CreateGameRequest,
  EvolvePokemonRequest,
  GameActionRequest,
  GameStateResponse,
  GameSyncResponse,
  DeckCardsResponse,
  AttackRequirementsResponse,
  HandResponse,
  DeckPreviewResponse,
  JoinGameRequest,
  MatchHistoryEntry,
  PlayTrainerRequest,
  UseAbilityRequest,
} from '../models/game.models';

@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.gameServiceUrl}/api/games`;

  create(request: CreateGameRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(this.baseUrl, request);
  }

  join(gameId: string, request: JoinGameRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/join`, request);
  }

  coinFlip(gameId: string, choice: 'HEADS' | 'TAILS'): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/coin-flip`, { choice });
  }

  chooseFirstPlayer(gameId: string, first: boolean): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/choose-first`, { first });
  }

  setup(gameId: string, request: {
    playerOneDeckSize?: number;
    playerOneBasicCount?: number;
    playerTwoDeckSize?: number;
    playerTwoBasicCount?: number;
    playerOneDeckId?: string;
    playerTwoDeckId?: string;
    interactive: boolean;
  }): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/setup`, request);
  }

  setupPlaceActive(gameId: string, cardInstanceId: string): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/setup/place-active`, { cardInstanceId });
  }

  setupPlaceBench(gameId: string, cardInstanceIds: string[]): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/setup/place-bench`, { cardInstanceIds });
  }

  setupConfirm(gameId: string): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/setup/confirm`, {});
  }

  benchPokemon(gameId: string, request: BenchPokemonRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions/bench-pokemon`, request);
  }

  evolvePokemon(gameId: string, request: EvolvePokemonRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions/evolve`, request);
  }

  playTrainer(gameId: string, request: PlayTrainerRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions/play-trainer`, request);
  }

  useAbility(gameId: string, request: UseAbilityRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions/use-ability`, request);
  }

  replaceActive(gameId: string, benchSlot: number): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions/replace-active`, {
      benchSlot,
    });
  }

  executeAction(gameId: string, request: GameActionRequest): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/actions`, request);
  }

  getState(gameId: string): Observable<GameStateResponse> {
    return this.http.get<GameStateResponse>(`${this.baseUrl}/${gameId}`);
  }

  sync(gameId: string): Observable<GameSyncResponse> {
    return this.http.get<GameSyncResponse>(`${this.baseUrl}/${gameId}/sync`);
  }

  getHand(gameId: string, playerId: string): Observable<HandResponse> {
    return this.http.get<HandResponse>(`${this.baseUrl}/${gameId}/players/${playerId}/hand`);
  }

  getDeckPreview(gameId: string, playerId: string, limit = 7): Observable<DeckPreviewResponse> {
    return this.http.get<DeckPreviewResponse>(`${this.baseUrl}/${gameId}/players/${playerId}/deck-preview?limit=${limit}`);
  }

  getDeck(gameId: string, playerId: string): Observable<DeckCardsResponse> {
    return this.http.get<DeckCardsResponse>(`${this.baseUrl}/${gameId}/players/${playerId}/deck`);
  }

  getAttackRequirements(gameId: string, attackIndex: number): Observable<AttackRequirementsResponse> {
    return this.http.get<AttackRequirementsResponse>(`${this.baseUrl}/${gameId}/attacks/${attackIndex}/requirements`);
  }

  getLogs(gameId: string): Observable<{ entries: ActionLogEntry[] }> {
    return this.http.get<{ entries: ActionLogEntry[] }>(`${this.baseUrl}/${gameId}/logs`);
  }

  concede(gameId: string): Observable<GameStateResponse> {
    return this.http.post<GameStateResponse>(`${this.baseUrl}/${gameId}/concede`, {});
  }

  getHistory(): Observable<MatchHistoryEntry[]> {
    return this.http.get<MatchHistoryEntry[]>(`${environment.gameServiceUrl}/api/history`);
  }

  clearHistory(): Observable<void> {
    return this.http.delete<void>(`${environment.gameServiceUrl}/api/history`);
  }
}
