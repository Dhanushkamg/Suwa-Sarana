'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { MapPin, ShieldAlert, Sparkles, Bot, ArrowRight, Wand2, LocateFixed } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/Toast';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { apiMessage, unwrap } from '@/lib/api/common';
import { useAuthStore } from '@/store/authStore';
import { DISTRICTS, districtCenter } from '@/lib/districts';
import { FilterChips } from '@/components/dash/FilterChips';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { CARD, EYEBROW } from '@/components/dash/styles';

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

type CoordSource = 'district' | 'device' | 'ai';

export default function NewBloodRequestPage() {
  const router = useRouter();
  const toast = useToast();
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
  // Where the coordinates came from; once they are device/AI supplied they stop following the district.
  const [coordSource, setCoordSource] = useState<CoordSource>('district');
  const [locating, setLocating] = useState(false);
  const [locationNote, setLocationNote] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);
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

  // Example prompts: one per language, shown for the active language only (the text IS the sample input, so it stays in that language)
  const samplePrompts = [
    { lang: 'EN', text: 'Urgent need for 2 units of O+ blood at Karapitiya Teaching Hospital in Galle for emergency surgery' },
    { lang: 'SI', text: 'ගාල්ල කරාපිටිය රෝහලේ හදිසි සැත්කමක් සඳහා B- ලේ යුනිට් 2ක් අවශ්‍යයි' },
    { lang: 'TA', text: 'கராபிட்டிய போதனா வைத்தியசாலையில் அவசர அறுவை சிகிச்சைக்கு 2 யூனிட் A- இரத்தம் தேவை' },
  ];

  const handleAiExtraction = async () => {
    if (!aiPrompt.trim() || aiLoading) return;
    setAiLoading(true);
    setError('');
    setAiMessage(null);

    try {
      const res = await apiClient.post('/requests/ai-draft', { text: aiPrompt.trim(), locale: locale || 'en' });
      const data = unwrap<AiDraft | null>(res.data);

      if (data) {
        const aiHasCoords = typeof data.latitude === 'number' && typeof data.longitude === 'number';
        if (aiHasCoords) setCoordSource('ai');
        setForm((prev) => {
          const district = data.district || prev.district;
          // Coordinates: AI-supplied > device-locked > centre of the (new) district.
          const [dLat, dLng] = districtCenter(district);
          const useDistrict = !aiHasCoords && coordSource === 'district';
          return {
            ...prev,
            patientBloodType: data.patientBloodType || prev.patientBloodType,
            unitsNeeded: data.unitsNeeded || prev.unitsNeeded,
            urgency: data.urgency === 'CRITICAL' && !isVerified ? 'URGENT' : data.urgency || prev.urgency,
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
      setError(apiMessage(err, t('dash.aiUnavailable')));
      setMode('form');
    } finally {
      setAiLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    setError('');

    if (form.urgency === 'CRITICAL' && !isVerified) {
      setError(t('dash.criticalRestrictedError'));
      return;
    }

    setLoading(true);
    try {
      await apiClient.post('/requests', {
        patientBloodType: form.patientBloodType,
        unitsNeeded: Math.min(10, Math.max(1, Number(form.unitsNeeded) || 1)),
        urgency: form.urgency,
        hospitalName: form.hospitalName.trim(),
        district: form.district,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
      });
      toast.show(t('request.successMessage'), 'success');
      router.push('/dashboard/requests');
    } catch (err: unknown) {
      setError(apiMessage(err, t('common.error')));
      setLoading(false);
    }
  };

  const handleDistrictChange = (district: string) => {
    setForm((prev) => {
      if (coordSource !== 'district') return { ...prev, district };
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
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoordSource('device');
        setLocating(false);
        setForm((prev) => ({
          ...prev,
          latitude: Number(pos.coords.latitude.toFixed(6)),
          longitude: Number(pos.coords.longitude.toFixed(6)),
        }));
      },
      // Denied: keep the district-based coordinates and tell the user.
      () => {
        setLocating(false);
        setLocationNote(t('dash.locationDenied'));
      },
      { timeout: 10000 }
    );
  };

  const locationText =
    coordSource === 'device' ? t('dash.locationDevice') : coordSource === 'ai' ? t('dashUi.req.locationFromText') : t('dash.locationDistrict', { district: form.district });

  return (
    <div className="max-w-3xl">
      <PageHeader eyebrow={t('dashUi.eyebrow.newRequest')} title={t('request.title')} description={t('request.subtitle')} backHref="/dashboard" />

      <div className="space-y-6">
        {/* Mode switch */}
        <FilterChips
          label={t('dashUi.intakeMode')}
          value={mode}
          onChange={setMode}
          options={[
            { value: 'ai', label: t('aiRequest.aiAssistant') },
            { value: 'form', label: t('aiRequest.standardForm') },
          ]}
        />

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

              <FormField label={t('aiRequest.describeEmergency')}>
                {(p) => (
                  <textarea
                    {...p}
                    rows={5}
                    placeholder={t('aiRequest.aiPlaceholder')}
                    value={aiPrompt}
                    onChange={(e) => setAiPrompt(e.target.value)}
                    className="w-full resize-y rounded-xl border border-line-strong bg-surface px-4 py-3 text-sm leading-relaxed text-foreground placeholder:text-muted hover:border-foreground/25 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20"
                  />
                )}
              </FormField>

              {/* Quick sample prompts in the active language */}
              <div className="space-y-2.5">
                <p className={EYEBROW}>{t('aiRequest.tryExample')}</p>
                <div className="flex flex-col gap-2">
                  {samplePrompts
                    .filter((p) => p.lang.toLowerCase() === (locale || 'en'))
                    .map((p) => (
                      <button
                        key={p.lang}
                        type="button"
                        onClick={() => setAiPrompt(p.text)}
                        className="group flex min-h-11 items-start justify-between gap-3 rounded-xl border border-line p-3.5 text-start text-sm leading-relaxed text-gray-300 transition-colors hover:border-line-strong hover:bg-surface-raised focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                      >
                        <span className="min-w-0">
                          <span className="me-2 font-medium text-primary-text">[{p.lang}]</span>
                          {p.text}
                        </span>
                        <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-gray-500 rtl:rotate-180" aria-hidden="true" />
                      </button>
                    ))}
                </div>
              </div>

              <Button type="button" size="lg" onClick={handleAiExtraction} loading={aiLoading} disabled={!aiPrompt.trim()} className="w-full">
                {!aiLoading && <Sparkles className="h-4 w-4" aria-hidden="true" />}
                {aiLoading ? t('aiRequest.analyzing') : t('aiRequest.extractButton')}
              </Button>
            </Panel>
          </Rise>
        )}

        {/* Structured review and final submission form */}
        {mode === 'form' && (
          <Rise>
            <form onSubmit={handleSubmit} className={`${CARD} space-y-9 p-5 sm:p-8`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl text-foreground">{t('aiRequest.reviewDetails')}</h2>
                  <p className="mt-1 text-sm text-gray-400">{t('aiRequest.verifyEdit')}</p>
                </div>
                <Button type="button" variant="ghost" onClick={() => setMode('ai')}>
                  <Sparkles className="h-4 w-4 text-primary-text" aria-hidden="true" />
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
                    <Notice tone="warning" icon={<ShieldAlert />} className="mt-3">
                      <strong className="font-semibold">{t('aiRequest.accountVerificationReq')}</strong> {t('aiRequest.criticalRestricted')}{' '}
                      <span className="rounded bg-warning-soft px-1.5 py-0.5 font-medium">{t(`dashUi.verification.${user?.verificationStatus || 'PENDING'}`)}</span>.
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

                  <Select id="district" label={t('request.district')} options={districtOptions} value={form.district} onChange={(e) => handleDistrictChange(e.target.value)} />
                </div>

                {/* Plain-language location: no raw coordinates for people to edit */}
                <div className="flex flex-col justify-between gap-3 rounded-xl border border-line bg-surface-raised p-4 sm:flex-row sm:items-center">
                  <div className="flex min-w-0 items-start gap-3">
                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
                    <div className="min-w-0 text-sm leading-relaxed text-gray-400">
                      <p>{locationText}</p>
                      <p className="mt-1 text-gray-500">{t('dashUi.req.coordsHint')}</p>
                      {locationNote && (
                        <p role="status" className="mt-1 text-warning-text">
                          {locationNote}
                        </p>
                      )}
                    </div>
                  </div>
                  <Button type="button" variant="secondary" onClick={detectLocation} loading={locating} className="shrink-0">
                    {!locating && <LocateFixed className="h-4 w-4" aria-hidden="true" />}
                    {t('dash.useMyLocation')}
                  </Button>
                </div>
              </section>

              {/* Submit bar: pinned to the bottom of the screen on mobile */}
              <div className="sticky bottom-3 z-20 rounded-2xl border border-line-strong bg-surface-raised p-2 shadow-lg sm:static sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0 sm:shadow-none">
                <Button type="submit" size="lg" className="w-full" loading={loading}>
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
