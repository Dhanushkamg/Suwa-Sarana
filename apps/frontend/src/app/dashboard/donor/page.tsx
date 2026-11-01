'use client';

import { useState, useEffect, useCallback } from 'react';
import { Droplets, Shield, MapPin, Award, Users, Wifi, WifiOff, AlertCircle, LocateFixed, ArrowUpRight, HandHeart } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { useToast } from '@/components/ui/Toast';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { apiMessage, apiStatus, asDateInput, num, unwrap } from '@/lib/api/common';
import { parseApiError } from '@/lib/apiError';
import { DONOR_PATHS, MIN_WEIGHT_KG_EXCLUSIVE } from '@/lib/api/donors';
import { useLowData } from '@/lib/lowData';
import { useDistricts } from '@/lib/api/meta';
import MyBookings from '@/components/slots/MyBookings';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { DonationHistory, EligibilityCard } from '@/components/dash/DonorPanels';
import { InfoDisclosure } from '@/components/dash/InfoDisclosure';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { SettingRow } from '@/components/dash/Switch';
import { Stat } from '@/components/dash/Stat';
import { StatsSkeleton } from '@/components/dash/Skeletons';
import { CARD } from '@/components/dash/styles';

/** GET/PUT /donors/me (DonorProfileDto). Counters can be missing on older servers: they then render as "no data", never as 0. */
interface DonorProfile {
  bloodType: string;
  dateOfBirth?: unknown;
  weightKg?: number | null;
  district: string;
  latitude?: number | null;
  longitude?: number | null;
  available: boolean;
  reliabilityScore?: number | null;
  totalDonations?: number | null;
  livesHelpedEstimate?: number | null;
}

type CoordSource = 'saved' | 'device' | 'district';

const MIN_AGE = 18;
const MAX_AGE = 60;

function ageFrom(dob: string): number {
  const d = new Date(dob);
  const now = new Date();
  let age = now.getFullYear() - d.getFullYear();
  const m = now.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < d.getDate())) age--;
  return age;
}

function yearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}

const optionalNumber = (v: number | null | undefined): number | null => (v == null ? null : num(v));

