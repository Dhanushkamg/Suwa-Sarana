'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Activity, Droplets, CheckCircle2,
  AlertCircle, ShieldCheck, Clock, User, Phone, Sparkles, Share2
} from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
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

export default function CircleResponseClient({ token, initialData }: Props) {
  const { t } = useI18n();

  const [info, setInfo] = useState<CircleInfo | null>(initialData || null);
  const [loading, setLoading] = useState(!initialData);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [form, setForm] = useState({
    responderName: '',
    responderPhone: '',
    bloodType: initialData?.patientBloodType || 'O+',
    notes: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const bloodTypeOptions = [
    { value: 'A+', label: t('bloodTypes.APositive') },
    { value: 'A-', label: t('bloodTypes.ANegative') },
    { value: 'B+', label: t('bloodTypes.BPositive') },
    { value: 'B-', label: t('bloodTypes.BNegative') },
    { value: 'AB+', label: t('bloodTypes.ABPositive') },
    { value: 'AB-', label: t('bloodTypes.ABNegative') },
    { value: 'O+', label: t('bloodTypes.OPositive') },
    { value: 'O-', label: t('bloodTypes.ONegative') },
  ];

  const loadCircleInfo = useCallback(async () => {
    if (initialData) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<any>(`/circle/${token}`);
      const data = res.data?.data ?? res.data;
      setInfo(data);
      if (data?.patientBloodType) {
        setForm((prev) => ({ ...prev, bloodType: data.patientBloodType }));
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'This circle invite link is invalid or has expired.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [token, initialData]);

  useEffect(() => {
    loadCircleInfo();
  }, [loadCircleInfo]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      await apiClient.post(`/circle/${token}/respond`, {
        responderName: form.responderName,
        responderPhone: form.responderPhone,
        bloodType: form.bloodType,
        notes: form.notes || undefined,
      });
      setSubmitted(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to submit response. Please verify your details.';
      setFormError(msg);
    } finally {
      setSubmitting(false);
    }
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
        <div className="flex items-center gap-3">
          <Link
            href={`/share/${token}`}
            className="inline-flex items-center gap-1.5 text-xs text-red-400 hover:text-red-300 font-medium px-3 py-1.5 rounded-lg bg-red-500/10 border border-red-500/20 transition-colors"
          >
            <Share2 className="w-3.5 h-3.5" /> Social Card
          </Link>
          <LanguageSwitcher />
        </div>
      </header>

      {/* Main Content */}
      <div className="max-w-xl mx-auto w-full py-6">
        {loading ? (
          <div className="glass-card rounded-3xl p-12 text-center">
            <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-gray-400">Loading emergency blood request...</p>
          </div>
        ) : error || !info ? (
          <div className="glass-card rounded-3xl p-10 text-center border-red-500/20">
            <div className="w-16 h-16 rounded-2xl bg-red-500/10 border border-red-500/20 mx-auto flex items-center justify-center text-red-400 mb-4">
              <AlertCircle className="w-8 h-8" />
            </div>
            <h1 className="text-2xl font-bold text-white mb-2">Invite Unavailable</h1>
            <p className="text-gray-400 text-sm max-w-md mx-auto mb-6">{error}</p>
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-white text-sm font-medium border border-white/10 transition-colors"
            >
              Go to Home Page
            </Link>
          </div>
        ) : submitted ? (
          /* Success Screen */
          <div className="glass-card rounded-3xl p-10 text-center border-emerald-500/30 bg-emerald-950/10 animate-in fade-in zoom-in-95 duration-300">
            <div className="w-20 h-20 rounded-3xl bg-emerald-500/20 border border-emerald-500/30 mx-auto flex items-center justify-center text-emerald-400 mb-5 shadow-xl shadow-emerald-500/20">
              <CheckCircle2 className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-white mb-2">Thank You, {form.responderName}!</h1>
            <p className="text-gray-300 text-sm max-w-md mx-auto mb-6">
              Your response has been delivered directly to the patient's family/requester. They will contact you shortly at <strong className="text-white">{form.responderPhone}</strong>.
            </p>

            <div className="p-5 rounded-2xl bg-white/5 border border-white/10 max-w-md mx-auto space-y-3 mb-6">
              <div className="flex items-center gap-2 text-sm text-emerald-300 font-medium justify-center">
                <Sparkles className="w-4 h-4" /> Want to save more lives in Sri Lanka?
              </div>
              <p className="text-xs text-gray-400">
                Register as a verified volunteer donor on Suwa Sarana to receive location-based alerts when someone near you needs blood.
              </p>
            </div>

            <Link
              href="/register"
              className="inline-flex items-center justify-center gap-2 w-full max-w-md py-3.5 rounded-xl bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-sm shadow-xl shadow-red-500/25 transition-all"
            >
              <Heart className="w-4 h-4 fill-white" /> Register as a Regular Donor
            </Link>
          </div>
        ) : (
          /* Active Requisition & Volunteer Form */
          <div className="space-y-6 animate-in fade-in duration-300">
            {/* Request Summary Card */}
            <div className="glass-card rounded-3xl p-6 md:p-8 border border-red-500/30 relative overflow-hidden bg-gradient-to-br from-red-950/40 via-neutral-900/60 to-neutral-900/90">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div className="flex items-center gap-4">
                  <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center text-3xl font-black text-white shadow-xl shadow-red-500/30">
                    {info.patientBloodType}
                  </div>
                  <div>
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-red-500/20 text-red-300 border border-red-500/30 mb-1 inline-block">
                      {info.urgency} Urgency
                    </span>
                    <h1 className="text-2xl font-bold text-white">{info.hospitalName}</h1>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-3 border-t border-white/10 text-xs text-gray-300">
                <div className="flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-red-400" />
                  <span>District: <strong className="text-white">{info.district}</strong></span>
                </div>
                <div className="flex items-center gap-2">
                  <Droplets className="w-4 h-4 text-red-400" />
                  <span>Required: <strong className="text-white">{info.unitsNeeded} Units</strong></span>
                </div>
              </div>
            </div>

            {/* Volunteer Form */}
            <form onSubmit={handleSubmit} className="glass-card rounded-3xl p-6 md:p-8 border border-white/10 space-y-5">
              <div>
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <User className="w-5 h-5 text-red-400" />
                  Volunteer to Donate
                </h2>
                <p className="text-xs text-gray-400 mt-1">
                  Fill in your contact information below so the patient's family can contact you directly.
                </p>
              </div>

              {formError && (
                <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 text-xs">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              <Input
                id="responder-name"
                label="Your Full Name"
                placeholder="e.g. Kasun Perera"
                value={form.responderName}
                onChange={(e) => setForm({ ...form, responderName: e.target.value })}
                required
              />

              <Input
                id="responder-phone"
                type="tel"
                label="Your Phone Number"
                placeholder="e.g. 077 123 4567"
                value={form.responderPhone}
                onChange={(e) => setForm({ ...form, responderPhone: e.target.value })}
                required
              />

              <Select
                id="responder-blood-type"
                label="Your Blood Type"
                options={bloodTypeOptions}
                value={form.bloodType}
                onChange={(e) => setForm({ ...form, bloodType: e.target.value })}
              />

              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">
                  Notes / Availability (Optional)
                </label>
                <textarea
                  rows={2}
                  placeholder="e.g. Available after 2 PM today"
                  value={form.notes}
                  onChange={(e) => setForm({ ...form, notes: e.target.value })}
                  className="w-full bg-white/5 border border-white/10 rounded-xl p-3 text-white placeholder-gray-500 focus:outline-none focus:border-red-500/50 transition-colors text-sm"
                />
              </div>

              <Button type="submit" size="lg" className="w-full" loading={submitting}>
                Submit Volunteering Response
              </Button>
            </form>
          </div>
        )}
      </div>

      {/* Footer */}
      <footer className="max-w-xl mx-auto w-full text-center text-xs text-gray-600 pt-6 border-t border-white/5">
        Suwa Sarana Emergency Blood Requisition Platform · Sri Lanka
      </footer>
    </main>
  );
}
