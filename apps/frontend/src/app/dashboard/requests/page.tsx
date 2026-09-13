'use client';

import { useState, useEffect, useCallback } from 'react';
import { Droplets, Plus, MapPin, Clock, ArrowLeft, AlertCircle, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { BloodRequest } from '@/types';

export default function RequestsListPage() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<BloodRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadRequests = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<any>('/requests');
      const reqData = res.data?.data ?? res.data;
      if (Array.isArray(reqData)) {
        setRequests(reqData);
      } else {
        setRequests([]);
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to load blood requests list.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'OPEN':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20">{t('request.statusOpen')}</span>;
      case 'ESCALATING':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">{t('request.statusEscalating')}</span>;
      case 'MATCHED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">{t('request.statusMatched')}</span>;
      case 'FULFILLED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-purple-500/10 text-purple-400 border border-purple-500/20">{t('request.statusFulfilled')}</span>;
      default:
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-gray-500/10 text-gray-400 border border-gray-500/20">{status}</span>;
    }
  };

  const getUrgencyBadge = (urgency: string) => {
    switch (urgency) {
      case 'CRITICAL':
        return <span className="text-xs text-red-400 font-semibold flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-ping" /> CRITICAL</span>;
      case 'URGENT':
        return <span className="text-xs text-amber-400 font-medium">URGENT</span>;
      default:
        return <span className="text-xs text-gray-400">ROUTINE</span>;
    }
  };

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

        <div className="flex items-center gap-3">
          <button
            onClick={loadRequests}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-medium transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <Link
            href="/dashboard/requests/new"
            className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-sm px-5 py-2.5 rounded-xl shadow-lg shadow-red-500/25 transition-all duration-200 self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            {t('dashboard.newRequest')}
          </Link>
        </div>
      </div>

      {error && (
        <div className="mb-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {loading ? (
        <div className="glass-card rounded-2xl p-12 text-center text-gray-400 border border-white/5">
          <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
          <p>{t('common.loading')}</p>
        </div>
      ) : requests.length === 0 ? (
        <div className="glass-card rounded-2xl p-12 text-center border border-white/5 space-y-3">
          <Droplets className="w-12 h-12 text-gray-600 mx-auto" />
          <p className="text-white font-medium">No blood requests active right now.</p>
          <p className="text-sm text-gray-400">Create a new blood request to initiate matching across registered donors.</p>
          <div className="pt-2">
            <Link
              href="/dashboard/requests/new"
              className="inline-flex items-center gap-2 bg-red-600 hover:bg-red-500 text-white font-medium text-sm px-4 py-2 rounded-xl transition-colors"
            >
              <Plus className="w-4 h-4" />
              Create Request
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {requests.map((req) => (
            <Link
              key={req.id}
              href={`/dashboard/requests/${req.id}`}
              className="block glass-card rounded-2xl p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-white/5 hover:border-red-500/30 hover:bg-white/[0.02] transition-all duration-200 cursor-pointer group"
            >
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-lg font-black text-red-400 flex-shrink-0">
                  {req.patientBloodType}
                </div>
                <div>
                  <div className="flex items-center gap-3 mb-1">
                    <span className="font-bold text-white text-base">{req.hospitalName}</span>
                    {getStatusBadge(req.status)}
                  </div>
                  <div className="flex flex-wrap items-center gap-4 text-xs text-gray-400">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-gray-500" />
                      {req.district} ({req.currentRadiusKm} km radius)
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5 text-gray-500" />
                      {getUrgencyBadge(req.urgency)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto">
                <span className="text-xs text-gray-500 font-mono">#{req.id}</span>
                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500/10 group-hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs font-semibold transition-colors">
                  Manage Circle →
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
