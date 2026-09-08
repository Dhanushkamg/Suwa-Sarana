'use client';

import { useState, useEffect } from 'react';
import { Droplets, Shield, MapPin, CheckCircle, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';

const DISTRICTS = [
  'Colombo', 'Gampaha', 'Kalutara', 'Kandy', 'Matale', 'Nuwara Eliya',
  'Galle', 'Matara', 'Hambantota', 'Jaffna', 'Kilinochchi', 'Mannar',
  'Vavuniya', 'Mullaitivu', 'Batticaloa', 'Ampara', 'Trincomalee',
  'Kurunegala', 'Puttalam', 'Anuradhapura', 'Polonnaruwa', 'Badulla',
  'Monaragala', 'Ratnapura', 'Kegalle'
];

export default function DonorProfilePage() {
  const { t } = useI18n();

  const [bloodType, setBloodType] = useState('O+');
  const [district, setDistrict] = useState('Colombo');
  const [isAvailable, setIsAvailable] = useState(true);
  const [reliabilityScore] = useState(50.0);
  const [loading, setLoading] = useState(false);
  const [saved, setSaved] = useState(false);

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
    async function loadProfile() {
      try {
        const res = await apiClient.get<{ bloodType: string; district: string; available: boolean; reliabilityScore: number }>('/donors/profile');
        if (res.data) {
          setBloodType(res.data.bloodType || 'O+');
          setDistrict(res.data.district || 'Colombo');
          setIsAvailable(res.data.available !== false);
        }
      } catch {
        // Fallback to default if not yet created
      }
    }
    loadProfile();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSaved(false);

    try {
      await apiClient.post('/donors/profile', {
        bloodType,
        district,
        available: isAvailable,
        latitude: 6.9271,
        longitude: 79.8612,
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch {
      // Handle error gracefully
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <Link
          href="/dashboard"
          className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
        >
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
          <span className="text-sm font-medium">{t('common.save')} successfully!</span>
        </div>
      )}

      {/* Reliability score badge */}
      <div className="glass-card rounded-2xl p-6 mb-6 flex items-center justify-between border-red-500/10">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center shadow-lg shadow-orange-500/20">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <div>
            <div className="text-sm font-medium text-gray-400">{t('features.reliability')}</div>
            <div className="text-2xl font-bold text-white">{reliabilityScore} / 100</div>
          </div>
        </div>
        <span className="text-xs px-3 py-1.5 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-300 font-medium">
          Good Standing
        </span>
      </div>

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

        <div className="pt-2">
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </div>
  );
}
