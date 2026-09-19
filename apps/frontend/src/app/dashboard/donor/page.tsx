'use client';

import { useState, useEffect } from 'react';
import { Droplets, Shield, MapPin, CheckCircle, ArrowLeft, Award, Users, Wifi, WifiOff, AlertCircle, CalendarCheck } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import MyBookings from '@/components/slots/MyBookings';

const DISTRICTS = [
  'Colombo', 'Gampaha', 'Kalutara', 'Kandy', 'Matale', 'Nuwara Eliya',
  'Galle', 'Matara', 'Hambantota', 'Jaffna', 'Kilinochchi', 'Mannar',
  'Vavuniya', 'Mullaitivu', 'Batticaloa', 'Ampara', 'Trincomalee',
  'Kurunegala', 'Puttalam', 'Anuradhapura', 'Polonnaruwa', 'Badulla',
  'Monaragala', 'Ratnapura', 'Kegalle'
];

type DonorProfile = {
  bloodType: string;
  district: string;
  available: boolean;
  reliabilityScore: number;
  totalDonations: number;
  livesHelpedEstimate: number;
};

export default function DonorProfilePage() {
  const { t } = useI18n();

  const [bloodType, setBloodType] = useState('O+');
  const [district, setDistrict] = useState('Colombo');
  const [isAvailable, setIsAvailable] = useState(true);
  const [profile, setProfile] = useState<DonorProfile | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
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

  useEffect(() => {
    const savedLowData = localStorage.getItem('lowDataMode') === 'true';
    setLowDataMode(savedLowData);

    async function loadProfile() {
      setInitialLoading(true);
      setErrorMessage(null);
      try {
        const res = await apiClient.get<any>('/donors/me');
        const profileData = res.data?.data ?? res.data;
        if (profileData) {
          setProfile(profileData);
          setBloodType(profileData.bloodType || 'O+');
          setDistrict(profileData.district || 'Colombo');
          setIsAvailable(profileData.available !== false);
        }
      } catch (err: unknown) {
        const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
          || t('donorDashboard.profileError');
        setErrorMessage(msg);
      } finally {
        setInitialLoading(false);
      }
    }
    loadProfile();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSaved(false);
    setErrorMessage(null);

    try {
      await apiClient.put('/donors/me', {
        bloodType,
        district,
        available: isAvailable,
        latitude: 6.9271,
        longitude: 79.8612,
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || t('donorDashboard.updateError');
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  const toggleLowDataMode = () => {
    const newMode = !lowDataMode;
    setLowDataMode(newMode);
    localStorage.setItem('lowDataMode', String(newMode));
  };

  const reliabilityScore = profile?.reliabilityScore ?? 50;
  const reliabilityColor =
    reliabilityScore >= 80 ? 'text-emerald-400' :
    reliabilityScore >= 60 ? 'text-amber-400' : 'text-red-400';
  const reliabilityBg =
    reliabilityScore >= 80 ? 'from-emerald-500 to-teal-600' :
    reliabilityScore >= 60 ? 'from-amber-500 to-orange-600' : 'from-red-500 to-rose-600';
  const reliabilityLabel =
    reliabilityScore >= 80 ? t('donorDashboard.excellentStanding') :
    reliabilityScore >= 60 ? t('donorDashboard.goodStanding') : t('donorDashboard.needsImprovement');

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
        <div className="glass-card rounded-2xl p-12 text-center text-gray-400">
          <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
          <p>{t('common.loading')}</p>
        </div>
      ) : (
        <>
          {/* Gamification Stats (hidden in Low-Data Mode) */}
          {!lowDataMode && (
            <div className="grid grid-cols-3 gap-4 mb-6">
              <div className="glass-card rounded-2xl p-5 border border-white/5 text-center">
                <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${reliabilityBg} flex items-center justify-center shadow-lg mx-auto mb-3`}>
                  <Shield className="w-6 h-6 text-white" />
                </div>
                <div className={`text-2xl font-bold ${reliabilityColor}`}>{reliabilityScore}</div>
                <div className="text-xs text-gray-500 mt-1">{t('features.reliability')}</div>
                <span className={`text-xs px-2 py-1 rounded-full mt-2 inline-block bg-white/5 font-medium ${reliabilityColor}`}>
                  {reliabilityLabel}
                </span>
              </div>

              <div className="glass-card rounded-2xl p-5 border border-white/5 text-center">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/20 mx-auto mb-3">
                  <Award className="w-6 h-6 text-white" />
                </div>
                <div className="text-2xl font-bold text-white">{profile?.totalDonations ?? 0}</div>
                <div className="text-xs text-gray-500 mt-1">{t('donorDashboard.donationsMade')}</div>
              </div>

              <div className="glass-card rounded-2xl p-5 border border-white/5 text-center">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-purple-500 to-violet-600 flex items-center justify-center shadow-lg shadow-purple-500/20 mx-auto mb-3">
                  <Users className="w-6 h-6 text-white" />
                </div>
                <div className="text-2xl font-bold text-white">{profile?.livesHelpedEstimate ?? 0}</div>
                <div className="text-xs text-gray-500 mt-1">{t('donorDashboard.livesHelped')}</div>
              </div>
            </div>
          )}

          <form onSubmit={handleSave} className="glass-card rounded-2xl p-8 space-y-6">
            <Select
              id="donor-blood-type"
              label={t('request.patientBloodType')}
              options={bloodTypeOptions}
              value={bloodType}
              onChange={(e) => setBloodType(e.target.value)}
            />

            <Select
              id="donor-district"
              label={t('request.district')}
              options={districtOptions}
              value={district}
              onChange={(e) => setDistrict(e.target.value)}
            />

            {/* Availability Toggle */}
            <div className="flex items-center justify-between p-4 rounded-xl bg-white/4 border border-white/5">
              <div className="flex items-center gap-3">
                <MapPin className="w-5 h-5 text-red-400" />
                <div>
                  <div className="text-sm font-medium text-white">{t('dashboard.updateAvailability')}</div>
                  <div className="text-xs text-gray-500">{t('dashboard.updateAvailabilityDesc')}</div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsAvailable(!isAvailable)}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ${
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
            <div className="flex items-center justify-between p-4 rounded-xl bg-white/4 border border-white/5">
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
                onClick={toggleLowDataMode}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ${
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
              <Button type="submit" size="lg" className="w-full" loading={loading}>
                {t('common.save')}
              </Button>
            </div>
          </form>

          <div className="mt-8 pt-8 border-t border-white/10">
            <h2 className="text-2xl font-bold text-white flex items-center gap-2 mb-6">
              <CalendarCheck className="w-6 h-6 text-emerald-500" />
              {t('slot.myBookings') || 'My Bookings'}
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
    </div>
  );
}
