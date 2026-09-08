'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Heart, MapPin, AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
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

export default function NewBloodRequestPage() {
  const router = useRouter();
  const { t } = useI18n();

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
    { value: 'CRITICAL', label: t('urgency.CRITICAL') },
  ];

  const districtOptions = DISTRICTS.map((d) => ({ value: d, label: d }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
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
        router.push('/dashboard');
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
          // Keep default Colombo coordinates if denied
        }
      );
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
          <Heart className="w-8 h-8 text-red-500 fill-red-500" />
          {t('request.title')}
        </h1>
        <p className="text-gray-400 mt-2">{t('request.subtitle')}</p>
      </div>

      {success && (
        <div className="mb-6 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-5 flex items-center gap-3 text-emerald-400">
          <CheckCircle2 className="w-6 h-6 flex-shrink-0" />
          <div>
            <p className="font-semibold">{t('request.successMessage')}</p>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-5 flex items-center gap-3 text-red-400">
          <AlertCircle className="w-6 h-6 flex-shrink-0" />
          <div>
            <p className="font-semibold">{error}</p>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="glass-card rounded-2xl p-8 space-y-6">
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

        <Select
          id="urgency"
          label={t('request.urgencyLevel')}
          options={urgencyOptions}
          value={form.urgency}
          onChange={(e) => setForm({ ...form, urgency: e.target.value })}
        />

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
          <Button type="submit" size="lg" className="w-full" loading={loading} disabled={success}>
            {t('request.submit')}
          </Button>
        </div>
      </form>
    </div>
  );
}
