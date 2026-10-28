'use client';

import { useState } from 'react';
import { Plus, AlertCircle, Droplets, Activity, ShieldCheck, ArrowRight, Radio, Users } from 'lucide-react';
import Link from 'next/link';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { fetchRequests } from '@/lib/api/requests';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { MatchesDrawer } from '@/components/dash/MatchesDrawer';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { RequestRow } from '@/components/dash/RequestRow';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { CARD, TEXT_LINK } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import SlotManager from '@/components/slots/SlotManager';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';
import type { BloodRequest } from '@/types';

// Own requests only (GET /requests/me)
const fetchOwnRequests = () => fetchRequests(false);

export default function HospitalRequesterDashboardPage() {
  const { user } = useAuthStore();
  const { t } = useI18n();
  const requests = useAsync(fetchOwnRequests);
  const [matchesFor, setMatchesFor] = useState<BloodRequest | null>(null);

  const list = requests.state.status === 'ready' ? requests.state.data : [];
  const ready = requests.state.status === 'ready';
  const activeRequests = list.filter((r) => r.status === 'OPEN' || r.status === 'ESCALATING');
  const criticalCount = list.filter((r) => r.urgency === 'CRITICAL').length;
  const fulfilledCount = list.filter((r) => r.status === 'FULFILLED').length;
  // No active request means there is no radius to report: "no data", not a made-up default
  const currentRadius = activeRequests.length ? activeRequests.reduce((max, r) => Math.max(max, r.currentRadiusKm), 0) : null;

  return (
    <div>
      <PageHeader
        eyebrow={t('hospitalDashboard.portalTitle')}
        badges={<VerificationBadge status={user?.verificationStatus} verifiedLabel={t('hospitalDashboard.verifiedUnit')} />}
        title={t('hospitalDashboard.dispatchCenter')}
        description={t('hospitalDashboard.dispatchDesc')}
        actions={
          <>
            <RefreshButton onClick={requests.reload} loading={requests.state.status === 'loading'} label={t('hospitalDashboard.refreshFeed')} />
            <ButtonLink href="/dashboard/requests/new" variant="primary">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('hospitalDashboard.broadcastRequest')}
            </ButtonLink>
          </>
        }
      />

      <div className="space-y-14">
        {/* KPIs */}
        {requests.state.status === 'loading' ? (
          <StatsSkeleton count={4} className="sm:grid-cols-2 lg:grid-cols-4" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Rise delay={0.04}>
              <Stat label={t('hospitalDashboard.activeBroadcasts')} value={ready ? activeRequests.length : null} tone="primary" icon={<Radio />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('hospitalDashboard.criticalUrgency')} value={ready ? criticalCount : null} tone="danger" icon={<AlertCircle />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('dash.fulfilledRequests')} value={ready ? fulfilledCount : null} tone="success" icon={<ShieldCheck />} className="h-full" />
            </Rise>
            <Rise delay={0.16}>
              <Stat label={t('hospitalDashboard.searchRadius')} value={ready ? currentRadius : null} suffix="km" icon={<Activity />} className="h-full" />
            </Rise>
          </div>
        )}

        {/* Blood requisitions */}
        <section>
          <SectionHeader
            eyebrow={t('dashUi.section.requisitions')}
            title={t('hospitalDashboard.telemetry')}
            action={
              <Link href="/dashboard/requests" className={TEXT_LINK}>
                {t('hospitalDashboard.viewAllRequests')} <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden="true" />
              </Link>
            }
          />

          {requests.state.status === 'loading' ? (
            <RowsSkeleton rows={3} />
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
          ) : list.length === 0 ? (
            <EmptyState
              icon={<Droplets className="h-12 w-12" aria-hidden="true" />}
              title={t('hospitalDashboard.noActiveRequests')}
              description={t('hospitalDashboard.noActiveRequestsDesc')}
              action={
                <ButtonLink href="/dashboard/requests/new" variant="secondary">
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  {t('hospitalDashboard.createRequest')}
                </ButtonLink>
              }
            />
          ) : (
            <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
              {list.map((req) => (
                <li key={req.id}>
                  <RequestRow
                    req={req}
                    cta={t('hospitalDashboard.manageCircle')}
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

        {/* Donation camp management */}
        <Rise inView>
          <div className="border-t border-line pt-12">
            <CampManagerDashboard />
          </div>
        </Rise>

        {/* Walk-in slot management */}
        <Rise inView>
          <section className="border-t border-line pt-12">
            <SectionHeader eyebrow={t('dashUi.section.slots')} title={t('hospitalDashboard.walkInSlots')} />
            <Panel>
              {user ? <SlotManager hostType="hospital" hostId={user.id} /> : <div className="py-6 text-center text-sm text-gray-500">{t('dash.sessionRequired')}</div>}
            </Panel>
          </section>
        </Rise>
      </div>

      <MatchesDrawer requestId={matchesFor?.id ?? null} hospitalName={matchesFor?.hospitalName} onClose={() => setMatchesFor(null)} />
    </div>
  );
}
