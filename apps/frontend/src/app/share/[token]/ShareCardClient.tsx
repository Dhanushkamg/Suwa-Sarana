'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Activity, Droplets, Share2, Copy, Check,
  MessageSquare, Send, ArrowRight, ArrowLeft, ExternalLink, AlertCircle
} from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';

interface CircleInfo {
  inviteToken: string;
  patientBloodType: string;
  unitsNeeded: number;
  urgency: string;
  hospitalName: string;
  district: string;
  expiresAt: string;
  isExpired: boolean;
}

interface Props {
  token: string;
  initialData?: CircleInfo | null;
}

export default function ShareCardClient({ token, initialData }: Props) {
  const { t } = useI18n();
  const [info, setInfo] = useState<CircleInfo | null>(initialData || null);
  const [loading, setLoading] = useState(!initialData);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const loadInfo = useCallback(async () => {
    if (initialData) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<any>(`/circle/${token}`);
      const data = res.data?.data ?? res.data;
      setInfo(data);
    } catch {
      setError('Emergency requisition card not found or expired.');
    } finally {
      setLoading(false);
    }
  }, [token, initialData]);

  useEffect(() => {
    loadInfo();
  }, [loadInfo]);

  const circleUrl = typeof window !== 'undefined'
    ? `${window.location.origin}/circle/${token}`
    : `https://suwasarana.lk/circle/${token}`;

  const shareText = info
    ? `🚨 EMERGENCY: Urgent ${info.patientBloodType} blood needed at ${info.hospitalName} (${info.district}). Urgency: ${info.urgency}. Please volunteer or share:`
    : '🚨 EMERGENCY: Urgent blood needed. Please volunteer or share:';

  const copyLink = () => {
    navigator.clipboard.writeText(circleUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  return (
    <main className="min-h-screen bg-[#0a0a0f] text-gray-100 flex flex-col justify-between py-8 px-4">
      {/* Header */}
      <header className="max-w-xl mx-auto w-full flex items-center justify-between pb-6 border-b border-white/5">
        <Link href="/" className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30">
            <Heart className="w-5 h-5 text-white fill-white" />
          </div>
          <span className="text-lg font-black tracking-tight text-white">Suwa Sarana</span>
        </Link>
        <Link
          href={`/circle/${token}`}
          className="inline-flex items-center gap-1.5 text-xs text-emerald-400 hover:text-emerald-300 font-medium px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 transition-colors"
        >
          Volunteer Form →
        </Link>
      </header>

      {/* Main Container */}
      <div className="max-w-xl mx-auto w-full py-6 space-y-6">
        {loading ? (
          <div className="glass-card rounded-3xl p-12 text-center">
            <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-gray-400">Loading social share card...</p>
          </div>
        ) : error || !info ? (
          <div className="glass-card rounded-3xl p-10 text-center border-red-500/20">
            <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4" />
            <h1 className="text-2xl font-bold text-white mb-2">Card Unavailable</h1>
            <p className="text-gray-400 text-sm mb-6">{error}</p>
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white/5 text-white text-sm"
            >
              Go to Home Page
            </Link>
          </div>
        ) : (
          <>
            {/* Shareable Card Preview Container */}
            <div className="glass-card rounded-3xl p-8 border-2 border-red-500/40 relative overflow-hidden bg-gradient-to-br from-red-950/60 via-neutral-900/80 to-black shadow-2xl shadow-red-500/20">
              <div className="flex items-center justify-between gap-4 mb-6">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-red-400 animate-ping" />
                  <span className="text-xs font-bold uppercase tracking-wider text-red-400">
                    Verified Emergency Broadcast
                  </span>
                </div>
                <span className="px-3 py-1 rounded-full text-xs font-bold uppercase bg-red-500/20 text-red-300 border border-red-500/30">
                  {info.urgency}
                </span>
              </div>

              <div className="flex items-center gap-6 mb-6">
                <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center text-4xl font-black text-white shadow-xl shadow-red-500/40 flex-shrink-0">
                  {info.patientBloodType}
                </div>
                <div>
                  <h1 className="text-2xl font-black text-white leading-snug">{info.hospitalName}</h1>
                  <p className="text-sm text-gray-400 flex items-center gap-1.5 mt-1">
                    <MapPin className="w-4 h-4 text-red-400" /> {info.district} District
                  </p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-between text-xs text-gray-300 mb-6">
                <div className="flex items-center gap-2">
                  <Droplets className="w-4 h-4 text-red-400" />
                  <span>Units Required: <strong className="text-white">{info.unitsNeeded} Units</strong></span>
                </div>
                <div className="flex items-center gap-2">
                  <Activity className="w-4 h-4 text-blue-400" />
                  <span>Status: <strong className="text-emerald-400">Accepting Donors</strong></span>
                </div>
              </div>

              <Link
                href={`/circle/${token}`}
                className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-bold text-sm shadow-xl shadow-red-500/30 flex items-center justify-center gap-2 transition-all transform hover:-translate-y-0.5"
              >
                <Heart className="w-4 h-4 fill-white" /> Volunteer to Donate Now
              </Link>
            </div>

            {/* Direct Social Broadcast Actions */}
            <div className="glass-card rounded-3xl p-6 border border-white/10 space-y-4">
              <h2 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wider">
                <Share2 className="w-4 h-4 text-red-400" />
                Share Across Social Networks
              </h2>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <a
                  href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + circleUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 transition-all font-medium text-xs text-center"
                >
                  <MessageSquare className="w-6 h-6" />
                  WhatsApp
                </a>

                <a
                  href={`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(circleUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 transition-all font-medium text-xs text-center"
                >
                  <Share2 className="w-6 h-6" />
                  Facebook
                </a>

                <a
                  href={`https://t.me/share/url?url=${encodeURIComponent(circleUrl)}&text=${encodeURIComponent(shareText)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl bg-sky-500/10 border border-sky-500/20 text-sky-400 hover:bg-sky-500/20 transition-all font-medium text-xs text-center"
                >
                  <Send className="w-6 h-6" />
                  Telegram
                </a>

                <button
                  onClick={copyLink}
                  className="flex flex-col items-center justify-center gap-2 p-4 rounded-2xl bg-white/5 border border-white/10 text-white hover:bg-white/10 transition-all font-medium text-xs text-center"
                >
                  {copied ? <Check className="w-6 h-6 text-emerald-400" /> : <Copy className="w-6 h-6" />}
                  {copied ? 'Copied!' : 'Copy Link'}
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Footer */}
      <footer className="max-w-xl mx-auto w-full text-center text-xs text-gray-600 pt-6 border-t border-white/5">
        Suwa Sarana Emergency Blood Requisition Platform · Sri Lanka
      </footer>
    </main>
  );
}
