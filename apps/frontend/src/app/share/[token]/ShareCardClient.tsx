'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Activity, Droplets, Share2, Copy, Check,
  MessageSquare, Send, AlertCircle, Clock, Smartphone
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
  const [timeLeft, setTimeLeft] = useState<string>('');

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

  useEffect(() => {
    if (!info?.expiresAt || info.isExpired) return;

    const calculateTimeLeft = () => {
      const difference = new Date(info.expiresAt).getTime() - new Date().getTime();
      
      if (difference <= 0) {
        setTimeLeft('Expired');
        return;
      }

      const hours = Math.floor((difference / (1000 * 60 * 60)) % 24);
      const minutes = Math.floor((difference / 1000 / 60) % 60);
      setTimeLeft(`${hours}h ${minutes}m left`);
    };

    calculateTimeLeft();
    const timer = setInterval(calculateTimeLeft, 60000); // Update every minute

    return () => clearInterval(timer);
  }, [info?.expiresAt, info?.isExpired]);

  const circleUrl = typeof window !== 'undefined'
    ? `${window.location.origin}/circle/${token}`
    : `https://suwasarana.lk/circle/${token}`;

  const shareText = info
    ? `🚨 EMERGENCY: ${info.unitsNeeded} Units of ${info.patientBloodType} blood needed URGENTLY at ${info.hospitalName} (${info.district}). Your donation can save a life today. Please volunteer or share this message:`
    : '🚨 EMERGENCY: Urgent blood needed. Please volunteer or share:';

  const copyLink = () => {
    navigator.clipboard.writeText(`${shareText}\n\n${circleUrl}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  return (
    <main className="min-h-screen bg-[#0a0005] text-gray-100 flex flex-col justify-between py-8 px-4 relative overflow-hidden">
      {/* Background Ambient Glows */}
      <div className="absolute top-[-10%] right-[-10%] w-[500px] h-[500px] rounded-full bg-red-600/10 blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[-10%] left-[-10%] w-[500px] h-[500px] rounded-full bg-red-900/20 blur-[100px] pointer-events-none" />
      
      {/* Subtle Diagonal Red Accent */}
      <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-red-600 to-red-900 pointer-events-none" />

      {/* Header */}
      <header className="max-w-xl mx-auto w-full flex items-center justify-between pb-6 border-b border-white/5 relative z-10">
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-red-500 to-red-800 flex items-center justify-center shadow-lg shadow-red-500/20 group-hover:shadow-red-500/40 transition-shadow">
            <Heart className="w-5 h-5 text-white fill-white" />
          </div>
          <div className="flex flex-col">
             <span className="text-lg font-black tracking-tight text-white leading-tight">Suwa Sarana</span>
             <span className="text-[10px] text-gray-400 font-medium">Sri Lanka Emergency Blood Network</span>
          </div>
        </Link>
        <Link
          href={`/circle/${token}`}
          className="inline-flex items-center gap-1.5 text-xs text-white font-medium px-4 py-2 rounded-xl bg-red-600 hover:bg-red-500 shadow-md shadow-red-600/20 transition-colors"
        >
          Volunteer Form →
        </Link>
      </header>

      {/* Main Container */}
      <div className="max-w-xl mx-auto w-full py-6 space-y-6 relative z-10">
        {loading ? (
          <div className="glass-card rounded-3xl p-12 text-center border border-white/5">
            <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-gray-400">Loading emergency details...</p>
          </div>
        ) : error || !info ? (
          <div className="glass-card rounded-3xl p-10 text-center border-red-500/20 bg-red-950/10">
            <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4" />
            <h1 className="text-2xl font-bold text-white mb-2">Card Unavailable</h1>
            <p className="text-gray-400 text-sm mb-6">{error}</p>
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-white text-sm transition-colors border border-white/10"
            >
              Go to Home Page
            </Link>
          </div>
        ) : (
          <>
            {/* Dramatic Social Card Preview Container */}
            <div className="rounded-[2rem] p-1 border border-red-500/30 bg-gradient-to-b from-red-500/20 to-transparent shadow-2xl shadow-red-900/30">
              <div className="bg-[#0f0505] rounded-[1.8rem] p-6 md:p-8 relative overflow-hidden">
                {/* Card Internal Glow */}
                 <div className="absolute top-0 right-0 w-64 h-64 bg-red-600/10 rounded-full blur-[60px]" />
                 
                <div className="relative z-10">
                  {/* Top Bar inside Card */}
                  <div className="flex items-center justify-between gap-4 mb-8">
                     <div className="flex items-center gap-2">
                       <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse" />
                       <span className="text-[10px] font-bold uppercase tracking-widest text-red-400">
                         Verified Broadcast
                       </span>
                     </div>
                     <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${
                        info.urgency === 'CRITICAL' 
                          ? 'bg-red-500/20 text-red-400 border border-red-500/30' 
                          : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                     }`}>
                       🚨 {info.urgency}
                     </span>
                  </div>

                  {/* Hero Section */}
                  <div className="flex items-center gap-6 mb-8">
                     <div className="w-24 h-24 md:w-28 md:h-28 rounded-[2rem] bg-gradient-to-br from-red-500 to-red-800 flex items-center justify-center text-4xl md:text-5xl font-black text-white shadow-xl shadow-red-600/40 border border-red-400/50 flex-shrink-0 relative overflow-hidden">
                        <div className="absolute inset-0 bg-white/10" style={{ clipPath: 'polygon(0 0, 100% 0, 100% 30%, 0 60%)' }} />
                        {info.patientBloodType}
                     </div>
                     <div className="flex flex-col justify-center">
                        <h2 className="text-xl md:text-2xl font-black text-white leading-tight mb-2">
                          <span className="text-red-400">{info.unitsNeeded} Units</span> of {info.patientBloodType} Needed <span className="bg-red-600 text-white px-2 py-0.5 rounded text-sm align-middle ml-1 shadow-md shadow-red-900/50">URGENTLY</span>
                        </h2>
                        <h3 className="text-sm md:text-base text-gray-300 font-medium">
                          {info.hospitalName}
                        </h3>
                     </div>
                  </div>

                  {/* Badges Row */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
                    <div className="bg-red-950/30 border border-red-500/20 rounded-xl p-3 flex flex-col justify-center">
                      <div className="text-red-400 text-xs font-semibold mb-1 flex items-center gap-1.5"><Clock className="w-3.5 h-3.5" /> Time Left</div>
                      <div className="text-white font-bold text-sm">{timeLeft || 'Expired'}</div>
                    </div>
                    <div className="bg-red-950/30 border border-red-500/20 rounded-xl p-3 flex flex-col justify-center">
                      <div className="text-red-400 text-xs font-semibold mb-1 flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5" /> Location</div>
                      <div className="text-white font-bold text-sm truncate">{info.district}</div>
                    </div>
                    <div className="bg-red-950/30 border border-red-500/20 rounded-xl p-3 flex flex-col justify-center">
                      <div className="text-red-400 text-xs font-semibold mb-1 flex items-center gap-1.5"><Droplets className="w-3.5 h-3.5" /> Required</div>
                      <div className="text-white font-bold text-sm">{info.unitsNeeded} Units</div>
                    </div>
                    <div className="bg-red-950/30 border border-red-500/20 rounded-xl p-3 flex flex-col justify-center">
                      <div className="text-red-400 text-xs font-semibold mb-1 flex items-center gap-1.5"><Activity className="w-3.5 h-3.5" /> Status</div>
                      <div className="text-emerald-400 font-bold text-sm">Accepting</div>
                    </div>
                  </div>

                  {/* Motivational Text */}
                  <div className="text-center py-4 border-t border-white/5">
                     <p className="text-sm text-gray-400 italic">"One blood donation can save up to 3 lives."</p>
                  </div>
                  
                  {/* CTA Button */}
                  <Link
                    href={`/circle/${token}`}
                    className="w-full py-4 rounded-xl bg-gradient-to-r from-red-600 to-red-800 hover:from-red-500 hover:to-red-700 text-white font-bold text-base shadow-lg shadow-red-600/30 flex items-center justify-center gap-2 transition-all transform hover:scale-[1.02]"
                  >
                    <Heart className="w-5 h-5 fill-white" /> Yes, I want to Volunteer
                  </Link>
                </div>
              </div>
            </div>

            {/* Direct Social Broadcast Actions */}
            <div className="glass-card rounded-3xl p-6 border border-white/10 space-y-5 bg-white/[0.02]">
              <div className="text-center">
                 <h2 className="text-lg font-bold text-white flex items-center justify-center gap-2 mb-1">
                   <Share2 className="w-5 h-5 text-red-400" />
                   Share This Request
                 </h2>
                 <p className="text-xs text-gray-400">Amplify this appeal across your networks to find a donor faster.</p>
              </div>

              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <a
                  href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + circleUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2.5 p-4 rounded-2xl bg-[#25D366]/10 border border-[#25D366]/30 text-[#25D366] hover:bg-[#25D366]/20 transition-colors"
                >
                  <MessageSquare className="w-7 h-7" />
                  <span className="font-semibold text-xs">WhatsApp</span>
                </a>

                <a
                  href={`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(circleUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2.5 p-4 rounded-2xl bg-[#1877F2]/10 border border-[#1877F2]/30 text-[#1877F2] hover:bg-[#1877F2]/20 transition-colors"
                >
                  <Share2 className="w-7 h-7" />
                  <span className="font-semibold text-xs">Facebook</span>
                </a>

                <a
                  href={`https://t.me/share/url?url=${encodeURIComponent(circleUrl)}&text=${encodeURIComponent(shareText)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2.5 p-4 rounded-2xl bg-[#0088cc]/10 border border-[#0088cc]/30 text-[#0088cc] hover:bg-[#0088cc]/20 transition-colors"
                >
                  <Send className="w-7 h-7" />
                  <span className="font-semibold text-xs">Telegram</span>
                </a>
                
                <a
                  href={`viber://forward?text=${encodeURIComponent(shareText + ' ' + circleUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-col items-center justify-center gap-2.5 p-4 rounded-2xl bg-[#7360f2]/10 border border-[#7360f2]/30 text-[#7360f2] hover:bg-[#7360f2]/20 transition-colors"
                >
                  <Smartphone className="w-7 h-7" />
                  <span className="font-semibold text-xs">Viber</span>
                </a>
              </div>
              
              <div className="pt-2">
                 <button
                    onClick={copyLink}
                    className="w-full flex items-center justify-center gap-2 p-3 rounded-xl bg-white/5 border border-white/10 text-white hover:bg-white/10 transition-colors text-sm font-medium"
                  >
                    {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    {copied ? 'Copied to Clipboard!' : 'Copy Appeal Message & Link'}
                  </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Footer */}
      <footer className="max-w-xl mx-auto w-full text-center py-6 border-t border-white/5 relative z-10 mt-4">
        <div className="flex flex-col items-center gap-2">
           <span className="text-sm font-bold text-gray-300">suwasarana.lk</span>
           <span className="text-[10px] text-gray-500 uppercase tracking-widest">Sri Lanka Emergency Blood Network</span>
        </div>
      </footer>
    </main>
  );
}
