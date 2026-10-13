'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { MapPin, ShieldAlert, Sparkles, Bot, ArrowRight, Wand2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/store/authStore';
import { DISTRICTS, districtCenter } from '@/lib/districts';
import { FilterChips } from '@/components/dash/FilterChips';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { EYEBROW } from '@/components/dash/styles';

interface AiDraft {
  patientBloodType?: string;
  unitsNeeded?: number;
  urgency?: string;
  hospitalName?: string;
  district?: string;
  latitude?: number;
  longitude?: number;
  message?: string;
}

interface ApiBody<T> {
  data?: T;
}

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
    latitude: districtCenter('Colombo')[0],
    longitude: districtCenter('Colombo')[1],
  });
  // True once the coordinates were set by GPS, the AI draft or manual typing: they then stop following the district.
  const [coordsLocked, setCoordsLocked] = useState(false);
  const [locationNote, setLocationNote] = useState<string | null>(null);

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
      label: isVerified ? t('urgency.CRITICAL') : `${t('urgency.CRITICAL')} (${t('dash.verifiedOnly')})`,
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
      const res = await apiClient.post<ApiBody<AiDraft>>('/requests/ai-draft', {
        text: aiPrompt.trim(),
        locale: locale || 'en',
      });
      const data = res.data?.data;

      if (data) {
        const aiHasCoords = typeof data.latitude === 'number' && typeof data.longitude === 'number';
        if (aiHasCoords) setCoordsLocked(true);
        setForm((prev) => {
          const district = data.district || prev.district;
          // Coordinates: AI-supplied > manually locked > centre of the (new) district.
          const [dLat, dLng] = districtCenter(district);
          const useDistrict = !aiHasCoords && !coordsLocked;
          return {
            ...prev,
            patientBloodType: data.patientBloodType || prev.patientBloodType,
            unitsNeeded: data.unitsNeeded || prev.unitsNeeded,
            urgency: data.urgency === 'CRITICAL' && !isVerified ? 'URGENT' : (data.urgency || prev.urgency),
            hospitalName: data.hospitalName || prev.hospitalName,
            district,
            latitude: aiHasCoords ? (data.latitude as number) : useDistrict ? dLat : prev.latitude,
            longitude: aiHasCoords ? (data.longitude as number) : useDistrict ? dLng : prev.longitude,
          };
        });
        setAiMessage(data.message || t('dash.aiDraftDone'));
        setMode('form'); // Switch to standard review form
      }
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('dash.aiUnavailable');
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
      setError(t('dash.criticalRestrictedError'));
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

  const handleDistrictChange = (district: string) => {
    setForm((prev) => {
      if (coordsLocked) return { ...prev, district };
      const [latitude, longitude] = districtCenter(district);
      return { ...prev, district, latitude, longitude };
    });
  };

  const detectLocation = () => {
    setLocationNote(null);
    if (!('geolocation' in navigator)) {
      setLocationNote(t('dash.locationDenied'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoordsLocked(true);
        setForm((prev) => ({
          ...prev,
          latitude: Number(pos.coords.latitude.toFixed(6)),
          longitude: Number(pos.coords.longitude.toFixed(6)),
        }));
      },
      // Denied: keep the district-based coordinates and tell the user.
      () => setLocationNote(t('dash.locationDenied')),
      { timeout: 10000 }
    );
  };

  return (
    <div className="max-w-3xl">
      <PageHeader
        eyebrow={t('dashUi.eyebrow.newRequest')}
        title={t('request.title')}
        description={t('request.subtitle')}
        backHref="/dashboard"
      />

      <div className="space-y-6">
        {/* Mode switch */}
        <FilterChips
          id="request-mode"
          label={t('dashUi.intakeMode')}
          value={mode}
          onChange={setMode}
          options={[
            { value: 'ai', label: t('aiRequest.aiAssistant') },
            { value: 'form', label: t('aiRequest.standardForm') },
          ]}
        />

        {success && <Notice tone="success">{t('request.successMessage')}</Notice>}
        {error && <Notice tone="error">{error}</Notice>}
        {aiMessage && (
          <Notice tone="info" icon={<Wand2 />}>
            {aiMessage}
          </Notice>
        )}

        {/* AI Conversational Intake Mode */}
        {mode === 'ai' && (
          <Rise>
            <Panel className="space-y-6 sm:p-8">
              <SectionHeader
                className="mb-0"
                eyebrow={t('aiRequest.aiAssistant')}
                title={t('aiRequest.describeEmergency')}
                description={t('aiRequest.aiInstructions')}
                icon={
                  <IconTile size="md">
                    <Bot />
                  </IconTile>
                }
              />

              <textarea
                rows={5}
                placeholder={t('aiRequest.aiPlaceholder')}
                aria-label={t('aiRequest.describeEmergency')}
                value={aiPrompt}
                onChange={(e) => setAiPrompt(e.target.value)}
                className="w-full resize-y rounded-xl border border-foreground/[0.16] bg-foreground/[0.035] px-4 py-3 text-sm leading-relaxed text-foreground placeholder-gray-600 transition-[border-color,background-color,box-shadow] duration-300 hover:border-foreground/25 focus:border-ember focus:bg-foreground/[0.06] focus:outline-none focus:ring-4 focus:ring-ember/15"
              />

              {/* Quick sample prompts in the active language */}
              <div className="space-y-2.5">
                <p className={EYEBROW}>{t('aiRequest.tryExample')}</p>
                <div className="flex flex-col gap-2">
                  {samplePrompts.filter((p) => p.lang.toLowerCase() === (locale || 'en')).map((p) => (
                    <button
                      key={p.lang}
                      type="button"
                      onClick={() => setAiPrompt(p.text)}
                      className="group flex items-start justify-between gap-3 rounded-xl border border-line p-3.5 text-left text-xs leading-relaxed text-gray-300 transition-colors hover:border-foreground/[0.16] hover:bg-foreground/[0.04]"
                    >
                      <span className="min-w-0">
                        <span className="mr-2 font-mono text-ember">[{p.lang}]</span>
                        {p.text}
                      </span>
                      <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-gray-500 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                    </button>
                  ))}
                </div>
              </div>

              <Button type="button" size="lg" onClick={handleAiExtraction} loading={aiLoading} disabled={!aiPrompt.trim()} className="w-full">
                <Sparkles className="h-4 w-4" aria-hidden="true" />
                {aiLoading ? t('aiRequest.analyzing') : t('aiRequest.extractButton')}
              </Button>
            </Panel>
          </Rise>
        )}

        {/* Structured review and final submission form */}
        {mode === 'form' && (
          <Rise>
            <form onSubmit={handleSubmit} className="glass-card space-y-9 rounded-2xl p-5 sm:p-8">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl text-foreground">{t('aiRequest.reviewDetails')}</h2>
                  <p className="mt-1 text-sm text-gray-400">{t('aiRequest.verifyEdit')}</p>
                </div>
                <Button type="button" variant="ghost" size="sm" onClick={() => setMode('ai')}>
                  <Sparkles className="h-3.5 w-3.5 text-ember" aria-hidden="true" />
                  {t('aiRequest.reparseAI')}
                </Button>
              </div>

              <section className="space-y-5">
                <SectionLabel>{t('dashUi.section.patient')}</SectionLabel>
                <div className="grid gap-4 sm:grid-cols-2">
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
                    <Notice tone="warning" icon={<ShieldAlert />} className="mt-3 text-xs">
                      <strong className="font-semibold text-foreground">{t('aiRequest.accountVerificationReq')}</strong> {t('aiRequest.criticalRestricted')}{' '}
                      <span className="rounded bg-amber-500/25 px-1.5 py-0.5 font-mono uppercase text-foreground">
                        {user?.verificationStatus || 'PENDING'}
                      </span>
                      .
                    </Notice>
                  )}
                </div>
              </section>

              <section className="space-y-5">
                <SectionLabel>{t('dashUi.section.hospital')}</SectionLabel>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input
                    id="hospital-name"
                    type="text"
                    label={t('request.hospitalName')}
                    placeholder={t('dash.hospitalPlaceholder')}
                    value={form.hospitalName}
                    onChange={(e) => setForm({ ...form, hospitalName: e.target.value })}
                    required
                  />

                  <Select
                    id="district"
                    label={t('request.district')}
                    options={districtOptions}
                    value={form.district}
                    onChange={(e) => handleDistrictChange(e.target.value)}
                  />
                </div>

                <div className="space-y-3 rounded-xl border border-line bg-foreground/[0.03] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className={EYEBROW}>{t('aiRequest.geospatialCoords')}</span>
                    <Button type="button" variant="ghost" size="sm" onClick={detectLocation}>
                      <MapPin className="h-3.5 w-3.5 text-ember" aria-hidden="true" />
                      {t('aiRequest.autoDetectGPS')}
                    </Button>
                  </div>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Input
                      id="latitude"
                      type="number"
                      step="any"
                      label={t('request.latitude')}
                      value={String(form.latitude)}
                      onChange={(e) => {
                        setCoordsLocked(true);
                        setForm({ ...form, latitude: Number(e.target.value) });
                      }}
                    />
                    <Input
                      id="longitude"
                      type="number"
                      step="any"
                      label={t('request.longitude')}
                      value={String(form.longitude)}
                      onChange={(e) => {
                        setCoordsLocked(true);
                        setForm({ ...form, longitude: Number(e.target.value) });
                      }}
                    />
                  </div>
                  <p className="text-xs leading-relaxed text-gray-500">{t('dash.coordsHint')}</p>
                  {locationNote && <p className="text-xs text-saffron">{locationNote}</p>}
                </div>
              </section>

              {/* Submit bar: pinned to the bottom of the screen on mobile */}
              <div className="sticky bottom-3 z-20 rounded-2xl border border-foreground/[0.16] bg-surface-raised p-2 shadow-2xl shadow-black/50 sm:static sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0 sm:shadow-none">
                <Button type="submit" size="lg" className="w-full" loading={loading} disabled={success}>
                  {t('request.submit')}
                </Button>
              </div>
            </form>
          </Rise>
        )}
      </div>
    </div>
  );
}
