import type { BadgeTone } from '@/components/ui/StatusBadge';

const KNOWN = ['PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'NO_RESPONSE', 'COMPLETED', 'NO_SHOW'] as const;

/**
 * The status to show for a match: an ACCEPTED match whose outcome the owner recorded becomes COMPLETED ("Donated") or
 * NO_SHOW ("Did not donate"); the server keeps the status ACCEPTED and sends `donated: true | false`.
 */
export function effectiveMatchStatus(status: string, donated?: boolean): string {
  if (status === 'ACCEPTED' && donated === true) return 'COMPLETED';
  if (status === 'ACCEPTED' && donated === false) return 'NO_SHOW';
  return status;
}

/** Localised label for a match status; unknown values from a newer backend get a neutral generic label instead of a raw key. */
export function matchStatusLabel(t: (key: string) => string, status: string): string {
  return t(`dashUi.matchStatus.${(KNOWN as readonly string[]).includes(status) ? status : 'UNKNOWN'}`);
}

export function matchStatusTone(status: string): BadgeTone {
  switch (status) {
    case 'ACCEPTED':
    case 'COMPLETED':
      return 'success';
    case 'PENDING':
      return 'pending';
    case 'DECLINED':
    case 'NO_SHOW':
      return 'failed';
    default:
      return 'neutral';
  }
}
