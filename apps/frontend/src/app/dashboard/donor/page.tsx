'use client';

import { useState, useEffect, useCallback } from 'react';
import { Droplets, Shield, MapPin, Award, Users, Wifi, WifiOff, AlertCircle, LocateFixed, ArrowUpRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { DISTRICTS, districtCenter } from '@/lib/districts';
import MyBookings from '@/components/slots/MyBookings';
import { Reveal } from '@/components/motion/Reveal';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { SettingRow } from '@/components/dash/Switch';
import { Stat } from '@/components/dash/Stat';
import { StatsSkeleton } from '@/components/dash/Skeletons';

interface DonorProfile {
  bloodType: string;
  dateOfBirth?: string | null;
  weightKg?: number | null;
  district: string;
  latitude?: number | null;
  longitude?: number | null;
  available: boolean;
  reliabilityScore: number;
  totalDonations: number;
  livesHelpedEstimate: number;
}

interface ApiBody<T> {
  data?: T;
}

type CoordSource = 'saved' | 'device' | 'district';

const MIN_AGE = 18;
const MAX_AGE = 60;

function apiStatus(err: unknown): number | undefined {
  return (err as { response?: { status?: number } })?.response?.status;
}

function apiMessage(err: unknown): string | undefined {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
}

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

export default function DonorProfilePage() {
  const { t } = useI18n();

  const [bloodType, setBloodType] = useState('O+');
  const [district, setDistrict] = useState('Colombo');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [weightKg, setWeightKg] = useState('');
  const [isAvailable, setIsAvailable] = useState(true);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [coordSource, setCoordSource] = useState<CoordSource>('district');
  const [locationNote, setLocationNote] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<{ dob?: string; weight?: string }>({});

  const [profile, setProfile] = useState<DonorProfile | null>(null);
  const [saving, setSaving] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadMessage, setLoadMessage] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lowDataMode, setLowDataMode] = useState(false);

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

  const districtOptions = DISTRICTS.map((d) => ({ value: d, label: d }));

  const applyProfile = useCallback((p: DonorProfile) => {
    setProfile(p);
    setBloodType(p.bloodType || 'O+');
    setDistrict(p.district || 'Colombo');
    setDateOfBirth(p.dateOfBirth ?? '');
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
      const res = await apiClient.get<ApiBody<DonorProfile>>('/donors/me');
      if (res.data?.data) applyProfile(res.data.data);
    } catch (err: unknown) {
      const msg = apiMessage(err);
      // A brand-new donor has no profile yet: show the empty form instead of an error.
      if (apiStatus(err) === 404 || /not found/i.test(msg ?? '')) {
        setProfile(null);
      } else {
        setLoadFailed(true);
        setLoadMessage(msg ?? null);
      }
    } finally {
      setInitialLoading(false);
    }
  }, [applyProfile]);

  useEffect(() => {
    setLowDataMode(localStorage.getItem('lowDataMode') === 'true');
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

  const detectLocation = () => {
    setLocationNote(null);
    if (!('geolocation' in navigator)) {
      setLocationNote(t('dash.locationDenied'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({
          lat: Number(pos.coords.latitude.toFixed(6)),
          lng: Number(pos.coords.longitude.toFixed(6)),
        });
        setCoordSource('device');
      },
      () => setLocationNote(t('dash.locationDenied')),
      { timeout: 10000 }
    );
  };

  const toggleAvailability = async () => {
    const next = !isAvailable;
    setIsAvailable(next);
    if (!profile) return; // New donor: applied after the first save.
    setErrorMessage(null);
    try {
      await apiClient.patch('/donors/me/availability', { isAvailable: next });
      setProfile({ ...profile, available: next });
    } catch (err: unknown) {
      setIsAvailable(!next);
      setErrorMessage(apiMessage(err) || t('dash.availabilityFailed'));
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(false);
    setErrorMessage(null);

    const errors: { dob?: string; weight?: string } = {};
    if (!dateOfBirth) {
      errors.dob = t('dash.dobInvalid');
    } else {
      const age = ageFrom(dateOfBirth);
      if (age < MIN_AGE || age > MAX_AGE) errors.dob = t('dash.dobInvalid');
    }
    const weight = weightKg.trim() === '' ? null : Number(weightKg);
    if (weight !== null && (!Number.isFinite(weight) || weight < 30 || weight > 300)) {
      errors.weight = t('dash.weightInvalid');
    }
    setFieldErrors(errors);
    if (errors.dob || errors.weight) return;

    const [fallbackLat, fallbackLng] = districtCenter(district);
    const isNew = !profile;
    setSaving(true);
    try {
      const res = await apiClient.put<ApiBody<DonorProfile>>('/donors/me', {
        bloodType,
        dateOfBirth,
        weightKg: weight,
        district,
        latitude: coords?.lat ?? fallbackLat,
        longitude: coords?.lng ?? fallbackLng,
      });
      // New profiles start as available on the server; honour an "unavailable" choice made beforehand.
      if (isNew && !isAvailable) {
        await apiClient.patch('/donors/me/availability', { isAvailable: false });
      }
      if (res.data?.data) {
        applyProfile({ ...res.data.data, available: isNew ? isAvailable : res.data.data.available });
      }
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err: unknown) {
      setErrorMessage(apiMessage(err) || t('donorDashboard.updateError'));
    } finally {
      setSaving(false);
    }
  };

  const toggleLowDataMode = () => {
    const newMode = !lowDataMode;
    setLowDataMode(newMode);
    localStorage.setItem('lowDataMode', String(newMode));
  };

  const reliabilityScore = Number(profile?.reliabilityScore ?? 50);
  const reliabilityTone = reliabilityScore >= 80 ? 'sage' : reliabilityScore >= 60 ? 'saffron' : 'ember';
  const reliabilityLabel =
    reliabilityScore >= 80 ? t('donorDashboard.excellentStanding') :
    reliabilityScore >= 60 ? t('donorDashboard.goodStanding') : t('donorDashboard.needsImprovement');

  const locationText =
    coordSource === 'device' ? t('dash.locationDevice') :
    coordSource === 'saved' ? t('dash.locationSaved') :
    t('dash.locationDistrict', { district });

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.profile')}
        title={t('common.myProfile')}
        description={t('dashboard.donorProfileDesc')}
        actions={
          profile ? (
            <ButtonLink href="/dashboard/donor/matches" variant="secondary">
              {t('donorDashboard.viewMyMatches')}
              <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
            </ButtonLink>
          ) : undefined
        }
      />

      <div className="mb-6 space-y-4 empty:hidden">
        {saved && <Notice tone="success">{t('donorDashboard.profileSaved')}</Notice>}
        {errorMessage && <Notice tone="error">{errorMessage}</Notice>}
      </div>

      {initialLoading ? (
        <div className="space-y-6">
          <StatsSkeleton count={3} className="sm:grid-cols-3" />
          <Skeleton className="h-96 w-full rounded-2xl" />
        </div>
      ) : loadFailed ? (
        <EmptyState
          icon={<AlertCircle className="w-10 h-10 text-red-400" />}
          title={loadMessage || t('donorDashboard.profileError')}
          action={<Button variant="secondary" onClick={loadProfile}>{t('dash.retry')}</Button>}
        />
      ) : (
        <>
          {!profile && (
            <Notice tone="info" icon={<Droplets />} className="mb-6">
              {t('dash.newProfile')}
            </Notice>
          )}

          {/* Donor stats (hidden in Low-Data Mode) */}
          {!lowDataMode && profile && (
            <div className="mb-6 grid gap-4 sm:grid-cols-3">
              <Rise delay={0.05}>
                <Stat
                  label={t('features.reliability')}
                  value={reliabilityScore}
                  tone={reliabilityTone}
                  meter={reliabilityScore}
                  icon={<Shield />}
                  hint={reliabilityLabel}
                  className="h-full"
                />
              </Rise>
              <Rise delay={0.1}>
                <Stat label={t('donorDashboard.donationsMade')} value={profile.totalDonations ?? 0} icon={<Award />} className="h-full" />
              </Rise>
              <Rise delay={0.15}>
                <Stat label={t('donorDashboard.livesHelped')} value={profile.livesHelpedEstimate ?? 0} tone="ember" icon={<Users />} className="h-full" />
              </Rise>
            </div>
          )}

          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_21rem]">
            <Rise delay={0.1}>
              <form onSubmit={handleSave} noValidate className="glass-card space-y-9 rounded-2xl p-5 sm:p-8">
                <section className="space-y-5">
                  <SectionLabel>{t('dashUi.section.health')}</SectionLabel>
                  <Select
                    id="donor-blood-type"
                    label={t('dash.yourBloodType')}
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
                      />
                      {!fieldErrors.dob && <p className="mt-1.5 text-xs leading-relaxed text-gray-500">{t('dash.dobHint')}</p>}
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
                      />
                      {!fieldErrors.weight && <p className="mt-1.5 text-xs leading-relaxed text-gray-500">{t('dash.weightHint')}</p>}
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

                  <div className="flex flex-col justify-between gap-3 rounded-xl border border-line bg-foreground/[0.03] p-4 sm:flex-row sm:items-center">
                    <div className="flex min-w-0 items-start gap-3">
                      <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-ember" aria-hidden="true" />
                      <div className="min-w-0 text-xs leading-relaxed text-gray-400">
                        <p>{locationText}</p>
                        {locationNote && <p className="mt-1 text-saffron">{locationNote}</p>}
                      </div>
                    </div>
                    <Button type="button" variant="secondary" size="sm" onClick={detectLocation} className="shrink-0">
                      <LocateFixed className="h-3.5 w-3.5" aria-hidden="true" />
                      {t('dash.useMyLocation')}
                    </Button>
                  </div>
                </section>

                {/* Save bar: pinned to the bottom of the screen on mobile */}
                <div className="sticky bottom-3 z-20 rounded-2xl border border-foreground/[0.16] bg-surface-raised p-2 shadow-2xl shadow-black/50 sm:static sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0 sm:shadow-none">
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
                    icon={<Droplets />}
                    title={t('dashboard.updateAvailability')}
                    description={t('dashboard.updateAvailabilityDesc')}
                    checked={isAvailable}
                    onChange={toggleAvailability}
                  />
                  <SettingRow
                    icon={lowDataMode ? <WifiOff /> : <Wifi />}
                    title={t('donorDashboard.lowDataMode')}
                    description={lowDataMode ? t('donorDashboard.lowDataModeOn') : t('donorDashboard.lowDataModeOff')}
                    checked={lowDataMode}
                    onChange={toggleLowDataMode}
                  />
                </div>
              </Panel>
            </Rise>
          </div>

          {profile && (
            <Reveal y={12} className="mt-12">
              <SectionHeader eyebrow={t('dashUi.section.schedule')} title={t('slot.myBookings')} />
              <MyBookings />
            </Reveal>
          )}
        </>
      )}
    </div>
  );
}
