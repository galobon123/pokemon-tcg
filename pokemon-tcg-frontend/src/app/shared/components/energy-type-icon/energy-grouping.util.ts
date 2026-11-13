import { EnergyType } from './energy-type-icon.component';

export interface GroupedEnergy {
  energyType: EnergyType;
  count: number;
}

export function groupEnergiesByType(energies: { energyType: string }[]): GroupedEnergy[] {
  const map = new Map<string, number>();
  for (const e of energies) {
    const key = e.energyType || 'Unknown';
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  const result: GroupedEnergy[] = [];
  const order: EnergyType[] = [
    'Fire', 'Water', 'Grass', 'Lightning', 'Psychic',
    'Fighting', 'Darkness', 'Metal', 'Fairy', 'Dragon', 'Colorless',
    'Double', 'Rainbow',
  ];
  for (const type of order) {
    const count = map.get(type);
    if (count) {
      result.push({ energyType: type, count });
    }
  }
  for (const [type, count] of map) {
    if (!order.includes(type as EnergyType)) {
      result.push({ energyType: type as EnergyType, count });
    }
  }
  return result;
}