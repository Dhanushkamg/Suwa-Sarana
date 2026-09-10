'use client';

import { useState, useEffect, useCallback } from 'react';
import { ArrowLeft, ShieldCheck, ShieldAlert, BarChart3, Users, AlertTriangle, CheckCircle2, Flag, UserCheck, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import apiClient from '@/lib/apiClient';

type AnalyticsData = Record<string, number>;
type Report = {
  id: number;
  requestId: number;
  reason: string;
  createdAt: string;
};

export default function AdminDashboardPage() {
  const [analytics, setAnalytics] = useState<AnalyticsData>({});
  const [reports, setReports] = useState<Report[]>([]);
  const [verifyUserId, setVerifyUserId] = useState('');
  const [verifyStatus, setVerifyStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAdminData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [analyticsRes, reportsRes] = await Promise.all([
        apiClient.get<any>('/admin/analytics'),
        apiClient.get<any>('/admin/reports'),
      ]);
      const analyticsData = analyticsRes.data?.data ?? analyticsRes.data;
      const reportsData = reportsRes.data?.data ?? reportsRes.data;
      setAnalytics(analyticsData || {});
      setReports(Array.isArray(reportsData) ? reportsData : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to load admin telemetry data.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAdminData();
  }, [loadAdminData]);

  const maxCount = Math.max(...Object.values(analytics).map((v) => Number(v) || 0), 1);

  const handleVerify = async () => {
    if (!verifyUserId.trim()) return;
    setVerifyStatus('loading');
    try {
      await apiClient.put(`/admin/verifications/${verifyUserId.trim()}/approve`);
      setVerifyStatus('success');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    } catch {
      setVerifyStatus('error');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4">
            <ArrowLeft className="w-4 h-4" />
            Back to Dashboard
          </Link>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <ShieldCheck className="w-8 h-8 text-purple-500" />
            Admin Overview & Telemetry
          </h1>
          <p className="text-gray-400 mt-1">Platform oversight, live geographic load metrics, and moderation queue.</p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={loadAdminData}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-medium transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <Link
            href="/dashboard/admin/triage"
            className="inline-flex items-center gap-2 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-semibold text-xs px-4 py-2.5 rounded-xl shadow-lg shadow-purple-600/25 transition-all"
          >
            <ShieldAlert className="w-4 h-4" />
            AI Triage Queue
          </Link>
          <Link
            href="/dashboard/admin/analytics"
            className="inline-flex items-center gap-2 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-semibold text-xs px-4 py-2.5 rounded-xl shadow-lg shadow-blue-600/25 transition-all"
          >
            <BarChart3 className="w-4 h-4" />
            Interactive Heatmap
          </Link>
          <Link
            href="/dashboard/admin/verifications"
            className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-xs px-4 py-2.5 rounded-xl shadow-lg shadow-red-500/25 transition-all"
          >
            <UserCheck className="w-4 h-4" />
            Verification Portal
          </Link>
        </div>
      </div>

      {error && (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* Interactive Map Banner */}
      <div className="glass-card p-6 rounded-2xl border border-blue-500/20 bg-gradient-to-r from-blue-950/30 via-neutral-900/40 to-neutral-900/60 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-cyan-400" />
            Geographic Shortage Heatmap (Sri Lanka 25 Districts)
          </h2>
          <p className="text-sm text-gray-300 mt-1">
            Explore live donor density, active requisition clusters, and critical shortage alarms on the interactive geospatial map.
          </p>
        </div>
        <Link
          href="/dashboard/admin/analytics"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-500 to-cyan-500 text-white text-xs font-bold shadow-lg shadow-cyan-500/20 hover:opacity-90 transition-all flex-shrink-0 self-start sm:self-auto"
        >
          Open Interactive Map →
        </Link>
      </div>

      {/* Analytics Heatmap */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-white flex items-center gap-3">
            <BarChart3 className="w-6 h-6 text-blue-400" />
            Blood Request Distribution by District
          </h2>
          <Link
            href="/dashboard/admin/analytics"
            className="text-xs text-blue-400 hover:text-blue-300 font-medium inline-flex items-center gap-1"
          >
            View Geospatial Map →
          </Link>
        </div>

        {loading ? (
          <div className="text-center py-8 text-gray-500">
            <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
            <p>Loading analytics...</p>
          </div>
        ) : Object.keys(analytics).length === 0 ? (
          <p className="text-gray-500 text-center py-6">No district analytics data available.</p>
        ) : (
          <div className="space-y-3">
            {Object.entries(analytics)
              .sort(([, a], [, b]) => Number(b) - Number(a))
              .map(([district, count]) => {
                const numCount = Number(count) || 0;
                const pct = Math.round((numCount / maxCount) * 100);
                const color =
                  pct > 70 ? 'bg-red-500' : pct > 40 ? 'bg-amber-500' : 'bg-emerald-500';
                return (
                  <div key={district} className="flex items-center gap-4">
                    <div className="w-28 text-sm text-gray-300 text-right flex-shrink-0">{district}</div>
                    <div className="flex-1 h-7 rounded-lg bg-white/5 overflow-hidden relative">
                      <div
                        className={`h-full ${color} opacity-80 rounded-lg transition-all duration-700`}
                        style={{ width: `${Math.max(pct, 5)}%` }}
                      />
                      <span className="absolute inset-0 flex items-center pl-3 text-xs font-semibold text-white">
                        {numCount} requests
                      </span>
                    </div>
                  </div>
                );
              })}
          </div>
        )}
      </section>

      {/* Quick User Verification */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-white flex items-center gap-3">
            <Users className="w-6 h-6 text-emerald-400" />
            Quick Requester Verification
          </h2>
          <Link
            href="/dashboard/admin/verifications"
            className="text-xs text-red-400 hover:text-red-300 font-medium inline-flex items-center gap-1"
          >
            Open full queue →
          </Link>
        </div>
        <p className="text-xs text-gray-400 mb-4">
          Quickly approve an individual hospital or requester account ID directly, or use the full verification queue.
        </p>
        <div className="flex gap-3">
          <input
            type="number"
            placeholder="Enter User ID to verify..."
            value={verifyUserId}
            onChange={(e) => setVerifyUserId(e.target.value)}
            className="flex-1 bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:border-emerald-500/50 transition-colors text-sm"
          />
          <button
            onClick={handleVerify}
            disabled={verifyStatus === 'loading'}
            className="px-6 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 transition-colors font-medium text-sm disabled:opacity-50 flex items-center gap-2"
          >
            {verifyStatus === 'loading' ? (
              <span>Verifying...</span>
            ) : verifyStatus === 'success' ? (
              <><CheckCircle2 className="w-4 h-4" /> Verified!</>
            ) : (
              <><ShieldCheck className="w-4 h-4" /> Verify User</>
            )}
          </button>
        </div>
        {verifyStatus === 'error' && (
          <p className="text-red-400 text-sm mt-3 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" /> Failed. Check that the User ID exists and is pending.
          </p>
        )}
      </section>

      {/* Abuse & Moderation Reports */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <h2 className="text-xl font-bold text-white flex items-center gap-3 mb-6">
          <Flag className="w-6 h-6 text-red-400" />
          Flagged Reports ({reports.length})
        </h2>
        {reports.length === 0 ? (
          <p className="text-gray-500 text-center py-6">No flagged reports at this time. Platform is healthy.</p>
        ) : (
          <div className="space-y-3">
            {reports.map((report) => (
              <div key={report.id} className="flex items-start gap-4 p-4 rounded-xl bg-red-500/5 border border-red-500/10">
                <AlertTriangle className="w-5 h-5 text-red-400 mt-0.5 flex-shrink-0" />
                <div>
                  <p className="text-sm text-white font-medium">Request #{report.requestId}</p>
                  <p className="text-sm text-gray-400 mt-0.5">{report.reason}</p>
                  <p className="text-xs text-gray-600 mt-1">{new Date(report.createdAt).toLocaleString()}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
