'use client';

import type { User } from '@/types';
import { useI18n } from '@/lib/i18n';
import { StatusBadge } from '@/components/ui/StatusBadge';

/** Account verification pill for organisation portals. `verifiedLabel` lets each portal say "Verified unit" / "Verified organization". */
export function VerificationBadge({ status, verifiedLabel }: { status?: User['verificationStatus']; verifiedLabel: string }) {
  const { t } = useI18n();
  if (status === 'VERIFIED') return <StatusBadge value="VERIFIED" label={verifiedLabel} />;
  return (
    <StatusBadge
      value={status ?? 'PENDING'}
      label={status === 'REJECTED' ? t('dash.rejectedVerification') : t('dash.pendingVerification')}
    />
  );
}
