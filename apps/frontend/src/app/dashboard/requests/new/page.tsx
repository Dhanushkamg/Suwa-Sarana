'use client';

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { MapPin, ShieldAlert, Sparkles, Bot, ArrowRight, Wand2, LocateFixed, TriangleAlert, Check } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/Toast';
import { useI18n } from '@/lib/i18n';
import { parseApiError } from '@/lib/apiError';
import { focusFirstInvalid } from '@/components/pub/apiErrors';
import { AI_DRAFT_MAX_CHARS, createRequest, requestAiDraft, type AiDraft, type DraftField, type Urgency } from '@/lib/api/requests';
import { useDistricts } from '@/lib/api/meta';
import { useAuthStore } from '@/store/authStore';
import { FilterChips } from '@/components/dash/FilterChips';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { CARD, EYEBROW } from '@/components/dash/styles';

type FieldKey = 'patientBloodType' | 'unitsNeeded' | 'urgency' | 'hospitalName' | 'district' | 'location';

/** Nothing here has a silent default: blood group, units, urgency, hospital and district start empty and must be chosen. */
interface FormState {
  patientBloodType: string;
  unitsNeeded: string;
  urgency: string;
  hospitalName: string;
  district: string;
}

const EMPTY_FORM: FormState = { patientBloodType: '', unitsNeeded: '', urgency: '', hospitalName: '', district: '' };
const HOSPITAL_MAX = 150;

/**
 * "Please check this" note under an AI-filled field that the server flagged (needsConfirmation). The value only counts after the
 * person presses the button or edits the field themselves.
 */
function CheckNote({ id, onConfirm }: { id: string; onConfirm: () => void }) {
  const { t } = useI18n();
  return (
    <div id={id} className="mt-2 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/30 bg-warning-soft px-3 py-2 text-sm text-foreground">
      <span className="flex min-w-0 items-start gap-2">
        <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0 text-warning-text" aria-hidden="true" />
        {t('aiRequest.pleaseCheck')}
      </span>
      <Button type="button" size="sm" variant="secondary" onClick={onConfirm}>
        <Check className="h-4 w-4" aria-hidden="true" />
        {t('aiRequest.thisIsCorrect')}
      </Button>
    </div>
  );
}

