'use client';

import { useState, useCallback, useMemo } from 'react';
import { Droplets, Plus, AlertCircle, Search, Users } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { fetchRequests } from '@/lib/api/requests';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { FilterChips } from '@/components/dash/FilterChips';
import { MatchesDrawer } from '@/components/dash/MatchesDrawer';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { RequestRow } from '@/components/dash/RequestRow';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { CARD } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import type { BloodRequest } from '@/types';

type StatusFilter = 'ALL' | 'OPEN' | 'MATCHED' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED';

const FILTERS: { value: StatusFilter; labelKey: string }[] = [
  { value: 'ALL', labelKey: 'dash.all' },
  { value: 'OPEN', labelKey: 'request.statusOpen' },
  { value: 'MATCHED', labelKey: 'request.statusMatched' },
  { value: 'FULFILLED', labelKey: 'request.statusFulfilled' },
  { value: 'CANCELLED', labelKey: 'request.statusCancelled' },
  { value: 'EXPIRED', labelKey: 'request.statusExpired' },
];

function matchesFilter(status: string, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'OPEN') return status === 'OPEN' || status === 'ESCALATING';
  return status === filter;
}

export default function RequestsListPage() {
  const { t } = useI18n();
  const role = useAuthStore((s) => s.user?.role);
  const isAdmin = role === 'ADMIN';
  // Admins see every request; everyone else only sees their own.
  const fetcher = useCallback(() => fetchRequests(isAdmin), [isAdmin]);
  const requests = useAsync(fetcher);
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [query, setQuery] = useState('');
  const [matchesFor, setMatchesFor] = useState<BloodRequest | null>(null);

  const all = useMemo(() => (requests.state.status === 'ready' ? requests.state.data : []), [requests.state]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return all.filter((r) => matchesFilter(r.status, filter) && (!q || r.hospitalName.toLowerCase().includes(q) || r.district.toLowerCase().includes(q)));
  }, [all, filter, query]);

  const counts = useMemo(() => {
    const out = {} as Record<StatusFilter, number>;
    for (const f of FILTERS) out[f.value] = all.filter((r) => matchesFilter(r.status, f.value)).length;
    return out;
  }, [all]);

  const loading = requests.state.status === 'loading';

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.requests')}
        title={t('common.bloodRequests')}
        description={t('dashboard.activeRequestsDesc')}
        actions={
          <>
            <RefreshButton onClick={requests.reload} loading={loading} />
            {/* Admins cannot create requests (the route is not open to them), so no dead button */}
            {!isAdmin && (
              <ButtonLink href="/dashboard/requests/new" variant="primary">
                <Plus className="h-4 w-4" aria-hidden="true" />
                {t('dashboard.newRequest')}
              </ButtonLink>
            )}
          </>
        }
      />

      {/* Status chips + search */}
      <div className="mb-6 flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <FilterChips
          label={t('bloodRequests.status')}
          value={filter}
          onChange={setFilter}
          options={FILTERS.map((f) => ({ value: f.value, label: t(f.labelKey), count: loading ? undefined : counts[f.value] }))}
        />
        <div className="md:w-72">
          <Input
            type="search"
            icon={<Search className="h-4 w-4" aria-hidden="true" />}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('dash.searchPlaceholder')}
            aria-label={t('common.search')}
          />
        </div>
      </div>

      {loading ? (
        <RowsSkeleton rows={4} />
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
      ) : all.length === 0 ? (
        <EmptyState
          icon={<Droplets className="h-12 w-12" aria-hidden="true" />}
          title={t('bloodRequests.noActiveRequests')}
          description={isAdmin ? t('dashUi.admin.noRequestsYet') : t('bloodRequests.noActiveRequestsDesc')}
          action={
            isAdmin ? undefined : (
              <ButtonLink href="/dashboard/requests/new" variant="secondary">
                <Plus className="h-4 w-4" aria-hidden="true" />
                {t('bloodRequests.new')}
              </ButtonLink>
            )
          }
        />
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search className="h-10 w-10" aria-hidden="true" />} title={t('dash.noResults')} />
      ) : (
        <Rise delay={0.04}>
          <p role="status" className="mb-3 text-sm text-gray-500">
            {t('dashUi.requestsCount', { count: filtered.length })}
          </p>
          <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
            {filtered.map((req) => (
              <li key={req.id}>
                <RequestRow
                  req={req}
                  cta={isAdmin ? undefined : t('bloodRequests.manageCircle')}
                  actions={
                    isAdmin ? undefined : (
                      <Button type="button" variant="secondary" onClick={() => setMatchesFor(req)} aria-label={`${t('dashUi.viewMatches')}: ${req.hospitalName} #${req.id}`}>
                        <Users className="h-4 w-4" aria-hidden="true" />
                        {t('dashUi.viewMatches')}
                      </Button>
                    )
                  }
                />
              </li>
            ))}
          </ul>
        </Rise>
      )}

      <MatchesDrawer requestId={matchesFor?.id ?? null} hospitalName={matchesFor?.hospitalName} onClose={() => setMatchesFor(null)} />
    </div>
  );
}
