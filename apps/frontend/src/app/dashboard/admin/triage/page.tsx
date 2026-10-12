'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  ArrowLeft, ShieldAlert, Bot, AlertTriangle, CheckCircle2,
  RefreshCw, Check, Eye, MapPin, Droplets, Activity
} from 'lucide-react';
import Link from 'next/link';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { BloodRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { SkeletonList } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';

interface ApiBody<T> {
  data?: T;
}

interface TriagedRequest extends BloodRequest {
  fraudRiskScore?: number;
  aiFlagReason?: string;
  triagedAt?: string;
}

export default function AdminTriageQueuePage() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<TriagedRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'HIGH' | 'MODERATE' | 'LOW'>('ALL');
  const [resolvingId, setResolvingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadTriageQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<ApiBody<TriagedRequest[]>>('/admin/triage/queue');
      const data = res.data?.data;
      setRequests(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTriageQueue();
  }, [loadTriageQueue]);

  const handleResolve = async (id: number) => {
    setResolvingId(id);
    setActionError(null);
    try {
      await apiClient.put(`/admin/triage/${id}/review`, {
        notes: 'Dismissed by administrator from triage console.',
      });
      loadTriageQueue();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setActionError(msg || t('dash.dismissFailed'));
    } finally {
      setResolvingId(null);
    }
  };

  const highRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) >= 70).length;
  const moderateRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) >= 40 && (r.fraudRiskScore || 0) < 70).length;
  const lowRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) < 40).length;

  const filtered = requests.filter((r) => {
    const score = r.fraudRiskScore || 0;
    if (filter === 'HIGH') return score >= 70;
    if (filter === 'MODERATE') return score >= 40 && score < 70;
    if (filter === 'LOW') return score < 40;
    return true;
  });

  return (
    <div className="max-w-6xl mx-auto space-y-8 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link
            href="/dashboard/admin"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-extrabold text-white flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-500/20 to-red-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <ShieldAlert className="w-6 h-6" />
            </div>
            {t('adminDashboard.triageTitle')}
          </h1>
          <p className="text-gray-400 mt-1">
            {t('adminDashboard.triageDesc')}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={loadTriageQueue}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-semibold transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            {t('adminDashboard.refreshQueue')}
          </button>
        </div>
      </div>

      {/* Advisory Banner */}
      <div className="rounded-2xl border border-blue-500/30 bg-blue-500/10 p-4 flex items-start gap-3 text-blue-300 text-xs">
        <Bot className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
        <div>
          <strong className="text-white block mb-0.5">{t('adminDashboard.aiPrioritization')}</strong>
          <span>
            {t('adminDashboard.aiPrioritizationDesc')}
          </span>
        </div>
      </div>

      {actionError && (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{actionError}</span>
        </div>
      )}

      {/* Metrics Counters */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <button
          onClick={() => setFilter('ALL')}
          className={`p-4 rounded-2xl border text-left transition-all ${
            filter === 'ALL'
              ? 'bg-white/10 border-white/20'
              : 'glass-card border-white/5 hover:bg-white/5'
          }`}
        >
          <span className="text-xs text-gray-400">{t('adminDashboard.totalTriaged')}</span>
          <p className="text-2xl font-bold text-white mt-1">{requests.length}</p>
        </button>

        <button
          onClick={() => setFilter('HIGH')}
          className={`p-4 rounded-2xl border text-left transition-all ${
            filter === 'HIGH'
              ? 'bg-red-500/20 border-red-500/40'
              : 'glass-card border-red-500/10 hover:bg-red-500/5'
          }`}
        >
          <span className="text-xs text-red-400 font-semibold flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-red-500 animate-ping" />
            {t('adminDashboard.highRisk')}
          </span>
          <p className="text-2xl font-bold text-red-400 mt-1">{highRiskCount}</p>
        </button>

        <button
          onClick={() => setFilter('MODERATE')}
          className={`p-4 rounded-2xl border text-left transition-all ${
            filter === 'MODERATE'
              ? 'bg-amber-500/20 border-amber-500/40'
              : 'glass-card border-amber-500/10 hover:bg-amber-500/5'
          }`}
        >
          <span className="text-xs text-amber-400 font-semibold flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            {t('adminDashboard.moderateRisk')}
          </span>
          <p className="text-2xl font-bold text-amber-400 mt-1">{moderateRiskCount}</p>
        </button>

        <button
          onClick={() => setFilter('LOW')}
          className={`p-4 rounded-2xl border text-left transition-all ${
            filter === 'LOW'
              ? 'bg-emerald-500/20 border-emerald-500/40'
              : 'glass-card border-emerald-500/10 hover:bg-emerald-500/5'
          }`}
        >
          <span className="text-xs text-emerald-400 font-semibold flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            {t('adminDashboard.lowRisk')}
          </span>
          <p className="text-2xl font-bold text-emerald-400 mt-1">{lowRiskCount}</p>
        </button>
      </div>

      {/* Triage List */}
      <div className="space-y-4">
        {loading ? (
          <div className="space-y-2" role="status" aria-label={t('adminDashboard.evaluatingQueue')}>
            <SkeletonList rows={3} />
          </div>
        ) : error !== null ? (
          <EmptyState
            icon={<AlertTriangle className="w-10 h-10 text-red-400" />}
            title={error || t('dash.loadFailed')}
            action={<Button variant="secondary" onClick={loadTriageQueue}>{t('dash.retry')}</Button>}
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<CheckCircle2 className="w-12 h-12 text-emerald-400 opacity-60" />}
            title={t('adminDashboard.queueClear')}
            description={t('adminDashboard.noThreshold')}
          />
        ) : (
          filtered.map((req) => {
            const score = req.fraudRiskScore || 0;
            const isHigh = score >= 70;
            const isMod = score >= 40 && score < 70;

            const badgeBg = isHigh
              ? 'bg-red-500/20 text-red-400 border-red-500/30'
              : isMod
              ? 'bg-amber-500/20 text-amber-400 border-amber-500/30'
              : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30';

            return (
              <div
                key={req.id}
                className={`glass-card p-6 rounded-2xl border transition-all ${
                  isHigh ? 'border-red-500/30 bg-red-950/10' : 'border-white/10'
                }`}
              >
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-4">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-2xl font-black text-red-400 flex-shrink-0">
                      {req.patientBloodType}
                    </div>
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-bold text-white break-words">{req.hospitalName}</h3>
                        <span className="text-xs text-gray-500 font-mono">#{req.id}</span>
                      </div>
                      <p className="text-xs text-gray-400 flex flex-wrap items-center gap-x-2 gap-y-1 mt-0.5">
                        <MapPin className="w-3.5 h-3.5 text-gray-500" /> {req.district}
                        <span className="text-gray-600">·</span>
                        <Droplets className="w-3.5 h-3.5 text-red-400" /> {t('dash.unitsCount', { n: req.unitsNeeded ?? 0 })}
                        <span className="text-gray-600">·</span>
                        <Activity className="w-3.5 h-3.5 text-blue-400" />
                        <StatusBadge value={req.urgency} label={t(`dash.urgency.${req.urgency}`)} />
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-3 self-start lg:self-auto">
                    <div className={`px-3.5 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5 ${badgeBg}`}>
                      <span>{t('adminDashboard.riskScore')}:</span>
                      <span className="font-mono text-sm">{score}/100</span>
                    </div>

                    <Link
                      href={`/dashboard/requests/${req.id}`}
                      className="px-3.5 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-gray-300 hover:text-white text-xs font-medium inline-flex items-center gap-1.5 transition-colors"
                    >
                      <Eye className="w-3.5 h-3.5" />
                      {t('common.view')}
                    </Link>

                    {score > 0 && (
                      <button
                        onClick={() => handleResolve(req.id)}
                        disabled={resolvingId === req.id}
                        className="px-3.5 py-2 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/20 text-emerald-400 text-xs font-medium inline-flex items-center gap-1.5 transition-colors disabled:opacity-50"
                      >
                        <Check className="w-3.5 h-3.5" />
                        {resolvingId === req.id ? t('common.loading') : t('adminDashboard.dismissFlag')}
                      </button>
                    )}
                  </div>
                </div>

                {/* AI Explainable Flag Reason */}
                {req.aiFlagReason && (
                  <div className="p-3.5 rounded-xl bg-black/30 border border-white/5 flex items-start gap-2.5 text-xs">
                    <Bot className="w-4 h-4 text-purple-400 flex-shrink-0 mt-0.5" />
                    <div>
                      <span className="font-semibold text-purple-300">{t('adminDashboard.aiDiagnosticHint')}</span>
                      <p className="text-gray-300 mt-0.5">{req.aiFlagReason}</p>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
