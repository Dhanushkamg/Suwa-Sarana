'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { AlertCircle, Bell, CheckCircle2, ListOrdered, Plus, Radio } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { homeForRole } from '@/lib/auth';
import { fetchRequests } from '@/lib/api/requests';
import { useUnreadCount } from '@/lib/api/notifications';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { MatchesDrawer } from '@/components/dash/MatchesDrawer';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { RequestRow } from '@/components/dash/RequestRow';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { SectionHeader } from '@/components/dash/Panel';
import { Stat } from '@/components/dash/Stat';
import { CARD, TEXT_LINK } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { Users } from 'lucide-react';
import type { BloodRequest } from '@/types';

const fetchOwn = () => fetchRequests(false);

/** Home for plain requesters (patients and families): their request numbers, the latest requests, and one clear next step. */
export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { t } = useI18n();
  const requests = useAsync(fetchOwn);
  const unread = useUnreadCount();
  const [matchesFor, setMatchesFor] = useState<BloodRequest | null>(null);

  useEffect(() => {
    // Only plain requesters stay here; every other role has its own home.
    const home = homeForRole(user?.role);
    if (user && home !== '/dashboard') router.replace(home);
  }, [user, router]);

  const list = requests.state.status === 'ready' ? requests.state.data : [];
  const ready = requests.state.status === 'ready';
  const active = list.filter((r) => r.status === 'OPEN' || r.status === 'ESCALATING' || r.status === 'MATCHED').length;
  const fulfilled = list.filter((r) => r.status === 'FULFILLED').length;
  const recent = list.slice(0, 3);

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.overview')}
        title={t('dashboard.welcome')}
        description={t('dashboard.requesterSubtitle')}
        actions={
          <>
            <RefreshButton onClick={requests.reload} loading={requests.state.status === 'loading'} />
            <ButtonLink href="/dashboard/requests/new" variant="primary">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('dashboard.newBloodRequest')}
            </ButtonLink>
          </>
        }
      />

      <div className="space-y-12">
        {requests.state.status === 'loading' ? (
          <StatsSkeleton count={3} className="sm:grid-cols-3" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-3">
            <Rise delay={0.04}>
              <Stat label={t('dashboard.activeRequests')} value={ready ? active : null} tone="primary" icon={<Radio />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('dash.fulfilledRequests')} value={ready ? fulfilled : null} tone="success" icon={<CheckCircle2 />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Link href="/dashboard/notifications" className="block h-full rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                <Stat label={t('common.notifications')} value={unread} icon={<Bell />} hint={unread === null ? undefined : t('dashUi.notif.unreadHint')} className="h-full transition-colors hover:border-line-strong" />
              </Link>
            </Rise>
          </div>
        )}

        <section>
          <SectionHeader
            eyebrow={t('dashUi.eyebrow.requests')}
            title={t('dashUi.req.latest')}
            action={
              <Link href="/dashboard/requests" className={TEXT_LINK}>
                <ListOrdered className="h-4 w-4" aria-hidden="true" />
                {t('hospitalDashboard.viewAllRequests')}
              </Link>
            }
          />
          {requests.state.status === 'loading' ? (
            <RowsSkeleton rows={2} />
          ) : requests.state.status !== 'ready' ? (
            <EmptyState
              icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
              title={(requests.state.status === 'error' && requests.state.message) || t('dash.loadFailed')}
              action={
                <Button variant="secondary" onClick={() => void requests.reload()}>
                  {t('dash.retry')}
                </Button>
              }
            />
          ) : recent.length === 0 ? (
            <EmptyState
              icon={<Plus className="h-10 w-10" aria-hidden="true" />}
              title={t('bloodRequests.noActiveRequests')}
              description={t('dashUi.req.emptyHome')}
            />
          ) : (
            <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
              {recent.map((req) => (
                <li key={req.id}>
                  <RequestRow
                    req={req}
                    cta={t('bloodRequests.manageCircle')}
                    actions={
                      <Button type="button" variant="secondary" onClick={() => setMatchesFor(req)} aria-label={`${t('dashUi.viewMatches')}: ${req.hospitalName} #${req.id}`}>
                        <Users className="h-4 w-4" aria-hidden="true" />
                        {t('dashUi.viewMatches')}
                      </Button>
                    }
                  />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <MatchesDrawer requestId={matchesFor?.id ?? null} hospitalName={matchesFor?.hospitalName} onClose={() => setMatchesFor(null)} />
    </div>
  );
}
