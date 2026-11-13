import { describe, expect, it } from 'vitest';
import { resolveWinReasonMessage, WIN_REASON_MESSAGES_FOR_TESTING } from './win-reason-message';

describe('resolveWinReasonMessage', () => {
  it('returns the concede message for the winner', () => {
    expect(resolveWinReasonMessage('CONCEDE', 'winner'))
      .toBe('Ganaste por rendición del oponente');
  });

  it('returns the concede message for the loser', () => {
    expect(resolveWinReasonMessage('CONCEDE', 'loser'))
      .toBe('Perdiste por rendición');
  });

  it('returns the prizes message for the winner', () => {
    expect(resolveWinReasonMessage('PRIZES', 'winner'))
      .toBe('Ganaste por cartas de premio');
  });

  it('returns the prizes message for the loser', () => {
    expect(resolveWinReasonMessage('PRIZES', 'loser'))
      .toBe('Perdiste por cartas de premio');
  });

  it('returns the ko_total message for the winner', () => {
    expect(resolveWinReasonMessage('KO_TOTAL', 'winner'))
      .toBe('Ganaste por knock-out total');
  });

  it('returns the ko_total message for the loser', () => {
    expect(resolveWinReasonMessage('KO_TOTAL', 'loser'))
      .toBe('Perdiste por knock-out total');
  });

  it('returns the deck_out message for the winner', () => {
    expect(resolveWinReasonMessage('DECK_OUT', 'winner'))
      .toBe('Ganaste porque tu oponente se quedó sin cartas');
  });

  it('returns the deck_out message for the loser', () => {
    expect(resolveWinReasonMessage('DECK_OUT', 'loser'))
      .toBe('Perdiste porque te quedaste sin cartas');
  });

  it('returns a generic victory for the winner on UNKNOWN', () => {
    expect(resolveWinReasonMessage('UNKNOWN', 'winner'))
      .toBe('Victoria');
  });

  it('returns a generic defeat for the loser on UNKNOWN', () => {
    expect(resolveWinReasonMessage('UNKNOWN', 'loser'))
      .toBe('Derrota');
  });

  it('falls back to UNKNOWN for null or undefined reasons', () => {
    expect(resolveWinReasonMessage(null, 'winner')).toBe('Victoria');
    expect(resolveWinReasonMessage(undefined, 'loser')).toBe('Derrota');
  });

  it('returns the inactivity message for both winner and loser', () => {
    expect(resolveWinReasonMessage('INACTIVITY', 'winner'))
      .toBe('Partida cancelada por inactividad');
    expect(resolveWinReasonMessage('INACTIVITY', 'loser'))
      .toBe('Partida cancelada por inactividad');
  });

  it('covers every WinReason key in the table', () => {
    // Triangulation: ensure no key is missing a mapping.
    const reasons = Object.keys(WIN_REASON_MESSAGES_FOR_TESTING) as Array<
      keyof typeof WIN_REASON_MESSAGES_FOR_TESTING
    >;
    expect(reasons).toEqual(
      expect.arrayContaining(['CONCEDE', 'PRIZES', 'KO_TOTAL', 'DECK_OUT', 'UNKNOWN', 'INACTIVITY'])
    );
    for (const reason of reasons) {
      const messages = WIN_REASON_MESSAGES_FOR_TESTING[reason];
      expect(messages.winner).toBeTruthy();
      expect(messages.loser).toBeTruthy();
    }
  });
});
