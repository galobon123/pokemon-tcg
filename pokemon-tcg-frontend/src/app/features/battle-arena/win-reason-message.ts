import type { WinReason } from '../../core/models/game.models';

export interface WinReasonMessages {
  winner: string;
  loser: string;
}

const WIN_REASON_MESSAGES: Record<WinReason, WinReasonMessages> = {
  CONCEDE: {
    winner: 'Ganaste por rendición del oponente',
    loser: 'Perdiste por rendición',
  },
  PRIZES: {
    winner: 'Ganaste por cartas de premio',
    loser: 'Perdiste por cartas de premio',
  },
  KO_TOTAL: {
    winner: 'Ganaste por knock-out total',
    loser: 'Perdiste por knock-out total',
  },
  DECK_OUT: {
    winner: 'Ganaste porque tu oponente se quedó sin cartas',
    loser: 'Perdiste porque te quedaste sin cartas',
  },
  UNKNOWN: {
    winner: 'Victoria',
    loser: 'Derrota',
  },
  INACTIVITY: {
    winner: 'Partida cancelada por inactividad',
    loser: 'Partida cancelada por inactividad',
  },
};

export function resolveWinReasonMessage(
  reason: WinReason | null | undefined,
  perspective: 'winner' | 'loser'
): string {
  const normalized = (reason ?? 'UNKNOWN') as WinReason;
  const messages = WIN_REASON_MESSAGES[normalized] ?? WIN_REASON_MESSAGES.UNKNOWN;
  return messages[perspective];
}

export const WIN_REASON_MESSAGES_FOR_TESTING = WIN_REASON_MESSAGES;
