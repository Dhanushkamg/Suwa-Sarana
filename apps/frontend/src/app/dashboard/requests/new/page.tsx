'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Heart, MapPin, AlertCircle, CheckCircle2, ArrowLeft, ShieldAlert,
  Sparkles, FileText, Bot, ArrowRight, Wand2
} from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/store/authStore';

const DISTRICTS = [
  'Colombo', 'Gampaha', 'Kalutara', 'Kandy', 'Matale', 'Nuwara Eliya',
  'Galle', 'Matara', 'Hambantota', 'Jaffna', 'Kilinochchi', 'Mannar',
  'Vavuniya', 'Mullaitivu', 'Batticaloa', 'Ampara', 'Trincomalee',
  'Kurunegala', 'Puttalam', 'Anuradhapura', 'Polonnaruwa', 'Badulla',
  'Monaragala', 'Ratnapura', 'Kegalle'
];

export default function NewBloodRequestPage() {
  const router = useRouter();
  const { t, locale } = useI18n();
  const { user } = useAuthStore();
  const isVerified = user?.verificationStatus === 'VERIFIED';

  const [mode, setMode] = useState<'ai' | 'form'>('ai');
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiMessage, setAiMessage] = useState<string | null>(null);

  const [form, setForm] = useState({
    patientBloodType: 'O+',
    unitsNeeded: 1,
    urgency: 'URGENT',
    hospitalName: '',
    district: 'Colombo',
    latitude: 6.9271,
    longitude: 79.8612,
  });

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

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

  const urgencyOptions = [
    { value: 'ROUTINE', label: t('urgency.ROUTINE') },
    { value: 'URGENT', label: t('urgency.URGENT') },
    {
      value: 'CRITICAL',
      label: isVerified ? t('urgency.CRITICAL') : `${t('urgency.CRITICAL')} (Verified Only)`,
      disabled: !isVerified,
    },
  ];

  const districtOptions = DISTRICTS.map((d) => ({ value: d, label: d }));

  const samplePrompts = [
    {
      lang: 'EN',
      text: 'Urgent need for 2 units of O+ blood at Karapitiya Teaching Hospital in Galle for emergency surgery',
    },
    {
      lang: 'SI',
      text: 'ගාල්ල කරාපිටිය රෝහලේ හදිසි සැත්කමක් සඳහා B- ලේ යුනිට් 2ක් අවශ්‍යයි',
    },
    {
      lang: 'TA',
      text: 'கராபிட்டிய போதனா வைத்தியசாலையில் அவசர அறுவை சிகிச்சைக்கு 2 யூனிட் A- இரத்தம் தேவை',
    },
  ];

  const handleAiExtraction = async () => {
    if (!aiPrompt.trim()) return;
    setAiLoading(true);
    setError('');
    setAiMessage(null);

    try {
      const res = await apiClient.post<any>('/requests/ai-draft', {
        text: aiPrompt.trim(),
        locale: locale || 'en',
      });
      const data = res.data?.data ?? res.data;

      if (data) {
        setForm((prev) => ({
          ...prev,
          patientBloodType: data.patientBloodType || prev.patientBloodType,
          unitsNeeded: data.unitsNeeded || prev.unitsNeeded,
          urgency: data.urgency === 'CRITICAL' && !isVerified ? 'URGENT' : (data.urgency || prev.urgency),
          hospitalName: data.hospitalName || prev.hospitalName,
          district: data.district || prev.district,
          latitude: data.latitude || prev.latitude,
          longitude: data.longitude || prev.longitude,
        }));
        setAiMessage(data.message || 'Draft extracted successfully! Review the pre-filled fields below.');
        setMode('form'); // Switch to standard review form
      }
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'AI parsing unavailable. Please use the standard structured form below.';
      setError(msg);
      setMode('form');
    } finally {
      setAiLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (form.urgency === 'CRITICAL' && !isVerified) {
      setError('CRITICAL urgency is restricted to verified accounts.');
      return;
    }

    setLoading(true);

    try {
      await apiClient.post('/requests', {
        patientBloodType: form.patientBloodType,
        unitsNeeded: Number(form.unitsNeeded),
        urgency: form.urgency,
        hospitalName: form.hospitalName,
        district: form.district,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
      });

      setSuccess(true);
      setTimeout(() => {
        router.push('/dashboard/requests');
      }, 2000);
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('common.error');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const detectLocation = () => {
    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setForm((prev) => ({
            ...prev,
            latitude: Number(pos.coords.latitude.toFixed(6)),
            longitude: Number(pos.coords.longitude.toFixed(6)),
          }));
        },
        () => {
          // Keep default coordinates if denied
        }
      );
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <Link
          href="/dashboard"
          className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('common.back')}
        </Link>
        <h1 className="text-3xl font-extrabold text-white flex items-center gap-3">
          <Heart className="w-8 h-8 text-red-500 fill-red-500" />
          {t('request.title')}
        </h1>
        <p className="text-gray-400 mt-2">{t('request.subtitle')}</p>
      </div>

      {/* Mode Switcher Tabs */}
      <div className="flex items-center gap-3 p-1.5 rounded-2xl bg-white/5 border border-white/10 w-full sm:w-fit">
        <button
          type="button"
          onClick={() => setMode('ai')}
          className={`flex-1 sm:flex-initial flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-xs font-bold transition-all ${
            mode === 'ai'
              ? 'bg-gradient-to-r from-red-500 to-rose-600 text-white shadow-lg shadow-red-500/30'
              : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <Sparkles className="w-4 h-4" />
          ✨ AI Natural Language Assistant
        </button>
        <button
          type="button"
          onClick={() => setMode('form')}
          className={`flex-1 sm:flex-initial flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-xs font-bold transition-all ${
            mode === 'form'
              ? 'bg-white/15 text-white border border-white/20'
              : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <FileText className="w-4 h-4" />
          Standard Structured Form
        </button>
      </div>

      {success && (
        <div className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-5 flex items-center gap-3 text-emerald-400 animate-in fade-in duration-200">
          <CheckCircle2 className="w-6 h-6 flex-shrink-0" />
          <div>
            <p className="font-semibold">{t('request.successMessage')}</p>
          </div>
        </div>
      )}

      {error && (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-5 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertCircle className="w-6 h-6 flex-shrink-0" />
          <div>
            <p className="font-semibold">{error}</p>
          </div>
        </div>
      )}

      {aiMessage && (
        <div className="rounded-2xl border border-blue-500/30 bg-blue-500/10 p-4 flex items-center gap-3 text-blue-300 text-xs animate-in fade-in duration-200">
          <Wand2 className="w-4 h-4 text-blue-400 flex-shrink-0" />
          <span>{aiMessage}</span>
        </div>
      )}

      {/* AI Conversational Intake Mode */}
      {mode === 'ai' && (
        <div className="glass-card rounded-3xl p-6 md:p-8 border border-red-500/20 bg-gradient-to-br from-red-950/20 via-neutral-900/60 to-black space-y-6">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center text-white shadow-lg shadow-red-500/30 flex-shrink-0">
              <Bot className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">Describe Your Emergency in Plain Words</h2>
              <p className="text-xs text-gray-300 mt-1">
                Speak or type naturally in <strong>English</strong>, <strong>Sinhala (සිංහල)</strong>, or <strong>Tamil (தமிழ்)</strong>. Our multilingual medical intake AI will extract the blood type, hospital, urgency, and district automatically into an editable draft.
              </p>
            </div>
          </div>

          <div className="space-y-2">
            <textarea
              rows={4}
              placeholder="e.g. Karapitiya hospital eke emergency ekakata O+ le units 2k ona wela thiyenawa..."
              value={aiPrompt}
              onChange={(e) => setAiPrompt(e.target.value)}
              className="w-full bg-white/5 border border-white/10 rounded-2xl p-4 text-white placeholder-gray-500 focus:outline-none focus:border-red-500/50 transition-colors text-sm leading-relaxed"
            />
          </div>

          {/* Quick Language Sample Chips */}
          <div className="space-y-2">
            <span className="text-[11px] font-bold uppercase tracking-wider text-gray-400">
              Or Try an Example Description:
            </span>
            <div className="flex flex-col gap-2">
              {samplePrompts.map((p, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setAiPrompt(p.text)}
                  className="text-left text-xs p-3 rounded-xl bg-white/3 hover:bg-white/8 border border-white/5 hover:border-white/15 text-gray-300 transition-colors flex items-center justify-between gap-3"
                >
                  <span className="truncate">
                    <strong className="text-red-400 font-mono mr-2">[{p.lang}]</strong>
                    {p.text}
                  </span>
                  <ArrowRight className="w-3.5 h-3.5 text-gray-500 flex-shrink-0" />
                </button>
              ))}
            </div>
          </div>

          <div className="pt-2">
            <Button
              type="button"
              size="lg"
              onClick={handleAiExtraction}
              loading={aiLoading}
              disabled={!aiPrompt.trim()}
              className="w-full flex items-center justify-center gap-2 font-bold"
            >
              <Sparkles className="w-4 h-4" />
              {aiLoading ? 'Analyzing & Extracting Details...' : 'Extract & Pre-fill Requisition Form'}
            </Button>
          </div>
        </div>
      )}

      {/* Structured Review & Final Submission Form */}
      {mode === 'form' && (
        <form onSubmit={handleSubmit} className="glass-card rounded-3xl p-6 md:p-8 space-y-6 border border-white/10">
          <div className="flex items-center justify-between border-b border-white/5 pb-4">
            <div>
              <h2 className="text-lg font-bold text-white">Requisition Details Review</h2>
              <p className="text-xs text-gray-400">
                Verify and edit every field before final submission.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setMode('ai')}
              className="text-xs text-red-400 hover:text-red-300 inline-flex items-center gap-1 font-semibold"
            >
              <Sparkles className="w-3.5 h-3.5" /> Re-parse with AI
            </button>
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              id="blood-type"
              label={t('request.patientBloodType')}
              options={bloodTypeOptions}
              value={form.patientBloodType}
              onChange={(e) => setForm({ ...form, patientBloodType: e.target.value })}
            />

            <Input
              id="units-needed"
              type="number"
              min="1"
              max="10"
              label={t('request.unitsNeeded')}
              value={String(form.unitsNeeded)}
              onChange={(e) => setForm({ ...form, unitsNeeded: Number(e.target.value) })}
              required
            />
          </div>

          <div>
            <Select
              id="urgency"
              label={t('request.urgencyLevel')}
              options={urgencyOptions}
              value={form.urgency}
              onChange={(e) => setForm({ ...form, urgency: e.target.value })}
            />
            {!isVerified && (
              <div className="mt-2 flex items-start gap-2 rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-300">
                <ShieldAlert className="w-4 h-4 flex-shrink-0 mt-0.5 text-amber-400" />
                <span>
                  <strong>Account Verification Required:</strong> CRITICAL urgency is restricted to admin-verified requesters and hospitals. Your current status is{' '}
                  <span className="font-semibold uppercase text-white bg-amber-500/30 px-1.5 py-0.5 rounded">
                    {user?.verificationStatus || 'PENDING'}
                  </span>
                  .
                </span>
              </div>
            )}
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              id="hospital-name"
              type="text"
              label={t('request.hospitalName')}
              placeholder="e.g. National Hospital of Sri Lanka"
              value={form.hospitalName}
              onChange={(e) => setForm({ ...form, hospitalName: e.target.value })}
              required
            />

            <Select
              id="district"
              label={t('request.district')}
              options={districtOptions}
              value={form.district}
              onChange={(e) => setForm({ ...form, district: e.target.value })}
            />
          </div>

          <div className="rounded-xl bg-white/4 p-4 border border-white/5 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-gray-300 uppercase tracking-wider">
                Geospatial Coordinates
              </span>
              <button
                type="button"
                onClick={detectLocation}
                className="inline-flex items-center gap-1.5 text-xs text-red-400 hover:text-red-300 transition-colors"
              >
                <MapPin className="w-3.5 h-3.5" />
                Auto-detect GPS
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Input
                id="latitude"
                type="number"
                step="any"
                label={t('request.latitude')}
                value={String(form.latitude)}
                onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })}
              />
              <Input
                id="longitude"
                type="number"
                step="any"
                label={t('request.longitude')}
                value={String(form.longitude)}
                onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })}
              />
            </div>
          </div>

          <div className="pt-2">
            <Button type="submit" size="lg" className="w-full font-bold" loading={loading} disabled={success}>
              {t('request.submit')}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
