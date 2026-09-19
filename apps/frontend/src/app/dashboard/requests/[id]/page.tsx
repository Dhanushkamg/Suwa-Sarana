'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowLeft, Clock, MapPin, Activity, Droplets, CheckCircle2,
  Share2, Copy, Check, Users, Phone, MessageSquare, AlertCircle, RefreshCw
} from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { API_BASE_URL } from '@/lib/constants';
import { BloodRequest } from '@/types';
import RequestDistanceMap from '@/components/maps/RequestDistanceMap';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useAuthStore } from '@/store/authStore';

interface CircleInviteData {
  requestId: number;
  inviteToken: string;
  inviteUrl: string;
  expiresAt: string;
}

interface CircleVolunteer {
  id: number;
  responderName: string;
  responderPhone: string;
  bloodType: string;
  notes?: string;
  respondedAt: string;
}

export default function RequestLiveStatusPage() {
  const { t } = useI18n();
  const params = useParams();
  const router = useRouter();
  const requestId = params.id as string;
  const { accessToken } = useAuthStore();

  const [requestData, setRequestData] = useState<BloodRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updates, setUpdates] = useState<string[]>([]);

  // Circle state
  const [circleInvite, setCircleInvite] = useState<CircleInviteData | null>(null);
  const [circleVolunteers, setCircleVolunteers] = useState<CircleVolunteer[]>([]);
  const [circleLoading, setCircleLoading] = useState(false);
  const [circleError, setCircleError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const loadRequest = useCallback(async () => {
    try {
      const res = await apiClient.get<any>(`/requests/${requestId}`);
      const data = res.data?.data ?? res.data;
      setRequestData(data);
    } catch {
      setError(t('common.error'));
    } finally {
      setLoading(false);
    }
  }, [requestId, t]);

  const loadCircleResponses = useCallback(async () => {
    try {
      const res = await apiClient.get<any>(`/requests/${requestId}/circle/responses`);
      const data = res.data?.data ?? res.data;
      if (Array.isArray(data)) {
        setCircleVolunteers(data);
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      if (msg === 'Only the request creator can generate or manage private circles' || msg === 'Access denied' || msg?.toLowerCase().includes('access denied')) {
        setCircleError('Access denied');
      }
      // Otherwise, circle might not be generated yet, which is normal
    }
  }, [requestId]);

  useEffect(() => {
    loadRequest();
    loadCircleResponses();
  }, [loadRequest, loadCircleResponses]);

  useEffect(() => {
    if (!accessToken) return;

    const abortController = new AbortController();

    const connectSSE = async () => {
      try {
        await fetchEventSource(`${API_BASE_URL}/notifications/stream`, {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: 'text/event-stream',
          },
          signal: abortController.signal,
          onmessage: (event) => {
            try {
              const data = JSON.parse(event.data);
              if (data.requestId === Number(requestId) || data.type === 'NEW_MATCH') {
                setUpdates((prev) => [data.body || data.message || 'New match activity', ...prev].slice(0, 5));
                loadRequest();
                loadCircleResponses();
              }
            } catch {
              // Plain message
            }
          },
          onerror: (err) => {
            throw err; // trigger auto-reconnect
          },
        });
      } catch {
        // Offline or aborted
      }
    };

    connectSSE();

    return () => {
      abortController.abort();
    };
  }, [requestId, accessToken, loadRequest, loadCircleResponses]);

  const handleGenerateCircle = async () => {
    setCircleLoading(true);
    setCircleError(null);
    try {
      const res = await apiClient.post<any>(`/requests/${requestId}/circle`);
      const data = res.data?.data ?? res.data;
      setCircleInvite(data);
      loadCircleResponses();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to generate private circle invite link.';
      setCircleError(msg);
    } finally {
      setCircleLoading(false);
    }
  };

  const copyToClipboard = (url: string) => {
    navigator.clipboard.writeText(url);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  if (loading) return <div className="text-center py-12 text-gray-500">{t('common.loading')}</div>;
  if (error || !requestData) return <div className="text-center py-12 text-red-500">{error || t('bloodRequests.notFound')}</div>;

  const shareText = `Emergency: Urgent ${requestData.patientBloodType} blood needed at ${requestData.hospitalName} (${requestData.district}). Please check if you can volunteer:`;
  const fullInviteUrl = circleInvite?.inviteUrl || (typeof window !== 'undefined' ? `${window.location.origin}/circle/${circleInvite?.inviteToken}` : '');

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Link href="/dashboard/requests" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors">
        <ArrowLeft className="w-4 h-4" />
        {t('common.back')}
      </Link>

      {/* Main Request Summary Card */}
      <div className="glass-card p-6 md:p-8 rounded-2xl border border-white/10">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-6">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-2xl font-black text-red-400">
              {requestData.patientBloodType}
            </div>
            <div>
              <h1 className="text-2xl font-bold text-white">{requestData.hospitalName}</h1>
              <p className="text-gray-400 flex items-center gap-2 mt-1 text-sm">
                <MapPin className="w-4 h-4" /> {requestData.district}
              </p>
            </div>
          </div>
          <div className="text-left sm:text-right">
            <span className={`inline-block px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider mb-1 ${
              requestData.urgency === 'CRITICAL'
                ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
            }`}>
              {requestData.urgency}
            </span>
            <p className="text-xs text-gray-500 font-mono">{t('bloodRequests.details')} #{requestData.id}</p>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('request.unitsNeeded')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Droplets className="w-4 h-4 text-red-400" /> {requestData.unitsNeeded} Units
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.status')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Activity className="w-4 h-4 text-blue-400" /> {requestData.status}
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.searchRadius')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <MapPin className="w-4 h-4 text-emerald-400" /> {requestData.currentRadiusKm} km
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.expires')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2 text-sm">
              <Clock className="w-4 h-4 text-gray-400" /> 
              {new Date(requestData.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </p>
          </div>
        </div>
      </div>

      {/* Replacement-Donor Private Circle Panel */}
      {circleError !== 'Access denied' && circleError !== 'Only the request creator can generate or manage private circles' && (
      <div className="glass-card p-6 md:p-8 rounded-2xl border border-purple-500/20 relative overflow-hidden bg-gradient-to-r from-purple-950/30 via-neutral-900/40 to-neutral-900/60">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-6">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-purple-500/20 border border-purple-500/30 flex items-center justify-center text-purple-400 flex-shrink-0">
              <Users className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white flex items-center gap-2">
                {t('bloodRequests.privateCircle')}
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-semibold uppercase">
                  {t('bloodRequests.familyAndFriends')}
                </span>
              </h2>
              <p className="text-sm text-gray-300 mt-1 max-w-xl">
                {t('bloodRequests.privateCircleDesc')}
              </p>
            </div>
          </div>

          {!circleInvite ? (
            <button
              onClick={handleGenerateCircle}
              disabled={circleLoading}
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white text-sm font-semibold shadow-lg shadow-purple-600/30 transition-all self-start md:self-auto disabled:opacity-50"
            >
              <Share2 className="w-4 h-4" />
              {circleLoading ? t('bloodRequests.generatingLink') : t('bloodRequests.inviteCircle')}
            </button>
          ) : (
            <button
              onClick={loadCircleResponses}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white text-xs font-medium self-start md:self-auto transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              {t('bloodRequests.refreshCircle')}
            </button>
          )}
        </div>

        {circleError && (
          <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 text-sm">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{circleError}</span>
          </div>
        )}

        {circleInvite && (
          <div className="space-y-4 pt-2">
            <div className="p-4 rounded-xl bg-black/40 border border-white/10 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="truncate text-xs font-mono text-gray-300 select-all">
                {fullInviteUrl}
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                <button
                  onClick={() => copyToClipboard(fullInviteUrl)}
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs font-medium transition-colors"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? t('bloodRequests.copied') : t('bloodRequests.copyLink')}
                </button>
                <Link
                  href={`/share/${circleInvite.inviteToken}`}
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/30 text-xs font-medium transition-colors"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  {t('bloodRequests.previewCard')}
                </Link>
                <a
                  href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + fullInviteUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition-colors"
                >
                  <MessageSquare className="w-3.5 h-3.5" />
                  {t('bloodRequests.whatsapp')}
                </a>
              </div>
            </div>

            {/* Circle Volunteers Roster */}
            <div className="pt-2">
              <h3 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
                <Users className="w-4 h-4 text-purple-400" />
                {t('bloodRequests.circleVolunteers')} ({circleVolunteers.length})
              </h3>
              {circleVolunteers.length === 0 ? (
                <div className="text-center py-8 rounded-xl bg-white/2 border border-white/5 text-gray-500 text-xs">
                  {t('bloodRequests.noResponsesYet')}
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {circleVolunteers.map((vol) => (
                    <div key={vol.id} className="p-4 rounded-xl bg-white/5 border border-white/10 space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-white text-sm">{vol.responderName}</span>
                        <span className="px-2 py-0.5 rounded-md text-xs font-bold bg-red-500/20 text-red-300 border border-red-500/30">
                          {vol.bloodType}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-xs text-gray-400">
                        <Phone className="w-3.5 h-3.5 text-gray-500" />
                        <a href={`tel:${vol.responderPhone}`} className="text-emerald-400 hover:underline">
                          {vol.responderPhone}
                        </a>
                        <span className="text-gray-600">·</span>
                        <span>{new Date(vol.respondedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      </div>
                      {vol.notes && (
                        <p className="text-xs text-gray-400 italic bg-black/20 p-2 rounded">
                          "{vol.notes}"
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
      )}

      {/* Live System Updates */}
      <div className="glass-card p-6 rounded-2xl border border-white/10 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Activity className="w-5 h-5 text-emerald-400" />
            {t('bloodRequests.liveMatchingTelemetry')}
          </h2>
          <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            {t('bloodRequests.radius')}: {requestData.currentRadiusKm} km
          </span>
        </div>

        {/* Dynamic Leaflet Search Perimeter Map */}
        <RequestDistanceMap
          hospitalName={requestData.hospitalName}
          district={requestData.district}
          radiusKm={requestData.currentRadiusKm}
          latitude={requestData.latitude}
          longitude={requestData.longitude}
        />
        
        {updates.length === 0 ? (
          <div className="text-center py-6 text-gray-500 border border-dashed border-white/10 rounded-xl">
            <div className="animate-pulse flex flex-col items-center gap-2">
              <Activity className="w-5 h-5 text-emerald-500/50" />
              <p className="text-xs">{t('bloodRequests.scanningRadius')}</p>
            </div>
          </div>
        ) : (
          <div className="space-y-2.5">
            {updates.map((msg, idx) => (
              <div key={idx} className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-start gap-3">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 flex-shrink-0" />
                <p className="text-emerald-200 text-xs">{msg}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
