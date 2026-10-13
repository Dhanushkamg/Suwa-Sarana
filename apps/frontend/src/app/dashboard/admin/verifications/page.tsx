'use client';

import { useCallback, useEffect, useState } from 'react';
import { ShieldCheck, ShieldX, AlertCircle, Hospital, User, Phone, Mail } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { ROW_HOVER, TH } from '@/components/dash/styles';
import { PulseDot } from '@/components/dash/UrgencyBadge';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface PendingUser {
  id: number;
  email: string;
  phoneNumber: string;
  role: string;
  verificationStatus: string;
}

export default function AdminVerificationsPage() {
  const { t } = useI18n();
  const { user } = useAuthStore();
  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [processingId, setProcessingId] = useState<number | null>(null);

  // This endpoint returns a bare array (not wrapped in ApiResponse).
  const fetchPendingUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    setLoadError(null);
    try {
      const response = await apiClient.get<PendingUser[]>('/admin/verifications/pending');
      setPendingUsers(Array.isArray(response.data) ? response.data : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setLoadError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPendingUsers();
  }, [fetchPendingUsers]);

  const handleApprove = async (userId: number, email: string) => {
    setProcessingId(userId);
    setError(null);
    try {
      await apiClient.put(`/admin/verifications/${userId}/approve`);
      setPendingUsers((prev) => prev.filter((u) => u.id !== userId));
      setActionSuccess(t('dash.approvedMsg', { email }));
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || t('dash.approveFailed');
      setError(msg);
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (userId: number, email: string) => {
    setProcessingId(userId);
    setError(null);
    try {
      await apiClient.put(`/admin/verifications/${userId}/reject`);
      setPendingUsers((prev) => prev.filter((u) => u.id !== userId));
      setActionSuccess(t('dash.rejectedMsg', { email }));
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || t('dash.rejectFailed');
      setError(msg);
    } finally {
      setProcessingId(null);
    }
  };

  if (!user || user.role !== 'ADMIN') {
    return (
      <div className="mx-auto max-w-xl py-12">
        <EmptyState
          icon={<ShieldX className="w-12 h-12 text-red-400" />}
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

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.verifications')}
        title={t('adminDashboard.verificationsTitle')}
        description={t('adminDashboard.verificationsDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={fetchPendingUsers} loading={loading} />}
      />

      <div className="mb-6 space-y-3 empty:hidden">
        {actionSuccess && <Notice tone="success">{actionSuccess}</Notice>}
        {error && <Notice tone="error">{error}</Notice>}
      </div>

      {loading ? (
        <div role="status" aria-label={t('adminDashboard.loadingPending')}>
          <RowsSkeleton rows={3} />
        </div>
      ) : loadError !== null ? (
        <EmptyState
          icon={<AlertCircle className="w-10 h-10 text-red-400" />}
          title={loadError || t('dash.loadFailed')}
          action={<Button variant="secondary" onClick={fetchPendingUsers}>{t('dash.retry')}</Button>}
        />
      ) : pendingUsers.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck className="w-10 h-10 text-emerald-400" />}
          title={t('adminDashboard.verificationsClear')}
          description={t('adminDashboard.noPendingAccounts')}
        />
      ) : (
        <Rise delay={0.04}>
          <div className="glass-card overflow-hidden rounded-2xl">
            {/* The table scrolls inside this container, never the page */}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[56rem] text-left text-sm text-gray-300">
                <caption className="sr-only">{t('adminDashboard.verificationsTitle')}</caption>
                <thead className="border-b border-line">
                  <tr>
                    <th scope="col" className={TH}>{t('adminDashboard.accountDetails')}</th>
                    <th scope="col" className={TH}>{t('publicPages.contact')}</th>
                    <th scope="col" className={TH}>{t('adminDashboard.roleScope')}</th>
                    <th scope="col" className={TH}>{t('bloodRequests.status')}</th>
                    <th scope="col" className={cn(TH, 'text-right')}>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {pendingUsers.map((u) => {
                    const isHospital = u.role === 'HOSPITAL_REQUESTER';
                    const isProcessing = processingId === u.id;

                    return (
                      <tr key={u.id} className={ROW_HOVER}>
                        <td className="whitespace-nowrap px-5 py-4">
                          <div className="flex items-center gap-3">
                            <IconTile size="md" tone={isHospital ? 'saffron' : 'muted'}>
                              {isHospital ? <Hospital /> : <User />}
                            </IconTile>
                            <div>
                              <div className="font-medium text-foreground">{u.email}</div>
                              <div className="font-mono text-xs text-gray-500">ID #{u.id}</div>
                            </div>
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-5 py-4">
                          <div className="flex items-center gap-1.5 text-xs text-gray-300">
                            <Phone className="h-3.5 w-3.5 text-gray-500" aria-hidden="true" />
                            {u.phoneNumber || t('dash.notAvailable')}
                          </div>
                          <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-400">
                            <Mail className="h-3.5 w-3.5 text-gray-500" aria-hidden="true" />
                            {u.email}
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-5 py-4">
                          <span className="inline-flex items-center rounded-full border border-foreground/[0.16] px-2.5 py-0.5 font-mono text-[11px] uppercase tracking-wide text-gray-300">
                            {u.role.replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-5 py-4">
                          <span className="inline-flex items-center gap-2">
                            <PulseDot tone="saffron" />
                            <StatusBadge value={u.verificationStatus} label={t(`dashUi.verification.${u.verificationStatus}`)} />
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-5 py-4 text-right">
                          <div className="inline-flex items-center gap-2">
                            <Button
                              size="sm"
                              variant="secondary"
                              className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:border-emerald-500/50 hover:bg-emerald-500/20"
                              disabled={isProcessing}
                              onClick={() => handleApprove(u.id, u.email)}
                            >
                              <ShieldCheck className="h-4 w-4" aria-hidden="true" />
                              {t('common.approve')}
                            </Button>
                            <Button
                              size="sm"
                              variant="secondary"
                              className="border-red-500/30 bg-red-500/10 text-red-300 hover:border-red-500/50 hover:bg-red-500/20"
                              disabled={isProcessing}
                              onClick={() => handleReject(u.id, u.email)}
                            >
                              <ShieldX className="h-4 w-4" aria-hidden="true" />
                              {t('common.reject')}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </Rise>
      )}
    </div>
  );
}
