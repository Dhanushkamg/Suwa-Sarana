'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { Droplets, Plus, AlertCircle, Search } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/store/authStore';
import { BloodRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { FilterChips } from '@/components/dash/FilterChips';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { RequestRow } from '@/components/dash/RequestRow';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';

interface ApiBody<T> {
  data?: T;
}

type StatusFilter = 'ALL' | 'OPEN' | 'MATCHED' | 'FULFILLED' | 'CANCELLED';

const FILTERS: { value: StatusFilter; labelKey: string }[] = [
  { value: 'ALL', labelKey: 'dash.all' },
  { value: 'OPEN', labelKey: 'request.statusOpen' },
  { value: 'MATCHED', labelKey: 'request.statusMatched' },
  { value: 'FULFILLED', labelKey: 'request.statusFulfilled' },
  { value: 'CANCELLED', labelKey: 'request.statusCancelled' },
];

function matchesFilter(status: string, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'OPEN') return status === 'OPEN' || status === 'ESCALATING';
  return status === filter;
}

export default function RequestsListPage() {
  const { t } = useI18n();
  const role = useAuthStore((s) => s.user?.role);
  const [requests, setRequests] = useState<BloodRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [query, setQuery] = useState('');

  const isAdmin = role === 'ADMIN';

  const loadRequests = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Admins see every request; everyone else only sees their own.
      const res = await apiClient.get<ApiBody<BloodRequest[]>>(isAdmin ? '/requests' : '/requests/me');
      const data = res.data?.data;
      setRequests(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return requests.filter(
      (r) =>
        matchesFilter(r.status, filter) &&
        (!q || r.hospitalName.toLowerCase().includes(q) || r.district.toLowerCase().includes(q))
    );
  }, [requests, filter, query]);

  const counts = useMemo(() => {
    const out = {} as Record<StatusFilter, number>;
    for (const f of FILTERS) out[f.value] = requests.filter((r) => matchesFilter(r.status, f.value)).length;
    return out;
  }, [requests]);

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.requests')}
        title={t('common.bloodRequests')}
        description={t('dashboard.activeRequestsDesc')}
        actions={
          <>
            <RefreshButton onClick={loadRequests} loading={loading} />
            <ButtonLink href="/dashboard/requests/new" variant="primary">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('dashboard.newRequest')}
            </ButtonLink>
          </>
        }
      />

      {/* Status chips + search */}
      <div className="mb-6 flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <FilterChips
          id="requests-status"
          label={t('bloodRequests.status')}
          value={filter}
          onChange={setFilter}
          options={FILTERS.map((f) => ({ value: f.value, label: t(f.labelKey), count: loading ? undefined : counts[f.value] }))}
        />
        <div className="md:w-72">
          <Input
            type="search"
            icon={<Search className="h-4 w-4" />}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('dash.searchPlaceholder')}
            aria-label={t('common.search')}
            className="py-2.5"
          />
        </div>
      </div>

      {loading ? (
        <RowsSkeleton rows={4} />
      ) : error !== null ? (
        <EmptyState
          icon={<AlertCircle className="w-10 h-10 text-red-400" />}
          title={error || t('dash.loadFailed')}
          action={<Button variant="secondary" onClick={loadRequests}>{t('dash.retry')}</Button>}
        />
      ) : requests.length === 0 ? (
        <EmptyState
          icon={<Droplets className="w-12 h-12" />}
          title={t('bloodRequests.noActiveRequests')}
          description={t('bloodRequests.noActiveRequestsDesc')}
          action={
            <ButtonLink href="/dashboard/requests/new" variant="primary">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('bloodRequests.new')}
            </ButtonLink>
          }
        />
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search className="w-10 h-10" />} title={t('dash.noResults')} />
      ) : (
        <Rise delay={0.04}>
          <ul className="glass-card divide-y divide-line overflow-hidden rounded-2xl">
            {filtered.map((req) => (
              <li key={req.id}>
                <RequestRow req={req} cta={t('bloodRequests.manageCircle')} />
              </li>
            ))}
          </ul>
        </Rise>
      )}
    </div>
  );
}
