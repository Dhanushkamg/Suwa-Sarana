'use client';

import { useState, useEffect, useCallback } from 'react';
import { Building2, Plus, AlertCircle, Droplets, Clock, Activity, ShieldCheck, ArrowRight, Radio, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { BloodRequest } from '@/types';
import SlotManager from '@/components/slots/SlotManager';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';

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
      const res = await apiClient.get<any>('/requests');
      const reqData = res.data?.data ?? res.data;
      if (Array.isArray(reqData)) {
        setRequests(reqData);
      } else {
        setRequests([]);
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to load hospital requests telemetry.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHospitalData();
  }, [loadHospitalData]);

  const criticalCount = requests.filter((r) => r.urgency === 'CRITICAL').length;
  const activeCount = requests.filter((r) => r.status === 'OPEN' || r.status === 'ESCALATING').length;

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Hospital Banner */}
      <div className="relative glass-card rounded-3xl p-8 border border-red-500/20 overflow-hidden bg-gradient-to-r from-red-950/40 via-rose-950/20 to-neutral-900/50">
        <div className="absolute top-0 right-0 w-96 h-96 bg-red-500/10 rounded-full blur-3xl pointer-events-none" />
        
        <div className="relative flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-xl shadow-red-500/30 flex-shrink-0">
              <Building2 className="w-8 h-8 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-500/20 text-red-300 border border-red-500/30 uppercase tracking-wider">
                  {t('hospitalDashboard.portalTitle')}
                </span>
                <span className="flex items-center gap-1 text-xs text-emerald-400 font-medium">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> {t('hospitalDashboard.verifiedUnit')}
                </span>
              </div>
              <h1 className="text-3xl font-bold text-white">{t('hospitalDashboard.dispatchCenter')}</h1>
              <p className="text-gray-400 text-sm mt-1 max-w-xl">
                {t('hospitalDashboard.dispatchDesc')}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={loadHospitalData}
              disabled={loading}
              className="inline-flex items-center gap-2 px-4 py-3 rounded-2xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-medium transition-colors"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              {t('hospitalDashboard.refreshFeed')}
            </button>
            <Link
              href="/dashboard/requests/new"
              className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-sm px-6 py-3.5 rounded-2xl shadow-xl shadow-red-500/30 transition-all duration-200 transform hover:-translate-y-0.5"
            >
              <Plus className="w-5 h-5" />
              {t('hospitalDashboard.broadcastRequest')}
            </Link>
          </div>
        </div>
      </div>

      {error && (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="glass-card rounded-2xl p-5 border border-white/5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400 flex-shrink-0">
            <Radio className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <div className="text-2xl font-bold text-white">{activeCount}</div>
            <div className="text-xs text-gray-400 mt-0.5">{t('hospitalDashboard.activeBroadcasts')}</div>
          </div>
        </div>

        <div className="glass-card rounded-2xl p-5 border border-white/5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 flex-shrink-0">
            <AlertCircle className="w-6 h-6" />
          </div>
          <div>
            <div className="text-2xl font-bold text-amber-300">{criticalCount}</div>
            <div className="text-xs text-gray-400 mt-0.5">{t('hospitalDashboard.criticalUrgency')}</div>
          </div>
        </div>

        <div className="glass-card rounded-2xl p-5 border border-white/5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 flex-shrink-0">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <div className="text-2xl font-bold text-white">100%</div>
            <div className="text-xs text-gray-400 mt-0.5">{t('hospitalDashboard.escalationActive')}</div>
          </div>
        </div>

        <div className="glass-card rounded-2xl p-5 border border-white/5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 flex-shrink-0">
            <Activity className="w-6 h-6" />
          </div>
          <div>
            <div className="text-2xl font-bold text-white">5 km - 25 km</div>
            <div className="text-xs text-gray-400 mt-0.5">{t('hospitalDashboard.searchRadius')}</div>
          </div>
        </div>
      </div>

      {/* Hospital Blood Requisitions List */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Droplets className="w-5 h-5 text-red-500" />
            {t('hospitalDashboard.telemetry')}
          </h2>
          <Link
            href="/dashboard/requests"
            className="text-sm text-red-400 hover:text-red-300 transition-colors inline-flex items-center gap-1"
          >
            {t('hospitalDashboard.viewAllRequests')} <ArrowRight className="w-4 h-4" />
          </Link>
        </div>

        {loading ? (
          <div className="glass-card rounded-2xl p-12 text-center text-gray-400 border border-white/5">
            <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
            <p>{t('common.loading')}</p>
          </div>
        ) : requests.length === 0 ? (
          <div className="glass-card rounded-2xl p-12 text-center border-dashed border-white/10 space-y-3">
            <Droplets className="w-12 h-12 text-gray-600 mx-auto" />
            <p className="text-white font-medium">{t('hospitalDashboard.noActiveRequests')}</p>
            <p className="text-sm text-gray-400">{t('hospitalDashboard.noActiveRequestsDesc')}</p>
            <div className="pt-2">
              <Link
                href="/dashboard/requests/new"
                className="inline-flex items-center gap-2 bg-red-600 hover:bg-red-500 text-white font-medium text-sm px-4 py-2 rounded-xl transition-colors"
              >
                <Plus className="w-4 h-4" />
                {t('hospitalDashboard.createRequest')}
              </Link>
            </div>
          </div>
        ) : (
          <div className="grid gap-4">
            {requests.map((req) => (
              <div
                key={req.id}
                className="glass-card rounded-2xl p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 border border-white/5 hover:border-red-500/20 transition-all duration-200"
              >
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-lg font-black text-red-400 flex-shrink-0">
                    {req.patientBloodType}
                  </div>
                  <div>
                    <div className="flex items-center gap-3 mb-1">
                      <span className="font-bold text-white text-base">{req.hospitalName}</span>
                      <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        req.urgency === 'CRITICAL'
                          ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                          : req.urgency === 'URGENT'
                          ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                          : 'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                      }`}>
                        {req.urgency}
                      </span>
                      <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-white/5 text-gray-300 border border-white/10">
                        {req.status}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-4 text-xs text-gray-400">
                      <span>{t('request.district')}: <strong className="text-gray-300">{req.district}</strong></span>
                      <span>{t('hospitalDashboard.radius')}: <strong className="text-gray-300">{req.currentRadiusKm} km</strong></span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-gray-500" />
                        {t('hospitalDashboard.expires')} {new Date(req.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3 self-end sm:self-auto">
                  <Link
                    href={`/dashboard/requests/${req.id}`}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-500/10 hover:bg-purple-500/20 text-purple-300 border border-purple-500/20 text-xs font-semibold transition-colors"
                  >
                    {t('hospitalDashboard.manageCircle')} →
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Donation Camp Management */}
      <div className="space-y-4 pt-8 border-t border-white/10 mt-8">
        <CampManagerDashboard />
      </div>

      {/* Walk-in Slot Management */}
      <div className="space-y-4 pt-8 border-t border-white/10 mt-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Clock className="w-5 h-5 text-amber-400" />
            {t('hospitalDashboard.walkInSlots')}
          </h2>
        </div>
        <div className="glass-card rounded-2xl p-6 border border-white/5 bg-gradient-to-br from-white/[0.02] to-transparent">
          {user ? (
            <SlotManager hostType="hospital" hostId={user.id} />
          ) : (
            <div className="text-sm text-gray-500 text-center py-6">User session required to manage slots.</div>
          )}
        </div>
      </div>
    </div>
  );
}
