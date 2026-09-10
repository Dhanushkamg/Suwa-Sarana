'use client';

import { useState, useEffect } from 'react';
import { ArrowLeft, ShieldCheck, BarChart3, Users, AlertTriangle, CheckCircle2, Flag } from 'lucide-react';
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

  const maxCount = Math.max(...Object.values(analytics), 1);

  useEffect(() => {
    async function load() {
      try {
        const [analyticsRes, reportsRes] = await Promise.all([
          apiClient.get<any>('/admin/analytics'),
          apiClient.get<any>('/admin/reports'),
        ]);
        const analyticsData = analyticsRes.data?.data ?? analyticsRes.data;
        const reportsData = reportsRes.data?.data ?? reportsRes.data;
        setAnalytics(analyticsData || {});
        setReports(reportsData || []);
      } catch {
        // Mock fallback
        setAnalytics({ Colombo: 150, Gampaha: 90, Kandy: 60, Jaffna: 15, Galle: 40 });
        setReports([
          { id: 1, requestId: 102, reason: 'Suspected fraudulent request', createdAt: new Date().toISOString() },
        ]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const handleVerify = async () => {
    if (!verifyUserId.trim()) return;
    setVerifyStatus('loading');
    try {
      await apiClient.post(`/admin/requesters/${verifyUserId.trim()}/verify`);
      setVerifyStatus('success');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    } catch {
      setVerifyStatus('error');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div>
        <Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4">
          <ArrowLeft className="w-4 h-4" />
          Back to Dashboard
        </Link>
        <h1 className="text-3xl font-bold text-white flex items-center gap-3">
          <ShieldCheck className="w-8 h-8 text-purple-500" />
          Admin Dashboard
        </h1>
        <p className="text-gray-400 mt-2">Platform oversight, analytics, and user verification.</p>
      </div>

      {/* Analytics Heatmap */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <h2 className="text-xl font-bold text-white flex items-center gap-3 mb-6">
          <BarChart3 className="w-6 h-6 text-blue-400" />
          Blood Request Heatmap by District
        </h2>

        {loading ? (
          <div className="text-center py-8 text-gray-500">Loading analytics...</div>
        ) : (
          <div className="space-y-3">
            {Object.entries(analytics)
              .sort(([, a], [, b]) => b - a)
              .map(([district, count]) => {
                const pct = Math.round((count / maxCount) * 100);
                const color =
                  pct > 70 ? 'bg-red-500' : pct > 40 ? 'bg-amber-500' : 'bg-emerald-500';
                return (
                  <div key={district} className="flex items-center gap-4">
                    <div className="w-28 text-sm text-gray-300 text-right flex-shrink-0">{district}</div>
                    <div className="flex-1 h-7 rounded-lg bg-white/5 overflow-hidden relative">
                      <div
                        className={`h-full ${color} opacity-80 rounded-lg transition-all duration-700`}
                        style={{ width: `${pct}%` }}
                      />
                      <span className="absolute inset-0 flex items-center pl-3 text-xs font-semibold text-white">
                        {count} requests
                      </span>
                    </div>
                  </div>
                );
              })}
          </div>
        )}
      </section>

      {/* User Verification */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <h2 className="text-xl font-bold text-white flex items-center gap-3 mb-6">
          <Users className="w-6 h-6 text-emerald-400" />
          Verify Requester
        </h2>
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
              <><ShieldCheck className="w-4 h-4" /> Verify</>
            )}
          </button>
        </div>
        {verifyStatus === 'error' && (
          <p className="text-red-400 text-sm mt-3 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" /> Failed. Check the user ID.
          </p>
        )}
      </section>

      {/* Abuse Reports */}
      <section className="glass-card rounded-2xl p-6 border border-white/10">
        <h2 className="text-xl font-bold text-white flex items-center gap-3 mb-6">
          <Flag className="w-6 h-6 text-red-400" />
          Flagged Requests ({reports.length})
        </h2>
        {reports.length === 0 ? (
          <p className="text-gray-500 text-center py-6">No reports at this time.</p>
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
