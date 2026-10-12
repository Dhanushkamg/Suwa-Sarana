'use client';

import { useState, useEffect, useCallback } from 'react';
import { Droplets, Shield, MapPin, CheckCircle, ArrowLeft, Award, Users, Wifi, WifiOff, AlertCircle, CalendarCheck, LocateFixed } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { DISTRICTS, districtCenter } from '@/lib/districts';
import MyBookings from '@/components/slots/MyBookings';

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
  const reliabilityColor =
    reliabilityScore >= 80 ? 'text-emerald-400' :
    reliabilityScore >= 60 ? 'text-amber-400' : 'text-red-400';
  const reliabilityBg =
    reliabilityScore >= 80 ? 'from-emerald-500 to-teal-600' :
    reliabilityScore >= 60 ? 'from-amber-500 to-orange-600' : 'from-red-500 to-rose-600';
  const reliabilityLabel =
    reliabilityScore >= 80 ? t('donorDashboard.excellentStanding') :
    reliabilityScore >= 60 ? t('donorDashboard.goodStanding') : t('donorDashboard.needsImprovement');

  const locationText =
    coordSource === 'device' ? t('dash.locationDevice') :
    coordSource === 'saved' ? t('dash.locationSaved') :
    t('dash.locationDistrict', { district });

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4">
          <ArrowLeft className="w-4 h-4" />
          {t('common.back')}
        </Link>
        <h1 className="text-3xl font-bold text-white flex items-center gap-3">
          <Droplets className="w-8 h-8 text-red-500" />
          {t('common.myProfile')}
        </h1>
        <p className="text-gray-400 mt-2">{t('dashboard.donorProfileDesc')}</p>
      </div>

      {saved && (
        <div className="mb-6 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4 flex items-center gap-3 text-emerald-400">
          <CheckCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{t('donorDashboard.profileSaved')}</span>
        </div>
      )}

      {errorMessage && (
        <div className="mb-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{errorMessage}</span>
        </div>
      )}

      {initialLoading ? (
        <div className="space-y-4" role="status" aria-label={t('common.loading')}>
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-96 w-full" />
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
            <div className="mb-6 rounded-2xl border border-blue-500/30 bg-blue-500/10 p-4 flex items-center gap-3 text-blue-300">
              <Droplets className="w-5 h-5 flex-shrink-0" />
              <span className="text-sm">{t('dash.newProfile')}</span>
            </div>
          )}

          {/* Gamification Stats (hidden in Low-Data Mode) */}
          {!lowDataMode && profile && (
            <div className="grid grid-cols-3 gap-2 sm:gap-4 mb-6">
              <div className="glass-card rounded-2xl p-3 sm:p-5 border border-white/5 text-center">
                <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${reliabilityBg} flex items-center justify-center shadow-lg mx-auto mb-3`}>
                  <Shield className="w-6 h-6 text-white" />
                </div>
                <div className={`text-2xl font-bold ${reliabilityColor}`}>{reliabilityScore}</div>
                <div className="text-xs text-gray-500 mt-1">{t('features.reliability')}</div>
                <span className={`text-xs px-2 py-1 rounded-full mt-2 inline-block bg-white/5 font-medium ${reliabilityColor}`}>
                  {reliabilityLabel}
                </span>
              </div>

              <div className="glass-card rounded-2xl p-3 sm:p-5 border border-white/5 text-center">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/20 mx-auto mb-3">
                  <Award className="w-6 h-6 text-white" />
                </div>
                <div className="text-2xl font-bold text-white">{profile.totalDonations ?? 0}</div>
                <div className="text-xs text-gray-500 mt-1">{t('donorDashboard.donationsMade')}</div>
              </div>

              <div className="glass-card rounded-2xl p-3 sm:p-5 border border-white/5 text-center">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-purple-500 to-violet-600 flex items-center justify-center shadow-lg shadow-purple-500/20 mx-auto mb-3">
                  <Users className="w-6 h-6 text-white" />
                </div>
                <div className="text-2xl font-bold text-white">{profile.livesHelpedEstimate ?? 0}</div>
                <div className="text-xs text-gray-500 mt-1">{t('donorDashboard.livesHelped')}</div>
              </div>
            </div>
          )}

          <form onSubmit={handleSave} noValidate className="glass-card rounded-2xl p-5 sm:p-8 space-y-6">
            <Select
              id="donor-blood-type"
              label={t('dash.yourBloodType')}
              options={bloodTypeOptions}
              value={bloodType}
              onChange={(e) => setBloodType(e.target.value)}
            />

            <div className="grid sm:grid-cols-2 gap-4">
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
                {!fieldErrors.dob && <p className="mt-1.5 text-xs text-gray-500">{t('dash.dobHint')}</p>}
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
                {!fieldErrors.weight && <p className="mt-1.5 text-xs text-gray-500">{t('dash.weightHint')}</p>}
              </div>
            </div>

            <Select
              id="donor-district"
              label={t('request.district')}
              options={districtOptions}
              value={district}
              onChange={(e) => handleDistrictChange(e.target.value)}
            />

            <div className="rounded-xl bg-white/4 p-4 border border-white/5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-start gap-3 min-w-0">
                <MapPin className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" />
                <div className="text-xs text-gray-400 min-w-0">
                  <p>{locationText}</p>
                  {locationNote && <p className="mt-1 text-amber-400">{locationNote}</p>}
                </div>
              </div>
              <button
                type="button"
                onClick={detectLocation}
                className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-medium text-red-300 transition-colors flex-shrink-0"
              >
                <LocateFixed className="w-3.5 h-3.5" />
                {t('dash.useMyLocation')}
              </button>
            </div>

            {/* Availability Toggle */}
            <div className="flex items-center justify-between gap-3 p-4 rounded-xl bg-white/4 border border-white/5">
              <div className="flex items-center gap-3">
                <MapPin className="w-5 h-5 text-red-400" />
                <div>
                  <div className="text-sm font-medium text-white">{t('dashboard.updateAvailability')}</div>
                  <div className="text-xs text-gray-500">{t('dashboard.updateAvailabilityDesc')}</div>
                </div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={isAvailable}
                aria-label={t('dashboard.updateAvailability')}
                onClick={toggleAvailability}
                className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition-colors focus:outline-none ${
                  isAvailable ? 'bg-red-500' : 'bg-gray-700'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    isAvailable ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>

            {/* Low-Data Mode Toggle */}
            <div className="flex items-center justify-between gap-3 p-4 rounded-xl bg-white/4 border border-white/5">
              <div className="flex items-center gap-3">
                {lowDataMode ? (
                  <WifiOff className="w-5 h-5 text-amber-400" />
                ) : (
                  <Wifi className="w-5 h-5 text-blue-400" />
                )}
                <div>
                  <div className="text-sm font-medium text-white">{t('donorDashboard.lowDataMode')}</div>
                  <div className="text-xs text-gray-500">
                    {lowDataMode ? t('donorDashboard.lowDataModeOn') : t('donorDashboard.lowDataModeOff')}
                  </div>
                </div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={lowDataMode}
                aria-label={t('donorDashboard.lowDataMode')}
                onClick={toggleLowDataMode}
                className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition-colors focus:outline-none ${
                  lowDataMode ? 'bg-amber-500' : 'bg-gray-700'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    lowDataMode ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>

            <div className="pt-2">
              <Button type="submit" size="lg" className="w-full" loading={saving}>
                {t('common.save')}
              </Button>
            </div>
          </form>

          {profile && (
            <>
              <div className="mt-8 pt-8 border-t border-white/10">
                <h2 className="text-2xl font-bold text-white flex items-center gap-2 mb-6">
                  <CalendarCheck className="w-6 h-6 text-emerald-500" />
                  {t('slot.myBookings')}
                </h2>
                <MyBookings />
              </div>

              <div className="mt-8 text-center">
                <Link href="/dashboard/donor/matches" className="text-sm text-red-400 hover:text-red-300 transition-colors font-medium">
                  {t('donorDashboard.viewMyMatches')} →
                </Link>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
