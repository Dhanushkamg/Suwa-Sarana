import type { BadgeTone } from '@/components/ui/StatusBadge';

const KNOWN = ['PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'NO_RESPONSE', 'COMPLETED'] as const;

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
      return 'failed';
    default:
      return 'neutral';
  }
}
