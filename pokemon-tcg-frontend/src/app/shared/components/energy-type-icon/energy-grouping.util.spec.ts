import { describe, expect, it } from 'vitest';
import { groupEnergiesByType } from './energy-grouping.util';

describe('groupEnergiesByType', () => {
  it('agrupa energías del mismo tipo', () => {
    const result = groupEnergiesByType([
      { energyType: 'Fire' },
      { energyType: 'Fire' },
      { energyType: 'Water' },
    ]);
    const fire = result.find(g => g.energyType === 'Fire');
    const water = result.find(g => g.energyType === 'Water');
    expect(fire?.count).toBe(2);
    expect(water?.count).toBe(1);
  });

  it('devuelve lista vacía para entrada vacía', () => {
    expect(groupEnergiesByType([])).toEqual([]);
  });

  it('respeta el orden canónico de tipos', () => {
    const result = groupEnergiesByType([
      { energyType: 'Colorless' },
      { energyType: 'Fire' },
      { energyType: 'Water' },
    ]);
    const types = result.map(g => g.energyType);
    expect(types.indexOf('Fire')).toBeLessThan(types.indexOf('Colorless'));
    expect(types.indexOf('Water')).toBeLessThan(types.indexOf('Colorless'));
  });

  it('incluye tipos Double y Rainbow', () => {
    const result = groupEnergiesByType([
      { energyType: 'Double' },
      { energyType: 'Rainbow' },
    ]);
    const types = result.map(g => g.energyType);
    expect(types).toContain('Double');
    expect(types).toContain('Rainbow');
  });

  it('tipos desconocidos van al final', () => {
    const result = groupEnergiesByType([
      { energyType: 'Fire' },
      { energyType: 'CustomType' },
    ]);
    const types = result.map(g => g.energyType);
    expect(types.indexOf('Fire')).toBeLessThan((types as string[]).indexOf('CustomType'));
  });

  it('usa "Unknown" cuando energyType es vacío', () => {
    const result = groupEnergiesByType([{ energyType: '' }]);
    expect(result[0].energyType).toBe('Unknown');
  });

  it('cuenta correctamente energías de múltiples tipos', () => {
    const energies = [
      { energyType: 'Fire' },
      { energyType: 'Water' },
      { energyType: 'Fire' },
      { energyType: 'Grass' },
      { energyType: 'Water' },
      { energyType: 'Water' },
    ];
    const result = groupEnergiesByType(energies);
    expect(result.find(g => g.energyType === 'Fire')?.count).toBe(2);
    expect(result.find(g => g.energyType === 'Water')?.count).toBe(3);
    expect(result.find(g => g.energyType === 'Grass')?.count).toBe(1);
  });

  it('una sola energía devuelve count 1', () => {
    const result = groupEnergiesByType([{ energyType: 'Lightning' }]);
    expect(result).toHaveLength(1);
    expect(result[0].count).toBe(1);
  });

  it('agrupa Rainbow y la cuenta correctamente', () => {
    const result = groupEnergiesByType([
      { energyType: 'Rainbow' },
      { energyType: 'Rainbow' },
    ]);
    expect(result.find(g => g.energyType === 'Rainbow')?.count).toBe(2);
  });
});
