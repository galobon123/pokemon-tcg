export interface TypeModifierResponse {
  type: string;
  value: string;
}

export interface CardAttackResponse {
  name: string;
  text: string;
  damage: string;
  cost: string[];
  convertedEnergyCost: number;
}

export interface CardAbilityResponse {
  name: string;
  text: string;
}

export interface CardSummaryResponse {
  id: string;
  name: string;
  setId: string;
  supertype: string;
  subtypes: string[];
  types: string[];
  hp: number | null;
  attacks: CardAttackResponse[];
  weaknesses: TypeModifierResponse[];
  resistances: TypeModifierResponse[];
  retreatCost: number | null;
  evolvesFrom: string | null;
  abilities: CardAbilityResponse[];
  rules: string[];
  imageUrl: string;
}

export interface CardSearchResponse {
  query: string;
  pageSize: number;
  count: number;
  totalCount: number;
  cards: CardSummaryResponse[];
}