export default function DonorProfilePage() {
  const { t } = useI18n();
  const toast = useToast();
  const { lowData, setLowData } = useLowData();
  // The 25 official districts (and their centre points) come from GET /api/meta/districts
  const { districts, centre: districtCenter } = useDistricts();

  const [bloodType, setBloodType] = useState('O+');
  const [district, setDistrict] = useState('Colombo');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [weightKg, setWeightKg] = useState('');
  const [isAvailable, setIsAvailable] = useState(true);
  const [availabilityBusy, setAvailabilityBusy] = useState(false);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [coordSource, setCoordSource] = useState<CoordSource>('district');
  const [locating, setLocating] = useState(false);
  const [locationNote, setLocationNote] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<{ dob?: string; weight?: string }>({});

  const [profile, setProfile] = useState<DonorProfile | null>(null);
  const [saving, setSaving] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadMessage, setLoadMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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

  const districtOptions = districts.map((d) => ({ value: d.name, label: d.name }));

  const applyProfile = useCallback((p: DonorProfile) => {
    setProfile(p);
    setBloodType(p.bloodType || 'O+');
    setDistrict(p.district || 'Colombo');
    setDateOfBirth(asDateInput(p.dateOfBirth));
    setWeightKg(p.weightKg != null ? String(p.weightKg) : '');
    setIsAvailable(p.available !== false);
    if (p.latitude != null && p.longitude != null) {
      setCoords({ lat: p.latitude, lng: p.longitude });
      setCoordSource('saved');
    }
  }, []);

  const loadProfile = useCallback(async () => {
    setInitialLoading(true);
    setLoadFailed(false);
    setLoadMessage(null);
    try {
      const res = await apiClient.get(DONOR_PATHS.me);
      const data = unwrap<DonorProfile | null>(res.data);
      if (data) applyProfile(data);
    } catch (err: unknown) {
      const msg = apiMessage(err, '');
      // A brand-new donor has no profile yet: show the empty form instead of an error.
      if (apiStatus(err) === 404 || /not found/i.test(msg)) {
        setProfile(null);
      } else {
        setLoadFailed(true);
        setLoadMessage(msg || null);
      }
    } finally {
      setInitialLoading(false);
    }
  }, [applyProfile]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadProfile();
  }, [loadProfile]);

  const handleDistrictChange = (value: string) => {
    setDistrict(value);
    // Follow the district centre unless the donor shared their device location.
    if (coordSource !== 'device') {
      const [lat, lng] = districtCenter(value);
      setCoords({ lat, lng });
      setCoordSource('district');
    }
  };

  /** Separate, explicit control: only this button reads the device location. */
  const detectLocation = () => {
    setLocationNote(null);
    if (!('geolocation' in navigator)) {
      setLocationNote(t('dash.locationDenied'));
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: Number(pos.coords.latitude.toFixed(6)), lng: Number(pos.coords.longitude.toFixed(6)) });
        setCoordSource('device');
        setLocating(false);
        setLocationNote(t('dashUi.donor.locationFound'));
      },
      () => {
        setLocating(false);
        setLocationNote(t('dash.locationDenied'));
      },
      { timeout: 10000 }
    );
  };

  const toggleAvailability = async (next: boolean) => {
    if (availabilityBusy) return;
    const previous = isAvailable;
    setIsAvailable(next);
    if (!profile) return; // New donor: applied after the first save.
    setAvailabilityBusy(true);
    setErrorMessage(null);
    try {
      await apiClient.patch(DONOR_PATHS.availability, { isAvailable: next });
      setProfile({ ...profile, available: next });
      toast.show(next ? t('dashUi.donor.availableOn') : t('dashUi.donor.availableOff'), 'success');
    } catch (err: unknown) {
      setIsAvailable(previous);
      setErrorMessage(apiMessage(err, t('dash.availabilityFailed')));
    } finally {
      setAvailabilityBusy(false);
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const errors: { dob?: string; weight?: string } = {};
    if (!dateOfBirth) {
      errors.dob = t('dash.dobInvalid');
    } else {
      const age = ageFrom(dateOfBirth);
      if (age < MIN_AGE || age > MAX_AGE) errors.dob = t('dash.dobInvalid');
    }
    const weight = weightKg.trim() === '' ? null : Number(weightKg);
    if (weight !== null && (!Number.isFinite(weight) || weight > 300)) {
      errors.weight = t('dash.weightInvalid');
    } else if (weight !== null && weight <= MIN_WEIGHT_KG_EXCLUSIVE) {
      errors.weight = t('dashUi.donor.weightTooLow', { kg: MIN_WEIGHT_KG_EXCLUSIVE });
    }
    setFieldErrors(errors);
    if (errors.dob || errors.weight) return;

    const [fallbackLat, fallbackLng] = districtCenter(district);
    const isNew = !profile;
    setSaving(true);
    try {
      const res = await apiClient.put(DONOR_PATHS.me, {
        bloodType,
        dateOfBirth,
        weightKg: weight,
        district,
        latitude: coords?.lat ?? fallbackLat,
        longitude: coords?.lng ?? fallbackLng,
      });
      // New profiles start as available on the server; honour an "unavailable" choice made beforehand.
      if (isNew && !isAvailable) {
        await apiClient.patch(DONOR_PATHS.availability, { isAvailable: false });
      }
      const saved = unwrap<DonorProfile | null>(res.data);
      if (saved) applyProfile({ ...saved, available: isNew ? isAvailable : saved.available });
      toast.show(t('donorDashboard.profileSaved'), 'success');
    } catch (err: unknown) {
      // Field problems from the server (English sentences) are replaced by our own localised texts
      const parsed = parseApiError(err, t, { fallback: t('donorDashboard.updateError') });
      const serverFields: { dob?: string; weight?: string } = {};
      if (parsed.fields.weightKg) serverFields.weight = t('dashUi.donor.weightTooLow', { kg: MIN_WEIGHT_KG_EXCLUSIVE });
      if (parsed.fields.dateOfBirth) serverFields.dob = t('dash.dobInvalid');
      if (serverFields.dob || serverFields.weight) setFieldErrors(serverFields);
      setErrorMessage(parsed.message);
    } finally {
      setSaving(false);
    }
  };

  const score = optionalNumber(profile?.reliabilityScore);
  const donations = optionalNumber(profile?.totalDonations);
  const hasHistory = (donations ?? 0) > 0;
  // A tier is only claimed once there is history behind it; the score alone (everyone starts at 50) proves nothing.
  const tier = score === null ? null : !hasHistory ? 'new' : score >= 80 ? 'excellent' : score >= 60 ? 'good' : 'building';
  const tierTone = tier === 'excellent' ? 'success' : tier === 'good' ? 'primary' : tier === 'building' ? 'warning' : 'default';

  const locationText =
    coordSource === 'device' ? t('dash.locationDevice') : coordSource === 'saved' ? t('dash.locationSaved') : t('dash.locationDistrict', { district });

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.profile')}
        title={t('common.myProfile')}
        description={t('dashboard.donorProfileDesc')}
        actions={
          profile ? (
            <ButtonLink href="/dashboard/donor/matches" variant="primary">
              {t('donorDashboard.viewMyMatches')}
              <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
            </ButtonLink>
          ) : undefined
        }
      />

      <div className="mb-6 space-y-4 empty:hidden">{errorMessage && <Notice tone="error">{errorMessage}</Notice>}</div>

      {initialLoading ? (
        <div className="space-y-6">
          <StatsSkeleton count={3} className="sm:grid-cols-3" />
          <Skeleton className="h-96 w-full rounded-2xl" />
        </div>
      ) : loadFailed ? (
        <EmptyState
          icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={loadMessage || t('donorDashboard.profileError')}
          action={
            <Button variant="secondary" onClick={loadProfile}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : (
        <>
          {!profile && (
            <Notice tone="info" icon={<Droplets />} className="mb-6">
              {t('dash.newProfile')}
            </Notice>
          )}

          {/* Donor stats: real numbers from the profile, or "no data" */}
          {profile && (
            <div className="mb-6 grid gap-4 sm:grid-cols-3">
              <Rise delay={0.05}>
                <Stat
                  label={t('dashUi.donor.reliabilityLabel')}
                  value={score}
                  suffix="/ 100"
                  tone={tierTone}
                  meter={score ?? undefined}
                  icon={<Shield />}
                  hint={tier ? t(`dashUi.donor.tier.${tier}`) : undefined}
                  footer={
                    <InfoDisclosure label={t('dashUi.donor.reliabilityHow')} className="mt-2">
                      <p>{t('dashUi.donor.reliabilityExplain')}</p>
                      <ul className="mt-2 list-disc space-y-1 ps-5">
                        <li>{t('dashUi.donor.reliabilityRuleStart')}</li>
                        <li>{t('dashUi.donor.reliabilityRuleUp')}</li>
                        <li>{t('dashUi.donor.reliabilityRuleDown')}</li>
                        <li>{t('dashUi.donor.reliabilityRuleTiers')}</li>
                      </ul>
                    </InfoDisclosure>
                  }
                  className="h-full"
                />
              </Rise>
              <Rise delay={0.1}>
                <Stat label={t('donorDashboard.donationsMade')} value={donations} icon={<Award />} className="h-full" />
              </Rise>
              <Rise delay={0.15}>
                <Stat
                  label={t('dashUi.donor.livesHelped')}
                  value={optionalNumber(profile.livesHelpedEstimate)}
                  hint={t('dashUi.donor.livesHelpedHint')}
                  tone="primary"
                  icon={<Users />}
                  className="h-full"
                />
              </Rise>
            </div>
          )}

          {profile && (
            <Rise delay={0.08} className="mb-6">
              <EligibilityCard />
            </Rise>
          )}

          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_21rem]">
            <Rise delay={0.1}>
              <form onSubmit={handleSave} noValidate className={`${CARD} space-y-9 p-5 sm:p-8`}>
                <section className="space-y-5">
                  <SectionLabel>{t('dashUi.section.health')}</SectionLabel>
                  <Select
                    id="donor-blood-type"
                    label={t('dashUi.donor.bloodType')}
                    options={bloodTypeOptions}
                    value={bloodType}
                    onChange={(e) => setBloodType(e.target.value)}
                  />

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <Input
                        id="donor-dob"
                        type="date"
                        label={t('dash.dob')}
                        value={dateOfBirth}
                        min={yearsAgo(MAX_AGE + 1)}
                        max={yearsAgo(MIN_AGE)}
                        onChange={(e) => setDateOfBirth(e.target.value)}
                        error={fieldErrors.dob}
                        aria-describedby={fieldErrors.dob ? undefined : 'donor-dob-hint'}
                      />
                      {!fieldErrors.dob && (
                        <p id="donor-dob-hint" className="mt-1.5 text-sm leading-relaxed text-gray-500">
                          {t('dash.dobHint')}
                        </p>
                      )}
                    </div>
                    <div>
                      <Input
                        id="donor-weight"
                        type="number"
                        step="0.1"
                        min="30"
                        max="300"
                        inputMode="decimal"
                        label={t('dash.weight')}
                        value={weightKg}
                        onChange={(e) => setWeightKg(e.target.value)}
                        error={fieldErrors.weight}
                        aria-describedby={fieldErrors.weight ? undefined : 'donor-weight-hint'}
                      />
                      {!fieldErrors.weight && (
                        <p id="donor-weight-hint" className="mt-1.5 text-sm leading-relaxed text-gray-500">
                          {t('dash.weightHint')}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                <section className="space-y-5">
                  <SectionLabel>{t('dashUi.section.location')}</SectionLabel>
                  <Select
                    id="donor-district"
                    label={t('request.district')}
                    options={districtOptions}
                    value={district}
                    onChange={(e) => handleDistrictChange(e.target.value)}
                  />

                  {/* Location is its own explicit control, separate from availability */}
                  <div className="flex flex-col justify-between gap-3 rounded-xl border border-line bg-surface-raised p-4 sm:flex-row sm:items-center">
                    <div className="flex min-w-0 items-start gap-3">
                      <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
                      <div className="min-w-0 text-sm leading-relaxed text-gray-400">
                        <p>{locationText}</p>
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

                {/* Save bar: pinned to the bottom of the screen on mobile */}
                <div className="sticky bottom-3 z-20 rounded-2xl border border-line-strong bg-surface-raised p-2 shadow-lg sm:static sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0 sm:shadow-none">
                  <Button type="submit" size="lg" className="w-full" loading={saving}>
                    {t('common.save')}
                  </Button>
                </div>
              </form>
            </Rise>

            <Rise delay={0.15}>
              <Panel>
                <SectionLabel>{t('dashUi.section.preferences')}</SectionLabel>
                <div className="mt-2 divide-y divide-line">
                  <SettingRow
                    id="donor-available"
                    icon={<HandHeart />}
                    title={t('dashUi.donor.availableToDonate')}
                    description={profile ? t('dashUi.donor.availableDesc') : t('dashUi.donor.availableAfterSave')}
                    checked={isAvailable}
                    onChange={toggleAvailability}
                    disabled={availabilityBusy}
                  />
                  <SettingRow
                    id="donor-low-data"
                    icon={lowData ? <WifiOff /> : <Wifi />}
                    title={t('donorDashboard.lowDataMode')}
                    description={lowData ? t('dashUi.lowDataOnDesc') : t('donorDashboard.lowDataModeOff')}
                    checked={lowData}
                    onChange={setLowData}
                  />
                </div>
              </Panel>
            </Rise>
          </div>

          {profile && (
            <div className="mt-12 space-y-12">
              <Rise inView>
                <DonationHistory />
              </Rise>
              <Rise inView>
                <SectionHeader eyebrow={t('dashUi.section.schedule')} title={t('slot.myBookings')} />
                <MyBookings />
              </Rise>
            </div>
          )}
        </>
      )}
    </div>
  );
}