export default function NewBloodRequestPage() {
  const router = useRouter();
  const toast = useToast();
  const { t, locale } = useI18n();
  const { user } = useAuthStore();
  const isVerified = user?.verificationStatus === 'VERIFIED';
  const { districts, centre } = useDistricts();
  const formRef = useRef<HTMLFormElement>(null);
  const uid = useId();

  const [mode, setMode] = useState<'ai' | 'form'>('ai');
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiMessage, setAiMessage] = useState<string | null>(null);
  const [draft, setDraft] = useState<AiDraft | null>(null);
  // Flagged fields the person has looked at (pressed "This is correct" or edited)
  const [acknowledged, setAcknowledged] = useState<Set<DraftField>>(new Set());

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  // Where the coordinates come from: the device (exact) or, otherwise, the centre of the chosen district
  const [device, setDevice] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationNote, setLocationNote] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [focusTick, setFocusTick] = useState(0);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  const bloodTypeOptions = [
    { value: '', label: t('aiRequest.choose') },
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
    { value: '', label: t('aiRequest.choose') },
    { value: 'ROUTINE', label: t('urgency.ROUTINE') },
    { value: 'URGENT', label: t('urgency.URGENT') },
    {
      value: 'CRITICAL',
      label: isVerified ? t('urgency.CRITICAL') : `${t('urgency.CRITICAL')} (${t('dash.verifiedOnly')})`,
      disabled: !isVerified,
    },
  ];

  const districtOptions = [{ value: '', label: t('aiRequest.choose') }, ...districts.map((d) => ({ value: d.name, label: d.name }))];

  // Example prompts: one per language, shown for the active language only (the text IS the sample input, so it stays in that language)
  const samplePrompts = [
    { lang: 'EN', text: 'Urgent need for 2 units of O+ blood at Karapitiya Teaching Hospital in Galle for emergency surgery' },
    { lang: 'SI', text: 'ගාල්ල කරාපිටිය රෝහලේ හදිසි සැත්කමක් සඳහා B- ලේ යුනිට් 2ක් අවශ්‍යයි' },
    { lang: 'TA', text: 'கராபிட்டிய போதனா வைத்தியசாலையில் அவசர அறுவை சிகிச்சைக்கு 2 யூனிட் A- இரத்தம் தேவை' },
  ];

  /** True while an AI-flagged value is still waiting for the person to look at it. */
  const needsCheck = (field: DraftField, value: string | number | null | undefined): boolean =>
    !!draft?.needsConfirmation[field] && !acknowledged.has(field) && value !== '' && value != null;

  const acknowledge = (field: DraftField) => setAcknowledged((prev) => new Set(prev).add(field));

  const setField = (field: keyof FormState, value: string, draftField?: DraftField, errorKey?: FieldKey) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (draftField) acknowledge(draftField); // the person changed it themselves: that counts as checked
    if (errorKey) setErrors((prev) => ({ ...prev, [errorKey]: undefined }));
  };

  const handleAiExtraction = async () => {
    if (!aiPrompt.trim() || aiLoading) return;
    setAiLoading(true);
    setError('');
    setAiMessage(null);

    try {
      const data = await requestAiDraft(aiPrompt, locale);
      setDraft(data);
      setAcknowledged(new Set());
      setErrors({});
      // Only what the AI found is filled in; everything else stays empty for the person to choose.
      setForm({
        patientBloodType: data.patientBloodType ?? '',
        unitsNeeded: data.unitsNeeded != null ? String(data.unitsNeeded) : '',
        // Never let a draft put the person on an urgency they are not allowed to use
        urgency: data.urgency === 'CRITICAL' && !isVerified ? '' : (data.urgency ?? ''),
        hospitalName: data.hospitalName ?? '',
        district: data.district ?? '',
      });
      setDevice(null);
      setAiMessage(data.extractedSuccessfully ? t('aiRequest.draftReady') : t('aiRequest.draftNone'));
      setMode('form'); // Switch to the review form
    } catch (err: unknown) {
      setError(parseApiError(err, t, { fallback: t('dash.aiUnavailable') }).message);
      setMode('form');
    } finally {
      setAiLoading(false);
    }
  };

  const coords = device ? { latitude: device.lat, longitude: device.lng } : form.district ? (() => { const [latitude, longitude] = centre(form.district); return { latitude, longitude }; })() : null;

  /** Required fields must be filled, and every field the AI flagged must have been checked. Returns the errors found. */
  const validate = (): Partial<Record<FieldKey, string>> => {
    const next: Partial<Record<FieldKey, string>> = {};
    const check = t('aiRequest.checkFirst');
    if (!form.patientBloodType) next.patientBloodType = t('aiRequest.chooseBloodType');
    else if (needsCheck('patientBloodType', form.patientBloodType)) next.patientBloodType = check;
    const units = Number(form.unitsNeeded);
    if (!form.unitsNeeded.trim() || !Number.isInteger(units) || units < 1 || units > 10) next.unitsNeeded = t('aiRequest.unitsInvalid');
    else if (needsCheck('unitsNeeded', form.unitsNeeded)) next.unitsNeeded = check;
    if (!form.urgency) next.urgency = t('aiRequest.chooseUrgency');
    else if (form.urgency === 'CRITICAL' && !isVerified) next.urgency = t('dash.criticalRestrictedError');
    else if (needsCheck('urgency', form.urgency)) next.urgency = check;
    if (!form.hospitalName.trim()) next.hospitalName = t('aiRequest.hospitalRequired');
    else if (form.hospitalName.trim().length > HOSPITAL_MAX) next.hospitalName = t('aiRequest.hospitalTooLong', { max: HOSPITAL_MAX });
    else if (needsCheck('hospitalName', form.hospitalName)) next.hospitalName = check;
    if (!form.district) next.district = t('aiRequest.chooseDistrict');
    else if (needsCheck('district', form.district)) next.district = check;
    if (form.district && !device && draft?.needsConfirmation.coordinates && !acknowledged.has('coordinates')) next.location = check;
    return next;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    setError('');

    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0 || !coords) {
      setFocusTick((n) => n + 1);
      return;
    }

    setLoading(true);
    try {
      await createRequest({
        patientBloodType: form.patientBloodType,
        unitsNeeded: Number(form.unitsNeeded),
        urgency: form.urgency as Urgency,
        hospitalName: form.hospitalName,
        district: form.district,
        latitude: coords.latitude,
        longitude: coords.longitude,
      });
      toast.show(t('request.successMessage'), 'success');
      router.push('/dashboard/requests');
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, {
        fallback: t('common.error'),
        fieldAliases: { patientBloodType: 'patientBloodType', unitsNeeded: 'unitsNeeded', hospitalName: 'hospitalName', district: 'district' },
      });
      setError(parsed.message);
      const fieldErrors: Partial<Record<FieldKey, string>> = {};
      for (const key of ['patientBloodType', 'unitsNeeded', 'urgency', 'hospitalName', 'district'] as const) {
        if (parsed.fields[key]) fieldErrors[key] = parsed.fields[key];
      }
      if (parsed.code === 'geo.unknown_district') fieldErrors.district = parsed.message;
      if (Object.keys(fieldErrors).length > 0) {
        setErrors(fieldErrors);
        setFocusTick((n) => n + 1);
      }
      setLoading(false);
    }
  };

  const handleDistrictChange = (district: string) => {
    setField('district', district, 'district', 'district');
    // Coordinates follow the district centre, which makes them the "approximate" kind again
    setDevice(null);
  };

  const detectLocation = useCallback(() => {
    setLocationNote(null);
    if (!('geolocation' in navigator)) {
      setLocationNote(t('dash.locationDenied'));
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        setDevice({ lat: Number(pos.coords.latitude.toFixed(6)), lng: Number(pos.coords.longitude.toFixed(6)) });
        acknowledge('coordinates');
        setErrors((prev) => ({ ...prev, location: undefined }));
      },
      // Denied: keep the district-based coordinates and tell the user.
      () => {
        setLocating(false);
        setLocationNote(t('dash.locationDenied'));
      },
      { timeout: 10000 }
    );
  }, [t]);

  const locationText = device ? t('dash.locationDevice') : form.district ? t('dash.locationDistrict', { district: form.district }) : t('aiRequest.locationNeedsDistrict');
  const locationFlagged = !device && !!form.district && !!draft?.needsConfirmation.coordinates && !acknowledged.has('coordinates');

  const noteId = (name: string) => `${uid}-${name}-check`;
  /** aria-describedby of a control: its own error text plus the "please check" note when there is one. */
  const describedBy = (id: string, flagged: boolean, name: string, error?: string) => [error ? `${id}-error` : null, flagged ? noteId(name) : null].filter(Boolean).join(' ') || undefined;

  const missingFromText = draft ? (['patientBloodType', 'unitsNeeded', 'urgency', 'hospitalName', 'district'] as const).filter((f) => draft.missingFields.includes(f)) : [];

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

              <FormField label={t('aiRequest.describeEmergency')} help={t('aiRequest.aiPrivacy')}>
                {(p) => (
                  <textarea
                    {...p}
                    rows={5}
                    maxLength={AI_DRAFT_MAX_CHARS}
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
            <form ref={formRef} onSubmit={handleSubmit} noValidate className={`${CARD} space-y-9 p-5 sm:p-8`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl text-foreground">{t('aiRequest.reviewDetails')}</h2>
                  <p className="mt-1 text-sm text-gray-400">{draft ? t('aiRequest.verifyEditAi') : t('aiRequest.verifyEdit')}</p>
                </div>
                <Button type="button" variant="ghost" onClick={() => setMode('ai')}>
                  <Sparkles className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  {t('aiRequest.reparseAI')}
                </Button>
              </div>

              {draft && missingFromText.length > 0 && (
                <Notice tone="warning" icon={<TriangleAlert />}>
                  <p className="font-medium">{t('aiRequest.notFound')}</p>
                  <ul className="mt-1 list-disc ps-5">
                    {missingFromText.map((f) => (
                      <li key={f}>{t(`aiRequest.field.${f}`)}</li>
                    ))}
                  </ul>
                </Notice>
              )}
              {draft && draft.warnings.length > 0 && (
                <Notice tone="warning" icon={<TriangleAlert />}>
                  <ul className="list-disc space-y-0.5 ps-5">
                    {draft.warnings.map((w) => (
                      <li key={w}>{w}</li>
                    ))}
                  </ul>
                </Notice>
              )}

              <section className="space-y-5">
                <SectionLabel>{t('dashUi.section.patient')}</SectionLabel>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <Select
                      id="blood-type"
                      label={t('request.patientBloodType')}
                      options={bloodTypeOptions}
                      value={form.patientBloodType}
                      onChange={(e) => setField('patientBloodType', e.target.value, 'patientBloodType', 'patientBloodType')}
                      error={errors.patientBloodType}
                      aria-describedby={describedBy('blood-type', needsCheck('patientBloodType', form.patientBloodType), 'patientBloodType', errors.patientBloodType)}
                      required
                    />
                    {needsCheck('patientBloodType', form.patientBloodType) && <CheckNote id={noteId('patientBloodType')} onConfirm={() => acknowledge('patientBloodType')} />}
                  </div>

                  <div>
                    <Input
                      id="units-needed"
                      type="number"
                      min="1"
                      max="10"
                      inputMode="numeric"
                      label={t('request.unitsNeeded')}
                      value={form.unitsNeeded}
                      onChange={(e) => setField('unitsNeeded', e.target.value, 'unitsNeeded', 'unitsNeeded')}
                      error={errors.unitsNeeded}
                      aria-describedby={describedBy('units-needed', needsCheck('unitsNeeded', form.unitsNeeded), 'unitsNeeded', errors.unitsNeeded)}
                      required
                    />
                    {needsCheck('unitsNeeded', form.unitsNeeded) && <CheckNote id={noteId('unitsNeeded')} onConfirm={() => acknowledge('unitsNeeded')} />}
                  </div>
                </div>

                <div>
                  <Select
                    id="urgency"
                    label={t('request.urgencyLevel')}
                    options={urgencyOptions}
                    value={form.urgency}
                    onChange={(e) => setField('urgency', e.target.value, 'urgency', 'urgency')}
                    error={errors.urgency}
                    aria-describedby={describedBy('urgency', needsCheck('urgency', form.urgency), 'urgency', errors.urgency)}
                    required
                  />
                  {needsCheck('urgency', form.urgency) && <CheckNote id={noteId('urgency')} onConfirm={() => acknowledge('urgency')} />}
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
                  <div>
                    <Input
                      id="hospital-name"
                      type="text"
                      maxLength={HOSPITAL_MAX + 20}
                      label={t('request.hospitalName')}
                      placeholder={t('dash.hospitalPlaceholder')}
                      value={form.hospitalName}
                      onChange={(e) => setField('hospitalName', e.target.value, 'hospitalName', 'hospitalName')}
                      error={errors.hospitalName}
                      aria-describedby={describedBy('hospital-name', needsCheck('hospitalName', form.hospitalName), 'hospitalName', errors.hospitalName)}
                      required
                    />
                    {needsCheck('hospitalName', form.hospitalName) && <CheckNote id={noteId('hospitalName')} onConfirm={() => acknowledge('hospitalName')} />}
                  </div>

                  <div>
                    <Select
                      id="district"
                      label={t('request.district')}
                      options={districtOptions}
                      value={form.district}
                      onChange={(e) => handleDistrictChange(e.target.value)}
                      error={errors.district}
                      aria-describedby={describedBy('district', needsCheck('district', form.district), 'district', errors.district)}
                      required
                    />
                    {needsCheck('district', form.district) && <CheckNote id={noteId('district')} onConfirm={() => acknowledge('district')} />}
                  </div>
                </div>

                {/* Plain-language location: no raw coordinates for people to edit. From the AI the point is only the centre of the district. */}
                <div className="rounded-xl border border-line bg-surface-raised p-4">
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
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
                        {errors.location && (
                          <p id={`${uid}-location-error`} role="alert" className="mt-1 text-danger-text">
                            {errors.location}
                          </p>
                        )}
                      </div>
                    </div>
                    <Button type="button" variant="secondary" onClick={detectLocation} loading={locating} className="shrink-0" aria-invalid={errors.location ? true : undefined} aria-describedby={errors.location ? `${uid}-location-error` : undefined}>
                      {!locating && <LocateFixed className="h-4 w-4" aria-hidden="true" />}
                      {t('dash.useMyLocation')}
                    </Button>
                  </div>
                  {locationFlagged && <CheckNote id={noteId('coordinates')} onConfirm={() => acknowledge('coordinates')} />}
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
