'use client';

import { useCallback, useMemo, useState } from 'react';
import { ShieldCheck, ShieldX, AlertCircle, Building2, User, Phone, Mail, CalendarClock, Search, Inbox } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useToast } from '@/components/ui/Toast';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { ConfirmDialog } from '@/components/dash/ConfirmDialog';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { CARD } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { apiMessage } from '@/lib/api/common';
import {
  decideVerification,
  fetchPendingVerifications,
  isInstitutionRole,
  VERIFICATION_REASON_MAX,
  VERIFICATION_REASON_MIN,
  type PendingVerification,
  type VerificationDecision,
} from '@/lib/api/admin';
import { FilterChips } from '@/components/dash/FilterChips';

export default function AdminVerificationsPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const { user } = useAuthStore();
  const [queue, setQueue] = useState<'INSTITUTIONS' | 'REQUESTERS'>('INSTITUTIONS');
  // Institutions (hospitals, blood banks) are the default queue; patient/family requesters are listed with role=REQUESTER
  const fetchQueue = useCallback(() => fetchPendingVerifications(queue === 'REQUESTERS' ? 'REQUESTER' : undefined), [queue]);
  const list = useAsync(fetchQueue);
  const [query, setQuery] = useState('');
  const [deciding, setDeciding] = useState<{ user: PendingVerification; decision: VerificationDecision } | null>(null);

  const pending = useMemo(() => (list.state.status === 'ready' ? list.state.data : []), [list.state]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return pending;
    return pending.filter((u) =>
      [u.fullName, u.organisation, u.email, u.phoneNumber, t(`dashUi.role.${u.role}`)].some((v) => v?.toLowerCase().includes(q))
    );
  }, [pending, query, t]);

  if (!user || user.role !== 'ADMIN') {
    return (
      <div className="mx-auto max-w-xl py-12">
        <EmptyState
          icon={<ShieldX className="h-12 w-12 text-danger-text" aria-hidden="true" />}
          title={t('adminDashboard.accessDenied')}
          description={t('adminDashboard.accessDeniedDesc')}
          action={
            <ButtonLink href="/dashboard" variant="secondary">
              {t('common.back')}
            </ButtonLink>
          }
        />
      </div>
    );
  }

  const confirm = async (reason: string) => {
    if (!deciding) return;
    const { user: target, decision } = deciding;
    await decideVerification(target.id, decision, reason);
    list.mutate((rows) => rows.filter((r) => r.id !== target.id));
    const name = target.fullName || target.organisation || target.email;
    toast.show(decision === 'APPROVE' ? t('dash.approvedMsg', { email: name }) : t('dash.rejectedMsg', { email: name }), 'success');
  };

  const errorFor = (err: unknown) => apiMessage(err, deciding?.decision === 'APPROVE' ? t('dash.approveFailed') : t('dash.rejectFailed'));

  const approving = deciding?.decision === 'APPROVE';
  const targetName = deciding ? deciding.user.fullName || deciding.user.organisation || deciding.user.email : '';

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.verifications')}
        title={t('adminDashboard.verificationsTitle')}
        description={t('adminDashboard.verificationsDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={list.reload} loading={list.state.status === 'loading'} />}
      />

      <div className="mb-6">
        <FilterChips
          label={t('dashUi.admin.queueFilter')}
          value={queue}
          onChange={setQueue}
          options={[
            { value: 'INSTITUTIONS', label: t('dashUi.admin.queueInstitutions') },
            { value: 'REQUESTERS', label: t('dashUi.admin.queueRequesters') },
          ]}
        />
      </div>

      {list.state.status === 'loading' ? (
        <div role="status" aria-label={t('adminDashboard.loadingPending')}>
          <RowsSkeleton rows={3} />
        </div>
      ) : list.state.status === 'error' || list.state.status === 'unavailable' ? (
        <EmptyState
          icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={(list.state.status === 'error' && list.state.message) || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={() => void list.reload()}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : pending.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck className="h-10 w-10 text-success-text" aria-hidden="true" />}
          title={t('adminDashboard.verificationsClear')}
          description={t('adminDashboard.noPendingAccounts')}
        />
      ) : (
        <div className="space-y-5">
          <div className="max-w-md">
            <Input
              type="search"
              icon={<Search className="h-4 w-4" aria-hidden="true" />}
              label={t('dashUi.admin.searchPending')}
              placeholder={t('dashUi.admin.searchPendingHint')}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          <p role="status" className="text-sm text-gray-500">
            {t('dashUi.admin.pendingShown', { shown: filtered.length, total: pending.length })}
          </p>

          {filtered.length === 0 ? (
            <EmptyState icon={<Inbox className="h-10 w-10" aria-hidden="true" />} title={t('dash.noResults')} />
          ) : (
            <Rise delay={0.04}>
              <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
                {filtered.map((u) => {
                  const isOrg = isInstitutionRole(u.role);
                  const name = u.fullName || u.organisation || u.email;
                  return (
                    <li key={u.id} className="flex flex-col gap-4 p-5 lg:flex-row lg:items-center lg:justify-between">
                      <div className="flex min-w-0 items-start gap-4">
                        <IconTile size="md" tone={isOrg ? 'primary' : 'muted'}>
                          {isOrg ? <Building2 /> : <User />}
                        </IconTile>
                        <div className="min-w-0 space-y-1.5">
                          <p className="break-words text-base font-medium text-foreground">{name}</p>
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                            <StatusBadge value="PENDING" tone="pending" label={t('dashUi.verification.PENDING')} />
                            <span className="text-sm text-gray-400">
                              {t(`dashUi.role.${u.role}`)}
                              {u.organisation && u.fullName ? ` · ${u.organisation}` : ''}
                            </span>
                          </div>
                          <p className="flex items-center gap-1.5 break-all text-sm text-gray-400">
                            <Mail className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                            {u.email}
                            {!u.emailVerified && <span className="shrink-0 text-warning-text">({t('dashUi.admin.emailUnverified')})</span>}
                          </p>
                          <p className="flex items-center gap-1.5 text-sm text-gray-400">
                            <Phone className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                            <span dir="ltr">{u.phoneNumber || t('dash.notAvailable')}</span>
                          </p>
                          <p className="flex items-center gap-1.5 text-sm text-gray-500">
                            <CalendarClock className="h-4 w-4 shrink-0" aria-hidden="true" />
                            {u.submittedAt ? t('dashUi.admin.submitted', { date: format.date(u.submittedAt, { dateStyle: 'medium' }) }) : t('dashUi.admin.submittedUnknown')}
                          </p>
                        </div>
                      </div>
                      <div className="flex flex-wrap gap-3 lg:shrink-0">
                        <Button type="button" variant="primary" onClick={() => setDeciding({ user: u, decision: 'APPROVE' })} aria-label={`${t('common.approve')}: ${name}`}>
                          <ShieldCheck className="h-4 w-4" aria-hidden="true" />
                          {t('common.approve')}
                        </Button>
                        <Button
                          type="button"
                          variant="secondary"
                          onClick={() => setDeciding({ user: u, decision: 'REJECT' })}
                          aria-label={`${t('common.reject')}: ${name}`}
                          className="border-danger/40 text-danger-text"
                        >
                          <ShieldX className="h-4 w-4" aria-hidden="true" />
                          {t('common.reject')}
                        </Button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </Rise>
          )}
        </div>
      )}

      <ConfirmDialog
        open={deciding !== null}
        onClose={() => setDeciding(null)}
        title={approving ? t('dashUi.admin.approveTitle', { name: targetName }) : t('dashUi.admin.rejectTitle', { name: targetName })}
        description={approving ? t('dashUi.admin.approveDesc') : t('dashUi.admin.rejectDesc')}
        confirmLabel={approving ? t('common.approve') : t('common.reject')}
        confirmVariant={approving ? 'primary' : 'danger'}
        reasonLabel={t('dashUi.admin.reasonLabel')}
        reasonHelp={approving ? t('dashUi.admin.reasonHelpApprove') : t('dashUi.admin.reasonHelpReject')}
        reasonRequired
        reasonMinLength={VERIFICATION_REASON_MIN}
        reasonMaxLength={VERIFICATION_REASON_MAX}
        onConfirm={confirm}
        errorMessage={errorFor}
      />
      {list.state.status === 'ready' && <Notice tone="info" className="mt-6">{t('dashUi.admin.decisionNote')}</Notice>}
    </div>
  );
}
