'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, AlertCircle, Droplets, Activity, ShieldCheck, ArrowRight, Radio } from 'lucide-react';
import Link from 'next/link';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { BloodRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Reveal } from '@/components/motion/Reveal';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { RequestRow } from '@/components/dash/RequestRow';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import SlotManager from '@/components/slots/SlotManager';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';

interface ApiBody<T> {
  data?: T;
}

export default function HospitalRequesterDashboardPage() {
  const { user } = useAuthStore();
  const { t } = useI18n();
  const [requests, setRequests] = useState<BloodRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadHospitalData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Only this hospital's own requests.
      const res = await apiClient.get<ApiBody<BloodRequest[]>>('/requests/me');
      const reqData = res.data?.data;
      setRequests(Array.isArray(reqData) ? reqData : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHospitalData();
  }, [loadHospitalData]);

  const criticalCount = requests.filter((r) => r.urgency === 'CRITICAL').length;
  const activeRequests = requests.filter((r) => r.status === 'OPEN' || r.status === 'ESCALATING');
  const activeCount = activeRequests.length;
  const fulfilledCount = requests.filter((r) => r.status === 'FULFILLED').length;
  const currentRadius = activeRequests.reduce((max, r) => Math.max(max, r.currentRadiusKm), 5);

  return (
    <div>
      <PageHeader
        eyebrow={t('hospitalDashboard.portalTitle')}
        badges={<VerificationBadge status={user?.verificationStatus} verifiedLabel={t('hospitalDashboard.verifiedUnit')} />}
        title={t('hospitalDashboard.dispatchCenter')}
        description={t('hospitalDashboard.dispatchDesc')}
        actions={
          <>
            <RefreshButton onClick={loadHospitalData} loading={loading} label={t('hospitalDashboard.refreshFeed')} />
            <ButtonLink href="/dashboard/requests/new" variant="primary">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('hospitalDashboard.broadcastRequest')}
            </ButtonLink>
          </>
        }
      />

      <div className="space-y-14">
        {/* KPIs */}
        {loading ? (
          <StatsSkeleton count={4} className="sm:grid-cols-2 lg:grid-cols-4" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Rise delay={0.04}>
              <Stat label={t('hospitalDashboard.activeBroadcasts')} value={activeCount} tone="ember" icon={<Radio />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('hospitalDashboard.criticalUrgency')} value={criticalCount} tone="saffron" icon={<AlertCircle />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('dash.fulfilledRequests')} value={fulfilledCount} tone="sage" icon={<ShieldCheck />} className="h-full" />
            </Rise>
            <Rise delay={0.16}>
              <Stat label={t('hospitalDashboard.searchRadius')} value={currentRadius} suffix="km" icon={<Activity />} className="h-full" />
            </Rise>
          </div>
        )}

        {/* Blood requisitions */}
        <section>
          <SectionHeader
            eyebrow={t('dashUi.section.requisitions')}
            title={t('hospitalDashboard.telemetry')}
            action={
              <Link href="/dashboard/requests" className="inline-flex items-center gap-1.5 text-sm text-ember transition-colors hover:text-foreground">
                {t('hospitalDashboard.viewAllRequests')} <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            }
          />

          {loading ? (
            <RowsSkeleton rows={3} />
          ) : error !== null ? (
            <EmptyState
              icon={<AlertCircle className="w-10 h-10 text-red-400" />}
              title={error || t('dash.loadFailed')}
              action={<Button variant="secondary" onClick={loadHospitalData}>{t('dash.retry')}</Button>}
            />
          ) : requests.length === 0 ? (
            <EmptyState
              icon={<Droplets className="w-12 h-12" />}
              title={t('hospitalDashboard.noActiveRequests')}
              description={t('hospitalDashboard.noActiveRequestsDesc')}
              action={
                <ButtonLink href="/dashboard/requests/new" variant="primary">
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  {t('hospitalDashboard.createRequest')}
                </ButtonLink>
              }
            />
          ) : (
            <ul className="glass-card divide-y divide-line overflow-hidden rounded-2xl">
              {requests.map((req) => (
                <li key={req.id}>
                  <RequestRow req={req} cta={t('hospitalDashboard.manageCircle')} />
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Donation camp management */}
        <Reveal y={12}>
          <div className="border-t border-line pt-12">
            <CampManagerDashboard />
          </div>
        </Reveal>

        {/* Walk-in slot management */}
        <Reveal y={12}>
          <section className="border-t border-line pt-12">
            <SectionHeader eyebrow={t('dashUi.section.slots')} title={t('hospitalDashboard.walkInSlots')} />
            <Panel>
              {user ? (
                <SlotManager hostType="hospital" hostId={user.id} />
              ) : (
                <div className="py-6 text-center text-sm text-gray-500">{t('dash.sessionRequired')}</div>
              )}
            </Panel>
          </section>
        </Reveal>
      </div>
    </div>
  );
}
