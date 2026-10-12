'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { Droplets, Plus, MapPin, Clock, ArrowLeft, AlertCircle, RefreshCw, Search } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/store/authStore';
import { BloodRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { SkeletonList } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';

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

  const statusLabel = (s: string) => t('request.status' + s.charAt(0) + s.slice(1).toLowerCase());

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <Droplets className="w-8 h-8 text-red-500" />
            {t('common.bloodRequests')}
          </h1>
          <p className="text-gray-400 mt-1">{t('dashboard.activeRequestsDesc')}</p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={loadRequests}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-medium transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            {t('common.refresh')}
          </button>
          <Link
            href="/dashboard/requests/new"
            className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-sm px-5 py-2.5 rounded-xl shadow-lg shadow-red-500/25 transition-all duration-200"
          >
            <Plus className="w-4 h-4" />
            {t('dashboard.newRequest')}
          </Link>
        </div>
      </div>

      {/* Status chips + search */}
      <div className="mb-6 flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2" role="group">
          {FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              onClick={() => setFilter(f.value)}
              aria-pressed={filter === f.value}
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                filter === f.value
                  ? 'bg-red-500/20 text-red-300 border-red-500/40'
                  : 'bg-white/5 text-gray-400 border-white/10 hover:text-white hover:bg-white/10'
              }`}
            >
              {t(f.labelKey)}
            </button>
          ))}
        </div>
        <div className="relative md:w-72">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('dash.searchPlaceholder')}
            aria-label={t('common.search')}
            className="w-full rounded-xl border border-white/10 bg-white/5 pl-10 pr-4 py-2.5 text-sm text-white placeholder-gray-500 focus:border-red-500/60 focus:outline-none focus:ring-2 focus:ring-red-500/20"
          />
        </div>
      </div>

      {loading ? (
        <SkeletonList rows={4} />
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
            <Link
              href="/dashboard/requests/new"
              className="inline-flex items-center gap-2 bg-red-600 hover:bg-red-500 text-white font-medium text-sm px-4 py-2 rounded-xl transition-colors"
            >
              <Plus className="w-4 h-4" />
              {t('bloodRequests.new')}
            </Link>
          }
        />
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search className="w-10 h-10" />} title={t('dash.noResults')} />
      ) : (
        <div className="space-y-4">
          {filtered.map((req) => (
            <Link
              key={req.id}
              href={`/dashboard/requests/${req.id}`}
              className="block glass-card rounded-2xl p-4 sm:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-white/5 hover:border-red-500/30 hover:bg-white/[0.02] transition-all duration-200 cursor-pointer group"
            >
              <div className="flex items-start gap-4 min-w-0">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-lg font-black text-red-400 flex-shrink-0">
                  {req.patientBloodType}
                </div>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mb-1">
                    <span className="font-bold text-white text-base break-words">{req.hospitalName}</span>
                    <StatusBadge value={req.status} label={statusLabel(req.status)} />
                    <StatusBadge value={req.urgency} label={t(`dash.urgency.${req.urgency}`)} />
                  </div>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-400">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-gray-500" />
                      {req.district} ({t('dash.radiusKm', { km: req.currentRadiusKm })})
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5 text-gray-500" />
                      {t('bloodRequests.expires')} {new Date(req.expiresAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto flex-shrink-0">
                <span className="text-xs text-gray-500 font-mono">#{req.id}</span>
                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500/10 group-hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs font-semibold transition-colors">
                  {t('bloodRequests.manageCircle')}
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
