import { Injectable } from '@angular/core';
import { DeckResponse } from '../models/deck.models';

const STORAGE_KEY = 'pokemon_decks';

@Injectable({ providedIn: 'root' })
export class DeckStorageService {
  getAll(): DeckResponse[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  getByPlayer(playerId: string): DeckResponse[] {
    return this.getAll().filter(d => d.playerId === playerId);
  }

  save(deck: DeckResponse): void {
    const decks = this.getAll();
    const index = decks.findIndex(d => d.id === deck.id);
    if (index >= 0) {
      decks[index] = deck;
    } else {
      decks.push(deck);
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(decks));
  }

  remove(deckId: string): void {
    const decks = this.getAll().filter(d => d.id !== deckId);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(decks));
  }
}
