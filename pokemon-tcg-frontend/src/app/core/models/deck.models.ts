import type { CardAttackResponse } from './card.models';

export interface DeckCardInput {
  cardId: string;
  name: string;
  setId: string | null;
  quantity: number;
  type: string;
  subtype: string | null;
  basicEnergy: boolean;
  basicPokemon: boolean;
  aceSpec: boolean;
  hp: number | null;
  attackDamage: number | null;
  attackRequiredEnergy: number | null;
  attackName?: string | null;
  attackText?: string | null;
  energyType?: string | null;
  attackCost?: string[];
  retreatCost?: number;
  evolvesFrom?: string | null;
  abilities?: string[];
  weaknessType?: string | null;
  weaknessAmount?: number | null;
  resistanceType?: string | null;
  resistanceAmount?: number | null;
  rules?: string[];
  attacks?: CardAttackResponse[];
}

export interface DeckValidationResult {
  valid: boolean;
  errors: string[];
}

export interface DeckResponse {
  id: string;
  playerId: string;
  name: string;
  cards: DeckCardInput[];
  validation: DeckValidationResult;
}

export interface DeckUpsertRequest {
  name: string;
  cards: DeckCardInput[];
}
